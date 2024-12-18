package pt.ulisboa.tecnico.sec.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import pt.ulisboa.tecnico.sec.crypto.Crypto;
import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc;

import java.security.*;

public class ClientAPIReceiveAmountTests {
    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void receiveAmount() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        long TID = ClientAPI.send_amount(pubSrcKey, pubDestKey, 1, privSrcKey);

        int amount = ClientAPI.receive_amount(pubDestKey, TID, privDestKey);
        Assert.assertEquals(amount, 1);
    }

    @Test
    public void receiveAmountNonExistentAccount() throws NoSuchAlgorithmException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubKey = keyPair.getPublic();
        PrivateKey privKey = keyPair.getPrivate();

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.receive_amount(pubKey, 0, privKey);
        });
        String errorCode = Status.NOT_FOUND.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Account associated with given public key does not exist."));
    }

    @Test
    public void receiveAmountNonExistentTransaction() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.receive_amount(pubDestKey,1000000, privDestKey);
        });
        String errorCode = Status.NOT_FOUND.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Transaction associated with TID does not exist."));
    }
}
