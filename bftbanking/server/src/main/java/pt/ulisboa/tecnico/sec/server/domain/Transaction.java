package pt.ulisboa.tecnico.sec.server.domain;

import java.io.Serializable;
import java.security.PublicKey;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private long timestamp;
    private PublicKey srcKey;
    private PublicKey destKey;
    private int amount;

    public Transaction(long id, PublicKey srcKey, PublicKey destKey, int amount){
        this.id = id;
        this.srcKey = srcKey;
        this.destKey = destKey;
        this.amount = amount;
    }

    public Transaction(PublicKey srcKey, PublicKey destKey, int amount){
        this.srcKey = srcKey;
        this.destKey = destKey;
        this.amount = amount;
    }

    public long getId() {
        return id;
    }

    public PublicKey getSrcKey() {
        return this.srcKey;
    }

    public PublicKey getDestKey() {
        return this.destKey;
    }

    public int getAmount() {
        return amount;
    }
}
