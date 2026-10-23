package io.github.diogohmcruz.towerdungeon.config;

import java.time.Duration;
import java.time.Instant;
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
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
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
  private final Wipe wipe = new Wipe();
  private final Magic magic = new Magic();
  private final Reinforcements reinforcements = new Reinforcements();
  private final Supply supply = new Supply();
  private final Healing healing = new Healing();
  private final Village village = new Village();
  private final Trades trades = new Trades();
  private final Tiers tiers = new Tiers();
  private final Salt salt = new Salt();
  private final Trust trust = new Trust();
  private final Spores spores = new Spores();
  private final Architecture architecture = new Architecture();
  private final Prestige prestige = new Prestige();
  private final Persistence persistence = new Persistence();
  private final Security security = new Security();
  private final Agent agent = new Agent();
  private final Trial trial = new Trial();

  /**
   * An independent deep copy of the whole configuration, so one Campaign's overrides never reach
   * another's. Shared parts that are shared on purpose (the Body weights the Enemies divide health
   * by) stay shared inside the copy.
   */
  public GameProperties copy() {
    return ConfigCopy.of(this);
  }

  /**
   * Hardens this Campaign's own Boss and Enemies tuning with Tower Memory for {@code generation}:
   * {@code m = min(generation - 1, maxSteps)}. Call it on a copy; never on the shared config.
   */
  public GameProperties withTowerMemory(int generation) {
    var memory = prestige.getMemory();
    var m = Math.clamp(generation - 1L, 0, Math.max(0, memory.getMaxSteps()));
    enemies.setMemoryBudget(1 + memory.getBudgetPerStep() * m);
    boss.setMemoryHealth(1 + memory.getGuardianHealthPerStep() * m);
    if (m > 0 && memory.getUpperGuardianFloor() > 0) {
      boss.setMemoryUpperFloor(memory.getUpperGuardianFloor());
      boss.setMemoryUpperHealthCap(1 + memory.getUpperGuardianBonusCap());
    }
    return this;
  }

  /**
   * The Trials a server offers: sealed Campaigns on identical kits, raced on a Tower seeded from
   * their Terms. See {@code docs/brainstorm/multiplayer.md} section 3.1.
   */
  @Data
  public static class Trial {
    /** The Terms on offer, by race. Empty offers no Trial. */
    private List<Race> races = new ArrayList<>();

    /** No Trial may run longer than this, however its Terms are written. */
    private Duration maxLimit = Duration.ofMinutes(90);

    /**
     * A race's results file is closed and a new one begun once it is this large, so no file grows
     * without bound; the closed files are read with the open one.
     */
    private long resultsRotateBytes = 1_048_576L;

    /**
     * One race's Terms as written in configuration. The seed is server-only: leave it blank to
     * derive it from the server secret and the race id, and never log or send it.
     */
    @Data
    public static class Race {
      private String raceId;

      /** SEASON or SPRINT; only SEASON can be entered so far. */
      private String kind = "SEASON";

      /** Pins the Tower plan: it must equal the plan version this build generates. */
      private int planVersion = 1;

      /** Pins the balance: blank pins it to the running configuration at start-up. */
      private String configHash;

      private Long seed;

      /** The Guardian Floor that ends the Trial; kept apart from {@code tower.max-floor}. */
      private int finishFloor = 10;

      /** Blank is open from the start. */
      private Instant opensAt;

      /** Blank never closes. */
      private Instant closesAt;

      /** The clock: connected lifecycle time a Trial may take before it times out. */
      private Duration limit = Duration.ofMinutes(90);

      private int maxAttempts = 3;

      /** Connected Trials of this race at once. */
      private int maxConcurrent = 20;
    }
  }

  /** Settings of the agent surface (the MCP server and its Sandbox Campaigns). */
  @Data
  public static class Agent {
    private final Sandbox sandbox = new Sandbox();

    /** Sandbox Campaigns: headless Campaigns that advance only when a driver ticks them. */
    @Data
    public static class Sandbox {
      /** Off by default: turn it on for a local or private run, never on a public server. */
      private boolean enabled = false;

      /** Most live Sandbox Campaigns one grant may hold. */
      private int maxPerGrant = 8;

      /** Most live Sandbox Campaigns across all grants. */
      private int maxGlobal = 64;

      /** Wall time one call may spend advancing a Sandbox before it stops and says so. */
      private Duration callWallTime = Duration.ofSeconds(2);

      /** Simulated seconds one Campaign may run in all (6 hours): idle farming has a ceiling. */
      private long maxSimulatedSeconds = 21_600;

      /** Simulated seconds (ticks) one wait may advance. */
      private int maxTicksPerWait = 3_600;
    }
  }

  /** What a Wipe leaves on the Tower: the Risen. */
  @Data
  public static class Wipe {
    /** Most Risen one floor can hold, however many fell there. */
    private int risenCapMax = 3;

    /** Share of the floor's eased base budget the Risen may weigh at most. */
    private double risenCapFraction = 0.30;
  }

  /** Where Campaigns are kept. */
  @Data
  public static class Persistence {
    /**
     * Root of the Campaign files; must be a mounted volume wherever the filesystem is ephemeral.
     */
    private String dataDir = "./data";

    /** How long a slot with no connection stays in memory, frozen, before it is evicted. */
    private Duration linger = Duration.ofSeconds(30);

    /** Quiet time after a purchase before it is written. */
    private Duration purchaseDebounce = Duration.ofSeconds(3);

    /** Longest a stream of purchases may delay their write. */
    private Duration purchaseMaxWait = Duration.ofSeconds(10);

    /** How often a connected player's Campaign is written while it has unsaved changes. */
    private Duration autosaveInterval = Duration.ofSeconds(30);

    /** The autosave interval while an Expedition is in flight. */
    private Duration expeditionAutosaveInterval = Duration.ofSeconds(5);

    /** Minimum time between backup rotations by autosave. */
    private Duration rotationInterval = Duration.ofMinutes(10);

    /** First retry delay after a failed write; it doubles up to {@link #retryMax}. */
    private Duration retryInitial = Duration.ofSeconds(1);

    /** Longest delay between retries of a failed write. */
    private Duration retryMax = Duration.ofSeconds(30);

    /** Failed writes in a row before the player is told that records are not being kept. */
    private int failuresBeforeNotice = 3;

    /** Longest the shutdown flush waits for the writes to finish. */
    private Duration shutdownWait = Duration.ofSeconds(20);

    /**
     * Longest the end of a Campaign, or the start of a Generation, waits for its write to finish
     * before the player is told it failed and nothing changes.
     */
    private Duration endWriteWait = Duration.ofSeconds(10);

    /**
     * Key that seals the spoiler-sensitive parts of exported Campaign files. Must stay the same
     * across deploys. Required in the {@code prod} profile; elsewhere a fixed development key is
     * used when unset.
     */
    private String sealKey;
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
                new Founder("Yuva", UnitStats.WARRIOR, 5.0, true, true),
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

    /**
     * Whether the multiplier and fixed strengths apply in Generation 1 only: from Generation 2 the
     * Founder is an ordinary recruit of the same name and type.
     */
    private boolean firstGenerationOnly = false;

    public Founder(String name, UnitStats unitType, double statMultiplier, boolean fixedStrengths) {
      this(name, unitType, statMultiplier, fixedStrengths, false);
    }
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

    /**
     * Tower Memory: a Guardian's total health is multiplied by this last, before it is divided
     * across its Body Parts. 1.0 in Generation 1; set on the Campaign's own copy.
     */
    private double memoryHealth = 1.0;

    /** Guardians above this Floor take no more than {@link #memoryUpperHealthCap} from Memory. */
    private int memoryUpperFloor = 0;

    /** The most a Guardian above {@link #memoryUpperFloor} gains from Memory; 0 for no cap. */
    private double memoryUpperHealthCap = 0.0;

    /**
     * The Open Breach: the Guardian Floor that holds only its garrison this Generation; 0 for none.
     * Set on the Campaign's own copy from the player's meta.
     */
    private int openBreach = 0;

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

    /**
     * Tower Memory: a garrison's budget is multiplied by this last, after Easing and step-ups. 1.0
     * in Generation 1; set on the Campaign's own copy.
     */
    private double memoryBudget = 1.0;

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
     * Easing is multiplied by this (both the garrison thinning and the tier reduction). 1.0 unless
     * an Epitaph (Ten Seals Broken) raises it on the Campaign's own copy.
     */
    private double easeMultiplier = 1.0;

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

    /** Accuracy points lost at zero sanity; the loss falls linearly to nothing at full sanity. */
    private double accuracyPenalty = 20.0;
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

  /**
   * What each Village Tier asks of the Tally, the Highest Floor and the Expeditions. Tier I is the
   * start and asks nothing. A Tier is reached when every condition of its row holds.
   */
  @Data
  public static class Tiers {
    private final Gate sheltered = new Gate(1000, 0, 0, 5, 0);
    private final Gate fortified = new Gate(0, 1000, 0, 10, 6);
    private final Gate entrenched = new Gate(0, 0, 60, 25, 0);

    /** One Tier's conditions; zero means the Tier does not ask for it. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Gate {
      private double foodGrown;
      private double materialsBanked;
      private double relicsBanked;
      private int highestFloor;
      private int expeditions;
    }
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

    /** Knowledge the first level of a Tome costs. */
    private double tomeBaseCost = 60.0;

    /** Each further Tome level costs this many times the one before. */
    private double tomeCostGrowth = 1.5;

    /** Output a Tome level adds: 0.25 is +25% of the base. */
    private double tomeBonusPerLevel = 0.25;

    /** Posts a Post upgrade adds to the Quarry or the Scribe's Study. */
    private int postsPerLevel = 5;

    /** Materials the first Post upgrade costs. */
    private double postBaseCost = 25.0;

    /** Each further Post upgrade costs this many times the one before. */
    private double postCostGrowth = 1.6;
  }

  /** Salt: made in the Salt Pans, spent at the Loadout to salt Supplies. */
  @Data
  public static class Salt {
    /** Salt one Salter makes per tick. */
    private double perSalterPerTick = 0.15;

    /** Supplies one Salt salts. */
    private double suppliesPerSalt = 10.0;

    /**
     * Drain per Unit per tick of fully salted Supplies, against {@code loop.supplyDrainPerUnit}.
     */
    private double saltedDrainPerUnit = 1.25;

    /** Credits one Salt sells for; a dump valve for a full Stockroom, not a source of Credits. */
    private double sellPrice = 2.0;

    /** Carrying Capacity the Salt Pack takes while the Party carries it. */
    private double packCapacity = 10.0;

    /** The one enemy type the Salt Pack works on; Slimes are not it. */
    private EnemyStats packEnemy = EnemyStats.CHYMUS_INSECT;

    /** Share added to the damage a salted enemy takes: 0.5 is +50%. */
    private double packDamageBonus = 0.5;

    /** Chance that a Chymus Insect aims at the Salt-bearer instead of choosing at random. */
    private double bearerTargetBias = 0.75;
  }

  /**
   * Trust, earned on Extraction, and the Thought-Binding Ring it buys. The Ring is a Village
   * mechanic, not an Artifact.
   */
  @Data
  public static class Trust {
    /** Levels the Ring can reach; the sink ends here. */
    private int ringMaxLevel = 5;

    /** Trust the first Ring level costs. */
    private double ringBaseCost = 10.0;

    /** Each further Ring level costs this many times the one before, rounded up. */
    private double ringCostGrowth = 1.5;

    /** Floors per tick a Ring level adds to a Reinforcement Wave's climb. */
    private double waveSpeedPerLevel = 0.25;

    /** Carrying Capacity a Ring level adds to every Wave. */
    private double waveCapacityPerLevel = 5.0;

    /** Carrying Capacity a level of Survivors' Notes adds to every Wave, and to nothing else. */
    private double survivorsNotesCapacityPerLevel = 10.0;
  }

  /** Dragon Spores: made in the Spore Cellar, found as loot, spent on Doses and the Panacea. */
  @Data
  public static class Spores {
    /** Spores one Sporewarden makes per tick (6 make 0.3). */
    private double perSporewardenPerTick = 0.05;

    /** Health one Spore Dose restores, or what a Remedy restores. */
    private double doseHeal = 20.0;

    /** Share of a part's maximum health a reattached arm or leg comes back with. */
    private double reattachShare = 0.25;

    /** Spores a Panacea is made from. */
    private int panaceaSpores = 4;

    /** Materials from the Vault a Panacea is made from. */
    private double panaceaMaterials = 40.0;

    /** Carrying Capacity a Panacea takes up. */
    private double panaceaCapacity = 10.0;

    /** Share of its maximum a vital part is left at when the Panacea saves it. */
    private double panaceaSaveShare = 0.10;

    /** The first Floor whose clearing yields Spores as loot. */
    private int lootFromFloor = 20;

    /** Spores each cleared Floor yields from that Floor. */
    private double lootPerFloor = 1.0;

    /** Multiplier of that loot on a Guardian Floor. */
    private double guardianLootMultiplier = 3.0;
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

    /** Tower Memory: how a Tower hardens each Generation. */
    private final Memory memory = new Memory();

    /** Whether each ended Expedition writes its Toll to the log, for the balance refit. */
    private boolean logToll = true;

    /** Legacy the forecast must reach before the player may choose Exodus. */
    private int minPointsToExodus = 3;

    /** The floor a Defeat pays once the Campaign has done enough. */
    private final DefeatPity defeatPity = new DefeatPity();

    /** What a Generation that follows a Defeat starts with. */
    private final DefeatStart defeatStart = new DefeatStart();

    /** Most names of the fallen kept in a Chronicle entry. */
    private int chronicleNames = 5;

    /** Most Campaigns the Chronicle keeps; the oldest drop off. */
    private int chronicleLimit = 50;

    /** Tower Memory {@code m = min(Generation - 1, maxSteps)}, derived and never stored. */
    @Data
    public static class Memory {
      private int maxSteps = 4;
      private double budgetPerStep = 0.05;
      private double guardianHealthPerStep = 0.08;

      /**
       * Guardians above this Floor gain at most {@link #upperGuardianBonusCap} of health from
       * Memory, until the scaling pass for Floors 40 to 100 lands. 0 turns the cap off.
       */
      private int upperGuardianFloor = 50;

      private double upperGuardianBonusCap = 0.16;
    }

    /** The Legacy a Defeat pays at least, and the Expeditions it takes to earn it. */
    @Data
    public static class DefeatPity {
      private int points = 1;
      private int minQualifyingExpeditions = 3;
    }

    /** The Hollow start after a Defeat: fewer Villagers, and a Writ of Credits. */
    @Data
    public static class DefeatStart {
      private int villagersLost = 5;
      private double writCredits = 100.0;
    }

    /** The Roll: what each Inheritance costs and what it does. */
    private final Roll roll = new Roll();

    /**
     * The Roll's numbers. Costs are Legacy; a Course opens when the Legacy already spent in the
     * Courses below reaches its gate. The effects apply when the Generation begins.
     */
    @Data
    public static class Roll {
      /** Legacy that must already be spent in lower Courses, for Courses I to IV. */
      private List<Integer> gates = new ArrayList<>(List.of(0, 5, 12, 21));

      /** Legacy each Inheritance costs; the full design totals 64. */
      private Map<InheritanceType, Integer> costs = defaultCosts();

      private final SeedGrain seedGrain = new SeedGrain();
      private final HardyStock hardyStock = new HardyStock();
      private final WhetstoneHabit whetstoneHabit = new WhetstoneHabit();
      private final Dowry dowry = new Dowry();
      private final HeavierPacks heavierPacks = new HeavierPacks();
      private final VillageCharter villageCharter = new VillageCharter();
      private final Heir heir = new Heir();
      private final OfferingStone offeringStone = new OfferingStone();
      private final NarrowStair narrowStair = new NarrowStair();
      private final Overreach overreach = new Overreach();
      private final QuartermastersTally quartermastersTally = new QuartermastersTally();

      private static Map<InheritanceType, Integer> defaultCosts() {
        var costs = new EnumMap<InheritanceType, Integer>(InheritanceType.class);
        costs.put(InheritanceType.SEED_GRAIN, 1);
        costs.put(InheritanceType.MUSTER_ROLL, 1);
        costs.put(InheritanceType.HARDY_STOCK, 2);
        costs.put(InheritanceType.WHETSTONE_HABIT, 2);
        costs.put(InheritanceType.THE_DOWRY, 3);
        costs.put(InheritanceType.HEAVIER_PACKS, 3);
        costs.put(InheritanceType.VILLAGE_CHARTER, 3);
        costs.put(InheritanceType.THE_HEIR, 3);
        costs.put(InheritanceType.BLOOD_OF_THE_DOOR, 6);
        costs.put(InheritanceType.NARROW_STAIR, 5);
        costs.put(InheritanceType.OVERREACH, 4);
        costs.put(InheritanceType.QUARTERMASTERS_TALLY, 3);
        costs.put(InheritanceType.HARD_WON_HABITS, 8);
        costs.put(InheritanceType.THE_OPEN_BREACH, 6);
        costs.put(InheritanceType.READER_OF_OMENS, 4);
        costs.put(InheritanceType.OFFERING_STONE, 5);
        costs.put(InheritanceType.SECOND_PACT, 5);
        return costs;
      }

      /** What the Inheritance costs; an id with no price is never for sale. */
      public int costOf(InheritanceType type) {
        return costs.getOrDefault(type, Integer.MAX_VALUE);
      }

      /**
       * Legacy that must already be spent in lower Courses before {@code course} (1 to 4) opens.
       */
      public int gateOf(int course) {
        if (course < 1 || course > gates.size()) {
          return Integer.MAX_VALUE;
        }
        return gates.get(course - 1);
      }
    }

    /** Seed Grain: the Pantry and the purse a Generation starts with, at least these. */
    @Data
    public static class SeedGrain {
      private double food = 150.0;
      private double credits = 20.0;
    }

    /** Hardy Stock: the Villagers a Generation starts with, at least this many. */
    @Data
    public static class HardyStock {
      private int villagers = 25;
    }

    /** Whetstone Habit: added to the Party's damage multiplier (0.05 is +5%). */
    @Data
    public static class WhetstoneHabit {
      private double damageBonus = 0.05;
    }

    /** The Dowry: what each choice pays at the start of a Generation. */
    @Data
    public static class Dowry {
      private double credits = 80.0;
      private double materials = 40.0;
      private double relics = 3.0;
    }

    /** Heavier Packs: added to the base Carrying Capacity. */
    @Data
    public static class HeavierPacks {
      private double capacity = 25.0;
    }

    /** Village Charter: the Village Tier (1 to 4) a Generation starts at, at least this. */
    @Data
    public static class VillageCharter {
      private int tier = 2;
    }

    /** The Heir: the extra Unit's total health, as a multiple of its rolled health. */
    @Data
    public static class Heir {
      private double healthMultiplier = 1.5;
    }

    /**
     * The Offering Stone: Relics still in the Vault at the end of a Campaign are offered, {@code
     * relicsPerLegacy} for each Legacy, at most {@code maxLegacy} a Generation. Relics now come
     * only from surplus items, so the threshold is likely too high until the par refit.
     */
    @Data
    public static class OfferingStone {
      private double relicsPerLegacy = 300.0;
      private int maxLegacy = 3;
    }

    /**
     * Epitaphs: free, stored as a ratchet when the Lifetime Ledger crosses a threshold (checked
     * when a Campaign ends, effective from the next Generation).
     */
    private final Epitaphs epitaphs = new Epitaphs();

    /** The three Epitaphs: when each is earned and what it does. */
    @Data
    public static class Epitaphs {
      private final FirstFooting firstFooting = new FirstFooting();
      private final NamesOnTheWall namesOnTheWall = new NamesOnTheWall();
      private final TenSealsBroken tenSealsBroken = new TenSealsBroken();
    }

    /** First Footing: the Vanguard Camp Shortcut is open from the start. */
    @Data
    public static class FirstFooting {
      private int lifetimeHighestFloor = 10;
    }

    /** Names on the Wall: every Unit's health total is raised before it is divided into parts. */
    @Data
    public static class NamesOnTheWall {
      private int lifetimeUnitsLost = 100;
      private double healthBonus = 0.05;
    }

    /** Ten Seals Broken: Easing is multiplied. */
    @Data
    public static class TenSealsBroken {
      private int lifetimeGuardiansSlain = 15;
      private double easingMultiplier = 1.25;
    }

    /**
     * Narrow Stair: the Floor of its Shortcut and the largest Party that may use it. Both are
     * placeholders chosen to be survivable on paper, until the measured figures are in.
     */
    @Data
    public static class NarrowStair {
      private int floor = 21;
      private int maxParty = 12;
    }

    /** Overreach: what the per-Expedition switch multiplies. */
    @Data
    public static class Overreach {
      private double supplyDrainMultiplier = 1.3;
      private double starvationDamageMultiplier = 2.0;
      private double lootMultiplier = 1.5;
    }

    /** Quartermaster's Tally: how many Loadouts may be saved. */
    @Data
    public static class QuartermastersTally {
      private int slots = 3;
    }

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
