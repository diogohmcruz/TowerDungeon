package io.github.diogohmcruz.towerdungeon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Externalised game-balance configuration. Every tuning value that used to be a {@code static
 * final} constant scattered across the domain classes now lives here and is bound from {@code
 * application.yml} under the {@code game} prefix, so designers can retune the game without touching
 * Java code.
 */
@Data
@ConfigurationProperties(prefix = "game")
public class GameProperties {
  private final Start start = new Start();
  private final Loop loop = new Loop();
  private final Reward reward = new Reward();
  private final Boss boss = new Boss();
  private final Enemies enemies = new Enemies();
  private final Supply supply = new Supply();
  private final Healing healing = new Healing();
  private final Village village = new Village();

  /** Values a freshly created player starts the game with. */
  @Data
  public static class Start {
    private double credit = 10.0;
    private double manaPerSecond = 1.0;
  }

  /** Game-loop cadence and per-tick expedition costs. */
  @Data
  public static class Loop {
    private long lifecycleMs = 1000;
    private long invasionTickMs = 10;
    private double supplyDrainPerUnit = 1.5;
    private double starvationDamage = 5.0;
  }

  /** Credit and loot granted for clearing floors, and vault sell prices back in the village. */
  @Data
  public static class Reward {
    private double creditPerFloor = 2.0;
    private double materialsPerFloor = 5.0;
    private double suppliesPerFloor = 3.0;
    private int relicDepthThreshold = 10;
    private double bossRewardMultiplier = 3.0;
    private double materialsSellPrice = 2.0;
    private double relicsSellPrice = 25.0;
  }

  /** Guardian (boss) floor cadence and scaling. */
  @Data
  public static class Boss {
    private int interval = 10;
    private double healthBaseMultiplier = 3.0;
    private double healthMultiplierPerFloor = 0.05;
    private double damageMultiplier = 1.25;
  }

  /**
   * How a floor's defending horde is sized. Instead of "one enemy per floor number" the garrison is
   * a weighted budget that grows non-linearly, surges on periodic floors and swings randomly, then
   * is thinned once the guardians above have been slain.
   */
  @Data
  public static class Enemies {
    /** Flat garrison budget every floor starts with. */
    private double baseBudget = 3.0;

    /** Additional budget per floor of depth (linear term). */
    private double budgetPerFloor = 0.8;

    /** Additional budget per floor squared (gentle non-linear ramp). */
    private double quadraticPerFloor = 0.04;

    /** Random swing applied to the budget, e.g. 0.25 = ±25%. */
    private double variance = 0.25;

    /** Every Nth floor is a denser "surge" floor (0 disables surges). */
    private int surgeInterval = 5;

    /** Budget multiplier applied on surge floors. */
    private double surgeMultiplier = 1.5;

    /** Fraction of the garrison removed for each cleared guardian standing above a floor. */
    private double easeBudgetPerBoss = 0.15;

    /** Reduction to a floor's max enemy tier for each cleared guardian above it. */
    private int easeDifficultyPerBoss = 1;

    /** Floor is never emptier than this (keeps at least a token defender). */
    private double minBudget = 1.0;
  }

  /** Expedition food-carrying capacity. */
  @Data
  public static class Supply {
    private double baseMax = 100.0;
  }

  /** Home-party healing fed from the village pantry. */
  @Data
  public static class Healing {
    private double costPerHp = 1.0;
    private double hpPerTick = 5.0;
  }

  /** Village production/consumption and starting stock. */
  @Data
  public static class Village {
    private double startingFood = 100.0;
    private int startingVillagers = 20;
    private double villagerFoodProduction = 0.5;
    private double unitFoodConsumption = 1.0;
  }
}
