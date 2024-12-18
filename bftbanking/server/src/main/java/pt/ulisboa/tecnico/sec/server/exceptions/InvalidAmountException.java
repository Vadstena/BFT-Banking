package pt.ulisboa.tecnico.sec.server.exceptions;

public class InvalidAmountException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidAmountException() {
        super("Invalid amount. Must be higher than zero.");
    }

    public InvalidAmountException(String message) {
        super(message);
    }
}
