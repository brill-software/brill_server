// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
package brill.server.controller;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import org.springframework.web.socket.WebSocketSession;
import brill.server.service.ChatGPT;
import brill.server.service.WebSocketService;
import brill.server.webSockets.annotations.*;

/**
 * Chatbot - Uses ChatGPT as the backend. 
 * 
 */
@WebSocketController
public class ChatbotController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatbotController.class);

    private ChatGPT chatService;
    private WebSocketService wsService;

    // @Autowired
    public ChatbotController(ChatGPT chatService, WebSocketService wsService) {
        this.chatService = chatService;
        this.wsService = wsService;
    }

    /**
     * Sends a set of message to Chat GPT and returns the response.
     * 
     * Example:
     *  {"event": "request", "topic": "chatbot:/", "content": {
     *      messages: [ {"role": "system", "content": "Helpful assistant that answers in Markdown format."},
     *                  {"role": "user", "content": "What is the capital of Spain?"},
     *                  {"role": "assistant", "content": "The capital of Spain is Madrid."},
     *                  {"role": "user", "content": "How amny people live there?"},          
     *                ] }
     * 
     *  {"event": "response", "topic": "chatbot:/", "content": {
     *      messages: [ {"role": "system", "content": "Helpful assistant that answers in Markdown format."},
     *                  {"role": "user", "content": "What is the capital of Spain?"},
     *                  {"role": "assistant", "content": "The capital of Spain is Madrid."},     
     *                  {"role": "user", "content": "How amny people live there?"},
     *                  {"role": "assistant", "content": "Approximately 3.3 million people."},            
     *                ] }
     * 
     * Questions can make reference to the previous conversation and therefore the full chat history needs
     * to be maintained. The response includes the answer and also the previous conversation. 
     * 
     * The first message should spcify that the assistant must reply in Markdown format. Other formats such as
     * HTML and JSON can be specified but the answers don't always come back with the same markup, even for the same
     * question. Markdown is the most reliable format to get the answers in.
     * 
     * The asyncProcessing value of "yes" means that if the user navigates to a different page while waiting for the
     * chatbot, the next page will be displayed. Without setting this vaule, the user would have to wait for the chatbot to
     * responsed before they can doing anything else.
     * 
     * @param session Web Socket session.
     * @param message The content containing previous messages and the latest message.
     */
    @Event(value = "request", topicMatches = "chatbot:/.*", permission="chatbot", asyncProcessing="Yes")
    public void sendToChatbot(@Session WebSocketSession session, @Message JsonObject message) {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = message.getJsonObject("content");      
            JsonArray messages = content.getJsonArray("messages");
            JsonArray responseMsgs = chatService.call(messages);

            String response =Json.createObjectBuilder().add("messages", responseMsgs).build().toString();
           
            wsService.sendMessageToClient(session, "response", topic, response);
            
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Chatbot Error", e.getMessage());
            log.error("Chatbot error: ", e);
        }
    }
}