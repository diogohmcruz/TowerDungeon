package io.github.diogohmcruz.towerdungeon.balance;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;

/**
 * What a competent player brings to the final push of a Generation: the Party, the Upgrades the
 * Campaign has bought, the Supplies carried and the state of the Tower. A provisional shape until
 * the floor duel harness (#112) defines its own {@code PartyPlan}; when that lands this record is
 * deleted and the table loads into the harness's type.
 *
 * @param startFloor the Floor the Expedition starts from (a Shortcut, or 1)
 * @param units how many Units of each Type the Party holds, Founders and the Heir included
 * @param upgrades the level of each Upgrade bought this Campaign (1 for an Unlock)
 * @param dowry the choice made at the Dowry (CREDITS, MATERIALS or RELICS), null when not held
 * @param supplies the Supplies the Party sets out with
 * @param carryingCapacity the most the Party can carry; {@code supplies} never exceeds it
 * @param aim how the Party's attacks are spread over a target's Body Parts
 * @param towerMemoryStep the Tower Memory step in force (Generation minus one, capped by config)
 * @param easingSteps the Guardians slain on the way in this Campaign, which thin the Floors below
 *     them
 * @param inheritances the Inheritances of the Roll held, in the order bought
 */
public record PartyPlan(
    int startFloor,
    Map<UnitStats, Integer> units,
    Map<UpgradeType, Integer> upgrades,
    String dowry,
    double supplies,
    double carryingCapacity,
    Aim aim,
    int towerMemoryStep,
    int easingSteps,
    List<InheritanceType> inheritances) {

  /** Units in the Party, all Types. */
  public int partySize() {
    return units.values().stream().mapToInt(Integer::intValue).sum();
  }

  /** The Upgrade's level, 0 when not bought. */
  public int level(UpgradeType type) {
    return upgrades.getOrDefault(type, 0);
  }

  public int count(UnitStats type) {
    return units.getOrDefault(type, 0);
  }
}
