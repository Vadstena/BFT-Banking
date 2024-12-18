package pt.ulisboa.tecnico.sec.server;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sec.crypto.Crypto;
import pt.ulisboa.tecnico.sec.server.exceptions.*;
import pt.ulisboa.tecnico.sec.server.grpc.*;

import pt.ulisboa.tecnico.sec.server.domain.Transaction; //prob shouldn't know about domain. very convenient though

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class ServerServiceImpl  extends ServerServiceGrpc.ServerServiceImplBase {
	static Server server;
	static int port;
	static PrivateKey privKey;
	static PublicKey pubKey;

	public ServerServiceImpl(int port, int basePort, int numServers, int numFaults, int id) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {
		server = new Server(port, numServers, numFaults, id);
		ServerServiceImpl.port = port;
		server.recoverState();
		server.initBroadcastService(basePort); //prob can send other arguments here but i'll have server save them for now just in case
		privKey = server.getPrivateKey();
		pubKey = server.getPublicKey();
	}

	public void populateKeys(){
		server.populateKeys();
	}

	public void shutdown(){
		server.shutdown();
	}

	@Override
	public void generateNonce(NonceRequest request, StreamObserver<NonceResponse> responseObserver) {
		NonceResponse.Builder builder = NonceResponse.newBuilder();
		try {
			long nonce = server.generateNonce(request.getData().getAccKey(), request.getSignature().toByteArray(), request.getData().toByteArray());

			NonceResponse.Data data = NonceResponse.Data.newBuilder().setValue(nonce).build();
			builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray()));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidSignatureException | SignatureException | InvalidKeyException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	public void getPublicKeyByID(PublicKeyRequest request, StreamObserver<PublicKeyResponse> responseObserver) {
		PublicKeyResponse.Builder builder = PublicKeyResponse.newBuilder();
		if (request.getAccountID() == -1){
			try {
				PublicKey key = server.getPubKey();

				PublicKeyResponse.Data data = PublicKeyResponse.Data.newBuilder().setPublicKey(Crypto.getEncodedKey(key)).build();
				builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray()));

				responseObserver.onNext(builder.build());
				responseObserver.onCompleted();
			} catch (Exception e) {
				responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
			}
		}
		else {
			try {
				PublicKey key = server.getPublicKeyByID(request.getAccountID());
	
				PublicKeyResponse.Data data = PublicKeyResponse.Data.newBuilder().setPublicKey(Crypto.getEncodedKey(key)).build();
				builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray()));
	
				responseObserver.onNext(builder.build());
				responseObserver.onCompleted();
			} catch (Exception e) {
				responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
			}
		}
	}
	
	@Override
	public void getServerKey(ServerKeyRequest request, StreamObserver<ServerKeyResponse> responseObserver) {
		ServerKeyResponse.Builder builder = ServerKeyResponse.newBuilder();
		try{
			builder.setPublicKey(Crypto.getEncodedKey(pubKey));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch(Exception e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void getTimestamp(TimestampRequest request, StreamObserver<TimestampResponse> responseObserver) {
		TimestampResponse.Builder builder = TimestampResponse.newBuilder();
		try{
			long ts = server.getTimestamp(request.getData().getAccKey(), request.getData().getNonce(),
					request.getSignature().toByteArray(), request.getData().toByteArray());

			TimestampResponse.Data data = TimestampResponse.Data.newBuilder().setValue(ts).build();
			builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray()));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch(NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException | InvalidSignatureException | InvalidNonceException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
		OpenAccountResponse.Builder builder = OpenAccountResponse.newBuilder();
		try {
			long accountID = server.openAccount(request.getData().getKey(), request.getData().getTimestamp(), request.getData().getNonce(),
					request.getProofOfWork(), request.getSignature().toByteArray(), request.getData().toByteArray());

			long ts = server.getBankTimestamp();

			OpenAccountResponse.Data data = OpenAccountResponse.Data.newBuilder().setAccountID(accountID).setTimestamp(ts).build();
			builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray())).setKey(ByteString.copyFrom(server.getPubKey().getEncoded()));
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();

		} catch(NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException | InvalidSignatureException |
				InvalidNonceException | InvalidTimestampException | InvalidProofOfWorkException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(AccountAlreadyExistsException e) {
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
		} catch (IOException e) {
			responseObserver.onError(Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
		SendAmountResponse.Builder builder = SendAmountResponse.newBuilder();
		try {
			long id = server.sendAmount(request.getData().getSrcKey(), request.getData().getDestKey(), request.getData().getAmount(),
					request.getData().getTimestamp(), request.getData().getNonce(), request.getProofOfWork(),
					request.getSignature().toByteArray(), request.getData().toByteArray());
			long ts = server.getBankTimestamp();

			SendAmountResponse.Data data = SendAmountResponse.Data.newBuilder().setTID(id).setTimestamp(ts).build();
			builder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray())).setKey(ByteString.copyFrom(server.getPubKey().getEncoded()));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch(InvalidAmountException | NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException |
				InvalidSignatureException | InvalidNonceException | InvalidTimestampException | InvalidProofOfWorkException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(NonExistentAccountException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch(InsufficientBalanceException | EqualSourceAndDestinationException e) {
			responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
		} catch (IOException e) {
			responseObserver.onError(Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
		CheckAccountResponse.Builder responseBuilder = CheckAccountResponse.newBuilder();
		ArrayList<TransactionMessage> transList = new ArrayList();
		try {
			long balance = server.checkAccountBalance(request.getData().getAccKey(), request.getData().getNonce(),
					request.getSignature().toByteArray(), request.getData().toByteArray());
			Collection<Transaction> credits = server.checkAccountCredits(request.getData().getAccKey());
			long ts = server.getBankTimestamp();
			for (Transaction t: credits){
				transList.add(TransactionMessage.newBuilder()
						.setSrcKey(Crypto.getEncodedKey(t.getSrcKey()))
						.setDestKey(Crypto.getEncodedKey(t.getDestKey()))
						.setAmount(t.getAmount())
						.setId(t.getId())
						.build());
			}

			CheckAccountResponse.Data data = CheckAccountResponse.Data.newBuilder().setBalance(balance).addAllTransactions(transList).setTimestamp(ts).build();
			responseBuilder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray())).setKey(ByteString.copyFrom(server.getPubKey().getEncoded()));

			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidNonceException | InvalidKeyException | SignatureException | InvalidSignatureException | InvalidTimestampException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(NonExistentAccountException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch (IOException e) {
			responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
		}
	}

	@Override
	public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
		ReceiveAmountResponse.Builder responseBuilder = ReceiveAmountResponse.newBuilder();
		try {
			int amount = server.receiveAmount(request.getData().getAccKey(), request.getData().getTID(), request.getData().getTimestamp(),
					request.getData().getNonce(), request.getProofOfWork(), request.getSignature().toByteArray(), request.getData().toByteArray());
			long ts = server.getBankTimestamp();

			ReceiveAmountResponse.Data data = ReceiveAmountResponse.Data.newBuilder().setAmount(amount).setTimestamp(ts).build();
			responseBuilder.setData(data).setSignature(Crypto.signMessage(privKey,data.toByteArray())).setKey(ByteString.copyFrom(server.getPubKey().getEncoded()));

			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException | InvalidSignatureException |
				InvalidNonceException | InvalidTimestampException | InvalidProofOfWorkException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (NonExistentAccountException | NonExistentTransactionException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch (IOException e) {
			responseObserver.onError(Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
		}
	}


	@Override
	public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
		AuditResponse.Builder responseBuilder = AuditResponse.newBuilder();
		ArrayList<TransactionMessage> transList = new ArrayList();
		try {
			Iterable<Transaction> acc_trans = server.audit(request.getData().getAccKey(), request.getData().getNonce(),
					request.getSignature().toByteArray(), request.getData().toByteArray());
			long ts = server.getBankTimestamp();
			for (Transaction t: acc_trans){
				transList.add(TransactionMessage.newBuilder()
						.setSrcKey(Crypto.getEncodedKey(t.getSrcKey()))
						.setDestKey(Crypto.getEncodedKey(t.getDestKey()))
						.setAmount(t.getAmount())
						.setId(t.getId())
						.build());
			}
			AuditResponse.Data dataBuilder = AuditResponse.Data.newBuilder().addAllTransactions(transList).setTimestamp(ts).build();
			ByteString signature = Crypto.signMessage(privKey, dataBuilder.toByteArray());
			responseBuilder.setData(dataBuilder).setSignature(signature).setKey(ByteString.copyFrom(server.getPubKey().getEncoded()));

			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidNonceException | InvalidKeyException | SignatureException | InvalidSignatureException | InvalidTimestampException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(NonExistentAccountException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	public ArrayList<Transaction> getTransactionsList(List<TransactionMessage> list) throws NoSuchAlgorithmException, InvalidKeySpecException {
		ArrayList<Transaction> transactions = new ArrayList<>();
		for(TransactionMessage tm : list){
			Transaction t = new Transaction(Crypto.getPublicKey(tm.getSrcKey()), Crypto.getPublicKey(tm.getDestKey()), (int) tm.getAmount());
			transactions.add(t);
		}
		return transactions;
	}

	@Override
	public void writeBackCheckAccount(WriteBackCheckAccountRequest request, StreamObserver<Empty> responseObserver) {
		Empty.Builder responseBuilder = Empty.newBuilder();
		try {
			ArrayList<Transaction> transactions = this.getTransactionsList(request.getData().getTransactionsList());
			server.writeBackCheckAccount(request.getData().getKey(), transactions, request.getData().getTimestamp(),
					request.getData().getNonce(), request.getSignature().toByteArray(), request.getData().toByteArray());
			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException | InvalidSignatureException | InvalidNonceException | InvalidTimestampException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(NonExistentAccountException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void writeBackAudit(WriteBackAuditRequest request, StreamObserver<Empty> responseObserver) {
		Empty.Builder responseBuilder = Empty.newBuilder();
		try {
			ArrayList<Transaction> transactions = this.getTransactionsList(request.getData().getTransactionsList());
			server.writeBackAudit(request.getData().getKey(), transactions, request.getData().getBalance(), request.getData().getTimestamp(),
					request.getData().getNonce(), request.getSignature().toByteArray(), request.getData().toByteArray());
			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | SignatureException | InvalidKeyException | InvalidSignatureException | InvalidNonceException | InvalidTimestampException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch(NonExistentAccountException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
