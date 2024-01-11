// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.controller;

import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;
import brill.server.domain.Subscriber;
import brill.server.exception.MissingValueException;
import brill.server.exception.WebSocketException;
import brill.server.service.*;
import brill.server.utils.JsonUtils;
import brill.server.webSockets.annotations.*;
import static java.lang.String.format;
import java.util.List;


/**
 * JavaScript request/response Controller.
 * 
 * Executes a JavaScript file, passing in the content as a JSON object. Returns an object returned by the script.
 * 
 */
@WebSocketController
public class JavaScriptController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JavaScriptController.class);
    private GitService gitService;
    private WebSocketService wsService;
    private JavaScriptService jsService;

    // This is a special field that can be returned by JavaScript to indicate a topic that needs to be re-published.
    private static String REPUBLISH_TOPIC = "republishTopic";
    private static String ERROR_OBJECT = "error";
    private static String ERROR_TITLE = "title";
    private static String ERROR_DETAIL = "detail";

    // @Autowired
    public JavaScriptController(GitService gitService, WebSocketService wsService, JavaScriptService jsService) {
        this.gitService = gitService;
        this.wsService = wsService;
        this.jsService = jsService;
    }

    /**
     * Executes JavaScript using request/response messaging. This can be used for example when
     * updating a table row and the client needs to know when execution of the update has completed.
     * 
     * Example:
     * {"event":"request","topic":"query:/brill_cms/updateUser.js","content":{"name":"Tony Fisher"}}
     * {"event":"response","topic":"query:/brill_cms/updateUser.js","content":"count": 1}
     * 
     * The JavaScript can return a special field called republishTopic and a JS topic to re-publish.
     * This is used when a script updates a table row and subscribers need to receive the new updated table.
     * 
     * @param session Web Socket session.
     * @param message Request message with the content containing parameters for the JavaScript.
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "javascript:/.*\\.js")
    public void executeJavaScript(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String javaScript = gitService.getFile(wsService.getWorkspace(session), topic);

            boolean dbWriteAllowed = wsService.hasPermission(session, "db_write");

            // Execute the JavaScript.
            String result = jsService.execute(javaScript, content.toString(), "", wsService.getUsername(session), 
                dbWriteAllowed);

            JsonObject resultObj = JsonUtils.jsonFromString(result);
            if (resultObj.containsKey(ERROR_OBJECT)) {
                JsonObject errorObj = JsonUtils.getJsonObject(resultObj, ERROR_OBJECT);
                String title = JsonUtils.getString(errorObj, ERROR_TITLE);
                String detail = JsonUtils.getString(errorObj, ERROR_DETAIL);
                wsService.sendErrorToClient(session, topic, title, detail);
            } else {
                wsService.sendMessageToClient(session, "response", topic, result);
            }
            
            // If the result object contains a field called "republishTopic", the value is used to get a JS topic
            // and re-published the topic. This is used, for example, when a script updates a table and the subscribers
            // to the JS topic for reading the table need to be provided with the updated table contents.
            
            if (resultObj.containsKey(REPUBLISH_TOPIC)) {
                String republishTopic = resultObj.getString(REPUBLISH_TOPIC);
                String js = gitService.getFile(wsService.getWorkspace(session), republishTopic);
                List<Subscriber> jsSubscribers = wsService.getSubscribers(republishTopic);
                for (Subscriber subscriber : jsSubscribers) {
                    String res = jsService.execute(js, "", subscriber.getFilter(), wsService.getUsername(session), dbWriteAllowed);
                    wsService.sendMessageToClient(subscriber.getSession(), "publish", republishTopic, res);
                }
            }
        } catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "JS Error", e.getMessage() + "\n in " + topic);
            log.error("JS exception:", e);
        }
    }
}