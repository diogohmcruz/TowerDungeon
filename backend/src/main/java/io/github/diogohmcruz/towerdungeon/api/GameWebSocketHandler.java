package io.github.diogohmcruz.towerdungeon.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import io.github.diogohmcruz.towerdungeon.api.dtos.AssignTradeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyPostsActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyTomeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.ClearLoadoutActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameAction;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameActionEnvelope;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.ReattachActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.ReinforceActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.RemedyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.SaveLoadoutActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.SuccessionActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.UpgradeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderLabel;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialDesk;
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
@ConditionalOnWebApplication
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {
  static final String SUBPROTOCOL = "td.v1";
  private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
  static final CloseStatus REPLACED = new CloseStatus(4001, "REPLACED");
  static final CloseStatus BAD_KEY = new CloseStatus(4400, "BAD_KEY");
  static final CloseStatus SCHEMA_NEWER = new CloseStatus(4426, "SCHEMA_NEWER");
  static final CloseStatus STORE_FAILURE = new CloseStatus(4500, "STORE_FAILURE");
  static final int TRIAL_REFUSED = 4403;
  private static final String TRIAL_PREFIX = "trial.";
  private static final String TRIAL_NEXT = ".new";
  private static final String ALIAS_PREFIX = "as.";
  private static final String AGENT_TOKEN = "agent";
  private static final Pattern RACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

  /**
   * Which slot a connection asks for: the real Campaign, or a Trial of a race (its current attempt,
   * or the next one).
   */
  record Selector(String raceId, boolean next, String alias, ContenderLabel label) {
    static final Selector CAMPAIGN = new Selector(null, false, null, ContenderLabel.HUMAN);

    boolean isTrial() {
      return raceId != null;
    }
  }

  /** The Key's player id and the slot it asks for. */
  record Handshake(String playerId, Selector selector) {}

  private final GameService gameService;
  private final ObjectMapper objectMapper;

  @Override
  public List<String> getSubProtocols() {
    return List.of(SUBPROTOCOL);
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    var handshake = handshakeOf(session);
    if (handshake.isEmpty()) {
      log.warn("Refusing session {}: missing or malformed Campaign Key", session.getId());
      return session.close(BAD_KEY);
    }
    var id = handshake.get().playerId();
    var selector = handshake.get().selector();
    var wsId = session.getId();
    var attaching =
        selector.isTrial()
            ? gameService.attachTrial(
                id, selector.raceId(), selector.next(), wsId, selector.alias(), selector.label())
            : gameService.attach(id, wsId);
    return attaching
        .onErrorResume(err -> refuseUnloadable(session, id, err))
        .flatMap(attachment -> serve(session, attachment.slotId(), attachment))
        .doFinally(
            signal -> {
              log.info("Session {} of player {} ended ({})", wsId, id, signal);
              gameService.detach(id, wsId);
            });
  }

  /** Closes a connection whose Campaign could not be loaded, with the matching code. */
  private Mono<GameService.Attachment> refuseUnloadable(
      WebSocketSession session, String playerId, Throwable err) {
    if (err instanceof TrialDesk.Refused refused) {
      log.info("A Trial was refused: {}", refused.reason());
      return session
          .close(new CloseStatus(TRIAL_REFUSED, refused.reason().name()))
          .then(Mono.empty());
    }
    if (err instanceof SchemaNewerException) {
      log.warn("Campaign of player {} was saved by a newer version", playerId);
      return session.close(SCHEMA_NEWER).then(Mono.empty());
    }
    log.error("Could not load the Campaign of player {}", playerId, err);
    return session.close(STORE_FAILURE).then(Mono.empty());
  }

  /**
   * The player id and slot from the {@code td.v1, <key>[, trial.<raceId>[.new][, as.<alias>][,
   * agent]]} subprotocol offer; the key is never logged.
   */
  private Optional<Handshake> handshakeOf(WebSocketSession session) {
    var offered = session.getHandshakeInfo().getHeaders().get(PROTOCOL_HEADER);
    if (offered == null) {
      return Optional.empty();
    }
    var tokens =
        offered.stream()
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .toList();
    if (tokens.size() < 2 || tokens.size() > 5 || !SUBPROTOCOL.equals(tokens.getFirst())) {
      return Optional.empty();
    }
    var selector =
        tokens.size() >= 3
            ? selectorOf(tokens.get(2), tokens.subList(3, tokens.size()))
            : Optional.of(Selector.CAMPAIGN);
    if (selector.isEmpty()) {
      return Optional.empty();
    }
    return CampaignKey.playerId(tokens.get(1)).map(id -> new Handshake(id, selector.get()));
  }

  /**
   * The slot token and what a Contender may declare after it: {@code as.<base64url alias>} (cleaned
   * before it is used) and {@code agent}.
   */
  private static Optional<Selector> selectorOf(String token, List<String> declarations) {
    if (!token.startsWith(TRIAL_PREFIX)) {
      return Optional.empty();
    }
    String alias = null;
    var label = ContenderLabel.HUMAN;
    for (var declaration : declarations) {
      if (AGENT_TOKEN.equals(declaration)) {
        label = ContenderLabel.AGENT;
      } else if (declaration.startsWith(ALIAS_PREFIX) && alias == null) {
        alias = decodeAlias(declaration.substring(ALIAS_PREFIX.length()));
      } else {
        return Optional.empty();
      }
    }
    var rest = token.substring(TRIAL_PREFIX.length());
    var next = rest.endsWith(TRIAL_NEXT);
    var raceId = next ? rest.substring(0, rest.length() - TRIAL_NEXT.length()) : rest;
    return RACE_ID.matcher(raceId).matches()
        ? Optional.of(new Selector(raceId, next, alias, label))
        : Optional.empty();
  }

  /** The alias a client sent as base64url text; unreadable text is no alias. */
  private static String decodeAlias(String encoded) {
    try {
      return new String(java.util.Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
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
      case SELL_SALT -> new Command.SellSalt();
      case INVADE -> {
        var invade = payload(message, new TypeReference<GameActionDTO<InvadeActionDTO>>() {});
        yield new Command.StartExpedition(
            invade.units(),
            invade.startFloor(),
            invade.salt(),
            invade.saltPack(),
            invade.saltBearer(),
            Boolean.TRUE.equals(invade.panacea()),
            Boolean.TRUE.equals(invade.overreach()));
      }
      case EXTRACT -> new Command.Extract();
      case HOLD -> new Command.Hold();
      case RESUME -> new Command.Resume();
      case BUY_TOME -> {
        var tome = payload(message, new TypeReference<GameActionDTO<BuyTomeActionDTO>>() {});
        yield new Command.BuyTome(tome == null ? null : tome.tome());
      }
      case BUY_RING -> new Command.BuyRing();
      case BUILD_PORT -> new Command.BuildPort();
      case BUY_POSTS -> {
        var posts = payload(message, new TypeReference<GameActionDTO<BuyPostsActionDTO>>() {});
        yield new Command.BuyPosts(posts == null ? null : posts.trade());
      }
      case REMEDY -> {
        var remedy = payload(message, new TypeReference<GameActionDTO<RemedyActionDTO>>() {});
        yield new Command.GiveRemedy(remedy == null ? null : remedy.unitId());
      }
      case REATTACH -> {
        var reattach = payload(message, new TypeReference<GameActionDTO<ReattachActionDTO>>() {});
        yield reattach == null
            ? new Command.ReattachPart(null, null)
            : new Command.ReattachPart(reattach.unitId(), reattach.part());
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
      case EXODUS -> new Command.Exodus();
      case MUSTER_ROLL -> new Command.MusterRoll();
      case SAVE_LOADOUT -> {
        var save = payload(message, new TypeReference<GameActionDTO<SaveLoadoutActionDTO>>() {});
        yield save == null
            ? new Command.SaveLoadout(null, null, null, null, false, false)
            : new Command.SaveLoadout(
                save.slot(),
                save.salt(),
                save.saltPack(),
                save.saltBearer(),
                Boolean.TRUE.equals(save.panacea()),
                Boolean.TRUE.equals(save.overreach()));
      }
      case CLEAR_LOADOUT -> {
        var clear = payload(message, new TypeReference<GameActionDTO<ClearLoadoutActionDTO>>() {});
        yield new Command.ClearLoadout(clear == null ? null : clear.slot());
      }
      case BEGIN_GENERATION -> new Command.BeginGeneration();
      case ROLL_BUY -> {
        var buy = payload(message, new TypeReference<GameActionDTO<SuccessionActionDTO>>() {});
        yield new Command.RollBuy(buy == null ? null : buy.id());
      }
      case ROLL_REFUND -> {
        var refund = payload(message, new TypeReference<GameActionDTO<SuccessionActionDTO>>() {});
        yield new Command.RollRefund(refund == null ? null : refund.id());
      }
      case CHOOSE -> {
        var choice = payload(message, new TypeReference<GameActionDTO<SuccessionActionDTO>>() {});
        yield new Command.Choose(choice == null ? null : choice.id());
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
