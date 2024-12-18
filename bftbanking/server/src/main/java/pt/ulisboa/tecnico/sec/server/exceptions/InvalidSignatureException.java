package pt.ulisboa.tecnico.sec.server.exceptions;

public class InvalidSignatureException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidSignatureException() {
        super("Invalid signature.");
    }

    public InvalidSignatureException(String message) {
        super(message);
    }
}
