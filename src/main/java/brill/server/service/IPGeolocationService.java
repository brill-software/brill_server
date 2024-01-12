package brill.server.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * IP Geolocation Service - finds the Country, City and Region of an IP address using the ip-api.com service.
 * 
 * See https://ip-api.com/docs/api:json for details of the API.
 * 
 */
@Service
public class IPGeolocationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IPGeolocationService.class);

    private static final int TIMEOUT = 60;

    private boolean serviceEnabled;

    public IPGeolocationService(@Value("${log.ip.session.to.api:false}") Boolean serviceEnabled) {
        this.serviceEnabled = serviceEnabled;
    }
    
    /**
     * 
     * 
     * @param remoteIpAddr
     * @return Either a map containing the country, city and regionName or null.
     */
    public Map<String,String> findIPLocation(String remoteIpAddr) {
        if (!serviceEnabled) {
            return null;
        }

        Map<String,String> location = new TreeMap<String,String>();
        String IP_API_Request = "?lang=en&fields=50205";
        String ipApiUrl = "http://ip-api.com/json/" + remoteIpAddr + IP_API_Request;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(ipApiUrl);
            connection = (HttpURLConnection) url.openConnection();

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
                    location.put("country",country);
                    location.put("city", city);
                    location.put("regionName", regionName);

                    // Get X-Rl to see if it's getting low.
                    String remainingCount = connection.getHeaderField("X-Rl");
                    log.trace("Remaining count = " + remainingCount);

                    String timeToNextReset = connection.getHeaderField("X-Ttl");
                    log.trace("Time to next reset = " + timeToNextReset);

                    return location; // Success
                }
            }
            switch (responseCode) {
                case 400: log.error("Bad request. (400)");
                case 401: log.error("Unauthrorized. (401)");
                case 404: log.error("IP Geolocation API not availble. (404).");
                case 429: log.error("Rate overflow.");
                case 502: log.error("Bad gateway. (502).");
                default: if (responseCode != 200) {
                    log.error("HTTP error occurred with response code:. (" + responseCode + ")");
                }
            }
        } catch (SocketTimeoutException e) {
            log.error("Timeout: No response received within " + TIMEOUT + " seconds.");
        } catch (IOException e) {
            log.error("IO Exception occurred: ", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;    
    }
}