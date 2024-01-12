package brill.server.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

public class SortSessionIP {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SortSessionIP.class);
    private static final int TIMEOUT = 60;
    
    public List<String> findIPLocation(String remoteIpAddr){
        List<String> location = new ArrayList<String>();
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
                    location.add(country);
                    location.add(city);
                    location.add(regionName);
                    return location;
                }
            } else {
                switch (responseCode) {
                    case 400: log.error("Bad request. (400)");
                    case 401: log.error("Unauthrorized. (401)");
                    case 404: log.error("Chatbot not availble. (404).");
                    case 502: log.error("Bad gateway. (502).");
                    default: if (responseCode != 200) {
                        log.error("HTTP error occurred with response code:. (" + responseCode + ")");
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            log.error("Timeout: No response received within "+TIMEOUT+" seconds.");
        } catch (IOException e) {
            log.error("IO Exception occurred: ", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return location;    
    }   
}
