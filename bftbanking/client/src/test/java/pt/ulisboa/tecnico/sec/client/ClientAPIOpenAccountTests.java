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

public class ClientAPIOpenAccountTests {
    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void openAccountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey key = keyPair.getPublic();
        PrivateKey priv = keyPair.getPrivate();

        long accountID = ClientAPI.open_account(key, priv);
        Assert.assertTrue(accountID >= 0);
    }

    @Test
    public void openExistingAccountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey key = keyPair.getPublic();
        PrivateKey priv = keyPair.getPrivate();

        ClientAPI.open_account(key, priv);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.open_account(key, priv);
        });
        String errorCode = Status.ALREADY_EXISTS.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Account associated with given public key already exists."));
    }
}
