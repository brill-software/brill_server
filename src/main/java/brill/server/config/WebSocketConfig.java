// © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
package brill.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import brill.server.webSockets.WebSocketManager;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
 
    public static int WEB_SOCKET_MAX_MESSAGE_SIZE = 1024 * 1024 * 100; // 100MB 

    // Normall only pages servered by the server are allowed to make WebSocket connections. To allow 
    // pages from the Development NodeJS to make WebSocket connections, the allowedOrigins must be set
    // to either * or http://localhost:3000. Setting allowedOrigins to * also allows WebSocket King to connect.
    @Value("${server.allowedOrigins:}")
    private String allowedOrigins;

    /**
     * Registers a SocksJS endpoint of /brill_socksjs. The SocksJS protocol has various fallback modes for
     * if WebSockets are blocked. The fallback protocols include xhr-streaming and xhr-polling.
     * 
     * A seconnd endpoint of /brill_ws is registed mainly for debug purposes. This can be used with 
     * the developer tools WebSocket King and Postman. The Brill Client can also be set to use the /brill_ws
     * endpoint, in which case the messages can be viewed in the Chrome Developer Network WS tool.
     * 
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        String[] allowedOriginsArray = StringUtils.tokenizeToStringArray(allowedOrigins, ",");
           
        // Register SOCKS endpoint
        registry.addHandler(wsHandler(), "/brill_socksjs").setAllowedOrigins(allowedOriginsArray).withSockJS()
           .setWebSocketEnabled(true).setHeartbeatTime(25000).setDisconnectDelay(5000).setSessionCookieNeeded(false);

        // Plain WebScoket endpoint
        registry.addHandler(wsHandler(), "/brill_ws").setAllowedOrigins(allowedOriginsArray);

    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(WEB_SOCKET_MAX_MESSAGE_SIZE);
        return container;
    }

    @Bean
    public WebSocketHandler wsHandler() {
        return new WebSocketManager();
    }
}