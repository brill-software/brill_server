// © 2021 Brill Software Limited - Brill Middleware, distributed under the MIT License.
package brill.server.webSockets;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import brill.server.service.AutomateIPService;
import brill.server.service.DatabaseService;

/**
 * WebSocket Session Manager - maintains a map of the active WebSocket session id's and sessions.
 * 
 */
@Component
public class WebSocketSessionManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketSessionManager.class);
    
    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;

    @Value("${permissions.default:}")
    private String permissionsDefault;

    private static String PERMISSIONS = "permissions";

    private DatabaseService db;
    
    private final AutomateIPService automateIPService;

    public WebSocketSessionManager(DatabaseService db,AutomateIPService automateIPService) {
        this.db = db;
        this.automateIPService=automateIPService;
    }

    private Map<String,WebSocketSession> activeSessions = new ConcurrentHashMap<String,WebSocketSession>();

    public void addSession(WebSocketSession session) {
        
        activeSessions.put(session.getId(), session);

        log.debug("New WebSocket session for IP address: " + session.getRemoteAddress());

        if (logSessionsToDb) {
            logNewSessionToDb(session);
        }

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
    
    private void logNewSessionToDb(WebSocketSession session) {
            String sql = "insert session_log (session_id, start_date_time, end_date_time, user_agent, referrer, ip_address, country, city, region_name, notes) values ( " +
            ":sessionId, :startDateTime, :endDateTime, :userAgent, :referrer, :ipAddress, :country, :city, :regionName, :notes )";

            HttpHeaders headers = session.getHandshakeHeaders();
            String userAgent =  headers.containsKey("user-agent") ? headers.getFirst("user-agent") : "";
            String referrer = headers.containsKey("referer") ? headers.getFirst("referer") : ""; // Mis-splet version
            referrer = headers.containsKey("referrer") ? headers.getFirst("referrer") : "";
            String currentTime = LocalDateTime.now().toString();
            String remoteIpAddr = session.getRemoteAddress().getAddress().toString().replace("/","");

            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            objBuilder.add("sessionId", session.getId())
                    .add("startDateTime", currentTime)
                    .add("endDateTime", JsonValue.NULL)
                    .add("userAgent", userAgent)
                    .add("referrer", referrer)
                    .add("ipAddress", remoteIpAddr)
                    .add("notes", "");
         
            // Call the IP API asynchronously
            CompletableFuture<Void> apiResult = automateIPService.callIpApiAsync(remoteIpAddr)
                .thenAcceptAsync(result -> {
                    if (result == null) {
                        log.warn("Unable to fetch IP information for the session: " + session.getId());
                    } else {
                        String country = result.getString("country", "");
                        String city = result.getString("city", "");
                        String regionName = result.getString("regionName", "");

                        objBuilder.add("country", country)
                                .add("city", city)
                                .add("regionName", regionName);
                    }
                }).exceptionally(e -> {
                    log.error("Error while fetching IP information for the session: " + session.getId(), e);
                    return null;
                });

            apiResult.thenRun(() -> {
                try {
                    JsonObject jsonParams = objBuilder.build();
                    db.executeNamedParametersUpdate(sql, jsonParams);
                } catch (SQLException e) {
                    log.warn("Unable to log new session details to DB table session_log: " + e.getMessage());
                }
            });
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