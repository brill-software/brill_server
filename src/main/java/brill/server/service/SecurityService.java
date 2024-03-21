// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import brill.server.exception.SecurityServiceException;
import static java.lang.String.format;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

/**
 * Security Services
 * 
 */
@Service
public class SecurityService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityService.class);

    @Autowired
    WebSocketService wsService;

    public void checkUserHasPermission(WebSocketSession session, String event, String topic, String permission) throws SecurityServiceException {
        if (!wsService.hasPermission(session, permission)) {
            log.warn(format("User %s requires permission %s to access topic %s", wsService.getUsername(session), permission, topic));
            // The message must contain the word "permission" for the client Router to re-direct users to the login page.
            throw new SecurityServiceException(format("Sorry but you don't have the <b>%s</b> permission, which is required for this action.<br/><br/>" +
                "Please ask an Administrator to provide you with the permission or ask someone that has the permission to perform the action.", permission));
        }
    }

    public void checkPermission(WebSocketSession session, String content) throws SecurityServiceException {
        String contentStr = content.stripLeading();
        if (contentStr.startsWith("{")) {
            JsonReader reader = Json.createReader(new StringReader(content));
            JsonObject contentObj = reader.readObject();
            if (contentObj.containsKey("permission")) {
                String requiredPermission = contentObj.getString("permission");
                if (requiredPermission.length() == 0) {
                    return;
                }
                if (!wsService.hasPermission(session, requiredPermission)) {
                    String username = wsService.getUsername(session);
                    // Note that the client relies on the error message containing "Please login" or "permission"
                    if (username.length() == 0) {
                        throw new SecurityServiceException(format("Session expired or not logged in. Please login.", requiredPermission));
                    } else {
                        throw new SecurityServiceException(format("Sorry but you don't have the <b>%s</b> permission.", requiredPermission));
                    }  
                }
            }
        }
    }
}