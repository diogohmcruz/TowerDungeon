package io.github.diogohmcruz.towerdungeon.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.diogohmcruz.towerdungeon.domain.trial.AttemptLedger;
import io.github.diogohmcruz.towerdungeon.domain.trial.RaceTable;
import io.github.diogohmcruz.towerdungeon.domain.trial.TermsView;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialDesk;
import io.github.diogohmcruz.towerdungeon.persistence.PlayerIdResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * A race's finished table. Only a Contender who has closed an attempt in the race may read it, so a
 * table is never a way to watch a race still being run. The caller is identified by the Campaign
 * Key as bearer; the table carries aliases and no ids.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/races")
@CrossOrigin(origins = {"http://localhost:4200"})
@Tag(name = "Race", description = "The finished table of a race")
public class RaceController {
  private static final String BEARER = "Bearer ";

  private final AttemptLedger ledger;
  private final PlayerIdResolver playerIds;
  private final TrialDesk desk;

  @Operation(
      summary = "The races on offer",
      description =
          "Each race's Terms without the seed, with the caller's attempts and the reason entry"
              + " would be refused now, if any. The Campaign Key is the bearer.")
  @GetMapping
  public Mono<List<TermsView>> races(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    return playerId(authorization)
        .map(
            id -> {
              var now = Instant.now();
              return desk.terms().all().stream().map(terms -> desk.view(terms, id, now)).toList();
            });
  }

  @Operation(
      summary = "The finished table of a race",
      description =
          "Order of Arrival and Cost side by side, with every Contender's Floor timeline; hands and"
              + " agents in separate tables. 403 until the caller has closed an attempt in the"
              + " race.")
  @GetMapping("/{raceId}/table")
  public Mono<RaceTable> table(
      @PathVariable String raceId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    return playerId(authorization)
        .map(
            id -> {
              var table = ledger.table(raceId);
              if (table.rowOf(id).isEmpty()) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "The table is shown once an attempt is over");
              }
              return table.viewedBy(id);
            });
  }

  private Mono<String> playerId(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER)) {
      return Mono.error(
          new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A Campaign Key is required"));
    }
    return playerIds
        .resolve(authorization.substring(BEARER.length()).strip())
        .switchIfEmpty(
            Mono.error(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Campaign Key")));
  }
}
