package io.github.diogohmcruz.towerdungeon.domain.models.architecture;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A Floor's structural form: how it is drawn and, once {@code game.architecture.enabled} switches
 * the levers on, the small rules of the fight there.
 *
 * <p>The three guardian architectures belong to Guardian Floors; the other nine to ordinary Floors.
 * {@code carriesCost} marks the Architectures whose rule costs the Party Attrition, Supplies drain
 * or Traverse time, which the plan never lets run longer than three Floors in a row. {@code
 * surgeEligible} marks the wide Architectures a Surge Floor may hold, so its bigger Garrison has a
 * visible reason.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Architecture {
  COLONNADE(false, false, true),
  NAVE(false, false, true),
  PILLAR_MAZE(false, true, true),
  STAIR_SHAFT(false, true, false),
  SPAN(false, true, false),
  WATERWAY(false, true, true),
  LOOP_ROOMS(false, true, false),
  STATUARY(false, false, true),
  WARREN(false, false, false),
  GATE(true, false, false),
  WELL(true, true, false),
  EXPANSE(true, true, false);

  private final boolean guardianArchitecture;
  private final boolean carriesCost;
  private final boolean surgeEligible;
}
