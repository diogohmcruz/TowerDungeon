package io.github.diogohmcruz.towerdungeon.api;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameAction;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionEnvelope;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {
  private final GameService gameService;
  private final ObjectMapper objectMapper;

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    var sessionId = session.getId();
    Mono<Void> receive = handleReceive(sessionId, session.receive());
    Mono<Void> send = handleSend(session);
    return Mono.zip(receive, send)
        .doOnError(
            err -> {
              log.error("Receive error on session {}", sessionId, err);
              gameService.closeSession(sessionId);
            })
        .doOnCancel(
            () -> {
              log.warn("Canceling the session {}", sessionId);
              gameService.closeSession(sessionId);
            })
        .then();
  }

  private Mono<Void> handleReceive(String sessionId, Flux<WebSocketMessage> sessionReceive) {
    return sessionReceive.doOnNext(message -> this.handleTextMessage(sessionId, message)).then();
  }

  private Mono<Void> handleSend(WebSocketSession session) {
    var sessionId = session.getId();
    Flux<WebSocketMessage> pingMessages =
        Flux.interval(Duration.ofMillis(100))
            .map(_ -> gameService.getState(sessionId))
            .<String>handle(
                (gameState, sink) -> {
                  try {
                    sink.next(objectMapper.writeValueAsString(gameState));
                  } catch (JsonProcessingException e) {
                    log.error("Error creating the game state", e);
                    sink.error(new RuntimeException(e));
                  }
                })
            .map(session::textMessage);

    return session
        .send(pingMessages)
        .doOnError(err -> log.error("Failed to send state to session {}", sessionId, err));
  }

  protected void handleTextMessage(String sessionId, WebSocketMessage message) {
    var payload = message.getPayloadAsText();
    try {
      GameActionEnvelope gameAction = objectMapper.readValue(payload, GameActionEnvelope.class);
      if (gameAction.gameAction() == null) {
        log.error("Invalid game action received: {}", gameAction);
        return;
      }
      handleGameAction(sessionId, payload, gameAction.gameAction());
    } catch (JsonProcessingException err) {
      log.error("Invalid message received from session {}", sessionId, err);
    }
  }

  private void handleGameAction(String sessionId, String message, GameAction gameAction)
      throws JsonProcessingException {
    switch (gameAction) {
      case BUY -> handleBuyAction(sessionId, message);
      case BUY_VILLAGERS -> gameService.handleBuyVillagersAction(sessionId);
      case SELL_FOOD -> gameService.handleSellFoodAction(sessionId);
      case INVADE -> handleInvadeAction(sessionId, message);
      default -> log.error("Invalid game action {} from session {}", gameAction, sessionId);
    }
  }

  private void handleBuyAction(String sessionId, String message) throws JsonProcessingException {
    var dto = objectMapper.readValue(message, new TypeReference<GameActionDTO<BuyActionDTO>>() {});
    gameService.handleMessage(sessionId, dto.payload());
  }

  private void handleInvadeAction(String sessionId, String message) throws JsonProcessingException {
    var dto =
        objectMapper.readValue(message, new TypeReference<GameActionDTO<InvadeActionDTO>>() {});
    gameService.handleMessage(sessionId, dto.payload());
  }
}
