package pt.ulisboa.tecnico.sec.server.exceptions;

public class NonExistentTransactionException extends Exception {

    private static final long serialVersionUID = 1L;

    public NonExistentTransactionException() {
        super("Transaction associated with TID does not exist.");
    }

    public NonExistentTransactionException(String message) {
        super(message);
    }
}
