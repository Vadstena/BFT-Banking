package pt.ulisboa.tecnico.sec.server.exceptions;

public class InsufficientBalanceException extends Exception {

    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException() {
        super("Insufficient balance.");
    }

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
