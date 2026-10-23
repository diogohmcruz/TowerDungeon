package io.github.diogohmcruz.towerdungeon.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.diogohmcruz.towerdungeon.domain.items.ItemEffect;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
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
  private final Recruit recruit = new Recruit();
  private final Body body = new Body();
  private final Enemies enemies = new Enemies(body.getWeights());
  private final Aiming aiming = new Aiming();
  private final Sanity sanity = new Sanity();
  private final Items items = new Items();
  private final Magic magic = new Magic();
  private final Reinforcements reinforcements = new Reinforcements();
  private final Supply supply = new Supply();
  private final Healing healing = new Healing();
  private final Village village = new Village();
  private final Trades trades = new Trades();
  private final Architecture architecture = new Architecture();
  private final Prestige prestige = new Prestige();
  private final Persistence persistence = new Persistence();
  private final Security security = new Security();

  /** Where Campaigns are kept. */
  @Data
  public static class Persistence {
    /**
     * Root of the Campaign files; must be a mounted volume wherever the filesystem is ephemeral.
     */
    private String dataDir = "./data";

    /** How long a slot with no connection stays in memory, frozen, before it is evicted. */
    private Duration linger = Duration.ofSeconds(30);
  }

  /** Who may open the game socket. */
  @Data
  public static class Security {
    /**
     * Allowed handshake {@code Origin} values (for example {@code https://play.example.com}). Empty
     * allows any origin. A handshake with no Origin header (not a browser) is allowed.
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /** Refuse a plain {@code ws} handshake; on in production, off in development. */
    private boolean requireWss = false;
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

    /** An independent copy, so one campaign's tower never shares tuning with another's. */
    public Boss copy() {
      var copy = new Boss();
      BeanUtils.copyProperties(this, copy);
      return copy;
    }
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
    private double gentleStartMultiplier = 0.45;

    /** Budget added per slain guardian standing below a floor (the mirror of the ease value). */
    private double stepBudgetPerBoss = 0.15;

    /** Floor is never emptier than this (keeps at least a token defender). */
    private double minBudget = 1.0;

    /**
     * How many body parts each enemy type is born with, inclusive. The torso is always one, the
     * head is another whenever the count is 2 or more, and the rest are random limbs; a part an
     * enemy does not have is born lost. A guardian ignores this and has all six. A type not listed
     * has all six.
     */
    private Map<EnemyStats, PartRange> parts = new EnumMap<>(EnemyStats.class);

    /** How an enemy's health is divided among its parts: the same weights as units use. */
    private final Body.Weights weights;

    public Enemies() {
      this(new Body.Weights());
    }

    /** Shares {@code weights} with the unit body config, so retuning one retunes both. */
    public Enemies(Body.Weights weights) {
      this.weights = weights;
      parts.put(EnemyStats.SLIME, new PartRange(1, 1));
      parts.put(EnemyStats.CHYMUS_INSECT, new PartRange(1, 1));
      parts.put(EnemyStats.DEAD_SOLDIERS, new PartRange(2, 6));
      parts.put(EnemyStats.TOWER_BEAST, new PartRange(3, 6));
      parts.put(EnemyStats.CHIMERA, new PartRange(4, 6));
      parts.put(EnemyStats.NECROMANCER, new PartRange(6, 6));
      parts.put(EnemyStats.STONE_GOLEM, new PartRange(6, 6));
      parts.put(EnemyStats.DEAD_DRAGON, new PartRange(6, 6));
      parts.put(EnemyStats.DRAGON, new PartRange(6, 6));
    }

    /** The part-count range of an enemy type. */
    public PartRange partsOf(EnemyStats type) {
      return parts.getOrDefault(
          type, new PartRange(BodyPart.values().length, BodyPart.values().length));
    }

    /** An independent copy, so one campaign's tower never shares tuning with another's. */
    public Enemies copy() {
      var copy = new Enemies(weights);
      BeanUtils.copyProperties(this, copy);
      copy.parts = new EnumMap<>(EnemyStats.class);
      parts.forEach((type, range) -> copy.parts.put(type, new PartRange(range.min, range.max)));
      return copy;
    }
  }

  /** An inclusive range of body parts. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PartRange {
    private int min;
    private int max;
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

  /** How a unit's rolled health total is divided among its body parts. */
  @Data
  public static class Body {
    private final Weights weights = new Weights();

    /** Each part's share of the total; with two arms and two legs the shares sum to 1. */
    @Data
    public static class Weights {
      private double torso = 0.50;
      private double head = 0.10;
      private double arm = 0.08;
      private double leg = 0.12;
    }
  }

  /**
   * How attacks are aimed at body parts and how often they miss. Chances are percentage points: 10
   * means 10%.
   */
  @Data
  public static class Aiming {
    /** Base miss chance of a hit on each kind of part, before accuracy and evasion. */
    private double torsoMiss = 10.0;

    private double headMiss = 50.0;
    private double armMiss = 30.0;
    private double legMiss = 30.0;

    /** A miss chance never falls below this, however accurate the attacker or clumsy the target. */
    private double minMiss = 5.0;

    /** A miss chance never rises above this. */
    private double maxMiss = 90.0;

    /** How much likelier than any other part the head is when this unit type aims at one part. */
    private Map<UnitStats, Double> headBias = new EnumMap<>(UnitStats.class);

    /** Weights over the aims for each unit type; a missing aim has weight 0. */
    private Map<UnitStats, Map<Aim, Double>> unitProfiles = new EnumMap<>(UnitStats.class);

    /** Weights over the aims for each enemy type. */
    private Map<EnemyStats, Map<Aim, Double>> enemyProfiles = new EnumMap<>(EnemyStats.class);

    public Aiming() {
      headBias.put(UnitStats.ARCHER, 3.0);
      headBias.put(UnitStats.ROGUE, 3.0);

      unitProfiles.put(UnitStats.WARRIOR, profile(30, 0, 50, 20));
      unitProfiles.put(UnitStats.ARCHER, profile(70, 20, 0, 10));
      unitProfiles.put(UnitStats.PORTER, profile(100, 0, 0, 0));
      unitProfiles.put(UnitStats.MAGE, profile(20, 0, 0, 80));
      unitProfiles.put(UnitStats.CAPTAIN, profile(40, 0, 40, 20));
      unitProfiles.put(UnitStats.TANK, profile(20, 30, 0, 50));
      unitProfiles.put(UnitStats.ROGUE, profile(70, 30, 0, 0));
      unitProfiles.put(UnitStats.NECROMANCER, profile(30, 0, 0, 70));
      unitProfiles.put(UnitStats.DRACO_METAMORPH, profile(0, 40, 0, 60));
      unitProfiles.put(UnitStats.HEALER, profile(100, 0, 0, 0));

      enemyProfiles.put(EnemyStats.SLIME, profile(100, 0, 0, 0));
      enemyProfiles.put(EnemyStats.CHYMUS_INSECT, profile(100, 0, 0, 0));
      enemyProfiles.put(EnemyStats.DEAD_SOLDIERS, profile(50, 0, 30, 20));
      enemyProfiles.put(EnemyStats.TOWER_BEAST, profile(40, 40, 0, 20));
      enemyProfiles.put(EnemyStats.CHIMERA, profile(20, 30, 0, 50));
      enemyProfiles.put(EnemyStats.NECROMANCER, profile(30, 0, 0, 70));
      enemyProfiles.put(EnemyStats.STONE_GOLEM, profile(20, 0, 50, 30));
      enemyProfiles.put(EnemyStats.DEAD_DRAGON, profile(10, 30, 0, 60));
      enemyProfiles.put(EnemyStats.DRAGON, profile(10, 30, 0, 60));
    }

    private static Map<Aim, Double> profile(double part, double lineV, double lineH, double body) {
      var weights = new EnumMap<Aim, Double>(Aim.class);
      weights.put(Aim.PART, part);
      weights.put(Aim.LINE_V, lineV);
      weights.put(Aim.LINE_H, lineH);
      weights.put(Aim.BODY, body);
      return weights;
    }

    /** The base miss chance of a hit on this kind of part, in percentage points. */
    public double baseMiss(BodyPart kind) {
      return switch (kind) {
        case TORSO -> torsoMiss;
        case HEAD -> headMiss;
        case LEFT_ARM, RIGHT_ARM -> armMiss;
        case LEFT_LEG, RIGHT_LEG -> legMiss;
      };
    }

    /** The aim weights of a unit type, or one that always aims at a single part. */
    public Map<Aim, Double> profileOf(UnitStats type) {
      return unitProfiles.getOrDefault(type, Map.of(Aim.PART, 1.0));
    }

    /** The aim weights of an enemy type, or one that always aims at a single part. */
    public Map<Aim, Double> profileOf(EnemyStats type) {
      return enemyProfiles.getOrDefault(type, Map.of(Aim.PART, 1.0));
    }

    public double headBiasOf(UnitStats type) {
      return headBias.getOrDefault(type, 1.0);
    }
  }

  /**
   * Sanity and phobias: what a unit's mind gains and loses, how phobias are drawn at recruit, and
   * how hard they bite. Values are provisional; the balance pass (#8) tunes them.
   */
  @Data
  public static class Sanity {
    /** Every unit starts at, and recovers toward, this much sanity. */
    private double max = 100.0;

    /** Gained by the unit that lands a killing blow. */
    private double killGain = 2.0;

    /** Gained by every party member when the party climbs to the next floor. */
    private double floorClimbedGain = 5.0;

    /** Lost by each living party member when an ally dies on the tower. */
    private double allyDeathDrain = 10.0;

    /** Lost, once per floor, by a unit that meets an enemy type it fears. */
    private double fearMeetDrain = 5.0;

    /** Lost by every party member each lifecycle tick, times the floor number. */
    private double depthDrainPerFloor = 0.01;

    /** Gained per lifecycle tick by each reserve whose sanity is below its maximum. */
    private double recoveryPerTick = 2.0;

    /** Pantry food that one reserve's tick of recovery costs. */
    private double recoveryFoodPerTick = 0.5;

    /** Chances of drawing 0, 1 and 2 phobias at recruit, in that order. */
    private List<Double> phobiaCountChances = new ArrayList<>(List.of(0.40, 0.40, 0.20));

    /** Severity at full sanity; it climbs linearly to 1.0 at zero sanity. */
    private double severityAtFullSanity = 0.25;

    /** Share of damage a phobia moves at severity 1: dealt x(1 - this), taken x(1 + this). */
    private double phobiaEffect = 0.5;
  }

  /** Gear: how items are rated against each other and how Leftovers are generated. */
  @Data
  public static class Items {
    private final Weights weights = new Weights();
    private final Leftover leftover = new Leftover();
    private final Bundle bundle = new Bundle();
    private final DropRates dropRates = new DropRates();
    private final SurplusRelics surplusRelics = new SurplusRelics();
    private final Effects effects = new Effects();
    private final Artifacts artifacts = new Artifacts();
    private final Affinity affinity = new Affinity();

    /** The leaning a unit may have towards one item category. */
    @Data
    public static class Affinity {
      /** Chance a fresh recruit has an affinity at all. */
      private double chance = 0.5;

      /** What the rating of items in the affinity category is multiplied by, for that unit. */
      private double ratingMultiplier = 1.25;

      /** What the unit's find chance is multiplied by for items in the affinity category. */
      private double findMultiplier = 1.5;
    }

    /** Most items a floor keeps for the party to find again; the lowest rating vanish first. */
    private int floorGearCap = 12;

    /** The Bundle of a cleared floor and the rolls that share it out. */
    @Data
    public static class Bundle {
      /** Chance that a cleared non-guardian floor generates a Leftover. */
      private double leftoverChance = 0.20;

      /** Chance that one living party member finds an item, before any affinity multiplier. */
      private double findChance = 0.15;

      /** Sanity a unit gains from finding an item. */
      private double sanityGain = 10.0;
    }

    /**
     * How likely a find is each tier, as weights (they need not sum to 1). Talismans are rarer than
     * Leftovers. Artifacts are never rolled: they sit at their home floor.
     */
    @Data
    public static class DropRates {
      private double leftover = 0.9;
      private double talisman = 0.1;
    }

    /** What a surplus item of a tier is worth when it is turned into Relics. */
    @Data
    public static class SurplusRelics {
      private int leftover = 1;
      private int talisman = 3;

      /** Relics for a surplus item of {@code tier}; an Artifact is never surplus. */
      public int of(ItemTier tier) {
        return switch (tier) {
          case LEFTOVER -> leftover;
          case TALISMAN -> talisman;
          case ARTIFACT -> 0;
        };
      }
    }

    /** The numbers behind each special effect. */
    @Data
    public static class Effects {
      /** Steadfast: maximum sanity added. */
      private double steadfastMaxSanity = 10.0;

      /** Keen: accuracy added, in percentage points. */
      private double keenAccuracy = 10.0;

      /** Wary: evasion added, in percentage points. */
      private double waryEvasion = 10.0;

      /** Ember: damage added to a Magic attack. */
      private double emberMagicDamage = 1.0;

      /** Gloom: share off damage taken from Dead Soldiers and Necromancers. */
      private double gloomTakenReduction = 0.05;

      /** Salted: share off damage taken from Slimes. */
      private double saltedTakenReduction = 0.25;

      /** Thorn: damage returned to a melee attacker that hurts the wearer. */
      private double thornDamage = 1.0;

      /** Lantern: sanity gained per floor climbed, on top of the usual gain. */
      private double lanternSanityPerFloor = 2.0;

      /** Lilicen's Mana Vessel: maximum mana added to a caster. */
      private double manaVesselMaxMana = 5.0;

      /** The Dragon-Slayer's Blade: damage multiplier against Dragon and Dead Dragon. */
      private double dragonSlayerMultiplier = 2.0;

      /** Yuva's Salt Satchel: what attrition damage is multiplied by. */
      private double saltSatchelAttritionFactor = 0.5;
    }

    /** The home floor of each Artifact. */
    @Data
    public static class Artifacts {
      private int sargansMaskFloor = 40;
      private int manaVesselFloor = 30;
      private int dragonSlayerFloor = 60;
      private int saltSatchelFloor = 20;

      /** The floor whose Bundle holds the Artifact of this effect. */
      public int homeFloorOf(ItemEffect effect) {
        return switch (effect) {
          case SARGANS_MASK -> sargansMaskFloor;
          case LILICENS_MANA_VESSEL -> manaVesselFloor;
          case DRAGON_SLAYERS_BLADE -> dragonSlayerFloor;
          case YUVAS_SALT_SATCHEL -> saltSatchelFloor;
          default -> throw new IllegalArgumentException(effect + " is not an Artifact effect");
        };
      }
    }

    /** What one point of each stat adds to an item's rating, and what an effect adds. */
    @Data
    public static class Weights {
      private double power = 1.0;
      private double armour = 0.8;
      private double accuracy = 0.6;
      private double evasion = 0.6;
      private double sanity = 0.4;
      private double effect = 2.0;
    }

    /** A stat's value is its base, times (1 + floorScaling x floor), times a uniform roll. */
    @Data
    public static class Leftover {
      private final Base base = new Base();
      private int minStats = 1;
      private int maxStats = 2;
      private double floorScaling = 0.05;
      private double rollMin = 0.5;
      private double rollMax = 1.5;

      /** The unscaled value of each stat. */
      @Data
      public static class Base {
        private double power = 0.5;
        private double armour = 0.5;
        private double accuracy = 5.0;
        private double evasion = 5.0;
        private double sanity = 5.0;
      }
    }
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

  /** The Villager pool's Trades, their yields, Posts, guards and the Stockroom and Quarry Heap. */
  @Data
  public static class Trades {
    /** Materials one Breaker cuts per tick, before the Highest Floor yield. */
    private double breakerMaterialsPerTick = 0.25;

    /** Knowledge one Scribe writes per tick, before the Highest Floor yield. */
    private double scribeKnowledgePerTick = 0.05;

    /** Yield of the Quarry and the Study grows by this share per Highest Floor. */
    private double yieldPerHighestFloor = 0.02;

    /** Posts a workplace other than the Fields starts with. */
    private int startingPosts = 10;

    /** Tillers who must stay at the Fields: an assignment limit, not a safeguard. */
    private int tillerMinimum = 5;

    /** Ticks a moved Villager is Settling, and cannot be moved again. */
    private int settlingTicks = 10;

    /** Emergency Tilling starts when the Pantry is below this and Upkeep exceeds the Fields. */
    private double emergencyTillingBelow = 20.0;

    /** Emergency Tilling ends once the Pantry is above this. */
    private double emergencyTillingAbove = 60.0;

    /** Stockroom capacity of each good is this plus {@code stockroomPerFloor} per Highest Floor. */
    private double stockroomBase = 60.0;

    private double stockroomPerFloor = 4.0;

    /** Quarry Heap capacity per Highest Floor. */
    private double heapPerFloor = 10.0;

    /** Materials of the Heap an Extraction delivers per Floor cleared in that Expedition. */
    private double heapDeliveryPerFloorCleared = 5.0;
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
