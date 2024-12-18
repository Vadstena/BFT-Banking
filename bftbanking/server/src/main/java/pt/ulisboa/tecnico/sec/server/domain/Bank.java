package pt.ulisboa.tecnico.sec.server.domain;

import pt.ulisboa.tecnico.sec.server.exceptions.*;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Bank implements Serializable {
    private static final long serialVersionUID = 1L;
    private HashMap<PublicKey, Account> accounts;

    private AtomicLong numTransactions;
    private AtomicLong numAccounts;

    private AtomicLong timestamp;

    public Bank(){
        this.accounts = new HashMap<>();
        this.numTransactions = new AtomicLong(0);
        this.numAccounts = new AtomicLong(0);
        this.timestamp = new AtomicLong(0);

    }

    public long getTimestamp(){
        return this.timestamp.get();
    }

    public void setTimestamp(long ts){
        this.timestamp.set(ts);
    }

    public int getNumberAccounts() {
        return this.accounts.size();
    }

    public Account getAccount(PublicKey key) {
        return this.accounts.get(key);
    }

    public Account getAccountByID(long userID) {
        for(Account acc : this.accounts.values()) {
            if(acc.hasID(userID))
                return acc;
        }
        return null;
    }

    public boolean hasAccount(PublicKey key) {
        return this.accounts.containsKey(key);
    }

    public long addAccount(PublicKey key) {
        long id = this.numAccounts.getAndIncrement();
        long timestamp = Instant.now().getEpochSecond();
        Account account = new Account(key, id,50, timestamp);
        this.accounts.put(key, account);
        return id;
    }


    public long addTransaction(Account srcAccount, Account destAccount, int amount) {
        long id = this.numTransactions.getAndIncrement();
        Transaction t = new Transaction(id, srcAccount.getKey(), destAccount.getKey(), amount);
        srcAccount.addPendingWithdrawal(t);
        destAccount.addPendingCredit(t);
        return t.getId();
    }

    // write back transaction in check account request
    public void addPendingTransaction(Account acc, Transaction t){
        Account acc2;

        // ensure the account is associated with the transaction and find the second one
        if(acc.getKey().equals(t.getDestKey()) && !acc.hasPendingCredit(t.getId())) {
            acc2 = this.getAccount(t.getSrcKey());
            acc.addPendingCredit(t);
            acc2.addPendingWithdrawal(t);
        }
    }

    // write back transaction in audit request
    public void addTransaction(Account acc, Transaction t){
        Account acc2;
        boolean is_source;

        // ensure the account is associated with the transaction and find the second one
        if(acc.getKey().equals(t.getSrcKey())) {
            acc2 = this.getAccount(t.getDestKey());
            is_source = true;
        }
        else if(acc.getKey().equals(t.getDestKey())) {
            acc2 = this.getAccount(t.getSrcKey());
            is_source = false;
        }
        else
            return; // byzantine action

        acc.addTransaction(t, is_source);
        acc2.addTransaction(t, !is_source);
    }

    public void completeTransfer(Account srcAccount, Account destAccount, long TID) {
        destAccount.completePendingCredit(TID);
        srcAccount.completePendingWithdrawal(TID);
    }

    /* ----------------------------- */
    /* ------ main operations ------ */
    /* ----------------------------- */

    public long openAccount(PublicKey key) throws AccountAlreadyExistsException {
        if(this.hasAccount(key)) throw new AccountAlreadyExistsException();
        return this.addAccount(key);
    }

    public long sendAmount(PublicKey srcKey, PublicKey destKey, int amount) throws NonExistentAccountException, EqualSourceAndDestinationException, InsufficientBalanceException, InvalidAmountException {
        if (amount <= 0) throw new InvalidAmountException();

        Account srcAccount = this.getAccount(srcKey);
        Account destAccount = this.getAccount(destKey);

        if (srcAccount == null || destAccount == null) throw new NonExistentAccountException();
        if (srcAccount.equals(destAccount)) throw new EqualSourceAndDestinationException();
        if (srcAccount.getBalance() - amount < 0) throw new InsufficientBalanceException();

        return this.addTransaction(srcAccount, destAccount, amount);

    }

    public long checkAccountBalance(PublicKey key) throws NonExistentAccountException {
        Account acc = this.getAccount(key);
        if (acc == null) throw new NonExistentAccountException();
        return acc.getBalance();
    }

    public int receiveAmount(PublicKey key, long TID) throws NonExistentAccountException, NonExistentTransactionException {
        Account acc = this.getAccount(key);
        if (acc == null) throw new NonExistentAccountException();

        Transaction t = acc.getPendingCredit(TID);
        if (t == null) throw new NonExistentTransactionException();

        Account srcAcc = this.getAccount(t.getSrcKey());
        if (srcAcc == null) throw new NonExistentAccountException();
        if (!srcAcc.hasPendingWithdrawal(TID)) throw new NonExistentTransactionException();

        this.completeTransfer(srcAcc, acc, TID);
        return t.getAmount();
    }

    public Iterable<Transaction> audit(PublicKey key){
        Account acc = this.getAccount(key);
        return acc.getTransactions();
    }
}
