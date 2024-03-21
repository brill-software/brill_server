// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import brill.server.config.WebSocketConfig;
import brill.server.domain.Subscriber;
import brill.server.exception.CryptoServiceException;
import brill.server.exception.WebSocketException;
import brill.server.utils.DirUtils;
import brill.server.utils.JsonUtils;
import brill.server.utils.LogUtils;
import brill.server.webSockets.WebSocketSessionManager;
import java.util.Base64;
import static java.lang.String.format;

/**
 * WebSocket Services
 * 
 */
@Service
public class WebSocketService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketService.class);
    
    public static String ERROR_SEVERITY = "error";
    public static String WARNING_SEVERITY = "warning";
    public static String INFO_SEVERITY = "info";
    public static String SUCCESS_SEVERITY = "success";

    private static String WORKSPACE = "workspace";
    private static String USERNAME = "username";
    private static String FIRST_NAME = "first_name";
    private static String LAST_NAME = "last_name";
    private static String EMAIL = "email";
    private static String SUBSCRIPTIONS = "subscriptions";
    private static String PERMISSIONS = "permissions";
    private static String SHARED_SECRET = "shared_secret";

    private static String DEFAULT_WORKSPACE = "production";

    @Value("${server.sessionsDirectory:sessions}")
    private String sessionsDir;

    @Value("${logging.level.brill:info}")
    private String loggingLevel;

    private WebSocketSessionManager sessionManager;
    private CryptoService cryptoService;

    public WebSocketService(WebSocketSessionManager sessionManager, CryptoService cryptoService) {
         this.sessionManager = sessionManager;
         this.cryptoService = cryptoService;
    }

    /**
     * Sends a message to the client.
     * 
     * @param session The WebSocket session.
     * @param event Most of the time the event will be 'publish' or 'error'.
     * @param topic The topic.
     * @param content A string containing JSON or JSONC or binary content if it's to be base64 encoded.
     * @param base64EncodeContent Set to true to base64 encode the content.
     * @param jsonc Indicates the content is JSONC rather than JSON and therefore may contain comments.
     *      
     */
    public void sendMessageToClient(WebSocketSession session, String event, String topic, String content, 
                                    boolean base64EncodeContent, boolean jsonc) throws WebSocketException {
        try {
            JsonObjectBuilder jsonObjBuilder = Json.createObjectBuilder();
            jsonObjBuilder.add("event", event);
            jsonObjBuilder.add("topic", topic);
            
            if (base64EncodeContent) {
                String contentEncoded = new String(Base64.getEncoder().encode(content.getBytes()));
                JsonReader reader = Json.createReader(new StringReader("{\"base64\": \"" + contentEncoded + "\"}"));
                JsonValue contentValue = reader.readValue();
                jsonObjBuilder.add("content", contentValue);
            } else {
                if (content == null) {
                    content = "null";
                }
                if (jsonc) {
                    content = JsonUtils.stripComments(content);
                } else {
                    content = content.stripLeading();
                }
                JsonReader reader = Json.createReader(new StringReader(content));
                if (content.startsWith("{")) {
                    JsonObject contentObj = reader.readObject();
                    jsonObjBuilder.add("content", contentObj);
                } else {
                    if (content.startsWith("[")) {
                        JsonArray contentArray = reader.readArray();
                        jsonObjBuilder.add("content", contentArray);
                    } else {
                        if (content.length() != 0) {
                            JsonValue contentValue = reader.readValue();
                            jsonObjBuilder.add("content", contentValue);
                        } else {
                            jsonObjBuilder.add("content", "");
                        }
                    }
                } 
            }
            JsonObject responseObj = jsonObjBuilder.build();
            String response = responseObj.toString();
            if (response.length() > WebSocketConfig.WEB_SOCKET_MAX_MESSAGE_SIZE) {
                throw new WebSocketException(format("Maximum WebSocket message length of %s exceeded. Length = %s", 
                    WebSocketConfig.WEB_SOCKET_MAX_MESSAGE_SIZE, response.length()));
            }
            session.sendMessage(new TextMessage(responseObj.toString()));
            if (loggingLevel.equals("TRACE")) {
                log.trace(LogUtils.truncate(responseObj.toString()));
            }
        } catch (IOException ioe) {
            log.warn(format("WebSocket sendMessageToClient exception: %s",ioe.getMessage()));
        }
    }

    public void sendMessageToClient(WebSocketSession session, String event, String topic, String content, boolean base64EncodeContent) throws WebSocketException {
        sendMessageToClient(session, event, topic, content, base64EncodeContent, false);
    }

    public void sendMessageToClient(WebSocketSession session, String event, String topic, String content) throws WebSocketException {
        sendMessageToClient(session, event, topic, content, false, false);
    }

    /**
     * Send a error, warning or info message to the client.
     * 
     * @param session
     * @param topic
     * @param title
     * @param detail
     * @param severity error, warning or info
     */
    public void sendErrorToClient(WebSocketSession session, String topic, String title, String detail, String severity) {
        try {
            JsonObjectBuilder jsonObjBuilder = Json.createObjectBuilder();
            jsonObjBuilder.add("event", "error");
            if (topic.length() > 0) {
                jsonObjBuilder.add("topic", topic);
            }
            JsonObjectBuilder contentObjBuilder = Json.createObjectBuilder();
            JsonObject contentObj = contentObjBuilder.add("title", title).add("detail", detail).add("severity", severity).build();
            JsonObject jsonObj = jsonObjBuilder.add("content", contentObj).build();
            session.sendMessage(new TextMessage(jsonObj.toString()));
        } catch (IOException ioe) {
            log.error(format("WebSocket sendErrorToClient exception: %s",ioe.getMessage()));
        }
    }

    /**
     * Clears any error message for a topic.
     * 
     * @param session
     * @param topic
     * @param title
     * @param detail
     * @param severity error, warning or info
     */
    public void sendClearErrorToClient(WebSocketSession session, String topic) {
        try {
            JsonObjectBuilder jsonObjBuilder = Json.createObjectBuilder();
            jsonObjBuilder.add("event", "error");
            if (topic.length() > 0) {
                jsonObjBuilder.add("topic", topic);
            }            
            JsonObject jsonObj = jsonObjBuilder.add("content", JsonValue.NULL).build();
            session.sendMessage(new TextMessage(jsonObj.toString()));
        } catch (IOException ioe) {
            log.error(format("WebSocket sendMessageToClient exception: %s",ioe.getMessage()));
        }
    }

    public void sendErrorToClient(WebSocketSession session, String topic, String title, String detail) {
        sendErrorToClient(session, topic, title, detail, ERROR_SEVERITY);
    }

    public void addSubscription(WebSocketSession session, String topic, JsonValue filter) {
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(SUBSCRIPTIONS)) {
            @SuppressWarnings("unchecked")
            Map<String, String> subscriptions = (Map<String, String>) map.get(SUBSCRIPTIONS);
            String filterStr = filter.toString();
            subscriptions.put(topic, filterStr);
        } else {
            Map<String, String> subscriptions = new ConcurrentHashMap<String, String>();
            String filterStr = filter.toString();
            subscriptions.put(topic, filterStr);
            map.put(SUBSCRIPTIONS, subscriptions);
        }
        // Persist the session.
        serializeSession(session.getId(), map);
    }

    public void addSubscription(WebSocketSession session, String topic) {
        addSubscription(session, topic, JsonValue.EMPTY_JSON_OBJECT);
    }

    public void removeSubscription(WebSocketSession session, String topic) {
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(SUBSCRIPTIONS)) {
            @SuppressWarnings("unchecked")
            Map<String, String> subscriptions = (Map<String, String>) map.get(SUBSCRIPTIONS);
            subscriptions.remove(topic);
        } 
    }
    /**
     * Returns a list of sessions that are subscribed to a topic. Also includes the filter in
     * the returned data.
     * 
     * @param topic
     * @return List of Subscibers. A Subscriber object incudes the WedSocket session and filter.
     */
    public List<Subscriber> getSubscribers(String topic) {
        List<Subscriber> subscribers = new ArrayList<>();
        Map<String,WebSocketSession> sessions = sessionManager.getActiveSessions();
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            Map<String, Object> map = session.getAttributes();
            if (map.containsKey(SUBSCRIPTIONS)) {
                @SuppressWarnings("unchecked")
                Map<String,String> subscriptions = (Map<String,String>) map.get(SUBSCRIPTIONS);
                if (subscriptions.containsKey(topic)){
                    String filter = subscriptions.get(topic);
                    subscribers.add(new Subscriber(entry.getValue(), filter));
                }
            }
        }
        return subscribers;
    }

    public String getUsername(WebSocketSession session) {
        String username = "";
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(USERNAME)) {
            username = (String) map.get(USERNAME);
        }
        return username;
    }

    public void setUsername(WebSocketSession session, String username) {
        session.getAttributes().put(USERNAME, username);
    }

    public String getFirstName(WebSocketSession session) {
        String firstName = "";
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(FIRST_NAME)) {
            firstName = (String) map.get(FIRST_NAME);
        }
        return firstName;
    }

    public void setFirstName(WebSocketSession session, String firstName) {
        session.getAttributes().put(FIRST_NAME, firstName);
    }

    public String getLastName(WebSocketSession session) {
        String lastName = "";
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(LAST_NAME)) {
            lastName = (String) map.get(LAST_NAME);
        }
        return lastName;
    }

    public String getName(WebSocketSession session) {
        return this.getFirstName(session) + " " + this.getLastName(session);
    }

    public void setLastName(WebSocketSession session, String lastName) {
        session.getAttributes().put(LAST_NAME, lastName);
    }

    public String getEmail(WebSocketSession session) {
        String email = "";
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(EMAIL)) {
            email = (String) map.get(EMAIL);
        }
        return email;
    }  

    public void setEmail(WebSocketSession session, String email) {
        session.getAttributes().put(EMAIL, email);
    }

    /**
     * The workspace is the location of the git repository workspace from which all 
     * files are read and written. Production systems will normally use "master.
     * 
     * @param session
     * @return
    */
    public String getWorkspace(WebSocketSession session) {
        String workspace = DEFAULT_WORKSPACE;
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(WORKSPACE)) {
            workspace = (String) map.get(WORKSPACE);
        }      
        return workspace;
    }

    public void setWorkspace(WebSocketSession session, String workspace) {
        session.getAttributes().put(WORKSPACE, workspace);
    }

    /**
     * 
     * @param session
     * @param permissions Comma separated list of permissions.
     */
    public void setPermissions(WebSocketSession session, String permissions) {
        List<String> list = Arrays.asList(permissions.split(","));
        Map<String, Object> map = session.getAttributes();
        map.put(PERMISSIONS, list);
    }

    public boolean hasPermission(WebSocketSession session, String permission) {
        Map<String, Object> map = session.getAttributes();
        if (map.containsKey(PERMISSIONS)) {
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) map.get(PERMISSIONS);
            return permissions.contains(permission);
        } else {
            return false;
        } 
    }

   /**
     * An Elliptic Curve key pair is generated and used in conjunction with the Client Public Key
     * to create a Shared Secret. The generated Server Public Key is returned to the client, so
     * that the client can generate the same Shared Secret.
     *  
     * 
     * @return The Server Public Key as hex.
     */
    public String generateServerKeysAndSharedSecret(WebSocketSession session, String clientPublicKey) throws CryptoServiceException {
        Map<String, Object> map = session.getAttributes();
        KeyPairHex keyPairHex = cryptoService.generateKeyPair();
        String sharedSecretHex = cryptoService.getSharedSecret(keyPairHex.privateKey, clientPublicKey);
        map.put(SHARED_SECRET, sharedSecretHex);
        return keyPairHex.publicKey;
    }

    public String decrypt(WebSocketSession session, String cypherTextHex) throws CryptoServiceException {
        Map<String, Object> map = session.getAttributes();
        if (!map.containsKey(SHARED_SECRET)) {
            throw new CryptoServiceException("Please reload the page. Unable to encrypt or decrypt messages, as public keys have not been exchanged. ");
        }
        return cryptoService.decrypt(cypherTextHex, (String) map.get(SHARED_SECRET));
    }

    public void restoreSession(WebSocketSession session, String prevSessionId) throws WebSocketException {
        Map<String, Object> prevSessAttribs = deserializeSession(prevSessionId);
        Map<String, Object> currSessAttribs = session.getAttributes();

        // Check the username mataches
        if ((!prevSessAttribs.containsKey(USERNAME)) || (!prevSessAttribs.get(USERNAME).equals(currSessAttribs.get(USERNAME)))) {
            throw new WebSocketException(format("Can't reconnect as username in previous session %s doesn't match username supplied.", prevSessionId));
        }

        // Copy over all the attributes to the new session.
        for (Map.Entry<String, Object> entry : prevSessAttribs.entrySet()) {
            currSessAttribs.put(entry.getKey(), entry.getValue());
        } 

        deleteSerializedSession(prevSessionId);
        
        // Save new session in case connection goes down before the session does another subscribe.
        serializeSession(session.getId(), session.getAttributes());
    } 

    private void serializeSession(String sessionId, Map<String, Object> map) {
        try {
            checkSessionsDir();
            DirUtils.cleanUpOldFiles(sessionsDir, 1);
            FileOutputStream fileOutputStream = new FileOutputStream(format("%s/%s.txt", sessionsDir, sessionId));
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(map);
            objectOutputStream.flush();
            objectOutputStream.close();
        } catch (IOException e) {
            log.error("Exception while serializing session", e);
        }
    }

    private void checkSessionsDir() throws IOException {
        Path sessionsDirPath = Paths.get(sessionsDir);
        if (Files.notExists(sessionsDirPath)) {
            Files.createDirectories(sessionsDirPath);
        }
    }

    private  Map<String, Object> deserializeSession(String sessionId) throws WebSocketException {
        try {
            Path sessionPath = Paths.get(format("%s/%s.txt", sessionsDir, sessionId));
            if (Files.notExists(sessionPath)) {
                throw new WebSocketException("Unable to restore previous session. Please logout and login again.");
            }
            FileInputStream fileInputStream= new FileInputStream(format("%s/%s.txt", sessionsDir, sessionId));
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) objectInputStream.readObject();
            objectInputStream.close(); 
            return map;

        } catch (IOException | ClassNotFoundException e) {
            throw new WebSocketException("Exception while de-serializing session", e);
        }
    }

    private void deleteSerializedSession(String sessionId) {
        try {
            Path sessionsPath = Paths.get(format("%s/%s.txt", sessionsDir, sessionId));
            Files.deleteIfExists(sessionsPath);
        } catch (IOException e) {
            log.error(format("Unable to delete session file for session %s", sessionId), e);
        }
    }
}