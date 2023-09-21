// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.controller;

import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;
import brill.server.service.WebSocketService;
import brill.server.webSockets.annotations.*;

/**
 * Unsubscribe Controller.
 * 
 */
@WebSocketController
public class UnsubscribeController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UnsubscribeController.class);

    private WebSocketService wsService;

    public UnsubscribeController(WebSocketService wsService) {
        this.wsService = wsService;
    }

    /**
     * Unsubscibes the Client from a Topic.
     * 
     * Example:
     * {"event":"unsubscribe","topic":"/theme/login_theme.json"}
     * 
     * @param session Web Socket session.
     * @param message JsonObject with the topic to unscubscribe from.
     */
    @Event(value = "unsubscribe")
    public void unsubscribe(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            wsService.removeSubscription(session, topic);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server unsubscribe error.", e.getMessage());
            log.error("Unsubscribe exception", e);
        }
    }
}