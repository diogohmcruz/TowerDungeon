package io.github.diogohmcruz.towerdungeon.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
  private final Tower tower = new Tower();
  private final Boss boss = new Boss();
  private final Enemies enemies = new Enemies();
  private final Recruit recruit = new Recruit();
  private final Magic magic = new Magic();
  private final Reinforcements reinforcements = new Reinforcements();
  private final Supply supply = new Supply();
  private final Healing healing = new Healing();
  private final Village village = new Village();
  private final Architecture architecture = new Architecture();
  private final Prestige prestige = new Prestige();
  private final Persistence persistence = new Persistence();

  /** Where Campaigns are kept. */
  @Data
  public static class Persistence {
    /**
     * Root of the Campaign files; must be a mounted volume wherever the filesystem is ephemeral.
     */
    private String dataDir = "./data";
  }

  /** Values a freshly created player starts the game with. */
  @Data
  public static class Start {
    private double credit = 10.0;

    /**
     * The Founders every new campaign starts with in Reserves, free of charge, in order. Empty the
     * list to start a campaign with none.
     */
    private List<Founder> founders =
        new ArrayList<>(
            List.of(
                new Founder("Yuva", UnitStats.WARRIOR, 5.0, true),
                new Founder("Fanro", UnitStats.WARRIOR, 1.0, false),
                new Founder("Bazen", UnitStats.WARRIOR, 1.0, false),
                new Founder("Calix", UnitStats.ARCHER, 1.0, false)));
  }

  /**
   * One Founder: a named unit seeded into Reserves at the start of a campaign.
   *
   * <p>{@code statMultiplier} scales every per-unit combat stat (health and damage now; later stat
   * issues multiply by the same factor). {@code fixedStrengths} keeps the unit out of the recruit
   * roll.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Founder {
    private String name;
    private UnitStats unitType;
    private double statMultiplier = 1.0;
    private boolean fixedStrengths = false;
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
    private double foodSellPrice = 1.0;
  }

  /** The tower structure the party climbs. */
  @Data
  public static class Tower {
    /** Height of the tower: the summit floor whose guardian must fall to win the campaign. */
    private int maxFloor = 100;
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

    /**
     * Floors over which the first-reach discount fades; Floor 1 + this is the first full budget.
     */
    private int gentleFloors = 9;

    /**
     * Budget multiplier on Floor 1, rising linearly to 1.0 once {@code gentleFloors} have passed.
     */
    private double gentleStartMultiplier = 0.5;

    /** Budget added per slain guardian standing below a floor (the mirror of the ease value). */
    private double stepBudgetPerBoss = 0.15;

    /** Floor is never emptier than this (keeps at least a token defender). */
    private double minBudget = 1.0;
  }

  /** How freshly recruited units differ from their type's baseline. */
  @Data
  public static class Recruit {
    /**
     * Each recruit rolls its health and damage independently and uniformly within this fraction
     * either side of the type's baseline, e.g. 0.30 = 70% to 130%.
     */
    private double strengthSpread = 0.30;
  }

  /** Expedition food-carrying capacity. */
  @Data
  public static class Supply {
    private double baseMax = 100.0;
  }

  /**
   * Scarce, precious magic (manga-accurate). Every magic-capable player unit (any MAGIC/HEAL
   * archetype) carries a finite personal mana reserve that is spent each time it casts and can only
   * be refilled slowly back at the village — magic power must be husbanded and rotated home, never
   * spammed.
   */
  @Data
  public static class Magic {
    /** Full mana reserve a caster is recruited with and recharges toward at home. */
    private double maxMana = 5.0;

    /** Mana spent per cast (one magic attack or one heal). */
    private double costPerCast = 1.0;

    /**
     * Mana a caster regains per lifecycle tick while standing by at home (the fast, safe refill).
     */
    private double rechargePerTick = 0.25;

    /**
     * Mana a caster slowly regains per tick even while delving on the tower — innate trickle, much
     * weaker than the village refill, so a deep run still drains reserves faster than they recover.
     */
    private double towerRechargePerTick = 0.05;
  }

  /** Follow-up waves the player sends up mid-run to resupply and reinforce an active delve. */
  @Data
  public static class Reinforcements {
    /** Floors a climbing wave ascends per invasion tick as it chases the party up the tower. */
    private double climbFloorsPerTick = 1.0;
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

  /** Floor Architectures: the seeded plan, and the switch for their gameplay effects. */
  @Data
  public static class Architecture {
    /**
     * Gameplay effects of Architectures (Garrison, Sightline, Attrition and so on). Off: for now an
     * Architecture only changes how a Floor is drawn.
     */
    private boolean enabled = false;

    /** The Tower seed; a shipped constant, so Generation 1 is the Tower the player learns. */
    private long seed = 362108372306L;
  }

  /** The Toll a Campaign pays out as Legacy: what the Tally is worth. */
  @Data
  public static class Prestige {
    /** Toll points per Legacy point. */
    private double pointsDivisor = 9.0;

    /** Marks per Floor climbed above the inherited Start Floor. */
    private double ascentMarksPerFloor = 0.4;

    /** Marks per Guardian slain. */
    private double guardianMarks = 1.5;

    /** Share of Marks added per point of Load. */
    private double loadMark = 0.05;

    /** Load above this pays nothing more. */
    private int loadCap = 8;

    /** Highest Floor above this pays nothing more (Floor 100 is paid through its Guardian). */
    private int doorFloor = 99;

    private final Counters counters = new Counters();

    /** The square-rooted counters, each with its Marks at par and its par. */
    @Data
    public static class Counters {
      private final Counter foodGrown = counter(3.0, 60000.0);
      private final Counter materialsBanked = counter(6.0, 17000.0);
      private final Counter creditsBanked = counter(3.0, 6800.0);
      private final Counter relicsBanked = counter(4.0, 320.0);
      private final Counter expeditions = counter(3.0, 17.0);

      private static Counter counter(double marks, double par) {
        var counter = new Counter();
        counter.setMarks(marks);
        counter.setPar(par);
        return counter;
      }
    }

    /** One counter's weight: the Marks it earns at par, and its par. */
    @Data
    public static class Counter {
      private double marks;
      private double par;
    }
  }
}
