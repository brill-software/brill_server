// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.webSockets.annotations;

import java.lang.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Controller;

/**
 * @WebSocketController class and field annotation
 * 
 * A WebSocketController is similar to a RestController but for WebSockets. Instead of requests and responses,
 * messages travel freely from Client to the Server or Server to Client. A WebSocketController provides methods 
 * for handling messages received from the Client. Unlike REST, there's no requirement to return a response. The 
 * method can send several messages to the Client or indead messages to other Clients.
 * 
 * Example: 
 * 
 * @WebSocketController
 * public class { ... }
 * 
 * The field version of the annotation allows the WebSocketManager to get an autowired list of all 
 * the WebSocket Controllers.
 * 
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Controller
public @interface WebSocketController {
    @AliasFor(annotation = Controller.class)
    String value() default "";
} 