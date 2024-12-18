package pt.ulisboa.tecnico.sec.server.exceptions;

public class EqualSourceAndDestinationException extends Exception {

    private static final long serialVersionUID = 1L;

    public EqualSourceAndDestinationException() {
        super("Destination account can't be the same as the source account.");
    }

    public EqualSourceAndDestinationException(String message) {
        super(message);
    }
}
