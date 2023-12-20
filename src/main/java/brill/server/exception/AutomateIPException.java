package brill.server.exception;

public class AutomateIPException extends Exception{
    //Need to modify the UID after checking
    private static final long serialVersionUID = 1628809567650823436L;

    public AutomateIPException(String message) {
        super(message);
    }

    public AutomateIPException(String message, Exception ex) {
        super(message, ex);
    }
    
}
