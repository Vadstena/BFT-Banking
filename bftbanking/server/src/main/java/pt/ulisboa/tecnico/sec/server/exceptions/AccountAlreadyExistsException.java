package pt.ulisboa.tecnico.sec.server.exceptions;

public class AccountAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public AccountAlreadyExistsException() {
        super("Account associated with given public key already exists.");
    }

    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
