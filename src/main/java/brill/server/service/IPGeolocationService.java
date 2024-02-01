package brill.server.service;

import static java.lang.String.format;

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

import brill.server.exception.IPGeoServiceException;

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

    private static final int TIMEOUT = 20;
    private static final int REMAINING_REQUESTS_MIN = 20;
    private static int remainingRequests = 45;
    private static int timeToNextReset = 60;
    private static long lastRequestTime = 0;

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
    public Map<String, String> findIPLocation(String remoteIpAddr) throws IPGeoServiceException {
        if (!serviceEnabled) {
            return null;
        }

        if (remainingRequests <= REMAINING_REQUESTS_MIN && 
            System.currentTimeMillis() < lastRequestTime + (timeToNextReset * 1000)) {
            throw new IPGeoServiceException("Exceeded usage limit for IP geolocation server. Time to next reset = " +
                ((lastRequestTime + (timeToNextReset * 1000)) - System.currentTimeMillis()) / 1000 + "s.");
        }

        Map<String, String> location = new TreeMap<String, String>();
        String IP_API_Request = "?lang=en&fields=50911";
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

            int responseStatus = connection.getResponseCode();
            if (responseStatus == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    String jsonResponse = response.toString();

                    JsonObject ipApiResponse = Json.createReader(new StringReader(jsonResponse)).readObject();

                    String status = ipApiResponse.getString("status");
                    if (status.equals("fail")) {
                        throw new IPGeoServiceException(ipApiResponse.getString("message"));
                    }

                    location.put("country", ipApiResponse.getString("country"));
                    location.put("countryCode", ipApiResponse.getString("countryCode"));
                    location.put("region", ipApiResponse.getString("region"));
                    location.put("regionName", ipApiResponse.getString("regionName"));
                    location.put("city", ipApiResponse.getString("city"));
                    location.put("lat", String.valueOf(ipApiResponse.getJsonNumber("lat").doubleValue()));
                    location.put("lon", String.valueOf(ipApiResponse.getJsonNumber("lon").doubleValue()));
                    location.put("isp", ipApiResponse.getString("isp"));
                    location.put("org", ipApiResponse.getString("org"));

                    // Get X-Rl to see if it's getting low.
                    remainingRequests = Integer.parseInt(connection.getHeaderField("X-Rl"));
                    timeToNextReset = Integer.parseInt(connection.getHeaderField("X-Ttl"));
                    lastRequestTime = System.currentTimeMillis();

                    log.trace(format("Remaining count = %s ,time to next reset = %s",remainingRequests, timeToNextReset));

                    return location; // Success
                }
            }
            switch (responseStatus) {
                case 400:
                    throw new IPGeoServiceException("Bad request. (400)");
                case 401:
                    throw new IPGeoServiceException("Unauthrorized. (401)");
                case 404:
                    throw new IPGeoServiceException("IP Geolocation API not availble. (404).");
                case 429:
                    throw new IPGeoServiceException("Rate overflow. (429)");
                case 502:
                    throw new IPGeoServiceException("Bad gateway. (502).");
                default:
                    throw new IPGeoServiceException(("HTTP error occurred with response code:. (" + responseStatus + ")"));
            }
        } catch (SocketTimeoutException e) {
            throw new IPGeoServiceException("Timeout: No response received within " + TIMEOUT + " seconds.");
        } catch (IOException e) {
            throw new IPGeoServiceException("IO Exception occurred: " + e.getMessage());
        } catch (NullPointerException e) {
            throw new IPGeoServiceException("Null pointer exception.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}