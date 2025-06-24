package io.github.diogohmcruz.towerdungeon.api;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final GameService gameService;
  private final ObjectMapper objectMapper;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.clear();
    var sessionC = sessions.put(session.getId(), session);
    if (sessionC != null) {
      log.info("New game session established: {}", session.getId());
      sendState(sessionC);
    }
  }

  private void sendState(WebSocketSession session) {
    GameState state = gameService.getState(session.getId());
    try {
      String payload = objectMapper.writeValueAsString(state);
      session.sendMessage(new TextMessage(payload));
    } catch (JsonProcessingException e) {
      log.error("Error creating the game state", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error("Error sending state to game", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    try {
      var gameAction = objectMapper.readValue(message.getPayload(), GameActionDTO.class);
      if (gameAction.gameAction() == null) {
        var errorMessage = String.format("Invalid game action received: %s", gameAction);
        log.error(errorMessage);
        return;
      }
      log.info("Received game action: {}", gameAction);
      switch (gameAction.gameAction()) {
        case BUY -> {
          var dto =
              objectMapper.readValue(
                  message.getPayload(), new TypeReference<GameActionDTO<BuyActionDTO>>() {});
          gameService.handleMessage(session.getId(), dto.payload());
        }
        case INVADE -> {
          var dto =
              objectMapper.readValue(
                  message.getPayload(), new TypeReference<GameActionDTO<InvadeActionDTO>>() {});
          gameService.handleMessage(session.getId(), dto.payload());
        }
        default ->
            throw new RuntimeException(String.format("Unknown game action type: %s", gameAction));
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    sendState(session);
  }

  @Scheduled(fixedRate = 1000)
  public void gameLoop() {
    sessions.values().forEach(this::sendState);
  }
}
