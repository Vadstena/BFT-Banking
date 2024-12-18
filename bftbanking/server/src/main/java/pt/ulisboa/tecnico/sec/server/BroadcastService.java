package pt.ulisboa.tecnico.sec.server;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.sec.server.grpc.ServerServiceGrpc.ServerServiceBlockingStub;
import pt.ulisboa.tecnico.sec.server.grpc.*;

import pt.ulisboa.tecnico.sec.crypto.Crypto;

import java.security.*;
import java.lang.InterruptedException;
import java.lang.Integer;
import java.lang.Thread;
import java.util.ArrayList;

public class BroadcastService {

    class IntegerWrapper{
        public Integer value;
        public IntegerWrapper(int value) {this.value = new Integer(value);}
    }

    private static float MIN_QUORUM;
    private static ArrayList<ServerServiceBlockingStub> servers;
    private static ArrayList<ManagedChannel> channels;
    private static int numServers;
    private static int numFaults;
    private static Server server;

    public BroadcastService(Server s, int basePort, int N, int f) {
        server = s;
        numServers = N;
        numFaults = f;
        servers = new ArrayList<>();
        channels = new ArrayList<>();

        MIN_QUORUM = (numServers+numFaults)/2;

        for(int i = 0; i < N; i++){
            int port = basePort + i;
            String target = "localhost:" + port;
            ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            ServerServiceBlockingStub stub = ServerServiceGrpc.newBlockingStub(channel);

            channels.add(channel);
            servers.add(stub);
        }
    }

    public void populateKeys(){

        class Call extends Thread {
            private PublicKey[] keys;
            private int id;
            private IntegerWrapper nReceivedKeys;

            public Call(PublicKey[] keys, int id, IntegerWrapper nReceivedKeys){
                this.keys = keys;
                this.id = id;
                this.nReceivedKeys = nReceivedKeys;
            }

            public void run(){
                try {
                    // SimplePubKeyExchangeRequest req = SimplePubKeyExchangeRequest.newBuilder().setSelfPubKey(Crypto.getEncodedKey(selfPubKey)).build();
                    PublicKeyRequest req = PublicKeyRequest.newBuilder().setAccountID(-1).build();
                    PublicKeyResponse response = servers.get(id).getPublicKeyByID(req);
                    keys[this.id] = Crypto.getPublicKey(response.getData().getPublicKey());
                    this.nReceivedKeys.value = this.nReceivedKeys.value + 1;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        PublicKey selfPubKey = server.getPubKey();
        IntegerWrapper nReceivedKeys = new IntegerWrapper(1); // this is a fault if, for some reason, the server fails to generate its own keyPair
        PublicKey[] replicaPubKeys = new PublicKey[numServers];
        replicaPubKeys[server.getId()] = selfPubKey;
        ArrayList<Call> threads = new ArrayList<Call>();

        for (int i = 0; i < numServers; i++) {
            if (replicaPubKeys[i] != null){
                continue;
            }
            System.out.println("Fetching " + i + "'s key..");
            Call t = new Call(replicaPubKeys, i, nReceivedKeys);
            threads.add(t);
            t.start();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e){}
        }
        int hasKeys;
        while (nReceivedKeys.value < numServers){
            hasKeys = 1;
            for(PublicKey k: replicaPubKeys){
                if (k == null)
                    hasKeys = 0;
            }
            if (hasKeys == 1)
                break;
            try {
                System.out.println("Waiting to receive Public Keys..");
                Thread.sleep(400);
            } catch (InterruptedException e){}
        }
        server.setReplicaPubKeys(replicaPubKeys);
        for (int i = 0; i < numServers; i++){
            try {
                System.out.println("\n # # # replica " + i + "'s pubKey:" + Crypto.hashMyKey(replicaPubKeys[i].toString()));
            } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e){}
        }
    }

    public void shutdown(){ for(ManagedChannel channel:channels) channel.shutdown();}

}