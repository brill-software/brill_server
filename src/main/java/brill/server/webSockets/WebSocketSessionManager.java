// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.webSockets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket Session Manager - maintains a map of the active WebSocket session id's and sessions.
 * 
 */
@Component
public class WebSocketSessionManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketSessionManager.class);
    
    private Map<String,WebSocketSession> activeSessions = new ConcurrentHashMap<String,WebSocketSession>();

    public void addSession(WebSocketSession session) {
        log.debug("New WebSocket session for IP address: " + session.getRemoteAddress());
        activeSessions.put(session.getId(), session); 
    }
    
    public void removeSession(WebSocketSession session) {
        log.debug("Removing WebSocket session for IP address: " + session.getRemoteAddress());
        activeSessions.remove(session.getId());
    }

    public Map<String,WebSocketSession> getActiveSessions() {
        return activeSessions;
    }
}