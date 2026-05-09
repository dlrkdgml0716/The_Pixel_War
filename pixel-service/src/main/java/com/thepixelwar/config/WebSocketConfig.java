package com.thepixelwar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer; // 스프링이 미리 만들어둔 웹소켓 설정용 인터페이스

@Configuration // 설정 파일임을 명시
@EnableWebSocketMessageBroker // 해당 애플리케이션 서버에 웹소켓 통신을 열고, 메시지를 분류해서 전달
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 메시지 라우팅 함수
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // WebSocket 위에서 돌아가는 STOMP라는 하위 프로토콜 덕분에 Pub/Sub, @EnableWebSocketMessageBroker 가능
        config.enableSimpleBroker("/sub"); // 구독
        config.setApplicationDestinationPrefixes("/pub"); // 발행
    }

    // 파이프 연결 함수
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-pixel") // 프론트엔드에서 웹소켓 연결을 처음 시도할 때(handshake) 접속하는 엔드포인트 지정
                .setAllowedOriginPatterns("*") // 모든 도메인 허용(CORS 에러 방지) - 실제 개발이 끝나면 도메인 변경
                .withSockJS(); // 웹소켓을 지원하지 않는 옛날 브라우저, 네이트크 등에 문제로 막힘을 SockJS가 방지(Polling 등 꼼수를 사용하여 웹소켓이 동작하는 것처럼 보임)
    }
}