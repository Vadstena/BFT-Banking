package pt.ulisboa.tecnico.sec.client;

import java.util.Arrays;

public class Transfer {

    private byte[] src;
    private byte[] dest;
    private long amount;

    public Transfer(byte[] src, byte[] dest, long amount){
        this.src = src;
        this.dest = dest;
        this.amount = amount;
    }

    public byte[] getSrc() {
        return src;
    }

    public byte[] getDest() {
        return dest;
    }

    public long getAmount() {
        return amount;
    }

    public void setSrc(byte[] src) {
        this.src = src;
    }

    public void setDest(byte[] dest) {
        this.dest = dest;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String toString(){
        return "Source (hashed): " + Arrays.toString(this.src).hashCode() + " ---> Destination (hashed): " + Arrays.toString(this.dest).hashCode() + ", amount: " + this.amount + "\n";
    }

    public boolean equals(Object o){
        if (o instanceof Transfer){
            Transfer t = (Transfer) o;
            return Arrays.equals(this.src, t.getSrc()) && Arrays.equals(this.dest, t.getDest()) && this.amount == t.getAmount();
        }
        return false;
    }
}