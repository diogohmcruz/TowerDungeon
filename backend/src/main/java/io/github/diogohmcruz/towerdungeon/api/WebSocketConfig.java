package io.github.diogohmcruz.towerdungeon.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebFlux
@RequiredArgsConstructor
public class WebSocketConfig {
  private final GameWebSocketHandler gameWebSocketHandler;

  @Bean
  public HandlerMapping webSocketHandlerMapping() {
    Map<String, WebSocketHandler> map = new HashMap<>();
    map.put("/api/ws", gameWebSocketHandler);

    SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
    handlerMapping.setOrder(1);
    handlerMapping.setUrlMap(map);
    var properties = new Properties();
    handlerMapping.setMappings(properties);
    return handlerMapping;
  }
}
