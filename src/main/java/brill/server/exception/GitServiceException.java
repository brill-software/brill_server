// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.exception;

public class GitServiceException extends Exception {

    private static final long serialVersionUID = 5478809075950863136L;

    public GitServiceException(String message) {
        super(message);
    }

    public GitServiceException(String message, Exception ex) {
        super(message, ex);
    }
}