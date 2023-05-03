package brill.server.exception;

public class SecurityServiceException extends Exception {

    private static final long serialVersionUID = 5478803426950865636L;

    public SecurityServiceException(String message) {
        super(message);
    }

    public SecurityServiceException(String message, Exception ex) {
        super(message, ex);
    }
}


