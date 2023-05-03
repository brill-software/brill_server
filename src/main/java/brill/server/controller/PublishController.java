// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.controller;

import javax.json.JsonObject;
import javax.json.JsonValue;
import org.springframework.web.socket.WebSocketSession;
import brill.server.domain.Subscriber;
import brill.server.service.*;
import brill.server.webSockets.annotations.*;
import java.util.Base64;
import java.util.List;

/**
 * Publish Controller - publishes .json, .jsonc, .js, .sql and other topics.
 * 
 */
@WebSocketController
public class PublishController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublishController.class);

    private GitService gitService;
    private JavaScriptService jsService;
    private WebSocketService wsService;
    private DatabaseService db;
    // @Autowired
    public PublishController(GitService gitService, JavaScriptService jsService, WebSocketService wsService, DatabaseService db) {
        this.gitService = gitService;
        this.jsService = jsService;
        this.wsService = wsService;
        this.db = db;
    }

    /**
     * Publishes a .json topic. The JSON content is written to the local git respository. All Clients that have
     * subscribed to the topic are sent a "publish" event and the new content.
     * 
     * Example
     * {"event": "publish", "topic": "json:/resource/pageTitle.json", "content": "This is the new page title."}
     * 
     * The content can be a string, number, boolean, array or object
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event, topic and new content.
     */
    @Event(value = "publish", topicMatches = "json:/.*\\.(json|jsonc)$", permission="file_write")  // JSON or JSONC
    public void publishJsonContent(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            String content = "";
            topic = message.getString("topic");
            content = message.get("content").toString(); 
            gitService.saveFile(wsService.getWorkspace(session), topic, content);
           
            // Publish to any sessions that have subscribed to the topic using "json:".
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, content, 
                    false, topic.endsWith(".jsonc"));
            }

            // Publish to any sessions that have subscribed to the topic using "file:".
            String fileTopic = topic.replace("json:", "file:");
            List<Subscriber> fileSubscribers = wsService.getSubscribers(fileTopic);
            for (Subscriber subscriber : fileSubscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", fileTopic, content, 
                    true, topic.endsWith(".jsonc"));
            }

        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server publish error.", e.getMessage());
            log.error("Publication exception.", e);
        }
    }

    /**
     * Publishes a .json file. The JSON content is written to the local git respository. All Clients that have
     * subscribed to the topic are sent a "publish" event and the new content.
     * 
     * Example
     * {"event": "publish", "topic": "file:/resource/pageTitle.json", "content": {"base64": "<<< Base64 encoded contents>>>"}}
     * 
     * The content must contain base64 encoded data.
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event, topic and new content.
     */
    @Event(value = "publish", topicMatches = "file:/.*\\.(json|jsonc)$", permission="file_write")  // JSON or JSONC
    public void publishJson(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            if (!isContentBase64Encoded(message)) {
                throw new Exception("Content must contain an object with a key of 'base64' containing a base64 encoded string");
            }
                
            String content = new String(Base64.getDecoder().decode(message.getJsonObject("content").getString("base64")));
            gitService.saveFile(wsService.getWorkspace(session), topic, content);
     
            // Publish to any sessions that have subscribed to the topic using "file:".
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, content, 
                    true, topic.endsWith(".jsonc"));
            }

            // Publish to any sessions that have subscribed to the topic using "json:".
            String jsonTopic = topic.replace("file:", "json:");
            List<Subscriber> jsonSubscribers = wsService.getSubscribers(jsonTopic);
            for (Subscriber subscriber : jsonSubscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", jsonTopic, content, 
                    false, topic.endsWith(".jsonc"));
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server publish error.", e.getMessage());
            log.error("Publication exception.", e);
        }
    }

    /**
     * Publishes a .js topic. The JavaScript content is written to the local git respository. All Clients that have
     * subscribed to the topic are sent a "publish" event and the new content. The new content is 
     * created by running the JavaScript.
     * 
     * Example
     * {"event": "publish", "topic": "file:/database/readPage.js", "content": {"base64": "<<<< The .js file base 64 encoded >>>>"}}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event, topic and content .js file base 64 encoded.
     */    
    @Event(value = "publish", topicMatches = "file:/.*\\.js", permission="file_write") // JavaScript
    public void publishJavaScript(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            if (!isContentBase64Encoded(message)) {
                throw new Exception("Content must contain an object with a key of 'base64' containing a base64 encoded string");
            }
            String javaScript = new String(Base64.getDecoder().decode(message.getJsonObject("content").getString("base64")));
            gitService.saveFile(wsService.getWorkspace(session), topic, javaScript);
            
            // Publish to any sessions that have subscribed to the topic using "file:".
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, javaScript, true);
            }
            
            // Publish to any sessions that have subscribed to the topic using "javascript:". The JavaScript is executed.
            String javascriptTopic = topic.replace("file:", "javascript:");
            List<Subscriber> jsSubscribers = wsService.getSubscribers(javascriptTopic);
            for (Subscriber subscriber : jsSubscribers) {
                String result = jsService.execute(javaScript, "", subscriber.getFilter(), wsService.getUsername(session));
                wsService.sendMessageToClient(subscriber.getSession(), "publish", javascriptTopic, result);
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server publish error.", e.getMessage());
            log.error("Publication exception.", e);
        }
    }

    /**
     * Publishes a .sql file. The SQL content is written to the local git respository. All Clients that have
     * subscribed to the topic are sent a "publish" event and the new content. The new content is 
     * created by running the SQL.
     * 
     * Example
     * {"event": "publish", "topic": "/database/readPage.sql", "content": {"base64": "<<<< The .js file base 64 encoded >>>>"}}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event, topic and content .sql file base 64 encoded.
     */    
    @Event(value = "publish", topicMatches = "file:/.*\\.sql", permission="file_write") // SQL
    public void publishSql(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            if (!isContentBase64Encoded(message)) {
                throw new Exception("Content must contain an object with a key of 'base64' containing a base64 encoded string");
            }
            String sql = new String(Base64.getDecoder().decode(   message.getJsonObject("content").getString("base64")));
            gitService.saveFile(wsService.getWorkspace(session), topic, sql);
            
            // Publish to any sessions that have subscribed to the topic using "file:".
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, sql, true);
            }

            // Publish to any sessions that have subscribed to the topic using "query:".
            String queryTopic = topic.replace("file:", "query:");
            List<Subscriber> querySubscribers = wsService.getSubscribers(queryTopic);
            for (Subscriber subscriber : querySubscribers) {
                // Execute the SQL.
                JsonValue result = db.queryUsingNamedParameters(sql, (JsonObject)subscriber.getFilterJsonValue());
                wsService.sendMessageToClient(subscriber.getSession(), "publish", queryTopic, result.toString());
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server publish error.", e.getMessage());
            log.error("Publication exception.", e);
        }
    }

    /**
     * Publishes any topic other than .json, .jsonc, .js and .sql. 
     * 
     * Example
     * {"event": "publish", "topic": "file:/database/readPage.txt", "content": {"base64": "<<<<< base64 encoded >>>>>", "noOverwrite": false}}
     * 
     * @param session Web Socket session.
     * @param message JSON Object containing the event, topic and content base64 encoded.
     */    
    @Event(value = "publish", topicMatches = "file:/.*(?<!\\.js)(?<!\\.json)(?<!\\.jsonc)(?<!\\.sql)(?<!/)$", permission="file_write") // Any other than .json, .jsonc, .js and .sql
    public void publishFile(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            if (!isContentBase64Encoded(message)) {
                throw new Exception("Content must contain an object with a key of 'base64' containing a base64 encoded string");
            }
            int index = topic.lastIndexOf('.');
            if (index == -1 || index == topic.length() - 1) {
                throw new Exception("The topic must end with a file extension.");
            }
            JsonObject content = message.getJsonObject("content");
            boolean noOverwrite = false;
            if (content.containsKey("noOverwrite")) {
                noOverwrite = content.getBoolean("noOverwrite");
            }
            
            gitService.saveFile(wsService.getWorkspace(session), topic, content.getString("base64"), true, noOverwrite);
            
            // Publish to any sessions that have subscribed to the topic.
            List<Subscriber> subscribers = wsService.getSubscribers(topic);
            for (Subscriber subscriber : subscribers) {
                wsService.sendMessageToClient(subscriber.getSession(), "publish", topic, content.toString());
            }
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Server publish error.", e.getMessage());
            log.error("Publication exception.", e);
        }
    }

    private boolean isContentBase64Encoded(JsonObject message) {
        JsonObject contentObj =  message.getJsonObject("content");
        if (contentObj == null || !contentObj.containsKey("base64")) {
            return false;
        }
        return true;
    }
}