package com.pj.stock_console.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 수신받을 구독(subscribe) Endpoint의 프리픽스
        config.enableSimpleBroker("/topic");
        // 클라이언트가 서버로 메시지를 보낼 때(send)의 프리픽스
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 소켓 서버 연결 주소 (/ws-crew) 설정, SockJS fallback 허용
        registry.addEndpoint("/ws-crew").withSockJS();
    }
}
