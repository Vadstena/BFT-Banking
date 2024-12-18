package pt.ulisboa.tecnico.sec.server.exceptions;

public class InvalidProofOfWorkException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidProofOfWorkException() {
        super("Invalid proof of work.");
    }

    public InvalidProofOfWorkException(String message) {
        super(message);
    }
}
