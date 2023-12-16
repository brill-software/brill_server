// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.controller;

import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;
import brill.server.service.GitService;
import brill.server.service.WebSocketService;
import brill.server.utils.TopicUtils;
import brill.server.webSockets.annotations.*;

/**
 * Image Controller.
 */
@WebSocketController
public class ImageController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageController.class);

    private GitService gitService;
    private WebSocketService wsService;

    // @Autowired
    public ImageController(GitService gitService, WebSocketService wsService) {
        this.gitService = gitService;
        this.wsService = wsService;
    }

    /**
     * Get an image from a file. The content is returned as a string that can be provided as the
     * src attribute to a html <img> tag.
     * 
     * @param session Web Socket session.
     * @param message JsonObject with the topic to unscubscribe from.
     */
    @Event(value = "subscribe", topicMatches = "image:/.*\\.(jpeg|jpg|gif|png|apng|bmp)$")
    public void subscribeToImage(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            String image = gitService.getFileBase64Encoded(wsService.getWorkspace(session), topic);
            String fileExt = TopicUtils.getFileExtension(topic);
            String content = "\"data:image/" + fileExt + ";base64," + image + "\"";
            wsService.sendMessageToClient(session, "publish", topic, content, false);
            wsService.addSubscription(session, topic);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Image Error", e.getMessage());
            log.error("Image error: ", e);
        }
    }
}