package brill.server.exception;

public class PasswordServiceException extends Exception {

    private static final long serialVersionUID = 5478803426950865636L;

    public PasswordServiceException(String message) {
        super(message);
    }

    public PasswordServiceException(String message, Exception ex) {
        super(message, ex);
    }
}


