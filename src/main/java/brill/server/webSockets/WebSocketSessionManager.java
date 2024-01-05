// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.webSockets;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import brill.server.service.SessionLogger;
import brill.server.service.DatabaseService;

/**
 * WebSocket Session Manager - maintains a map of the active WebSocket session id's and sessions.
 * 
 */
@Component
public class WebSocketSessionManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketSessionManager.class);
    private DatabaseService db;
    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;

    @Value("${permissions.default:}")
    private String permissionsDefault;

    private static String PERMISSIONS = "permissions";

    
    private final SessionLogger sessionLogger;
    
    public WebSocketSessionManager(SessionLogger sessionLogger) {
        this.sessionLogger = sessionLogger;
    }


    private Map<String,WebSocketSession> activeSessions = new ConcurrentHashMap<String,WebSocketSession>();

    public void addSession(WebSocketSession session) {
        
        activeSessions.put(session.getId(), session);

        log.debug("New WebSocket session for IP address: " + session.getRemoteAddress());

        sessionLogger.logNewSessionToDb(session.getId(), session.getHandshakeHeaders(), session.getRemoteAddress().toString());

        // The permissions.default parameter specifies the initial permissions the user has before they are
        // logged in. A user might for example need db_write to complete a feedback form when not logged in. 
        List<String> list = Arrays.asList(permissionsDefault.split(","));
        Map<String, Object> map = session.getAttributes();
        map.put(PERMISSIONS, list);
    }
    
    public void removeSession(WebSocketSession session) {
        activeSessions.remove(session.getId());

        log.debug("Removing WebSocket session for IP address: " + session.getRemoteAddress());
        if (logSessionsToDb) { 
           logEndSessionToDb(session);
        }
    }

    public Map<String,WebSocketSession> getActiveSessions() {
        return activeSessions;
    }
    
    
    
    private void logEndSessionToDb(WebSocketSession session) {
        try {
            String sql = "update session_log set end_date_time = :endDateTime where session_id = :sessionId";
            String currentTime = LocalDateTime.now().toString();
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            JsonObject jsonParams = objBuilder.add("sessionId", session.getId())
                .add("endDateTime", currentTime).build();
            db.executeNamedParametersUpdate(sql, jsonParams);
        } catch (SQLException e) { 
            log.warn("Unble to log end session details to DB table session_page_log: " + e.getMessage()); 
        }
    }
}