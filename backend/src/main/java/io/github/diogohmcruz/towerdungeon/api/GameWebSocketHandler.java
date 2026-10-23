package io.github.diogohmcruz.towerdungeon.api;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import io.github.diogohmcruz.towerdungeon.api.dtos.AssignTradeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyPostsActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyTomeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameAction;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionEnvelope;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.ReinforceActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.UpgradeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignKey;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {
  static final String SUBPROTOCOL = "td.v1";
  private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
  static final CloseStatus REPLACED = new CloseStatus(4001, "REPLACED");
  static final CloseStatus BAD_KEY = new CloseStatus(4400, "BAD_KEY");
  static final CloseStatus SCHEMA_NEWER = new CloseStatus(4426, "SCHEMA_NEWER");
  static final CloseStatus STORE_FAILURE = new CloseStatus(4500, "STORE_FAILURE");

  private final GameService gameService;
  private final ObjectMapper objectMapper;

  @Override
  public List<String> getSubProtocols() {
    return List.of(SUBPROTOCOL);
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    var playerId = playerIdOf(session);
    if (playerId.isEmpty()) {
      log.warn("Refusing session {}: missing or malformed Campaign Key", session.getId());
      return session.close(BAD_KEY);
    }
    var id = playerId.get();
    var wsId = session.getId();
    return gameService
        .attach(id, wsId)
        .onErrorResume(err -> refuseUnloadable(session, id, err))
        .flatMap(attachment -> serve(session, id, attachment))
        .doFinally(
            signal -> {
              log.info("Session {} of player {} ended ({})", wsId, id, signal);
              gameService.detach(id, wsId);
            });
  }

  /** Closes a connection whose Campaign could not be loaded, with the matching code. */
  private Mono<GameService.Attachment> refuseUnloadable(
      WebSocketSession session, String playerId, Throwable err) {
    if (err instanceof SchemaNewerException) {
      log.warn("Campaign of player {} was saved by a newer version", playerId);
      return session.close(SCHEMA_NEWER).then(Mono.empty());
    }
    log.error("Could not load the Campaign of player {}", playerId, err);
    return session.close(STORE_FAILURE).then(Mono.empty());
  }

  /** The player id from the {@code td.v1, <key>} subprotocol offer; the key is never logged. */
  private Optional<String> playerIdOf(WebSocketSession session) {
    var offered = session.getHandshakeInfo().getHeaders().get(PROTOCOL_HEADER);
    if (offered == null) {
      return Optional.empty();
    }
    var tokens =
        offered.stream()
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .toList();
    if (tokens.size() != 2 || !SUBPROTOCOL.equals(tokens.getFirst())) {
      return Optional.empty();
    }
    return CampaignKey.playerId(tokens.get(1));
  }

  private Mono<Void> serve(
      WebSocketSession session, String playerId, GameService.Attachment attachment) {
    Mono<Void> receive =
        session.receive().doOnNext(message -> this.handleTextMessage(playerId, message)).then();
    Mono<Void> replaced = attachment.replaced().then(Mono.defer(() -> session.close(REPLACED)));
    // Whichever ends first ends the session and cancels the rest: a client that closes cleanly
    // completes receive, a failed send errors, a newer connection completes replaced.
    return Mono.firstWithSignal(receive, handleSend(session, playerId), replaced);
  }

  private Mono<Void> handleSend(WebSocketSession session, String playerId) {
    Flux<WebSocketMessage> frames =
        Flux.interval(Duration.ofMillis(100))
            .onBackpressureLatest()
            .<String>handle(
                (_, sink) -> {
                  try {
                    gameService.renderState(playerId).ifPresentOrElse(sink::next, sink::complete);
                  } catch (Exception e) {
                    log.error("Error creating the game state", e);
                    sink.error(new RuntimeException(e));
                  }
                })
            .map(session::textMessage);

    return session
        .send(frames)
        .doOnError(err -> log.error("Failed to send state to player {}", playerId, err));
  }

  /**
   * Handles one client message. Any failure is logged and answered with a notice; it never ends the
   * session, so one bad message cannot drop the Campaign.
   */
  protected void handleTextMessage(String playerId, WebSocketMessage message) {
    try {
      var payload = message.getPayloadAsText();
      GameActionEnvelope gameAction = objectMapper.readValue(payload, GameActionEnvelope.class);
      if (gameAction.gameAction() == null) {
        log.error("Invalid game action received: {}", gameAction);
        return;
      }
      run(playerId, commandOf(payload, gameAction.gameAction()));
    } catch (Exception err) {
      log.error("Failed to handle a message from player {}", playerId, err);
      gameService.notifyPlayer(
          playerId, new Notice("ACTION_FAILED", "That action could not be processed."));
    }
  }

  /** Runs the command and passes its notices on to the player; the rest of the result is unused. */
  private void run(String playerId, Command command) {
    gameService
        .execute(playerId, command)
        .notices()
        .forEach(notice -> gameService.notifyPlayer(playerId, notice));
  }

  private Command commandOf(String message, GameAction gameAction) throws JacksonException {
    return switch (gameAction) {
      case BUY -> {
        var buy = payload(message, new TypeReference<GameActionDTO<BuyActionDTO>>() {});
        yield new Command.Recruit(buy.unitStats(), buy.quantity());
      }
      case BUY_VILLAGERS -> new Command.BuyVillagers();
      case SELL_FOOD -> new Command.SellFood();
      case SELL_MATERIALS -> new Command.SellMaterials();
      case SELL_RELICS -> new Command.SellRelics();
      case INVADE -> {
        var invade = payload(message, new TypeReference<GameActionDTO<InvadeActionDTO>>() {});
        yield new Command.StartExpedition(invade.units(), invade.startFloor(), invade.salt());
      }
      case EXTRACT -> new Command.Extract();
      case RESUME -> new Command.Resume();
      case BUY_TOME -> {
        var tome = payload(message, new TypeReference<GameActionDTO<BuyTomeActionDTO>>() {});
        yield new Command.BuyTome(tome == null ? null : tome.tome());
      }
      case BUY_POSTS -> {
        var posts = payload(message, new TypeReference<GameActionDTO<BuyPostsActionDTO>>() {});
        yield new Command.BuyPosts(posts == null ? null : posts.trade());
      }
      case ASSIGN_TRADE -> {
        var assign = payload(message, new TypeReference<GameActionDTO<AssignTradeActionDTO>>() {});
        yield assign == null
            ? new Command.AssignTrade(null, null)
            : new Command.AssignTrade(assign.trade(), assign.delta());
      }
      case REINFORCE -> {
        var reinforce = payload(message, new TypeReference<GameActionDTO<ReinforceActionDTO>>() {});
        yield new Command.SendReinforcements(reinforce.units());
      }
      case UPGRADE -> {
        var upgrade = payload(message, new TypeReference<GameActionDTO<UpgradeActionDTO>>() {});
        yield new Command.BuyUpgrade(upgrade.upgradeId());
      }
    };
  }

  private <T> T payload(String message, TypeReference<GameActionDTO<T>> type)
      throws JacksonException {
    return objectMapper.readValue(message, type).payload();
  }
}
