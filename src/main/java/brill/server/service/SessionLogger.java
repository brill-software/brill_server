package brill.server.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import brill.server.exception.AutomateIPException;

@Service
public class SessionLogger {
    
    private static final int TIMEOUT = 60;
    @Value("${log.sessions.to.db:false}")
    private Boolean logSessionsToDb;
    
    private DatabaseService db;
    public SessionLogger(DatabaseService db){
        this.db=db;
    }
    /*
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<JsonObject> callIpApiAsync(String ipMessage){
        return CompletableFuture.supplyAsync(() -> {
            try {
                return makeApiCall(ipMessage);
            } catch (AutomateIPException e) {
                e.printStackTrace();
                return null;
            }
        }, executor);
    }
     */
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
    
    private void logNewSession(String sessionId, HttpHeaders headers, String remoteIpAddr) throws AutomateIPException {
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
                String IP_API_Request = "?lang=en&fields=50205";
                String ipApiUrl = "http://ip-api.com/json/" + remoteIpAddr + IP_API_Request;
                URL url = new URL(ipApiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(TIMEOUT * 1000);
                connection.setConnectTimeout(TIMEOUT * 1000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setDoInput(true);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String inputLine;
                        StringBuilder response = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        String jsonResponse = response.toString();

                        JsonObject ipApiResponse = Json.createReader(new StringReader(jsonResponse)).readObject();
                        
                        String country = ipApiResponse.getString("country");
                        String city = ipApiResponse.getString("city");
                        String regionName = ipApiResponse.getString("regionName");

                        objBuilder.add("country", country);
                        objBuilder.add("city", city);
                        objBuilder.add("regionName", regionName);
                        objBuilder.add("messages", remoteIpAddr);
                        JsonObject jsonParams = objBuilder.build();
                        db.executeNamedParametersUpdate(sql, jsonParams);
                    }
                }else {
                    handleException(responseCode);
                }
            } catch (SocketTimeoutException e) {
                handleException(HttpURLConnection.HTTP_CLIENT_TIMEOUT);
            } catch (Exception e) {
                handleException(HttpURLConnection.HTTP_INTERNAL_ERROR);
            }
    }
    
    
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