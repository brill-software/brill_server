package brill.server.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArray;
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
        final String sqlInsert = "insert session_log (session_id, start_date_time, end_date_time, user_agent, ip_address_id) values ( "
            + ":sessionId, :startDateTime, :endDateTime, :userAgent, :ipAddressId)";

        // Remove an leading slash and any : parts.
        String ipAddress = remoteIpAddr.replace("/", "");
        if (ipAddress.contains(":")) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(":"));
        }
       
        try {
            int ipAddressId = getIpAddressId(ipAddress, false);

            JsonObject jsonParams = Json.createObjectBuilder().add("sessionId", sessionId)
                    .add("startDateTime", LocalDateTime.now().toString())
                    .add("endDateTime", JsonValue.NULL)
                    .add("userAgent", userAgent)
                    .add("ipAddressId", ipAddressId)
                    .build();

            db.executeNamedParametersUpdate(sqlInsert, jsonParams);

        } catch (SQLException e) {
            log.warn("SessionLogger SQL Exception: " + e.getMessage());
        } catch (Exception e) {
            log.error("SessionLoger Exception: " + e.getMessage());
        }
    }

    /**
     * The ip_address table maintains details of IP Addresses and contains the
     * geolocation data for each IP address.
     * 
     * @param ipAddress
     * @param throttle
     * @return
     * @throws SQLException
     * @throws IPGeoServiceException
     */
    private int getIpAddressId(String ipAddress, boolean throttle) throws SQLException, IPGeoServiceException {
        final String ipAddressSql = "select ip_address_id from ip_address where ip_address = :ipAddress";
        final String insertSql = "insert ip_address (ip_address, country, country_code, region, region_name, city, lat, lon, isp, org) values ( "
                + ":ipAddress, :country, :countryCode, :region, :regionName, :city, :lat, :lon, :isp, :org)";

        // See if ip_address table already contains the IP address.
        JsonObject jsonParams = Json.createObjectBuilder().add("ipAddress", ipAddress).build();
        JsonArray ipAddrResult = db.queryUsingNamedParameters(ipAddressSql, jsonParams);

        if (ipAddrResult.size() == 1) {
            // IP Address already in the ip_address table.
            return ipAddrResult.getJsonObject(0).getInt("ip_address_id");
        }

        if (throttle) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
        }

        // Add IP address and geolocation data to the ip_address table
        Map<String, String> location = ipGeolocationService.findIPLocation(ipAddress);
        JsonObject ipAddrParams = Json.createObjectBuilder()
            .add("ipAddress", ipAddress)
            .add("country", location != null ? location.get("country") : "")
            .add("countryCode", location != null ? location.get("countryCode") : "")
            .add("region", location != null ? location.get("region") : "")
            .add("regionName", location != null ? location.get("regionName") : "")
            .add("city", location != null ? location.get("city") : "")
            .add("lat", location != null ? location.get("lat") : "")
            .add("lon", location != null ? location.get("lon") : "")
            .add("isp", location != null ? location.get("isp") : "")
            .add("org", location != null ? location.get("org") : "")
            .build();
        db.executeNamedParametersUpdate(insertSql, ipAddrParams);

        JsonArray ipAddrResult2 = db.queryUsingNamedParameters(ipAddressSql, jsonParams);
        if (ipAddrResult2.size() != 1) {
            throw new SQLException("Unable to get ip_address_id");
        }
        
        // Return the id of the just inserted row.
        return ipAddrResult2.getJsonObject(0).getInt("ip_address_id");
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

    // /**
    //  * Goes through the session_log table and adds
    //  * 
    //  */
    // public void addMissingIPAddressId() {
    //     final String selectSql = "select session_log_id, ip_address from session_log where ip_address_id IS NULL limit 1000";
    //     final String updateIdSql2 = "update session_log set ip_address_id = :ipAddressId where session_log_id = :sessionLogId";
    //     try {
    //         JsonArray result = db.query(selectSql, null);
    //         System.out.println("Rows = " + result.size());

    //         for (int i = 0; i < result.size(); i++) {
                
    //             String sessionLogId = result.getJsonObject(i).getJsonNumber("session_log_id").numberValue().toString();
    //             String ipAddress = result.getJsonObject(i).getString("ip_address").replace("/", "");
    //             if (ipAddress.contains(":")) {
    //                 ipAddress = ipAddress.substring(0, ipAddress.indexOf(":"));
    //             }

    //             int ipAddressId;
    //             try {
    //                 ipAddressId = getIpAddressId(ipAddress, true);
    //             } catch (IPGeoServiceException e) {
    //                 log.error(format("Unable to get geolocation details for IP address %s. Reason: %s", ipAddress, e.getMessage()));
    //                 continue;
    //             }
              
    //             // Update the ip_address_id in the session_log row.
    //             JsonObject sessionLogIdParam = Json.createObjectBuilder().add("sessionLogId", sessionLogId).add("ipAddressId", ipAddressId).build();
    //             db.executeNamedParametersUpdate(updateIdSql2, sessionLogIdParam);
    //         }
    //     } catch (SQLException e) {
    //         log.debug("Uanble to add missing IP location data: " + e.getMessage());
    //     }
        
    // }
}