package brill.server.service;

import java.net.HttpURLConnection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import brill.server.exception.AutomateIPException;

@Service
public class SessionLogger {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionLogger.class);
    
    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;



    private static final int TIMEOUT = 60;

    private IPGeolocationService ipGeolocationService;
    private DatabaseService db;
    
    public SessionLogger(DatabaseService db, IPGeolocationService ipGeolocationService){
        this.db = db;
        this.ipGeolocationService = ipGeolocationService;
    }
    
    public void logNewSessionToDb(String sessionId, HttpHeaders headers, String remoteIpAddr){
        if (!logSessionsToDb) {
            return;
        }
       
        Thread apiThread=new Thread(
            () -> { 
                    try {
                        logNewSession(sessionId, headers, remoteIpAddr);
                    } catch (Exception e) {
                        log.error("Unexpected exception", e);
                    }
                });
        apiThread.start();            
    }

    
    private void logNewSession(String sessionId, HttpHeaders headers, String remoteIpAddr) throws AutomateIPException{
            remoteIpAddr=remoteIpAddr.replace("/","");
            String sql = "insert session_log (session_id, start_date_time, end_date_time, user_agent, ip_address, country, city, region_name, notes) values ( " +
            ":sessionId, :startDateTime, :endDateTime, :userAgent, :ipAddress, :country, :city, :regionName, :notes )";
            String userAgent =  headers.containsKey("user-agent") ? headers.getFirst("user-agent") : "";
            String currentTime = LocalDateTime.now().toString();
            try {
                JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                objBuilder.add("sessionId", sessionId)
                        .add("startDateTime", currentTime)
                        .add("endDateTime", JsonValue.NULL)
                        .add("userAgent", userAgent)
                        .add("ipAddress", remoteIpAddr)
                        .add("notes", "");
               
                // here we call the request to ip_api to get the city, country of the session
                // the Json response could be customized from the ip_api website:https://ip-api.com/docs/api:json 

                Map<String,String> location = ipGeolocationService.findIPLocation(remoteIpAddr);
                objBuilder.add("country", (location != null ? location.get("country") : ""))
                        .add("city", (location != null ? location.get("city") : ""))
                        .add("regionName", (location != null ? location.get("regionName") : ""));           
    
                JsonObject jsonParams = objBuilder.build();
                db.executeNamedParametersUpdate(sql, jsonParams);

            } catch(SQLException e){
                log.warn("SessionLogger: ", e.getMessage());
            } 
            catch (Exception e) {
                switch (HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    case 400:
                        log.error("Bad request. (400)");
                        break;
                    case 401:
                        log.error("Unauthorized. (401)");
                        break;
                    case 404:
                        log.error("IP_API not available. (404)");
                        break;
                    case 429:
                        log.warn("Too many requests. Discarding next 100 request. (429)");
                        break;
                    case 502:
                        log.error("Bad gateway. (502)");
                        break;
                    case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                        log.error("Timeout: No response received within " + TIMEOUT + " seconds.");
                        break;
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        log.error("Internal server error.");
                        break;
                    default:
                        if (HttpURLConnection.HTTP_INTERNAL_ERROR != HttpURLConnection.HTTP_OK) {
                            log.error("Response error. (" + HttpURLConnection.HTTP_INTERNAL_ERROR + ")");
                        }
                }
            }
    }
    public void logEndSessionToDb(WebSocketSession session) {
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