// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.webSockets;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import brill.server.service.SessionLoggerService;
/**
 * WebSocket Session Manager - maintains a map of the active WebSocket session id's and sessions.
 * 
 */
@Component
public class WebSocketSessionManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketSessionManager.class);

    @Value("${permissions.default:}")
    private String permissionsDefault;

    private static String PERMISSIONS = "permissions";

    
    private final SessionLoggerService sessionLogger;
    
    public WebSocketSessionManager(SessionLoggerService sessionLogger) {
        this.sessionLogger = sessionLogger;
    }


    private Map<String,WebSocketSession> activeSessions = new ConcurrentHashMap<String,WebSocketSession>();

    public void addSession(WebSocketSession session) {
        
        activeSessions.put(session.getId(), session);

        log.debug("New WebSocket session for IP address: " + session.getRemoteAddress());

        String userAgent =  session.getHandshakeHeaders().containsKey("user-agent") ? session.getHandshakeHeaders().getFirst("user-agent") : "";
        sessionLogger.logNewSessionToDb(session.getId(), userAgent, session.getRemoteAddress().getAddress().toString());

        // The permissions.default parameter specifies the initial permissions the user has before they are
        // logged in. A user might for example need db_write to complete a feedback form when not logged in. 
        List<String> list = Arrays.asList(permissionsDefault.split(","));
        Map<String, Object> map = session.getAttributes();
        map.put(PERMISSIONS, list);
    }
    
    public void removeSession(WebSocketSession session) {
        activeSessions.remove(session.getId());
        sessionLogger.logEndSessionToDb(session.getId());
    }

    public Map<String,WebSocketSession> getActiveSessions() {
        return activeSessions;
    }
}