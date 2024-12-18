package pt.ulisboa.tecnico.sec.server.exceptions;

public class InvalidNonceException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidNonceException() {
        super("Invalid nonce.");
    }

    public InvalidNonceException(String message) {
        super(message);
    }
}
