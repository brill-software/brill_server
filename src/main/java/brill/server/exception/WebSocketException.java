// © 2021 Brill Software Limited - Brill Midleware, distributed under the MIT License.
package brill.server.exception;

public class WebSocketException extends Exception {

    private static final long serialVersionUID = 5478809054650823436L;

    public WebSocketException(String message) {
        super(message);
    }

    public WebSocketException(String message, Exception ex) {
        super(message, ex);
    }
}