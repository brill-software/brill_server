// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.controller;

import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;
import brill.server.exception.WebSocketException;
import brill.server.service.*;
import brill.server.utils.JsonUtils;
import brill.server.webSockets.annotations.*;

/**
 * Topic Tree Controller - provides events for handling trees of topics.
 * 
 */
@WebSocketController
public class TopicTreeController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TopicTreeController.class);

    private WebSocketService wsService;
    private GitService gitService;

    // @Autowired
    public TopicTreeController(WebSocketService wsService, GitService gitService) {
        this.wsService = wsService;
        this.gitService = gitService;
    }

    /**
    * Subscribes to the Topic Tree for a specified Topic Branch. A Topic Branch is any topic
    * name that ends in a '/' and corresponds to a file directory. The file contents can also be
    * included by setting the filter value includeFileContent to true. The file contents are
    * inlcuded in the result as a base64 encoded string.
    *
    * Used by the CMS to get the list of files to display on the left hand side.
    * 
    * Examples: {"event": "subscribe", "topic": "/"}
    *  {"event": "subscribe", "topic": "/myApp", "filter": {includeFileContent: true, hiddenApps: "brill_cms"}}
    *   
    */
    @Event(value = "subscribe", topicMatches = "file:.*/$", permission="file_read") // Match any file Topic that ends in a /
    public void subscribeToTopicTree(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject filter =  message.getJsonObject("filter");
            String content = gitService.getFileTree(wsService.getWorkspace(session), topic, filter).toString();
            wsService.sendMessageToClient(session, "publish", topic, content);
            wsService.addSubscription(session, topic, filter);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Topic Tree subscription error.", e.getMessage() );
            log.error("Topic Tree exception.", e);
        }
    }

    /**
    * Publishes a topic tree. The tree needs to include base64 content for each of the files. 
    * Used by the CMS when doing a Copy and Paste of a directory.
    *   
    */
    @Event(value = "publish", topicMatches = "file:.*/$", permission="file_write") // Match any file Topic that ends in a /.
    public void publishTopicTree(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");

            JsonObject content = JsonUtils.getJsonObject(message, "content");

            gitService.saveFileTree(wsService.getWorkspace(session),topic, content);

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Topic Tree publish error.", e.getMessage() );
            log.error("Topic Tree Publish exception.", e);
        }
    }
}