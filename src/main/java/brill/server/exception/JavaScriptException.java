// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.exception;

public class JavaScriptException extends Exception {

    private static final long serialVersionUID = 5478809075950863136L;

    public JavaScriptException(String message) {
        super(message);
    }

    public JavaScriptException(String message, Exception ex) {
        super(message, ex);
    }
}