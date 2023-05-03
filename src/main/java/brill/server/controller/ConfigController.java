// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.controller;

import javax.json.JsonObject;
// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.WebSocketSession;
import brill.server.service.WebSocketService;
import brill.server.webSockets.annotations.*;
import static java.lang.String.format;

/**
 * Config Controller - provides the client with access to various config values.
 */
@WebSocketController
public class ConfigController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfigController.class);

    private WebSocketService wsService;
    // @Autowired
    public ConfigController(WebSocketService wsService) {
        this.wsService = wsService;
    }

    @Value("${passwords.pepper:}")
    String passwordsPepper;

    @Value("${brill.apps.repo:}")
    String respository;

    // Add additional config values here

    /**
     * Gets a config value from the application.yaml file.
     * 
     * Example:
     * {"event":"subscribe","topic":" config:/passwords.pepper"}
     * 
     * Config values only change when the server is re-started. There's no need to unsubscribe.
     * 
     * @param session Web Socket session.
     * @param message JsonObject with the topic to unscubscribe from.
     */
    @Event(value = "subscribe", topicMatches = "config:/.+")
    public void subscribeToConfigValue(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            if (topic.equals("config:/passwords.pepper")) {
                wsService.sendMessageToClient(session, "publish", topic, "\"" + passwordsPepper + "\"");
            } else 
            if (topic.equals("config:/repository")) {
                wsService.sendMessageToClient(session, "publish", topic, "\"" + respository + "\"");
            } else 
            if (topic.equals("config:/workspace")) {
                wsService.sendMessageToClient(session, "publish", topic, "\"" + wsService.getWorkspace(session) + "\"");
            } else 
            {
                wsService.sendErrorToClient(session, topic, "Unsupported", "Unsupported config value: " + topic);
                log.error(format("Unsupported config value. Topic = %s", topic));
            }
           
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Config Error", e.getMessage());
            log.error("Config error: ", e);
        }
    }
}