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

import java.util.HashMap;
import java.util.ArrayList;

public class ClientAPICheckAccountTests {
    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

   @Test
    public void checkAccountsTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair kP1 = Crypto.generateKeyPair();
        PublicKey k1 = kP1.getPublic();
        PrivateKey privk1 = kP1.getPrivate();

        KeyPair kP2 = Crypto.generateKeyPair();
        PublicKey k2 = kP2.getPublic();
        PrivateKey privk2 = kP2.getPrivate();

        ClientAPI.open_account(k1, privk1);
        ClientAPI.open_account(k2, privk2);

        ClientAPI.send_amount(k1, k2, 3, privk1);
        ClientAPI.send_amount(k1, k2, 6, privk1);

        HashMap<String, Object> acc1 = ClientAPI.check_account(k1, privk1);
        HashMap<String, Object> acc2 = ClientAPI.check_account(k2, privk2);
        boolean bal1 = 41 == (long) acc1.get("balance");
        boolean bal2 = 50 == (long) acc2.get("balance");

        ArrayList<Transfer> transList1 = (ArrayList<Transfer>) acc1.get("pendingTransfers");
        boolean trans1 = transList1.isEmpty();

        ArrayList<Transfer> transList2 = (ArrayList<Transfer>) acc2.get("pendingTransfers");
        Transfer t1 = transList2.get(0);
        Transfer t2 = transList2.get(1);

        Transfer t1comp = new Transfer(k1.getEncoded(), k2.getEncoded(), 3);
        Transfer t2comp = new Transfer(k1.getEncoded(), k2.getEncoded(), 6);

         boolean trans2first = t1.equals(t1comp);
         boolean trans2second = t2.equals(t2comp);

        Assert.assertTrue(bal1 && bal2 && trans1 && trans2first && trans2second);
    }
}
