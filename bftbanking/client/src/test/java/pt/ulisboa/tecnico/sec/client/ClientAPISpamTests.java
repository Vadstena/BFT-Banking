package pt.ulisboa.tecnico.sec.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import pt.ulisboa.tecnico.sec.crypto.Crypto;

import java.security.*;
import java.util.concurrent.TimeUnit;

public class ClientAPISpamTests {
    private static final int N = 15; // number of loops to overflow server

    // defined at the server side
    private static final int MAX_WORK = 10;
    private static final int TIME_STEP = 4;

    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void spamSendAmountTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InterruptedException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        String errorCode = Status.PERMISSION_DENIED.getCode().toString();
        for(int i = 0; i < MAX_WORK; i++){
            ClientAPI.send_amount(pubSrcKey, pubDestKey, 1, privSrcKey);
        }
        for(int i = MAX_WORK; i < N; i++){
            Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
                ClientAPI.send_amount(pubSrcKey, pubDestKey, 1, privSrcKey);
            });
            Assert.assertTrue(e.getMessage().contains(errorCode));
        }

        TimeUnit.SECONDS.sleep(TIME_STEP);
        long TID = ClientAPI.send_amount(pubSrcKey, pubDestKey, 1, privSrcKey);
        Assert.assertTrue(TID > 0);
    }
}
