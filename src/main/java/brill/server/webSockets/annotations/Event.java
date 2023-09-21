// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.webSockets.annotations;

import java.lang.annotation.*;

/**
 * @Event method annotation for use with @WebSocketController classes.
 * 
 * value: The event for which the method is to be called.
 * topicMatches: Optional regular expression that must match the Topic.
 * 
 * Example: 
 * 
 * @Event(value = "publish", topicMatches = ".*\\.json")
 *   public void publishJsonResource() { ... }
 * 
 * @Event(value = "publish", topicMatches = ".*\\.js", execute = true)
 *   public void publishJsonResource() { ... }
 * 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Event {
    public String value() default "";
    public String topicMatches() default "";
    public String permission() default "";
}