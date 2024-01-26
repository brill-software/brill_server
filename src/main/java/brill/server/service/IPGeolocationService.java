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
 * IP Geolocation Service - finds the Country, City and Region of an IP address
 * using the ip-api.com service.
 * 
 * See https://ip-api.com/docs/api:json for details of the API.
 * 
 */
@Service
public class IPGeolocationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IPGeolocationService.class);

    private static int responseStatus;

    private static final int TIMEOUT = 60;

    private boolean serviceEnabled;

    public IPGeolocationService(@Value("${log.ip.session.to.geolocation:false}") Boolean serviceEnabled) {
        this.serviceEnabled = serviceEnabled;
    }

    /**
     * 
     * 
     * @param remoteIpAddr
     * @return Either a map containing the country, city and regionName or null.
     */
    public Map<String, String> findIPLocation(String remoteIpAddr) {
        if (!serviceEnabled) {
            return null;
        }

        Map<String, String> location = new TreeMap<String, String>();
        String IP_API_Request = "?lang=en&fields=50205";
        String ipApiUrl = "http://ip-apiX.com/json/" + remoteIpAddr + IP_API_Request;
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

            // int responseCode
            responseStatus = connection.getResponseCode();
            if (responseStatus == HttpURLConnection.HTTP_OK) {
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
                    location.put("country", country);
                    location.put("city", city);
                    location.put("regionName", regionName);

                    // Get X-Rl to see if it's getting low.
                    String remainingCount = connection.getHeaderField("X-Rl");
                    log.trace("Remaining count = " + remainingCount);

                    int remainingRequests = Integer.parseInt(remainingCount);

                    String timeToNextReset = connection.getHeaderField("X-Ttl");
                    log.trace("Time to next reset = " + timeToNextReset);

                    // Drop the request if no more remaining requests
                    if (remainingRequests <= 0) {
                        return null;
                    }
                    return location; // Success
                }
            }
            switch (responseStatus) {
                case 400:
                    log.error("Bad request. (400)");
                    break;
                case 401:
                    log.error("Unauthrorized. (401)");
                    break;
                case 404:
                    log.error("IP Geolocation API not availble. (404).");
                    break;
                case 429:
                    log.error("Rate overflow. (429)");
                    break;
                case 502:
                    log.error("Bad gateway. (502).");
                    break;
                default:
                    if (responseStatus != 200) {
                        log.error("HTTP error occurred with response code:. (" + responseStatus + ")");
                        break;
                    }
            }
        } catch (SocketTimeoutException e) {
            log.error("Timeout: No response received within " + TIMEOUT + " seconds.");
        } catch (IOException e) {
            log.error("IO Exception occurred: ", e.getMessage());
        } catch (NullPointerException e) {
            log.error("No response");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    // New method to get the last HTTP response status
    public int getLastResponseStatus() {
        return responseStatus;
    }
}