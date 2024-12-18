package pt.ulisboa.tecnico.sec.server;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import pt.ulisboa.tecnico.sec.server.domain.Account;
import pt.ulisboa.tecnico.sec.server.domain.Bank;
import pt.ulisboa.tecnico.sec.server.domain.Transaction;
import pt.ulisboa.tecnico.sec.server.exceptions.*;

import pt.ulisboa.tecnico.sec.crypto.Crypto;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class Server {
    private String backupFilepath;
    private String tmpBackupFilepath;
    private int port;
    private Bank bank;
    private HashMap<PublicKey, Long> nonces;
    private int nServers;
    private int nFaults;
    private int id;
    private BroadcastService brb;
    private PublicKey pubKey = null;
    private PrivateKey privKey = null;
    private PublicKey[] replicaPubKeys;

    public Server(int port, int numServers, int numFaults, int id) throws NoSuchAlgorithmException{
        this.bank = new Bank();
        this.port = port;
        this.nonces = new HashMap<>();
        this.nServers = numServers;
        this.nFaults = numFaults;
        this.id = id;

        try {
            KeyPair keyPair = Crypto.generateKeyPair();
            this.pubKey = keyPair.getPublic();
            this.privKey = keyPair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }

        KeyPair keyPair = Crypto.generateKeyPair();
		this.pubKey = keyPair.getPublic();
		this.privKey = keyPair.getPrivate();

        this.backupFilepath = "backups/" + this.port + '_' + "bank.ser";
        this.tmpBackupFilepath ="backups/" + this.port + '_' + "bank_tmp.ser";

    }

    public void initBroadcastService(int basePort){ brb = new BroadcastService(this, basePort, nServers , nFaults);}
    public void populateKeys(){ brb.populateKeys();}
    public void shutdown(){ brb.shutdown();}
    public PublicKey getPubKey() { return pubKey;}
    public int getId(){ return id;}
    public void setReplicaPubKeys(PublicKey[] keys){ this.replicaPubKeys = keys;}
	public PublicKey getPublicKey(){ return pubKey;}
	public PrivateKey getPrivateKey(){ return privKey;}

    /* -------------------------------------- */
    /* ------ dependability ensurances ------ */
    /* -------------------------------------- */

    public void recoverState() throws IOException, ClassNotFoundException {
        FileInputStream fis = null;
        File file = new File(this.backupFilepath);
        File tmpFile = new File(this.tmpBackupFilepath);

        if(file.exists()){
            System.out.println("Recovering bank data from backup file " + this.backupFilepath);
            fis = new FileInputStream(this.backupFilepath);
        }
        else if(!file.exists() && tmpFile.exists()){
            System.out.println("Recovering bank data from backup file " + this.tmpBackupFilepath);
            fis = new FileInputStream(this.tmpBackupFilepath);
        }
        else
            return;

        ObjectInputStream in = new ObjectInputStream(fis);
        this.bank = (Bank) in.readObject();
        fis.close();
        in.close();
        System.out.println("Total number of bank accounts recovered = " + this.bank.getNumberAccounts());
        System.out.println("Current server timestamp = " + this.bank.getTimestamp());
    }

    public synchronized boolean doBackup() throws IOException {
        boolean success = true;

        // create temporary backup
        FileOutputStream fos = new FileOutputStream(this.tmpBackupFilepath);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this.bank);
        fos.close();
        oos.close();

        // delete old backup
        File old_backup = new File(this.backupFilepath);
        if (old_backup.exists())
            success = old_backup.delete();

        // make temporary backup definitive
        if (success) {
            File new_backup = new File(this.tmpBackupFilepath);
            success = new_backup.renameTo(new File(this.backupFilepath));
        }

        return success;
    }

    public long getBankTimestamp(){
        return this.bank.getTimestamp();
    }

    public long getTimestamp(ByteString encodedPubKey, long nonce, byte[] signature, byte[] message)
            throws InvalidSignatureException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException,
            InvalidKeyException, InvalidNonceException {

        this.validateSignature(encodedPubKey, signature, message);
        this.validateNonce(encodedPubKey, nonce);
        return getBankTimestamp();
    }

    /* ----------------------------- */
    /* ------ main operations ------ */
    /* ----------------------------- */

    public long openAccount(ByteString encodedKey, long ts, long nonce, long pow, byte[] signature, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeySpecException, AccountAlreadyExistsException, IOException,
            InvalidNonceException, InvalidSignatureException, SignatureException, InvalidKeyException,
            InvalidTimestampException, InvalidProofOfWorkException{

        this.validateWriteRequest(encodedKey, signature, data, nonce, ts, pow);
        PublicKey key = Crypto.getPublicKey(encodedKey);
        long id = this.bank.openAccount(key);

        this.doBackup();
        System.out.println("[Open Account] Create account with public key hash value " + key.hashCode());
        return id;
    }

    public long sendAmount(ByteString encodedSrcKey, ByteString encodedDestKey, int amount, long ts, long nonce, long pow, byte[] signature, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException, InvalidAmountException,
            InsufficientBalanceException, EqualSourceAndDestinationException, IOException, InvalidNonceException,
            InvalidSignatureException, SignatureException, InvalidKeyException, InvalidTimestampException,
            InvalidProofOfWorkException {

        this.validateWriteRequest(encodedSrcKey, signature, data, nonce, ts, pow);
        PublicKey srcKey =  Crypto.getPublicKey(encodedSrcKey);
        PublicKey destKey =  Crypto.getPublicKey(encodedDestKey);
        long id = this.bank.sendAmount(srcKey, destKey, amount);

        this.doBackup();
        System.out.println("[Send amount] Create pending transfer (TID=" + id + ") of " + amount + " euros from " + srcKey.hashCode() + " to " + destKey.hashCode());
        return id;
    }

    public long checkAccountBalance(ByteString encodedKey, long nonce, byte[] signature, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException, IOException,
            InvalidNonceException, InvalidSignatureException, InvalidTimestampException, SignatureException, InvalidKeyException {

        this.validateReadRequest(encodedKey, signature, data, nonce);
        PublicKey srcKey =  Crypto.getPublicKey(encodedKey);
        long balance = this.bank.checkAccountBalance(srcKey);

        System.out.println("[Check Account] Account Key: " + srcKey.hashCode() + ", balance: " + balance);
        return balance;
    }

    public Collection<Transaction> checkAccountCredits(ByteString encodedKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException {

        // validation was previously done
        Account acc = this.getAccount(encodedKey);
        Collection<Transaction> credits = acc.getPendingCredits();
        System.out.println("[Check Account] Account Key: " + acc.getKey().hashCode() + ", credits: " + credits);
        return credits;
    }

    public int receiveAmount(ByteString encodedKey, long TID, long ts, long nonce, long pow, byte[] signature, byte[] data)
            throws NonExistentAccountException, NoSuchAlgorithmException, InvalidKeySpecException,
            NonExistentTransactionException, IOException, InvalidNonceException, InvalidSignatureException,
            SignatureException, InvalidKeyException, InvalidTimestampException, InvalidProofOfWorkException {

        this.validateWriteRequest(encodedKey, signature, data, nonce, ts, pow);
        PublicKey key =  Crypto.getPublicKey(encodedKey);
        int amount = this.bank.receiveAmount(key,TID);

        this.doBackup();
        System.out.println("[Receive Amount] Account key: " + key.hashCode() + " received a credit of " + amount);
        return amount;
    }

    public Iterable<Transaction> audit(ByteString encodedKey, long nonce, byte[] signature, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException, InvalidNonceException,
            InvalidSignatureException, InvalidTimestampException, SignatureException, InvalidKeyException {

        this.validateReadRequest(encodedKey, signature, data, nonce);
        PublicKey key =  Crypto.getPublicKey(encodedKey);
        Iterable<Transaction> trans = this.bank.audit(key);

        System.out.println("[Audit] Account Key: " + key.hashCode());
        return trans;
    }

    /* --------------------------------------------------------- */
    /* ------ auxiliar methods to support main operations ------ */
    /* --------------------------------------------------------- */

    public long getAccountBalance(ByteString encodedKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException, IOException, InvalidNonceException {
        Account acc = this.getAccount(encodedKey);
        long balance = acc.getBalance();
        return balance;
    }

    public PublicKey getPublicKeyByID(long userID) throws NonExistentAccountException {
        Account acc = this.bank.getAccountByID(userID);
        if(acc == null) throw new NonExistentAccountException();
        return acc.getKey();
    }

    public Account getAccount(ByteString encodedKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NonExistentAccountException {
        PublicKey accKey =  Crypto.getPublicKey(encodedKey);

        Account acc = this.bank.getAccount(accKey);
        if (acc == null) throw new NonExistentAccountException();

        return acc;
    }

    /* --------------------------------- */
    /* ------ requests validation ------ */
    /* --------------------------------- */

    public long generateNonce(ByteString encodedPubKey, byte[] signature, byte[] message)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidSignatureException, SignatureException, InvalidKeyException {

        this.validateSignature(encodedPubKey, signature, message);

        Random random = new Random();
        long nonce = random.nextLong();
        this.nonces.put(Crypto.getPublicKey(encodedPubKey), nonce);
        return nonce;
    }

    public void validateNonce(ByteString encodedPubKey, long nonce) throws InvalidNonceException, NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey key = Crypto.getPublicKey(encodedPubKey);
        if(!this.nonces.containsKey(key)) throw new InvalidNonceException();
        long saved_nonce = this.nonces.get(key);
        if (Long.valueOf(saved_nonce) == null | Long.valueOf(nonce) == null | saved_nonce != nonce)
            throw new InvalidNonceException();
        this.nonces.remove(key); // remove from hashmap to avoid message replay with same nonce
    }

    public void validateTimestamp(long ts) throws InvalidTimestampException {
        if (ts <= this.getBankTimestamp()) throw new InvalidTimestampException();
        this.bank.setTimestamp(ts);
    }

    public void validateReadRequest(ByteString encodedKey, byte[] signature, byte[] data, long nonce)
            throws InvalidSignatureException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException,
            InvalidKeyException, InvalidNonceException, InvalidTimestampException {
        this.validateSignature(encodedKey, signature, data);
        this.validateNonce(encodedKey, nonce);
    }

    public void validateWriteRequest(ByteString encodedKey, byte[] signature, byte[] data, long nonce, long ts, long pow)
            throws InvalidSignatureException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException,
            InvalidKeyException, InvalidNonceException, InvalidTimestampException, IOException, InvalidProofOfWorkException {
        this.validateSignature(encodedKey, signature, data);
        this.validateProofOfWork(data, pow);
        this.validateNonce(encodedKey, nonce);
        this.validateTimestamp(ts);
    }

    public void validateSignature(ByteString encodedPubKey, byte[] signedMessage, byte[] message)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidSignatureException, InvalidKeySpecException {

        if(!Crypto.verifySignature(Crypto.getPublicKey(encodedPubKey), signedMessage, message))
            throw new InvalidSignatureException();
    }

    public void validateProofOfWork(byte[] message, long pow) throws NoSuchAlgorithmException, InvalidProofOfWorkException, IOException {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        byteArray.write(message);
        byteArray.write(Longs.toByteArray(pow));
        byte[] digest = Crypto.hash(byteArray.toByteArray());

        // a valide proof of works contains a first byte set to 0 (1 byte = 16 bits = 2^16 possible values)
        if(digest[0] != 0x0 || digest[1] != 0x0)
            throw new InvalidProofOfWorkException();
    }

    /* ----------------------------------- */
    /* ------ atomicy of operations ------ */
    /* ----------------------------------- */

    public void writeBackCheckAccount(ByteString encodedKey, ArrayList<Transaction> transactions, long ts, long nonce, byte[] signature, byte[] message) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidNonceException, InvalidSignatureException, SignatureException, InvalidKeyException, InvalidTimestampException, NonExistentAccountException {
        PublicKey key =  Crypto.getPublicKey(encodedKey);
        System.out.println("WriteBack Check Account: Received timestamp " + ts + ". Current server ts = " + this.getBankTimestamp());
        this.validateSignature(encodedKey, signature, message);
        this.validateNonce(encodedKey, nonce);
        this.validateTimestamp(ts);

        Account acc = this.getAccount(encodedKey);

        for(Transaction t : transactions){
            bank.addPendingTransaction(acc, t);
        }
    }

    public void writeBackAudit(ByteString encodedKey, ArrayList<Transaction> transactions, long balance, long ts, long nonce, byte[] signature, byte[] message) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidNonceException, InvalidSignatureException, SignatureException, InvalidKeyException, InvalidTimestampException, NonExistentAccountException {
        PublicKey key =  Crypto.getPublicKey(encodedKey);
        System.out.println("WriteBack Audit: Received timestamp " + ts + ". Current server ts = " + this.getBankTimestamp());
        this.validateSignature(encodedKey, signature, message);
        this.validateNonce(encodedKey, nonce);
        this.validateTimestamp(ts);

        Account acc = this.getAccount(encodedKey);

        long latestTID = acc.getLastestTransactionID();
        for(Transaction t : transactions){
            if(t.getId() > latestTID)
                bank.addTransaction(acc, t);
        }
    }
}
