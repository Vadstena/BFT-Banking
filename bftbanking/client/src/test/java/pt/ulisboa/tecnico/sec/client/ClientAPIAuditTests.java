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

import java.util.ArrayList;
import java.util.Iterator;

public class ClientAPIAuditTests {

    @BeforeClass
    public static void setup() {
        ClientAPI.init("localhost", 8000, 3, 1);
    }

    @AfterClass
    public static void teardown() {
        ClientAPI.close();
    }

    @Test
    public void auditTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        KeyPair keyPair = Crypto.generateKeyPair();
        PublicKey pubSrcKey = keyPair.getPublic();
        PrivateKey privSrcKey = keyPair.getPrivate();

        keyPair = Crypto.generateKeyPair();
        PublicKey pubDestKey = keyPair.getPublic();
        PrivateKey privDestKey = keyPair.getPrivate();

        ClientAPI.open_account(pubSrcKey, privSrcKey);
        ClientAPI.open_account(pubDestKey, privDestKey);

        long TID1 = ClientAPI.send_amount(pubSrcKey, pubDestKey, 1, privSrcKey);
        long TID2 = ClientAPI.send_amount(pubSrcKey, pubDestKey, 5, privSrcKey);

        Transfer t1 = new Transfer(pubSrcKey.getEncoded(), pubDestKey.getEncoded(), 1);
        Transfer t2 = new Transfer(pubSrcKey.getEncoded(), pubDestKey.getEncoded(), 5);

        ClientAPI.receive_amount(pubDestKey, TID2, privDestKey);
        ClientAPI.receive_amount(pubDestKey, TID1, privDestKey);

        Iterable<Transfer> trans = ClientAPI.audit(pubDestKey, privDestKey);
        Iterator<Transfer> iter = trans.iterator();

        Assert.assertTrue(iter.next().equals(t2) && iter.next().equals(t1));

    }
}
