// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.exception;

public class CryptoServiceException extends Exception {
    private static final long serialVersionUID = 2478803423950812636L;

    public CryptoServiceException(String message) {
        super(message);
    }

    public CryptoServiceException(String message, Exception ex) {
        super(message, ex);
    }
}