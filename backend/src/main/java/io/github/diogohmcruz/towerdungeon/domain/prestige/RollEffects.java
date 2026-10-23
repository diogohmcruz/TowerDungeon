package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;

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
  private final Set<Epitaph> engraved = EnumSet.noneOf(Epitaph.class);
  private final GameProperties.Prestige.Roll numbers;
  private final GameProperties.Prestige.Epitaphs epitaphNumbers;
  private final int openBreach;

  private RollEffects(
      List<RollEntry> roll, List<String> epitaphs, int openBreach, GameProperties.Prestige config) {
    this.numbers = config.getRoll();
    this.epitaphNumbers = config.getEpitaphs();
    this.openBreach = openBreach;
    for (var entry : roll) {
      InheritanceType.find(entry.id()).filter(InheritanceType::isAvailable).ifPresent(held::add);
    }
    for (var id : epitaphs) {
      Epitaph.find(id).ifPresent(engraved::add);
    }
  }

  /** The effects of {@code roll}, with the numbers of {@code config}. */
  public static RollEffects of(List<RollEntry> roll, GameProperties config) {
    return new RollEffects(roll, List.of(), 0, config.getPrestige());
  }

  /**
   * The effects of the whole meta: its Roll, its Epitaphs and the Guardian Floor the Generation in
   * play holds open (which only counts while the Roll holds the Open Breach).
   */
  public static RollEffects of(PlayerMeta meta, GameProperties config) {
    return new RollEffects(
        meta.roll(),
        meta.epitaphs(),
        meta.holds(InheritanceType.THE_OPEN_BREACH) ? meta.openBreach() : 0,
        config.getPrestige());
  }

  /** Whether the Epitaph has been earned. */
  public boolean has(Epitaph epitaph) {
    return engraved.contains(epitaph);
  }

  /**
   * Legacy the Offering Stone pays for the Relics still in the Vault when a Campaign ends: one for
   * each {@code relicsPerLegacy}, at most {@code maxLegacy}. 0 when the Roll holds no Stone.
   */
  public int offering(double vaultRelics) {
    if (!holds(InheritanceType.OFFERING_STONE)
        || numbers.getOfferingStone().getRelicsPerLegacy() <= 0) {
      return 0;
    }
    var stone = numbers.getOfferingStone();
    var legacy = (int) Math.floor(vaultRelics / stone.getRelicsPerLegacy());
    return Math.clamp(legacy, 0, Math.max(0, stone.getMaxLegacy()));
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
    if (openBreach > 0) {
      campaignConfig.getBoss().setOpenBreach(openBreach);
    }
    if (has(Epitaph.TEN_SEALS_BROKEN)) {
      var enemies = campaignConfig.getEnemies();
      enemies.setEaseMultiplier(
          enemies.getEaseMultiplier() * epitaphNumbers.getTenSealsBroken().getEasingMultiplier());
    }
  }

  /** What outlives the start and is not in the saved file: given to a new or a loaded state. */
  public void carry(GameState state) {
    state.setRollDamageBonus(
        holds(InheritanceType.WHETSTONE_HABIT) ? numbers.getWhetstoneHabit().getDamageBonus() : 0);
    state.setHealthBonus(
        has(Epitaph.NAMES_ON_THE_WALL) ? epitaphNumbers.getNamesOnTheWall().getHealthBonus() : 0);
  }

  /**
   * What a new Campaign is given once it is built: the Village Tier, the Dowry and the Heir, by the
   * player's choices (which a Succession fills in with the first option when none was made).
   */
  public void start(GameState state, Map<String, String> choices) {
    carry(state);
    state.raiseAllHealth();
    if (has(Epitaph.FIRST_FOOTING)) {
      state.openShortcut(ShortcutType.VANGUARD_CAMP);
    }
    if (holds(InheritanceType.HARD_WON_HABITS)) {
      state.grantAllMilestones();
    }
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
