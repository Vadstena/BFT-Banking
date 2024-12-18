package pt.ulisboa.tecnico.sec.server.exceptions;

public class AmountOfWorkExceededException extends Exception {

    private static final long serialVersionUID = 1L;

    public AmountOfWorkExceededException() {
        super("Account exceeded maximum amount of work (anti-spam protection).");
    }

    public AmountOfWorkExceededException(String message) {
        super(message);
    }
}
