// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.controller;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import org.springframework.web.socket.WebSocketSession;
import brill.server.exception.SecurityServiceException;
import brill.server.exception.WebSocketException;
import brill.server.service.DatabaseService;
import brill.server.service.GitService;
import brill.server.service.JavaScriptService;
import brill.server.service.SecurityService;
import brill.server.service.WebSocketService;
import brill.server.webSockets.annotations.*;
import static java.lang.String.format;

/**
 * Subscribe Controller for topic leafs such as .json , .jsonc, .js and .sql and other extensions.
 * 
 */
@WebSocketController
public class SubscribeController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SubscribeController.class);

    private GitService gitService;
    private JavaScriptService jsService;
    private WebSocketService wsService;
    private DatabaseService db;
    private SecurityService securityService;
    // @Autowired
    public SubscribeController(GitService gitService, JavaScriptService jsService, WebSocketService wsService, 
                               DatabaseService db, SecurityService securitySerivce) {
        this.gitService = gitService;
        this.jsService = jsService;
        this.wsService = wsService;
        this.db = db;
        this.securityService = securitySerivce;
    }

    /**
     * Adds a subscription for a file Topic to the session subscriptions. The file contents are returned
     * base64 encoded.
     * 
     * Example: 
     * {"event": "subscribe", "topic": "file:/database/readTable.js"}
     * 
     * @param session Web Socket session.
     * @param message JSonObject containing the Topic.
     */
    @Event(value = "subscribe", topicMatches = "file:/.*\\..+")
    public void subscribeToFile(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            String fileContent = gitService.getFile(wsService.getWorkspace(session), topic);
            wsService.sendMessageToClient(session, "publish", topic, fileContent, true);
            wsService.addSubscription(session, topic);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server subscribe error.", e.getMessage());
            log.error("Subscribe .js exception.", e);
        }
    }

    /**
     * Adds a subscription for a .json or .jsonc Topic Leaf. If the git repository contains the Topic, the
     * content is published to the Client as JSON content. .jsonc is JSON with comments.
     * 
     * Example: {"event": "subscribe", "topic": "json:/brill_cms/app.json"}
     * 
     * By default all .json resources are accessable without requiring a permission. Access can be 
     * restricted by adding a "permission" field to the JSON.
     * 
     * {
     *  "page"
     * }
     * 
     * @param session Web Socket session.
     * @param message JSonObject containing the Topic of interest.
     */
    @Event(value = "subscribe", topicMatches = "json:/.*\\.(json|jsonc)$")  // JSON or JSONC
    public void subscribeToJson(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String content = "";
        try {
            topic = message.getString("topic");
            content = gitService.getFile(wsService.getWorkspace(session), topic);
            securityService.checkPermission(session, content);
            wsService.sendMessageToClient(session, "publish", topic, content, isFilterSpecifyingBase64(message), topic.endsWith(".jsonc"));
            wsService.addSubscription(session, topic);
        } catch (JsonParsingException e) {      
            if (content.contains("<<<<<<<") && content.contains("=======") && content.contains(">>>>>>>")) {
                log.error(format("Unable to parse JSON as it contains Git conflict markup. Topic = %s", topic));
                String msgContent = "{\"title\": \"Source Control Conflicts.\", \"detail\": \"Please close the tab and re-open with the JSON editor and do a manual merge. <b>Stage the file</b> and do a commit to complete the merge.\", " +
                                    "\"severity\": \"warning\"}";
                wsService.sendMessageToClient(session, "error", topic, msgContent);
                return;
            }
            log.error(format("JSON parsing exception: Please correct %s : Reason: %s", topic, e.getMessage()));
            String msgContent = "{\"title\": \"Server JSON parsing error.\", \"detail\": \"" + e.getMessage() + "\"}";
            wsService.sendMessageToClient(session, "error", topic, msgContent);
        } catch (SecurityServiceException e) {
            String msgContent = "{\"title\": \"No Permission.\", \"detail\": \"" + e.getMessage() + "\"}";
            wsService.sendMessageToClient(session, "error", topic, msgContent);
        } catch (Exception e) {
            log.error(format("Subscribe .json exception: %s", e.getMessage()));
            String msgContent = "{\"title\": \"Server subscribe error.\", \"detail\": \"" + e.getMessage() + "\"}";
            wsService.sendMessageToClient(session, "error", topic, msgContent);
        }
    }

    /**
     * Adds a subscription for a .js Topic to the session subscriptions. The JavaScript is
     * executed and the results published to the Client. The filter objects is passed into the JavaScript. 
     * The JavaScript is expected to return JSON object containing the results.
     * 
     * Example: 
     * {"event": "subscribe", "topic": "javascript:/db_app/database/readTable.js", "filter": {"page": 10}}
     * 
     * Its up to the JavaScript to check any permissions that are required.
     * 
     * @param session Web Socket session.
     * @param message JSonObject containing the subscription request.
     */ 
    @Event(value = "subscribe", topicMatches = "javascript:/.*\\.js") // JavaScript
    public void subscribeAndRunJavaScript(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject filterObj =  message.getJsonObject("filter");
            String javaScript = gitService.getFile(wsService.getWorkspace(session), topic);
            boolean dbWriteAllowed = wsService.hasPermission(session, "db_write");
            // Execute the JavaScript and publish the results to the Client.
            String results = jsService.execute(javaScript, "", filterObj.toString(), wsService.getUsername(session),
                dbWriteAllowed);
            wsService.sendMessageToClient(session, "publish", topic, results);       
            wsService.addSubscription(session, topic, filterObj);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server subscribe error.", e.getMessage());
            log.error("Subscribe .js exception.", e);
        }
    }

    /**
     * Adds a subscription for .sql Topics to the session subscriptions. The content is returned
     * as a JSON array of objects.
     * 
     * Example: {"event": "subscribe", "topic": "query:/db_app/database/query.sql"}
     * 
     * @param session Web Socket session.
     * @param message JSonObject containing the .sql Topic.
     */   
    @Event(value = "subscribe", topicMatches = "query:/.*\\.sql") // SQL
    public void subscribeToSqlAndExecute(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject filterObj =  message.getJsonObject("filter");
            String sql = gitService.getFile(wsService.getWorkspace(session), topic);
            
            // Execute the SQL.
            JsonValue result = db.queryUsingNamedParameters(sql, filterObj);
            wsService.sendMessageToClient(session, "publish", topic, result.toString());
            
            wsService.addSubscription(session, topic, filterObj);
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server subscribe error.", e.getMessage());
            log.error("Subscribe exception.", e);
        }
    }

    private boolean isFilterSpecifyingBase64(JsonObject message) {
        JsonObject filterObj =  message.getJsonObject("filter");
        if (filterObj == null) {
            return false;
        }
        return filterObj.getOrDefault("base64", JsonValue.FALSE) == JsonValue.TRUE;
    }

}