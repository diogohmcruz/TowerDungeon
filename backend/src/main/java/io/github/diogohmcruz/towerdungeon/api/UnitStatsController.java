package io.github.diogohmcruz.towerdungeon.api;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/unit-stats")
@CrossOrigin(origins = {"http://localhost:4200"})
@Tag(name = "Unit Stats", description = "Unit Stats APIs")
public class UnitStatsController {

  @Operation(summary = "Get all unit stats", description = "Retrieves all available unit stats")
  @ApiResponse(
      responseCode = "200",
      description = "Unit Stats found",
      content = @Content(schema = @Schema(implementation = UnitStats.class)))
  @GetMapping("/")
  public CompletableFuture<ResponseEntity<Map<String, UnitStats>>> getOrder() {
    return CompletableFuture.supplyAsync(
        () ->
            ResponseEntity.ok(
                Arrays.stream(UnitStats.values())
                    .collect(
                        Collectors.toMap(UnitStats::name, (UnitStats unitStats) -> unitStats))));
  }
}
