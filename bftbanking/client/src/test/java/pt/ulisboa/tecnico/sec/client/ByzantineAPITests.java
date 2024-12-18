package pt.ulisboa.tecnico.sec.client;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import pt.ulisboa.tecnico.sec.crypto.Crypto;
import pt.ulisboa.tecnico.sec.server.grpc.OpenAccountRequest;
import pt.ulisboa.tecnico.sec.server.grpc.OpenAccountResponse;
import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc;
import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc.ServerServiceBlockingStub;

import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;

public class ByzantineAPITests {
    private static final String HOST = "localhost";
    private static final int BASEPORT = 8000;
    private static final int N = 3;
    private static final int f = 1;

    private static ArrayList<ServerServiceBlockingStub> servers;
    private static long wts;

    private static PublicKey pubKey;
    private static PrivateKey privKey;

    @BeforeClass
    public static void setup() throws NoSuchAlgorithmException {
        servers = ClientAPI.init(HOST, BASEPORT, N, f);
        KeyPair keyPair = Crypto.generateKeyPair();
        pubKey = keyPair.getPublic();
        privKey = keyPair.getPrivate();
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void tamperedOpenAccountTest() {
        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            tamperedOpenAccount(pubKey, privKey);
        });
        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Invalid signature."));
    }

    @Test
    public void replayOpenAccountTest(){
        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            replayOpenAccount(pubKey, privKey);
        });
        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Invalid nonce."));
    }

    @Test
    public void invalidProofOfWorkOpenAccountTest(){
        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            invalidProofOfWorkOpenAccount(pubKey, privKey);
        });
        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Invalid proof of work."));
    }

    public long tamperedOpenAccount(PublicKey pubKey, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException {
        ByteString encodedKey = Crypto.getEncodedKey(pubKey);

        KeyPair keyPair = Crypto.generateKeyPair();
        ByteString random_key = Crypto.getEncodedKey(keyPair.getPublic());

        // Authenticated-Data Byzantine Quorum with (N, N) Byzantine Atomic Register
        wts = ClientAPI.update_wts(pubKey, privKey);
        HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
        ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
        for (ServerServiceGrpc.ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
            try {
                long nonce = ClientAPI.get_nonce(pubKey, privKey, server);
                OpenAccountRequest.Data original_data = OpenAccountRequest.Data.newBuilder().setKey(encodedKey).setTimestamp(wts).setNonce(nonce).build();
                ByteString signature = Crypto.signMessage(privKey, original_data.toByteArray());
                OpenAccountRequest.Data tampered_data = OpenAccountRequest.Data.newBuilder().setKey(random_key).setTimestamp(5).setNonce(5).build();
                long pow = ClientAPI.computeProofOfWork(tampered_data.toByteArray());
                OpenAccountRequest request = OpenAccountRequest.newBuilder().setData(tampered_data).setSignature(signature).setProofOfWork(pow).build();

                OpenAccountResponse response = server.openAccount(request);
                ClientAPI.addToReadlist(readlist, response.getData().getAccountID(), response.getData().getTimestamp());

            } catch (StatusRuntimeException e) {
                exceptions.add(e);
            } catch (Exception e) {
                exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
            }
        }

        return ClientAPI.getQuorumResponse(readlist, exceptions);
    }

    public long replayOpenAccount(PublicKey pubKey, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        ByteString encodedKey = Crypto.getEncodedKey(pubKey);

        // Authenticated-Data Byzantine Quorum with (N, N) Byzantine Atomic Register
        wts = ClientAPI.update_wts(pubKey, privKey);
        HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
        ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
        for (ServerServiceGrpc.ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
            try {
                long nonce = ClientAPI.get_nonce(pubKey, privKey, server);
                OpenAccountRequest.Data data = OpenAccountRequest.Data.newBuilder().setKey(encodedKey).setTimestamp(wts).setNonce(nonce).build();
                ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
                long pow = ClientAPI.computeProofOfWork(data.toByteArray());
                OpenAccountRequest request = OpenAccountRequest.newBuilder().setData(data).setSignature(signature).setProofOfWork(pow).build();
                server.openAccount(request);

                // replay message
                OpenAccountResponse response = server.openAccount(request);

                ClientAPI.addToReadlist(readlist, response.getData().getAccountID(), response.getData().getTimestamp());
            } catch (StatusRuntimeException e) {
                exceptions.add(e);
            } catch (Exception e) {
                exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
            }
        }

        return ClientAPI.getQuorumResponse(readlist, exceptions);
    }

    public long invalidProofOfWorkOpenAccount(PublicKey pubKey, PrivateKey privKey) throws StatusRuntimeException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        ByteString encodedKey = Crypto.getEncodedKey(pubKey);

        // Authenticated-Data Byzantine Quorum with (N, N) Byzantine Atomic Register
        wts = ClientAPI.update_wts(pubKey, privKey);
        HashMap<Long, ArrayList<Long>> readlist = new HashMap<>();
        ArrayList<StatusRuntimeException> exceptions = new ArrayList<>();
        for (ServerServiceGrpc.ServerServiceBlockingStub server : servers) { // would be a good idea to asynchronously run this
            try {
                long nonce = ClientAPI.get_nonce(pubKey, privKey, server);
                OpenAccountRequest.Data data = OpenAccountRequest.Data.newBuilder().setKey(encodedKey).setTimestamp(wts).setNonce(nonce).build();
                ByteString signature = Crypto.signMessage(privKey, data.toByteArray());
                long pow = 0;
                OpenAccountRequest request = OpenAccountRequest.newBuilder().setData(data).setSignature(signature).setProofOfWork(pow).build();
                server.openAccount(request);

                // replay message
                OpenAccountResponse response = server.openAccount(request);

                ClientAPI.addToReadlist(readlist, response.getData().getAccountID(), response.getData().getTimestamp());
            } catch (StatusRuntimeException e) {
                exceptions.add(e);
            } catch (Exception e) {
                exceptions.add(new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage())));
            }
        }

        return ClientAPI.getQuorumResponse(readlist, exceptions);
    }
}
