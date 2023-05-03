// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.webSockets.annotations;

import java.lang.annotation.*;

/**
 * @Session parameter annotation for use wtih @WebSocketController classes.
 * 
 * The parameter to which @Session is applied must have the type WebSocketSession.
 * 
 * Example:
 * 
 * public void publishJsonResource(@Session WebSocketSession session) { ... }
 * 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Session {
}