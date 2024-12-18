package pt.ulisboa.tecnico.sec.server.exceptions;

public class InvalidTimestampException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTimestampException() {
        super("Invalid Timestamp. Server is already up to date.");
    }

    public InvalidTimestampException(String message) {
        super(message);
    }
}
