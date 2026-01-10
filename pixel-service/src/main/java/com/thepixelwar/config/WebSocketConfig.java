package com.thepixelwar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 웹소켓 서버 활성화!
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커가 "/topic"으로 시작하는 애들한테 소식을 전해줌
        config.enableSimpleBroker("/topic");
        // 클라이언트가 메시지 보낼 때 앞에 붙이는 주소
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 웹소켓 연결 주소: "ws://localhost:8080/ws-pixel"
        registry.addEndpoint("/ws-pixel").setAllowedOriginPatterns("*").withSockJS();
    }
}