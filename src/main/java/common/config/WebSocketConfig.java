package common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置：启用基于 STOMP 的消息代理。
 * 前端通过 /ws/sim-events 连接，后端通过 SimpMessagingTemplate 广播事件。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理，前端可以订阅 /topic/sim-events
        config.enableSimpleBroker("/topic");
        // 前端发送消息到服务端的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS 回退支持，前端使用 STOMP Client 连接 /ws/sim-events
        registry.addEndpoint("/ws-sim")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

