// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.webSockets;

import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import brill.server.exception.SecurityServiceException;
import brill.server.service.DatabaseService;
import brill.server.service.SecurityService;
import brill.server.service.WebSocketService;
import brill.server.utils.LogUtils;
import brill.server.webSockets.annotations.*;
import static java.lang.String.format;

/**
 * WebSocket Manager - handles incomming WebSocket text messages.
 * 
 */
@Component
public class WebSocketManager extends TextWebSocketHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketManager.class);

    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;

    @Autowired
    private WebSocketSessionManager webSocketSessionManager;

    @Autowired
    private WebSocketService wsService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private DatabaseService db;

    // Injects a list of classes that have the @WebSocketController annotation
    @Autowired
    @WebSocketController
    private List<Object> webSocketControllers;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws java.lang.Exception {
        webSocketSessionManager.addSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        webSocketSessionManager.removeSession(session);
    }

    /**
     * Handles a WebSocket text message. Checks each of the @WebSocketController classes and calls any
     * methods that have an @Event that matches. Uses reflection.
     * 
     * @param session Web Socket session.
     * @param TextMessage Received WebSocket Text message.
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage request) {
        String topic = "";
        try {
            log.trace("IP: " + session.getRemoteAddress() + " Msg: " + LogUtils.truncate(request.getPayload()));
            int callCount = 0;
            JsonObject message = Json.createReader(new StringReader(request.getPayload())).readObject();
            String event = message.containsKey("event") ? message.getString("event") : "";
            topic = message.containsKey("topic") ? message.getString("topic") : "";
            for (Object wsController : webSocketControllers) {
                Class<?> clazz = wsController.getClass();
                // Go through each method of the class
                for (Method method : clazz.getDeclaredMethods()) {
                    // Check for @Event annotation
                    if (method.isAnnotationPresent(Event.class)) { 
                        Event eventAnnotation = method.getAnnotation(Event.class);
                        String eventValue = eventAnnotation.value();
                        // Check if the Event matches
                        String topicMatches = eventAnnotation.topicMatches();
                        if (event.equals(eventValue) && 
                            (topic.length() == 0 || topicMatches.length() == 0 || topic.matches(topicMatches))) {
                            // Call the method along with the parameters
                            String permission = eventAnnotation.permission();
                            if (permission.length() > 0) {
                                securityService.checkUserHasPermission(session, event, topic, permission);
                            }
                            method.invoke(wsController, getParams(method, session, message));
                            callCount++;
                        }
                    }
                }
            }
            if (callCount == 0) {
                log.error("No @Event method matches Topic " + topic);
                wsService.sendErrorToClient(session, topic, "Server Error.", format("No server event method for <b>%s</b> to topic <b>%s</b>", event, topic));
            } else if (callCount > 1) {
                log.error("More than one @Event method matches Topic " + topic);
                wsService.sendErrorToClient(session, topic, "Server Error.", "More than one server event method for topic.");
            }
            if (logSessionsToDb && event.equals("subscribe") && topic.contains("/Pages/")) {
                logPageAccessToDb(session, topic);
            }
        } catch (SecurityServiceException e) {
            wsService.sendErrorToClient(session, topic, "No Permission", e.getMessage());
        }
        catch (Throwable e) {
            try {
                log.error("Exception while processing WebSocket message.", e);
                wsService.sendErrorToClient(session, "", "Server error.", e.getMessage() + request.getPayload());
            } catch (Throwable t) {
                log.error("Exception while sending error to Client: " + t.getMessage());
            }
        } 
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        log.trace("Pong received from the client.");
        super.handlePongMessage(session, message);
    }

    /**
     * Puts the method parameters into an array ready for invoking the method. Requires the parameters to be annotated
     * with @Session and @Message.
     * 
     * @param method The method to be invoked.
     * @param session The WebSocket session to be passed as a parameter.
     * @param message The message to be passed as a parameter.
     * @return An array containing the parameters.
     */
    private Object[] getParams(Method method, WebSocketSession session, JsonObject message) {
        ArrayList<Object> params = new ArrayList<Object>();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        int paramNum = 0;
        for (Annotation[] annotationsForParam : annotations) {
            for (Annotation annotation : annotationsForParam) {
                if (annotation instanceof Session) {
                    if (!parameterTypes[paramNum].getName().endsWith("WebSocketSession")) {
                        log.warn(format("Annotation @Session parameter is expected to be of type WebSocketSession for method %s",
                            method.getName()));
                    }
                    params.add(session);
                } else 
                if (annotation instanceof Message) {
                    if (!parameterTypes[paramNum].getName().endsWith("JsonObject")) {
                        log.warn(format("Annotation @Message parameter is expected to be of type JsonObject for method %s",
                            method.getName()));
                    }
                    params.add(message);
                } else {
                    log.error(format("@WebSocketController: Unexpected parameter annotation @%s. Method: %s", 
                        annotation.getClass().getName(), method.getName()));
                }
            }
            paramNum++;
        }
        return params.toArray();
    }

    private void logPageAccessToDb(WebSocketSession session, String topic) {
        try {
            String sql = "insert session_page_log (session_id, date_time, page) values ( :sessionId, :dateTime, :page)";
            String currentTime = LocalDateTime.now().toString();
            String page = topic.substring(topic.indexOf(":") + 1);
            JsonObject jsonParams = Json.createObjectBuilder().add("sessionId", session.getId())
                .add("dateTime", currentTime)
                .add("page", page).build();
            db.executeNamedParametersUpdate(sql, jsonParams);
        } catch (SQLException e) { 
            log.warn("Unble to log page access to DB table session_page_log: " + e.getMessage());
        }
    }
}