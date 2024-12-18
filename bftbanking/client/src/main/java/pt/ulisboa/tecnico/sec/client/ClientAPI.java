package pt.ulisboa.tecnico.sec.client;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc.ServerServiceBlockingStub;
import pt.ulisboa.tecnico.sec.server.grpc.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

import pt.ulisboa.tecnico.sec.crypto.Crypto;

public class ClientAPI {
	private static float MIN_QUORUM;
	private static ArrayList<ServerServiceBlockingStub> servers;
	private static ArrayList<ManagedChannel> channels;
	private static int numServers;
	private static int numFaults;
	private static long wts;

	public static ArrayList<ServerServiceBlockingStub> init(String host, int basePort, int N, int f) {
		numServers = N;
		numFaults = f;
		wts = 0;
		servers = new ArrayList<>();
		channels = new ArrayList<>();

		MIN_QUORUM = (numServers+numFaults)/2;

		for(int i = 0; i < N; i++){
			int port = basePort + i;
			String target = host + ":" + port;
			ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
			ServerServiceBlockingStub stub = ServerServiceGrpc.newBlockingStub(channel);

			channels.add(channel);
			servers.add(stub);
		}
		return servers; // useful for ByzantineAPITests
	}

	public static void close(){
		for(ManagedChannel channel:channels)
			channel.shutdown();
	}

	public static void validateSignature(PublicKey encodedPubKey, byte[] signedMessage, byte[] message)
			throws NoSuchAlgorithmException, SignatureException,InvalidKeyException{

		if(!Crypto.verifySignature(encodedPubKey, signedMessage, message))
			throw new SignatureException();
	}

	public static long get_nonce(PublicKey pubKey, PrivateKey privKey, ServerServiceBlockingStub server) throws StatusRuntimeException, NoSuchAlgorithmException,InvalidKeySpecException, InvalidKeyException, SignatureException  {
		PublicKey server_key = getServerKey(server);

		NonceRequest.Data data = NonceRequest.Data.newBuilder().setAccKey(Crypto.getEncodedKey(pubKey)).build();
		ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
		NonceRequest request = NonceRequest.newBuilder().setData(data).setSignature(signature).build();

		
		NonceResponse response = server.generateNonce(request);

		validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());

		return response.getData().getValue();
	}
	public static PublicKey getServerKey(ServerServiceBlockingStub server) throws StatusRuntimeException, NoSuchAlgorithmException, InvalidKeySpecException {

		ServerKeyRequest request = ServerKeyRequest.newBuilder().build();
		ServerKeyResponse response = server.getServerKey(request);

		return Crypto.getPublicKey(response.getPublicKey());
	}

	public static PublicKey get_public_key(long userID) throws StatusRuntimeException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, InvalidKeyException{
		PublicKey server_key = getServerKey(servers.get(0));

		PublicKeyRequest request = PublicKeyRequest.newBuilder().setAccountID(userID).build();
		PublicKeyResponse response = servers.get(0).getPublicKeyByID(request);

		validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());

		return Crypto.getPublicKey(response.getData().getPublicKey());
	}

	public static long computeProofOfWork(byte[] message) throws IOException, NoSuchAlgorithmException {
		for(long pow = 0L;; pow++) {
			ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
			byteArray.write(message);
			byteArray.write(Longs.toByteArray(pow));
			byte[] digest = Crypto.hash(byteArray.toByteArray());

			// a valide proof of works contains first two bytes set to 0 (2 byte = 16 bits = 2^16 possible values)
			if (digest[0] == 0x0 && digest[1] == 0x0)
				return pow;
		}
	}

	/* ---------------------------------- */
	/* ------ main bank operations ------ */
	/* ---------------------------------- */

	/*
	create a new account in the system with the associated public key,
	before first use. In particular, it should make the necessary initializations to
	enable the first use of the BFTB system. The account should start with a pre-
	defined positive balance.
	 */
	public static long open_account(PublicKey pubKey, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		ByteString encodedKey = Crypto.getEncodedKey(pubKey);

		// Authenticated-Data Byzantine Quorum with (1, N) Byzantine Atomic Register
		update_wts(pubKey, privKey);
		HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
		ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
		for (ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
			try {
				long nonce = get_nonce(pubKey, privKey, server);
				OpenAccountRequest.Data data = OpenAccountRequest.Data.newBuilder().setKey(encodedKey).setTimestamp(wts).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				long pow = computeProofOfWork(data.toByteArray());
				OpenAccountRequest request = OpenAccountRequest.newBuilder().setData(data).setSignature(signature).setProofOfWork(pow).build();

				OpenAccountResponse response = server.openAccount(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());
				addToReadlist(readlist, response.getData().getAccountID(), response.getData().getTimestamp());
			} catch (StatusRuntimeException e) {
				exceptions.add(e);
			} catch (InvalidKeySpecException | IOException e) {
				exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
			}
		}

		return getQuorumResponse(readlist, exceptions);
	}


	/*
	send a given amount from account source to account destination,
	if the balance of the source allows it. If the server responds positively to this
	invocation, it must be guaranteed that the source has the authority to perform
	the transfer. The transfer will only be finalized when the destination approves
	it via the receive_amount() method defined below.
	 */
	public static long send_amount(PublicKey srcPublicKey, PublicKey destPublicKey, int amount, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		ByteString encodedSrcKey = Crypto.getEncodedKey(srcPublicKey);
		ByteString encodedDestKey = Crypto.getEncodedKey(destPublicKey);


		// Authenticated-Data Byzantine Quorum with (1, N) Byzantine Atomic Register
		update_wts(srcPublicKey, privKey);
		HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
		ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
		for (ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
			try {
				long nonce = get_nonce(srcPublicKey, privKey, server);
				SendAmountRequest.Data data = SendAmountRequest.Data.newBuilder().setSrcKey(encodedSrcKey)
						.setDestKey(encodedDestKey).setAmount(amount).setTimestamp(wts).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				long pow = computeProofOfWork(data.toByteArray());
				SendAmountRequest request = SendAmountRequest.newBuilder().setData(data).setSignature(signature).setProofOfWork(pow).build();
				SendAmountResponse response = server.sendAmount(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());
				addToReadlist(readlist, response.getData().getTID(), response.getData().getTimestamp());
			} catch (StatusRuntimeException e) {
				exceptions.add(e);
			} catch (InvalidKeySpecException | IOException e) {
				exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
			}
		}

		return getQuorumResponse(readlist, exceptions);
	}

	/*
	obtain the balance of the account associated with the key passed
	as input. This method also returns the list of pending incoming transfers that
	require approval by the account’s owner, if any.
	 */
	public static HashMap<String, Object> check_account(PublicKey key, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		ByteString encodedKey = Crypto.getEncodedKey(key);
		HashMap<String, Object> acc_info = new HashMap<String, Object>();
		ArrayList<Transfer> pendingTransfers = new ArrayList<Transfer>();
		acc_info.put("pendingTransfers", pendingTransfers);

		// Authenticated-Data Byzantine Quorum with (1, N) Byzantine Atomic Register
		HashMap<Long, ArrayList<CheckAccountResponse>> readlist = new HashMap<>();
		HashMap<ServerServiceBlockingStub, Long> serverTS = new HashMap<>();
		ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
		for (ServerServiceBlockingStub server : servers) {
			try {
				long nonce = get_nonce(key, privKey, server);

				CheckAccountRequest.Data data = CheckAccountRequest.Data.newBuilder().setAccKey(encodedKey).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				CheckAccountRequest request = CheckAccountRequest.newBuilder().setData(data).setSignature(signature).build();
				CheckAccountResponse response = server.checkAccount(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());

				long ts = response.getData().getTimestamp();
				if(readlist.containsKey(ts)) {
					readlist.get(ts).add(response);
				}
				else{
					ArrayList<CheckAccountResponse> list = new ArrayList<>();
					list.add(response);
					readlist.put(ts, list);
				}
				serverTS.put(server, response.getData().getTimestamp());
			} catch (StatusRuntimeException e) {
				exceptions.add(e);
			} catch (InvalidKeySpecException e) {
				exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
			} finally {
				serverTS.put(server, -1L);
			}
		}

		CheckAccountResponse response = getCheckAccountQuorumResponse(readlist, serverTS, exceptions, key, privKey);
		writeBackCheckAccount(key, privKey, response, serverTS);

		acc_info.put("balance", response.getData().getBalance());
		for (TransactionMessage m: response.getData().getTransactionsList()){
			pendingTransfers.add(new Transfer(m.getSrcKey().toByteArray(), m.getDestKey().toByteArray(), m.getAmount()));
		}
		return acc_info;
	}

	/*
	used by the recipient of a transfer to accept in a non-repudiable
	way a pending incoming transfer that must have been previously authorized by
	the sender.
	 */
	public static int receive_amount(PublicKey key, long TID, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		ByteString encodedKey = Crypto.getEncodedKey(key);

		// Authenticated-Data Byzantine Quorum with Byzantine Atomic Register
		update_wts(key, privKey);
		HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
		ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
		for (ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
			try {
				long nonce = get_nonce(key, privKey, server);
				ReceiveAmountRequest.Data data = ReceiveAmountRequest.Data.newBuilder().setAccKey(encodedKey)
						.setTID(TID).setTimestamp(wts).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				long pow = computeProofOfWork(data.toByteArray());
				ReceiveAmountRequest request = ReceiveAmountRequest.newBuilder().setData(data).setSignature(signature).setProofOfWork(pow).build();
				ReceiveAmountResponse response = server.receiveAmount(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());
				addToReadlist(readlist, (long) response.getData().getAmount(), response.getData().getTimestamp());

			} catch (StatusRuntimeException e) {
				exceptions.add(e);
			} catch (InvalidKeySpecException | IOException e) {
				exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
			}
		}

		return (int) getQuorumResponse(readlist, exceptions);
	}

	/*
	obtain the full transaction history of the account associated with
	the key passed as input. The returned history should reflect the order of
	execution of operations
	 */
	public static Iterable<Transfer> audit(PublicKey key, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		ByteString encodedKey = Crypto.getEncodedKey(key);
		ArrayList<Transfer> acc_trans = new ArrayList<Transfer>();

		// Authenticated-Data Byzantine Quorum with (1, N) Byzantine Atomic Register
		HashMap<Long, ArrayList<AuditResponse>> readlist = new HashMap<>();
		HashMap<ServerServiceBlockingStub, Long> serverTS = new HashMap<>();
		ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
		for (ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
			try {
				long nonce = get_nonce(key, privKey, server);

				AuditRequest.Data data = AuditRequest.Data.newBuilder().setAccKey(encodedKey).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				AuditRequest request = AuditRequest.newBuilder().setData(data).setSignature(signature).build();
				AuditResponse response = server.audit(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());

				long ts = response.getData().getTimestamp();
				if(readlist.containsKey(ts)) {
					readlist.get(ts).add(response);
				}
				else{
					ArrayList<AuditResponse> list = new ArrayList<>();
					list.add(response);
					readlist.put(ts, list);
				}
				serverTS.put(server, response.getData().getTimestamp());

			} catch (StatusRuntimeException e) {
				exceptions.add(e);
			} catch (InvalidKeySpecException e) {
				exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
			}
		}

		AuditResponse response = getAuditQuorumResponse(readlist, exceptions);
		writeBackAudit(key, privKey, response, serverTS);

		for (TransactionMessage m: response.getData().getTransactionsList()){
			acc_trans.add(new Transfer(m.getSrcKey().toByteArray(), m.getDestKey().toByteArray(), m.getAmount()));
		}

		return acc_trans;
	}

	/* ----------------------------------------------------------- */
	/* ------ auxiliar methods of atomic register algorithm ------ */
	/* ----------------------------------------------------------- */

	public static  long update_wts(PublicKey pubKey, PrivateKey privKey) {
		long highestTS = 0;

		for (ServerServiceBlockingStub server : servers) {
			try {
				long nonce = get_nonce(pubKey, privKey, server);
				TimestampRequest.Data data = TimestampRequest.Data.newBuilder().setAccKey(Crypto.getEncodedKey(pubKey)).setNonce(nonce).build();
				ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
				TimestampRequest request = TimestampRequest.newBuilder().setData(data).setSignature(signature).build();

				TimestampResponse response = server.getTimestamp(request);

				PublicKey server_key = getServerKey(server);
				validateSignature(server_key,response.getSignature().toByteArray(), response.getData().toByteArray());

				long ts = response.getData().getValue();
				if (ts > highestTS) highestTS = ts;
			} catch (Exception e) {
				// ignore if request is unavailable
			}
		}
		if(highestTS > wts) wts = highestTS;
		wts++;
		return wts; // useful for ByzantineAPITests
	}

	public static HashMap<Long, ArrayList<Long>> addToReadlist(HashMap<Long, ArrayList<Long>> readlist, Long value, long ts){
		if(readlist.containsKey(ts)) {
			readlist.get(ts).add(value);
		}
		else{
			ArrayList<Long> list = new ArrayList<>();
			list.add(value);
			readlist.put(ts, list);
		}
		return readlist;
	}

	public static void verifyQuorumExceptions(ArrayList<StatusRuntimeException> exceptions){
		for(int index = 0; index < exceptions.size(); index++){
			int count = 0;
			StatusRuntimeException final_exception = exceptions.get(index);
			for(StatusRuntimeException e : exceptions){
				if(final_exception.getMessage().equals(e.getMessage()))
					count++;
			}
			if(count > MIN_QUORUM) throw final_exception; // found exceptions
		}
	}

	public static long getQuorumResponse(HashMap<Long, ArrayList<Long>> readlist, ArrayList<StatusRuntimeException> exceptions) throws StatusRuntimeException {
		int numResponses = 0;
		// get response value received from more than (N+f)/2 servers
		for(Long ts : readlist.keySet()) {
			if(readlist.get(ts).size() > MIN_QUORUM){
				for(int index = 0; index < readlist.get(ts).size(); index++){ // in the worst case, each index can have a different value
					int count = 0;
					long final_value = readlist.get(ts).get(index);
					for(Long value : readlist.get(ts)){
						if(value == final_value)
							count++;
					}

					if(count > MIN_QUORUM) // verify if response value already counter reaches the minimum quorum
						return final_value;


				}
				numResponses += readlist.get(ts).size();
			}
		}
		verifyQuorumExceptions(exceptions);
		throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("ERROR: insufficient server quorum. Got " + numResponses + " out of " + numServers + " servers."));
	}

	public static CheckAccountResponse getCheckAccountQuorumResponse(HashMap<Long, ArrayList<CheckAccountResponse>> readlist, HashMap<ServerServiceBlockingStub, Long> serverTS,
																	 ArrayList<StatusRuntimeException> exceptions, PublicKey pubKey, PrivateKey privKey) throws StatusRuntimeException {
		CheckAccountResponse final_response = null;

		int numResponses = 0;
		// get response value received from more than (N+f)/2 servers
		for(Long ts : readlist.keySet()) {
			if(readlist.get(ts).size() > MIN_QUORUM){
				for(int index = 0; index < readlist.get(ts).size(); index++){ // in the worst case, each index can have a different value
					int count = 0;
					final_response = readlist.get(ts).get(index);
					for(CheckAccountResponse response: readlist.get(ts)){
						if(response.getData().getBalance() == final_response.getData().getBalance() && response.getData().getTransactionsList().equals(final_response.getData().getTransactionsList()))
							count++;
					}
					if(count > MIN_QUORUM) break;
					final_response = null;
				}
			}
			numResponses += readlist.get(ts).size();
		}

		if(final_response == null){
			verifyQuorumExceptions(exceptions);
			throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("ERROR: insufficient server quorum. Got " + numResponses + " out of " + numServers + " servers."));
		}
		return final_response;
	}



	public static AuditResponse getAuditQuorumResponse(HashMap<Long, ArrayList<AuditResponse>> readlist, ArrayList<StatusRuntimeException> exceptions) throws StatusRuntimeException {
		AuditResponse final_response = null;
		int numResponses = 0;
		// get response value received from more than (N+f)/2 servers
		for(Long ts : readlist.keySet()) {
			if(readlist.get(ts).size() > MIN_QUORUM){
				for(int index = 0; index < readlist.get(ts).size(); index++){ // in the worst case, each index can have a different value
					int count = 0;
					final_response = readlist.get(ts).get(index);
					for(AuditResponse response : readlist.get(ts)){
						if(response.getData().getTransactionsList().equals(final_response.getData().getTransactionsList())) {
							count++;
						}
					}
					if(count > MIN_QUORUM) break;
					final_response = null;
				}
			}
			numResponses += readlist.get(ts).size();
		}

		if(final_response == null){
			verifyQuorumExceptions(exceptions);
			throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("ERROR: insufficient server quorum. Got " + numResponses + " out of " + numServers + " servers."));
		}
		return final_response;
	}

	public static void writeBackCheckAccount(PublicKey pubKey, PrivateKey privKey, CheckAccountResponse response, HashMap<ServerServiceBlockingStub, Long> serverTS){
		long ts = response.getData().getTimestamp();
		List<TransactionMessage> transactions = response.getData().getTransactionsList();
		ByteString encodedKey = Crypto.getEncodedKey(pubKey);

		for(Map.Entry<ServerServiceBlockingStub, Long> entry: serverTS.entrySet()){
			if(entry.getValue() < ts){
				try {
					ServerServiceBlockingStub server = entry.getKey();
					long nonce = get_nonce(pubKey, privKey, server);

					WriteBackCheckAccountRequest.Data data = WriteBackCheckAccountRequest.Data.newBuilder().setKey(encodedKey).addAllTransactions(transactions)
							.setTimestamp(ts).setNonce(nonce).build();
					ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
					WriteBackCheckAccountRequest request = WriteBackCheckAccountRequest.newBuilder().setData(data).setSignature(signature).build();
					server.writeBackCheckAccount(request);
				} catch(Exception e){
					// api doesn't care if it wasn't successful. it will try again later for a new request
				}
			}
		}
	}

	public static void writeBackAudit(PublicKey pubKey, PrivateKey privKey, AuditResponse response, HashMap<ServerServiceBlockingStub, Long> serverTS){
		long ts = response.getData().getTimestamp();
		List<TransactionMessage> transactions = response.getData().getTransactionsList();
		ByteString encodedKey = Crypto.getEncodedKey(pubKey);

		for(Map.Entry<ServerServiceBlockingStub, Long> entry: serverTS.entrySet()){
			if(entry.getValue() < ts){
				try {
					ServerServiceBlockingStub server = entry.getKey();
					long nonce = get_nonce(pubKey, privKey, server);

					WriteBackAuditRequest.Data data = WriteBackAuditRequest.Data.newBuilder().setKey(encodedKey).addAllTransactions(transactions)
							.setTimestamp(ts).setNonce(nonce).build();
					ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
					WriteBackAuditRequest request = WriteBackAuditRequest.newBuilder().setData(data).setSignature(signature).build();
					server.writeBackAudit(request);
				} catch(Exception e){
					// api doesn't care if it wasn't successful. it will try again later for a new request
				}
			}
		}
	}
}
