// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.exception;

public class MissingValueException extends Exception {

    private static final long serialVersionUID = 6478809023450866736L;

    public MissingValueException(String message) {
        super(message);
    }

    public MissingValueException(String message, Exception ex) {
        super(message, ex);
    }
}