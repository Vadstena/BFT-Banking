package pt.ulisboa.tecnico.sec.server.domain;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;
    private PublicKey key;
    private long balance;
    private List<Transaction> transactions;
    private HashMap<Long, Transaction> pendingWithdrawals;
    private HashMap<Long, Transaction> pendingCredits;
    private long id;

    private AtomicLong work;
    private AtomicLong timestamp; // work related

    public Account(PublicKey key, long id, long balance, long timestamp) {
        this.key = key;
        this.id = id;
        this.balance = balance;
        this.transactions = new ArrayList<>();
        this.pendingWithdrawals = new HashMap<>();
        this.pendingCredits = new HashMap<>();
    }

    public long getWork(){
        return this.work.get();
    }

    public long getTimestamp(){
        return this.timestamp.get();
    }

    public void setWork(long work){
        this.work.set(work);
    }

    public void setTimestamp(long ts){
        this.timestamp.set(ts);
    }

    public long getID() {
        return this.id;
    }

    public boolean hasID(long userID){
        return id == userID;
    }

    public long getBalance() {
        return this.balance;
    }
    public PublicKey getKey(){
        return this.key;
    }
    public Iterable<Transaction> getTransactions(){ return this.transactions; }

    public long getLastestTransactionID(){
        if(this.transactions.isEmpty())
            return -1;
        return this.transactions.get(this.transactions.size()-1).getId();
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void addPendingWithdrawal(Transaction t) {
        this.pendingWithdrawals.put(t.getId(), t);
        this.balance -= t.getAmount();
    }
    public void addPendingCredit(Transaction t){
        this.pendingCredits.put(t.getId(), t);
    }

    public Transaction getPendingWithdrawal(long id) {
        return this.pendingWithdrawals.get(id);
    }
    public Transaction getPendingCredit(long id) {
        return this.pendingCredits.get(id);
    }
    public Collection<Transaction> getPendingCredits(){
        return this.pendingCredits.values();
    }

    public boolean hasPendingWithdrawal(long id) {
        return pendingWithdrawals.containsKey(id);
    }
    public boolean hasPendingCredit(long id) {
        return pendingCredits.containsKey(id);
    }

    public void completePendingCredit(long TID) {
        Transaction t = this.pendingCredits.remove(TID);
        this.balance += t.getAmount();
        transactions.add(t);
    }

    public void completePendingWithdrawal(long TID) {
        Transaction t = this.pendingWithdrawals.remove(TID);
        transactions.add(t);
    }

    public void addTransaction(Transaction t, boolean source){
        if(source)
            this.balance -= t.getAmount();
        else
            this.balance += t.getAmount();
        this.transactions.add(t);
    }
}
