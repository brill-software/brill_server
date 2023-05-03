// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.webSockets.annotations;

import java.lang.annotation.*;

/**
 * @Message parameter annotation for use wtih @WebSocketController classes.
 * 
 * The parameter to which @Message is applied must have the type JSonObject.
 * 
 * Example:
 * 
 *   public void publishJsonResource(@Message JsonObject message) { ... }
 * 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Message {
}