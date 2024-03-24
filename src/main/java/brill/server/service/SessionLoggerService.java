package brill.server.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        final String sqlInsert = "insert session_log (session_id, start_date_time, end_date_time, user_agent_id, ip_address_id, visits) values ( "
             + ":sessionId, :startDateTime, :endDateTime, :userAgentId, :ipAddressId, " 
             + "(select count(0) from session_log sl where sl.ip_address_id = :ipAddressId) + 1)";

        // Remove an leading slash and any : parts.
        String ipAddress = remoteIpAddr.replace("/", "");
        if (ipAddress.contains(":")) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(":"));
        }
       
        try {
            int userAgentId = getUserAgentId(userAgent);
            int ipAddressId = getIpAddressId(ipAddress, false);

            JsonObject jsonParams = Json.createObjectBuilder().add("sessionId", sessionId)
                    .add("startDateTime", LocalDateTime.now().toString())
                    .add("endDateTime", JsonValue.NULL)
                    .add("userAgentId", userAgentId)
                    .add("ipAddressId", ipAddressId)
                    .build();

            db.executeNamedParametersUpdate(sqlInsert, jsonParams);

        } catch (SQLException e) {
            log.error("SessionLogger SQL Exception: " + e.getMessage());
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
            .add("lat", location != null ? location.get("lat") : "0.0")
            .add("lon", location != null ? location.get("lon") : "0.0")
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

    private int getUserAgentId(String userAgent) throws SQLException {
        final String userAgentSql = "select user_agent_id from user_agent where user_agent = :userAgent";
        final String insertSql = "insert user_agent (user_agent, os, browser, browser_version, mobile) values ( "
                + ":userAgent, :os, :browser, :browserVersion, :mobile)";

        if (userAgent.length() > 512) {
            userAgent = userAgent.substring(0, 512);
        }

        // See if ip_address table already contains the IP address.
        JsonObject jsonParams = Json.createObjectBuilder().add("userAgent", userAgent).build();
        JsonArray result = db.queryUsingNamedParameters(userAgentSql, jsonParams);

        if (result.size() == 1) {
            // IP Address already in the ip_address table.
            return result.getJsonObject(0).getInt("user_agent_id");
        }

        Map<String, String> browserInfo = findBrowserInfo(userAgent);

        JsonObject params = Json.createObjectBuilder()
            .add("userAgent", userAgent)
            .add("os", browserInfo.get("os"))
            .add("browser", browserInfo.get("browser"))
            .add("browserVersion", browserInfo.get("browserVersion"))
            .add("mobile", browserInfo.get("mobile"))
            .build();
        db.executeNamedParametersUpdate(insertSql, params);

        JsonArray result2 = db.queryUsingNamedParameters(userAgentSql, jsonParams);
        if (result2.size() != 1) {
            throw new SQLException("Unable to get user_agent_id");
        }
        
        // Return the id of the just inserted row.
        return result2.getJsonObject(0).getInt("user_agent_id");
    }

    private Map<String, String> findBrowserInfo(String userAgentString) {
        Map<String, String> browserInfo = new TreeMap<String, String>();
        browserInfo.put("os","Unknown");
        browserInfo.put("browser", "Unkown");
        browserInfo.put("browserVersion","Unknown");
        browserInfo.put("mobile", "");

        // ChatGPT generated code.
        String osPattern = ".*(Windows|Macintosh|Android|iOS|Linux).*";
        String browserPattern = ".*(Chrome|Firefox|Safari|Opera|MSIE|Trident|LinkedinApp|Instagram).*";
        String versionPattern = "(Chrome|Firefox|Safari|Opera|MSIE|rv)[\\/\\s]([\\d.]+)";

        Pattern os = Pattern.compile(osPattern, Pattern.CASE_INSENSITIVE);
        Pattern browser = Pattern.compile(browserPattern, Pattern.CASE_INSENSITIVE);
        Pattern version = Pattern.compile(versionPattern, Pattern.CASE_INSENSITIVE);

        if (userAgentString.contains("Chrome") && userAgentString.contains("Safari")) {
            userAgentString = userAgentString.replace("Safari", "");
            if (userAgentString.contains("Opera")) {
                userAgentString = userAgentString.replace("Chrome", "");
            }
        }
        if (userAgentString.contains("Opera") && userAgentString.contains("Chrome")) {
            userAgentString = userAgentString.replace("Chrome", "");
        }
        userAgentString = userAgentString.replace("iPhone", "iOS").
                            replace("Mozilla","Firefox");

        Matcher osMatcher = os.matcher(userAgentString);
        Matcher browserMatcher = browser.matcher(userAgentString);
        Matcher versionMatcher = version.matcher(userAgentString);

        if (osMatcher.find()) {
            browserInfo.put("os", osMatcher.group(1));
        }

        if (browserMatcher.find()) {
            browserInfo.put("browser", browserMatcher.group(1));
        }

        if (versionMatcher.find()) {
            browserInfo.put("browserVersion", versionMatcher.group(2));
        }

        // Check for mobile device
        boolean isMobile = userAgentString.matches(".*(Mobile|Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini).*");
        browserInfo.put("mobile", isMobile ? "Y" : "N");

        return browserInfo;
    }

    public void logEndSessionToDb(String sessionId) {
        if (!serviceEnabled) {
            return;
        }
        try {
            String sql = "update session_log set end_date_time = :endDateTime" +
                ", session_length = (UNIX_TIMESTAMP(:endDateTime) - UNIX_TIMESTAMP(session_log.start_date_time))" +
                " where session_id = :sessionId";
            String currentTime = LocalDateTime.now().toString();
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            JsonObject jsonParams = objBuilder.add("sessionId", sessionId)
                    .add("endDateTime", currentTime).build();
            db.executeNamedParametersUpdate(sql, jsonParams);
        } catch (SQLException e) {
            log.error("Unble to log end session details to DB table session_log: " + e.getMessage());
        }
    }

    public void addMissingUserAgentId() {
        final String selectSql = "select session_log_id, user_agent from session_log where user_agent_id IS NULL limit 4000";
        final String updateIdSql2 = "update session_log set user_agent_id = :userAgentId where session_log_id = :sessionLogId";
        try {
            JsonArray result = db.query(selectSql, null);
            System.out.println("Rows = " + result.size());

            for (int i = 0; i < result.size(); i++) {
                String sessionLogId = result.getJsonObject(i).getJsonNumber("session_log_id").numberValue().toString();
                String userAgent = result.getJsonObject(i).getString("user_agent");
                int userAgentId = getUserAgentId(userAgent);
              
                // Update the ip_address_id in the session_log row.
                JsonObject sessionLogIdParam = Json.createObjectBuilder().add("sessionLogId", sessionLogId).add("userAgentId", userAgentId).build();
                db.executeNamedParametersUpdate(updateIdSql2, sessionLogIdParam);
            }
        } catch (SQLException e) {
            log.debug("Uanble to add missing IP location data: " + e.getMessage());
        }
        
    }

    public void logPageAccessToDb(String sessionId, String topic) {
        if (!serviceEnabled) {
            return;
        }

        Thread apiThread = new Thread(
                () -> {
                    try {
                        logPageAccess(sessionId, topic);
                    } catch (Exception e) {
                        log.error("Unexpected exception", e);
                    }
                });
        apiThread.start();
    }

    public void logPageAccess(String sessionId, String topic) {
        try {
            // Add the page to the session_page_log table.
            String sql = "insert session_page_log (session_id, date_time, page) values ( :sessionId, :dateTime, :page)";
            String currentTime = LocalDateTime.now().toString();
            String page = topic.substring(topic.indexOf(":") + 1);
            JsonObject jsonParams = Json.createObjectBuilder().add("sessionId", sessionId)
                .add("dateTime", currentTime)
                .add("page", page).build();
            db.executeNamedParametersUpdate(sql, jsonParams);

            // Get the session start time and page count.
            String sql1 = "select start_date_time, pages from session_log where session_id = :sessionId";
            JsonObject jsonParam = Json.createObjectBuilder().add("sessionId", sessionId).build();
            JsonObject queryResult = db.queryUsingNamedParameters(sql1, jsonParam).getJsonObject(0);
            String startTime = queryResult.getString("start_date_time");
            int pages = queryResult.getInt("pages") + 1;
    
            // Updtate session_length and pages in the session_log table.
            String sql2 = "update session_log set session_length = ((UNIX_TIMESTAMP(:currentTime) - UNIX_TIMESTAMP(:startTime)))" +
                ", pages = :pages where session_id = :sessionId";
            JsonObject jsonParams2 = Json.createObjectBuilder()
                .add("sessionId", sessionId)
                .add("currentTime", currentTime)
                .add("startTime", startTime)
                .add("pages", pages).build();
            db.executeNamedParametersUpdate(sql2, jsonParams2);

        } catch (SQLException e) { 
            log.error("Unble to log page access to DB table session_page_log: " + e.getMessage());
        } catch (Exception e) { 
            log.error("Exception while updating session_page_log: " + e.getMessage());
        }
    }
}