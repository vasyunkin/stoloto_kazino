package com.stoloto.balloongame.config;

import com.stoloto.balloongame.ws.StompPlayerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket (S14). Transport endpoint {@code /ws}; topics {@code /topic/game/{gameId}}.
 * REST polling remains the fallback.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final StompPlayerInterceptor stompPlayerInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = corsProperties.getAllowedOrigins().toArray(String[]::new);
        // Raw WS: allow any Origin so integration clients / local tools can connect.
        // SockJS keeps the HTTP CORS whitelist used by browsers.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompPlayerInterceptor);
    }
}
