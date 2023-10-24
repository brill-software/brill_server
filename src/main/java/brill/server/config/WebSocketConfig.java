// © 2021 Brill Software Limited - Brill Framework, distributed under the Brill Software Proprietry License.
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
    public static int WEB_SOCKET_SNED_TIMEOUT_MS = 120 * 1000; // 120 seconds

    // Normall only pages servered by the server are allowed to make WebSocket connections. To allow 
    // pages from the Development NodeJS to make WebSocket connections, the allowedOrigins must be set
    // to either * or http://localhost:3000. Setting allowedOrigins to * also allows WebSocket King to connect.
    @Value("${server.allowedOrigins:}")
    private String allowedOrigins;

    /**
     * Registers the WebSocket endpoint of /brill_ws.
     * 
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOriginsArray = StringUtils.tokenizeToStringArray(allowedOrigins, ",");
        // WebScoket endpoint
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