package brill.server.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.springframework.stereotype.Service;
import brill.server.exception.AutomateIPException;

@Service
public class AutomateIPService {
   
    private static final int TIMEOUT = 60;
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
    private JsonObject makeApiCall(String ipMessage) throws AutomateIPException {
        JsonObject resultObject = null;
        int responseCode = -1;
        try {
            String IP_API_Request = "?lang=en&fields=50205";
            String ipApiUrl = "http://ip-api.com/json/" + ipMessage + IP_API_Request;

            URL url = new URL(ipApiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(TIMEOUT * 1000);
            connection.setConnectTimeout(TIMEOUT * 1000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");

            connection.setDoOutput(true);
            connection.setDoInput(true);

            responseCode = connection.getResponseCode();
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

                    JsonObjectBuilder resultObjBuilder = Json.createObjectBuilder();
                    resultObjBuilder.add("country", country);
                    resultObjBuilder.add("city", city);
                    resultObjBuilder.add("regionName", regionName);
                    resultObjBuilder.add("messages", ipMessage);
                    resultObject = resultObjBuilder.build();
                }
            }
            connection.disconnect();
        } catch (SocketTimeoutException e) {
            throw new AutomateIPException("Timeout: No response received within " + TIMEOUT + " seconds.");
        } catch (Exception e) {
            handleException(responseCode, e);
        }
        return resultObject;
    }

    private void handleException(int responseCode, Exception e) throws AutomateIPException {
        switch (responseCode) {
            case 400:
                throw new AutomateIPException("Bad request. (400)");
            case 401:
                throw new AutomateIPException("Unauthorized. (401)");
            case 404:
                throw new AutomateIPException("IP_API not available. (404)");
            case 502:
                throw new AutomateIPException("Bad gateway. (502)");
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