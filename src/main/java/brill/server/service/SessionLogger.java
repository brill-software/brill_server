package brill.server.service;

import java.net.HttpURLConnection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private static final int TIMEOUT = 60;

    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;
    
    @Value("${log.ip.session.to.api:false}")
    private Boolean logSessionToAPI;

    private SortSessionIP sortSessionIP;
    
    private DatabaseService db;
    
    public SessionLogger(DatabaseService db){
        this.db=db;
    }
    
    public void logNewSessionToDb(String sessionId, HttpHeaders headers, String remoteIpAddr){
        if (!logSessionsToDb) {
            return;
        }
        Thread apiThread=new Thread(() ->{try {
            logNewSession(sessionId, headers, remoteIpAddr);
        } catch (AutomateIPException e) {
            e.printStackTrace();
        }});
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
                if (logSessionToAPI) {
                    // here we call the request to ip_api to get the city, country of the session
                    // the Json response could be customized from the ip_api website:https://ip-api.com/docs/api:json  
                    List<String>theLocation=new ArrayList<String>();
                    theLocation=sortSessionIP.findIPLocation(remoteIpAddr);
                    objBuilder.add("country", theLocation.get(0));
                    objBuilder.add("city", theLocation.get(1));
                    objBuilder.add("regionName", theLocation.get(2));
                    objBuilder.add("messages", remoteIpAddr);
                }
                JsonObject jsonParams = objBuilder.build();
                db.executeNamedParametersUpdate(sql, jsonParams);
            }catch(SQLException e){
                log.warn("Error while updating session end time in the database: ", e.getMessage());
            } 
            catch (Exception e) {
                switch (HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    case 400:
                        log.error("Bad request. (400)");
                    case 401:
                        log.error("Unauthorized. (401)");
                    case 404:
                        log.error("IP_API not available. (404)");
                    case 502:
                        log.error("Bad gateway. (502)");
                    case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                        log.error("Timeout: No response received within " + TIMEOUT + " seconds.");
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        log.error("Internal server error.");
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
    
    /*
    private void handleException(int responseCode) throws AutomateIPException {
        switch (responseCode) {
            case 400:
                throw new AutomateIPException("Bad request. (400)");
            case 401:
                throw new AutomateIPException("Unauthorized. (401)");
            case 404:
                throw new AutomateIPException("IP_API not available. (404)");
            case 502:
                throw new AutomateIPException("Bad gateway. (502)");
            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                throw new AutomateIPException("Timeout: No response received within " + TIMEOUT + " seconds.");
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                throw new AutomateIPException("Internal server error.");
            default:
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new AutomateIPException("Response error. (" + responseCode + ")");
                }
        }
    }
    */
    
    /* 
    public void exportResult(JsonArray result) {
        try (FileWriter file = new FileWriter("result.json")) {
            file.write(result.toString());
            System.out.println("Result exported to result.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */
}