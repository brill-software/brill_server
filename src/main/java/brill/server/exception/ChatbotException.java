// © 2021 Brill Software Limited - Brill Midleware, distributed under the MIT License.
package brill.server.exception;

public class ChatbotException extends Exception {

    private static final long serialVersionUID = 7628809567650823436L;

    public ChatbotException(String message) {
        super(message);
    }

    public ChatbotException(String message, Exception ex) {
        super(message, ex);
    }
}