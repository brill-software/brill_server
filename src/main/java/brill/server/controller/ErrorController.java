// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.controller;

import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;

import brill.server.exception.MissingValueException;
import brill.server.service.WebSocketService;
import brill.server.utils.JsonUtils;
import brill.server.webSockets.annotations.*;
import static java.lang.String.format;

/**
 * Logs an error that was reported by the client.
 * 
 */
@WebSocketController
public class ErrorController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ErrorController.class);

    private WebSocketService wsService;

    public ErrorController(WebSocketService wsService) {
        this.wsService = wsService;
    }

    @Event(value = "error", topicMatches = ".*")
    public void logError(@Session WebSocketSession session, @Message JsonObject message) {
        
        String topic = "";
        try {
            String username = wsService.getUsername(session);
            topic = message.getString("topic");
            String title = JsonUtils.getString(message, "title");
            String detail = JsonUtils.getString(message, "detail");
            log.error(format("Client Error: \nUsername: %s\nTopic: %s\nTitle: %s\nDetail: %s", username, topic, title, detail));
        } catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Error", e.getMessage());
            log.error(format("Error with Client Error report: %s\n%s", e.getMessage(), message.toString()));
        }
    }
}