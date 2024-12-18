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

public class ClientAPISendAmountTests {
    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void sendAmountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        long TID = ClientAPI.send_amount(pubSrcKey, pubDestKey, 10, privSrcKey);
        Assert.assertTrue(TID > 0);
    }

    @Test
    public void sendAmountInvalidAmountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.send_amount(pubSrcKey, pubDestKey, -10, privSrcKey);
        });
        String errorCode = Status.INVALID_ARGUMENT.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Invalid amount. Must be higher than zero."));
    }

    @Test
    public void sendAmountFromNonExistentSourceAccountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubDestKey, privDestKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.send_amount(pubSrcKey, pubDestKey, 20, privSrcKey);
        });
        String errorCode = Status.NOT_FOUND.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Account associated with given public key does not exist."));
    }

    @Test
    public void sendAmountToNonExistentDestinationAccountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();

        ClientAPI.open_account(pubSrcKey, privSrcKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.send_amount(pubSrcKey, pubDestKey, 20, privSrcKey);
        });
        String errorCode = Status.NOT_FOUND.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Account associated with given public key does not exist."));
    }

    @Test
    public void sendAmountInsufficientBalanceTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.send_amount(pubSrcKey, pubDestKey, 9999, privSrcKey);
        });
        String errorCode = Status.FAILED_PRECONDITION.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Insufficient balance."));
    }

    @Test
    public void sendAmountEqualAccountsTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubKey = keyPair.getPublic();
        PrivateKey privKey = keyPair.getPrivate();

        ClientAPI.open_account(pubKey, privKey);

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            ClientAPI.send_amount(pubKey, pubKey, 1, privKey);
        });
        String errorCode = Status.FAILED_PRECONDITION.getCode().toString();
        Assert.assertTrue(e.getMessage().contains(errorCode));
        Assert.assertTrue(e.getMessage().contains("Destination account can't be the same as the source account."));
    }
}
