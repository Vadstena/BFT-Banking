package pt.ulisboa.tecnico.sec.server.exceptions;

public class NonExistentAccountException extends Exception {

    private static final long serialVersionUID = 1L;

    public NonExistentAccountException() {
        super("Account associated with given public key does not exist.");
    }

    public NonExistentAccountException(String message) {
        super(message);
    }
}
