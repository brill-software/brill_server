// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT License.
package brill.server.controller;

import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import org.springframework.web.socket.WebSocketSession;
import brill.server.exception.MissingValueException;
import brill.server.exception.WebSocketException;
import brill.server.service.*;
import brill.server.utils.JsonUtils;
import brill.server.webSockets.annotations.*;
import static brill.server.service.WebSocketService.*;   
import static java.lang.String.format;

/**
 * User authentication contoller.
 */
@WebSocketController
public class AuthenticationController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthenticationController.class);
    private WebSocketService wsService;
    private DatabaseService db;
    private PasswordService pwdService;
     private GitService gitService; 

    // @Autowired
    public AuthenticationController(WebSocketService wsService, DatabaseService db, PasswordService pwdService, GitService gitService) {
        this.wsService = wsService;
        this.db = db;
        this.pwdService = pwdService;
        this.gitService = gitService;
    }


    /**
     * Supports an Elliptic-curve Diffie–Hellman (ECDH) key exchange using the secp256k1 curve, for the purposes of 
     * creating a Shared Secret. The Shared Secret is used for encryption and decryption and also signing and 
     * verifying messages.
     * 
     * Use is made of the request/response messaging, as this is more secure than publish/subscribe. The request contains 
     * the Client Public Key. The response contains the Server Public key. Each side keeps their private key secret and 
     * once the Shared Secret is calculated, the public and private keys are deleted.
     * 
     * Example:
     * {"event":"request","topic":"auth:/brill_cms/server_public_key","content":{"clientPublicKey":"...65 bytes hex encoded..."}}
     * {"event":"response","topic":"auth:/brill_cms/server_public_key","content":"... 65 bytes hex encoded..."}
     * 
     * 
     * @param session Web Socket session.
     * @param message Request message with the content containing the Client Public Key hex encoded.
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "auth:/.*/server_public_key")
    public void requestServerPublicKey(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject content = JsonUtils.getJsonObject(message, "content");
            String clientPublicKey = JsonUtils.getString(content, "clientPublicKey");

            String serverPublicKey = wsService.generateServerKeysAndSharedSecret(session, clientPublicKey);
            wsService.sendMessageToClient(session, "response", topic,  "\"" + serverPublicKey + "\"");
        } catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Authentication Error", e.getMessage());
            log.error("Authentication exception:", e);
        }
    }

    /**
     * Authenticates the user and returns the user details. The content must contain the username and password. The
     * password is an encrypted SHA-256 hash of the cleartext password plus some pepper. The server never gets to see the 
     * cleartext password. The decrypted SHA-256 hash is used as the password by the server. The password is stored in the 
     * DB using a PBKDF2WithHmacSHA384 hash with a random salt and random iteration count.
     * 
     * Use is made of request/response messaging, rather than publish/subscribe. The password is encrypted using
     * the Shared Secret and AES encryption.
     * 
     * To hack into the admin account in case of loss of password, in application.yml set
     * 
     *      passwords.allowClearText: true
     * 
     * and re-start the server. Use MySQL Workbench to update the password field with a new clear text password. The clear text 
     * password must meet the password rules such as at least 8 characters and not easy to guess, otherwise it won't work.
     * 
     * Login and change the password. This will result in the changed password getting stored in the database as a hash.
     * 
     * Remeber to change passwords.allowClearText back to false.
     * 
     * Example:
     *  {"event":"request", "topic": "auth:/app_name/authenticate", "content": {"username":"chris", "password":"...32 bytes hex encoded ..."}}
     * 
     * @param session Web Socket session.
     * @param message Json Message containing the credentials in the filter.
     */
    @Event(value = "request", topicMatches = "auth:/.*/authenticate")
    public void authenticateUser(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        String username = "";
        try {
            topic = message.getString("topic");
            JsonObject credentials = JsonUtils.getJsonObject(message, "content"); 
            username = JsonUtils.getString(credentials, "username").toLowerCase();
            String encryptedPassword = JsonUtils.getString(credentials, "password");
            String password = wsService.decrypt(session, encryptedPassword);

            JsonObject response = db.getUserDetails(username);
            if (response == null) {
                wsService.sendErrorToClient(session, topic, "Login failed", "Incorrect Username / Password.", WebSocketService.WARNING_SEVERITY);
                return;
            }

            if (!pwdService.isValidPassword(username, password, response.getString("password"))) {
                wsService.sendErrorToClient(session, topic, "Login failed", "Incorrect Username / Password.", WebSocketService.WARNING_SEVERITY);
                return;
            }

            db.updateLastLoginDateTime(username);
            wsService.setUsername(session, username);
            wsService.setName(session, response.getString("name"));
            wsService.setEmail(session, response.getString("email"));
            String workspace = JsonUtils.getString(response, "workspace");
            if (workspace != null && workspace.length() > 0) {
                if (!gitService.doesWorkspaceAlreadyExist(workspace)) {
                   // Create the workspace
                   wsService.sendErrorToClient(session, topic, "Creating Workspace", "Please wait while the workspace is created.", INFO_SEVERITY);
                   gitService.createNewWorkspace(workspace, "master");
                }
                wsService.setWorkspace(session, workspace);
            }
            wsService.setPermissions(session, response.getString("permissions"));
            
            response = removePassword(response);
            response = JsonUtils.add(response, "sessionId", session.getId());
            
            String content = response.toString();
            wsService.sendMessageToClient(session, "response", topic, content);

        } 
        catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Authentication Failure", e.getMessage());
            log.error(format("Authentication exception for user %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Restores a session after a re-connection. When the user authenticates, a message is published
     * to the client containing the username and session id. Everytime a change occurs to the session
     * data, the server serializes the session to a file. If the Web Socket connection is lost, the 
     * client tries to re-connect every so often. On re-connection the client will send a request
     * to the reconnect topic and provide the username and previous session id. The previous session 
     * file is found and de-serialized. The permissions and subscriptions are copied to the 
     * new session and the previous session file deleted.
     * 
     * @param session
     * @param message
     * @throws WebSocketException
     */
    @Event(value = "request", topicMatches = "auth:/.*/reconnect")
    public void restoreSession(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            JsonObject credentials = JsonUtils.getJsonObject(message, "content");
            String username = JsonUtils.getString(credentials, "username").toLowerCase();
            String previousSessionId = JsonUtils.getString(credentials, "sessionId");
            String pwd = JsonUtils.getString(credentials, "password");

            JsonObject response = db.getUserDetails(username);
            if (response == null) {
                wsService.sendErrorToClient(session, topic, "Session Restore Failed", "Unable to get the user details.");
                return;
            }
            
            wsService.setUsername(session, username);
            wsService.restoreSession(session, previousSessionId);

            // Check the password in the reconnect request.
            String decryptedPwd = wsService.decrypt(session, pwd);     
            if (!pwdService.isValidReconnectPassword(username, previousSessionId, decryptedPwd)) {
                // We shouldn't normally get here. If we do, it's a hacker that needs to be investigated and delt with!
                log.error(format("Security Violation on Reconnect: username: %s", username));
                wsService.setUsername(session, "");
                wsService.setPermissions(session, "");
                wsService.sendErrorToClient(session, topic, "Authentication Failure", 
                    "Unable to re-connect session. Please logout and log back in.", 
                    WebSocketService.WARNING_SEVERITY);
                return;
            }

            response = removePassword(response); // Remove the DB hashed password
            response = JsonUtils.add(response, "sessionId", session.getId());

            wsService.sendMessageToClient(session, "response", topic, response.toString());
        } 
        catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.setUsername(session, "");
            wsService.setPermissions(session, "");
            wsService.sendErrorToClient(session, topic, "Session Restore Failed", e.getMessage());
            log.error("Authentication exception:", e);
        }
    }

   /**
     * Authenticates the user and returns the user details. The filter must contain the username and password.
     * 
     * Example:
     *  {"event":"subscribe", "topic": "auth:/app_name/authenticate", "filter": {"username":"chris", "password":"x3f5s2g6"}}
     * 
     * @param session Web Socket session.
     * @param message Json Message containing the credentials in the filter.
     */
    @Event(value = "request", topicMatches = "auth:/.*/changePassword")
    public void changePassword(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            String username = wsService.getUsername(session);
            JsonObject credentials = JsonUtils.getJsonObject(message, "content");
            String oldPassword = JsonUtils.getString(credentials, "oldPassword");
            String newPassword = JsonUtils.getString(credentials, "newPassword");
            String confirmNewPassword = JsonUtils.getString(credentials, "confirmNewPassword");

            if (oldPassword.equals(newPassword)) {
                wsService.sendErrorToClient(session, topic, "Same Password", "The old and new passwords are the same.");
                return;
            }

            JsonObject response = db.getUserDetails(username);
            if (response == null) {
                wsService.sendErrorToClient(session, topic, "Error", "Unable to get user details. Please re-login.");
                return;
            }

            if (!pwdService.isValidPassword(username, oldPassword, response.getString("password"))) {
                wsService.sendErrorToClient(session, topic, "Old Password Incorrect", "The old password is incorrect. Please correct and try again.");
                return;
            }

            if (!newPassword.equals(confirmNewPassword)) {
                wsService.sendErrorToClient(session, topic, "Password Mismatch", "Your new password and confirmation password don't match.");
                return;
            }

            String changePwd = JsonUtils.getString(response, "changePassword");
            if (changePwd.equals("X")) {
                wsService.sendErrorToClient(session, topic, "Password Change Failed", 
                    format("The password for user <b>%s</b> can't be changed.", username));
                return;
            }

            String newPwdHash = pwdService.hashPassword(username, newPassword);

            db.updateUserDetails(username, newPwdHash);
            response = removePassword(response);

            response = JsonUtils.add(response, "sessionId", session.getId());

            wsService.setUsername(session, username);
            wsService.setPermissions(session, response.getString("permissions"));

            wsService.sendMessageToClient(session, "response", topic, response.toString());
        } 
        catch (MissingValueException e) {
            wsService.sendErrorToClient(session, topic, "Missing Value Error", e.getMessage());
            log.error(format("%s : %s", e.getMessage(), message.toString()));
        }
        catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Authentication Failure", e.getMessage());
            wsService.setUsername(session, "");
            wsService.setPermissions(session, "");
            log.error(format("Authentication exception: %s", e.getMessage()));
        }
    }

    /**
     * Logs a user out.
     * 
     * Example:
     *  {"event":"subscribe", "topic": "auth:/app_name/logout"}, "content": ""}
     * 
     * @param session Web Socket session.
     * @param message Json Message.
     */
    @Event(value = "publish", topicMatches = "auth:/.*/logout")
    public void logout(@Session WebSocketSession session, @Message JsonObject message) throws WebSocketException {
        String topic = "";
        try {
            topic = message.getString("topic");
            wsService.setUsername(session, "");
            wsService.setPermissions(session, "");
        } catch (Exception e) {
            wsService.sendErrorToClient(session, topic, "Logout Failure", e.getMessage());
            log.error("Logout exception:", e);
        }
    }

    /**
     * Removes the password. JsonObject is immutable and throws an exception if an attempt is made to do
     * a origin.remove("password"), so the object has to be copied to a new object minus the password.
     * 
     * Also forces the user to change their password if the password in the DB is stored as clear text.
     * 
     */
    private static JsonObject removePassword(JsonObject origin){
        boolean forcePwdChange = (origin.getString("password").length() < 30);
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String,JsonValue> entry : origin.entrySet()){
            String key = entry.getKey();
            JsonValue value = entry.getValue();
            if (key.equals("password")){
                continue;
            }
            if (forcePwdChange && key.equals("changePassword")) {
                builder.add(key, "Y"); // Force the user to change their password.
            } else {
                builder.add(key, value);
            } 
        }       
        return builder.build();
    }
}