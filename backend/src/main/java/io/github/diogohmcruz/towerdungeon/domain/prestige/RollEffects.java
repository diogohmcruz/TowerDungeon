package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;

/**
 * What the Roll does to a Generation, read from the Inheritances held and the numbers in config.
 * The Roll is sealed when the Generation begins, so these are the same for the whole Campaign: the
 * start values shape the Campaign's own copy of the config before it is built, the rest is given to
 * the new state, and the two that outlive the start (damage, a reload) are re-applied when a saved
 * Campaign is loaded.
 *
 * <p>Muster Roll has no start effect: it is a command, checked where the command is handled.
 */
public final class RollEffects {
  private final Set<InheritanceType> held = EnumSet.noneOf(InheritanceType.class);
  private final GameProperties.Prestige.Roll numbers;

  private RollEffects(List<RollEntry> roll, GameProperties.Prestige.Roll numbers) {
    this.numbers = numbers;
    for (var entry : roll) {
      InheritanceType.find(entry.id()).filter(InheritanceType::isAvailable).ifPresent(held::add);
    }
  }

  /** The effects of {@code roll}, with the numbers of {@code config}. */
  public static RollEffects of(List<RollEntry> roll, GameProperties config) {
    return new RollEffects(roll, config.getPrestige().getRoll());
  }

  /** Whether the Roll holds the Inheritance and its effect exists. */
  public boolean holds(InheritanceType type) {
    return held.contains(type);
  }

  /**
   * Shapes the Campaign's own copy of the config, before the Campaign is built: the Pantry, the
   * purse and the Villagers it starts with (never below what the config already gives), and the
   * base Carrying Capacity.
   */
  public void shape(GameProperties campaignConfig) {
    if (holds(InheritanceType.SEED_GRAIN)) {
      var grain = numbers.getSeedGrain();
      var village = campaignConfig.getVillage();
      village.setStartingFood(Math.max(village.getStartingFood(), grain.getFood()));
      var start = campaignConfig.getStart();
      start.setCredit(Math.max(start.getCredit(), grain.getCredits()));
    }
    if (holds(InheritanceType.HARDY_STOCK)) {
      var village = campaignConfig.getVillage();
      village.setStartingVillagers(
          Math.max(village.getStartingVillagers(), numbers.getHardyStock().getVillagers()));
    }
    if (holds(InheritanceType.HEAVIER_PACKS)) {
      var supply = campaignConfig.getSupply();
      supply.setBaseMax(supply.getBaseMax() + numbers.getHeavierPacks().getCapacity());
    }
  }

  /** What outlives the start and is not in the saved file: given to a new or a loaded state. */
  public void carry(GameState state) {
    state.setRollDamageBonus(
        holds(InheritanceType.WHETSTONE_HABIT) ? numbers.getWhetstoneHabit().getDamageBonus() : 0);
  }

  /**
   * What a new Campaign is given once it is built: the Village Tier, the Dowry and the Heir, by the
   * player's choices (which a Succession fills in with the first option when none was made).
   */
  public void start(GameState state, Map<String, String> choices) {
    carry(state);
    if (holds(InheritanceType.VILLAGE_CHARTER)) {
      state.raiseVillageTierTo(numbers.getVillageCharter().getTier());
    }
    if (holds(InheritanceType.THE_DOWRY)) {
      var dowry = numbers.getDowry();
      var choice = chosen(choices, SuccessionChoice.Group.DOWRY);
      if (choice.dowryResource() == ResourceType.MATERIALS) {
        state.grant(ResourceType.MATERIALS, dowry.getMaterials());
      } else if (choice.dowryResource() == ResourceType.RELICS) {
        state.grant(ResourceType.RELICS, dowry.getRelics());
      } else {
        state.addCredit(dowry.getCredits());
      }
    }
    if (holds(InheritanceType.THE_HEIR)) {
      var choice = chosen(choices, SuccessionChoice.Group.HEIR);
      state.addHeir(choice.heirType(), numbers.getHeir().getHealthMultiplier());
    }
  }

  private static SuccessionChoice chosen(
      Map<String, String> choices, SuccessionChoice.Group group) {
    return SuccessionChoice.find(choices.get(group.name()))
        .filter(choice -> choice.group() == group)
        .orElse(group.fallback());
  }
}
