// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.exception;

public class IPGeoServiceException extends Exception {

    private static final long serialVersionUID = 5478209975930863236L;

    public IPGeoServiceException(String message) {
        super(message);
    }

    public IPGeoServiceException(String message, Exception ex) {
        super(message, ex);
    }
}