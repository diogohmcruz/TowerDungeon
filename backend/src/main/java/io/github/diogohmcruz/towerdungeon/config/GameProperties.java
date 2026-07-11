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
