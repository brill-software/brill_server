package brill.server.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import brill.server.exception.ChatbotException;

@Service
public class ChatGPT {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatGPT.class);
 
    @Value("${chatbot.api.key:}")
    String apiKey;

    @Value("${chatbot.api.url:}")
    String apiUrl;

    @Value("${chatbot.model:gpt-3.5-turbo}")
    String model;

    @Value("${chatbot.timeout:60}")
    int timeout;

    private static double TEMPERATURE = 0.7;

    public JsonArray call(JsonArray messages) throws ChatbotException {
        JsonArray resultArray = null;
        int responseCode = -1;
        try {
            // Setup the connection.
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(timeout * 1000);
            connection.setConnectTimeout(timeout * 1000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");

            // Create the JSON request message.
            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            objBuilder.add("model", model);
            JsonArrayBuilder msgsArrayBuilder = Json.createArrayBuilder(messages);
            objBuilder.add("messages", msgsArrayBuilder);
            objBuilder.add("temperature", TEMPERATURE);
            JsonObject requestObj = objBuilder.build();
            String requestBody = requestObj.toString();

            log.trace("ChatGPT Request: " + requestBody);
            
            // Send the request to ChatCPT.
            connection.setDoOutput(true);
            connection.setDoInput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.writeBytes(requestBody);
                outputStream.flush(); 
            }

            // Get the Response Code
            responseCode = connection.getResponseCode();

            if (responseCode ==HttpURLConnection.HTTP_OK) {

                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    // Read the response.
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    String jsonResponse = response.toString();
                    log.debug("ChatGPT Response: " + jsonResponse);

                    // Create the result JSON array.
                    JsonReader jsonReader = Json.createReader(new StringReader(jsonResponse));
                    JsonObject responseObject = jsonReader.readObject();
                    JsonArray choices = responseObject.getJsonArray("choices");
                    JsonArrayBuilder resultArrayBuilder = Json.createArrayBuilder(messages);
                    for (JsonValue jsonValue : choices)
                    {
                        JsonObject jsonObject = jsonValue.asJsonObject();
                        JsonObject msgObject = jsonObject.getJsonObject("message");
                        String role = msgObject.getString("role");
                        String content = msgObject.getString("content");
                        JsonObjectBuilder newEntryBuilder = Json.createObjectBuilder();
                        newEntryBuilder.add("role", role);
                        newEntryBuilder.add("content", content);
                        resultArrayBuilder.add(newEntryBuilder.build());
                    }
                    resultArray = resultArrayBuilder.build();
                }
            }
            connection.disconnect();
        } catch (SocketTimeoutException e) {
            throw new ChatbotException("Timeout: No response recieved within " + timeout + " seconds.");
        }
        catch (Exception e) {
            throw new ChatbotException(e.getMessage());
        }

        // Return the result.
        switch (responseCode) {
            case 400: throw new ChatbotException("Bad request. (400)");
            case 401: throw new ChatbotException("Unauthrorized. (401)");
            case 404: throw new ChatbotException("Chatbot not availble. (404).");
            case 502: throw new ChatbotException("Bad gateway. (502).");
            default: if (responseCode != 200) {
                throw new ChatbotException("Response error. (" + responseCode + ")");
            }
        }
        return resultArray;
    }

    // public String getModelsAvailable() throws ChatbotException {
    //     int responseCode = -1;
    //     StringBuffer response = new StringBuffer();
    //     try {
    //         URL url = new URL("https://api.openai.com/v1/models");
    //         HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    //         connection.setRequestMethod("GET");
    //         connection.setRequestProperty("Authorization", "Bearer " + apiKey);
    //         connection.setRequestProperty("Content-Type", "application/json");

    //         responseCode = connection.getResponseCode();

    //         System.out.println("Reponse code = " + responseCode);

    //         BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    //         String inputLine;
    //         while ((inputLine = in.readLine()) != null) {
    //             response.append(inputLine);
    //         }
    //         in.close();
    //         return response.toString();

    //     } catch (Exception e) {
    //         throw new ChatbotException("Get models failed: " + e.getMessage());
    //     }
    // }
}
