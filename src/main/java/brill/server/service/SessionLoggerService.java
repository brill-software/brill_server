package brill.server.service;

import static java.lang.String.format;

import java.net.HttpURLConnection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import brill.server.exception.AutomateIPException;
import brill.server.exception.IPGeoServiceException;

@Service
public class SessionLoggerService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionLoggerService.class);

    private Boolean serviceEnabled;
    private IPGeolocationService ipGeolocationService;
    private DatabaseService db;

    public SessionLoggerService(@Value("${log.sessions.to.db:false}") Boolean serviceEnabled,
            DatabaseService db, IPGeolocationService ipGeolocationService) {
        this.serviceEnabled = serviceEnabled;
        this.db = db;
        this.ipGeolocationService = ipGeolocationService;
    }

    public void logNewSessionToDb(String sessionId, String userAgent, String remoteIpAddr) {
        if (!serviceEnabled) {
            return;
        }

        Thread apiThread = new Thread(
                () -> {
                    try {
                        logNewSession(sessionId, userAgent, remoteIpAddr);
                    } catch (Exception e) {
                        log.error("Unexpected exception", e);
                    }
                });
        apiThread.start();
    }

    public void logNewSession(String sessionId, String userAgent, String remoteIpAddr) throws AutomateIPException {
        remoteIpAddr = remoteIpAddr.replace("/", "");
        String sqlInsert = "insert session_log (session_id, start_date_time, end_date_time, user_agent, ip_address, country, city, region) values ( "
                + ":sessionId, :startDateTime, :endDateTime, :userAgent, :ipAddress, :country, :city, :region )";
        String currentTime = LocalDateTime.now().toString();
        try {

            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            objBuilder.add("sessionId", sessionId)
                    .add("startDateTime", currentTime)
                    .add("endDateTime", JsonValue.NULL)
                    .add("userAgent", userAgent)
                    .add("ipAddress", remoteIpAddr);

            // Get the geolocation details for the IP address.
            Map<String, String> location = null;
            try {
                location = ipGeolocationService.findIPLocation(remoteIpAddr);
            } catch (IPGeoServiceException e) {
                log.error(format("Unable to get geolocation details for IP address %s. Reason: %s", remoteIpAddr,
                        e.getMessage()));
            }

            objBuilder.add("country", (location != null ? location.get("country") : ""))
                    .add("city", (location != null ? location.get("city") : ""))
                    .add("region", (location != null ? location.get("regionName") : ""));

            JsonObject jsonParams = objBuilder.build();
            db.executeNamedParametersUpdate(sqlInsert, jsonParams);
        } catch (SQLException e) {
            log.warn("SessionLogger: " + e.getMessage());
        } catch (Exception e) {
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
                    log.error("Timeout: No response received within x seconds.");
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

    public void logEndSessionToDb(String sessionId) {
        if (!serviceEnabled) {
            return;
        }

        try {
            String sql = "update session_log set end_date_time = :endDateTime where session_id = :sessionId";
            String currentTime = LocalDateTime.now().toString();
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            JsonObject jsonParams = objBuilder.add("sessionId", sessionId)
                    .add("endDateTime", currentTime).build();
            db.executeNamedParametersUpdate(sql, jsonParams);
        } catch (SQLException e) {
            log.warn("Unble to log end session details to DB table session_page_log: " + e.getMessage());
        }
    }

    public void addMissingIPGeoData(String sessionId) {
        String selectSql = "select session_log_id from session_log where country == null OR country == ''";


    }


}