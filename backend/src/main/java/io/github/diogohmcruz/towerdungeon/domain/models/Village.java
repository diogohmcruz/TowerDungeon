package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The Village: a pool of Villagers assigned to Trades, the Pantry they feed, the Stockroom and the
 * Quarry Heap they fill. Tillers are whoever is not assigned elsewhere, so the pool and the Trades
 * can never disagree.
 */
@Slf4j
@Data
public class Village {
  @Setter(AccessLevel.NONE)
  private Double food;

  @Setter(AccessLevel.NONE)
  private Integer villagersCount;

  @Setter(AccessLevel.NONE)
  private int breakers;

  @Setter(AccessLevel.NONE)
  private int scribes;

  /** Ticks left to settle, one entry per Settling hand of each Trade. */
  @JsonIgnore
  @Setter(AccessLevel.NONE)
  private Map<Trade, List<Integer>> settling = new EnumMap<>(Trade.class);

  /** Knowledge in the Stockroom. */
  @Setter(AccessLevel.NONE)
  private double knowledge;

  /** Materials cut and not yet delivered. */
  @Setter(AccessLevel.NONE)
  private double quarryHeap;

  /** Levels bought of each Tome. */
  @Setter(AccessLevel.NONE)
  private Map<Tome, Integer> tomes = new EnumMap<>(Tome.class);

  /** Post upgrades bought for each Trade; the Fields have none. */
  @Setter(AccessLevel.NONE)
  private Map<Trade, Integer> postLevels = new EnumMap<>(Trade.class);

  /** Whether Emergency Tilling is running; it ends only once the Pantry passes its upper mark. */
  @Setter(AccessLevel.NONE)
  private boolean emergencyTilling;

  /** Villagers in the Salt Pans. */
  @Setter(AccessLevel.NONE)
  private int salters;

  /** Salt in the Stockroom. */
  @Setter(AccessLevel.NONE)
  private double salt;

  /** Trust held. It is earned on Extraction, never sold, and spent only on the Ring. */
  @Setter(AccessLevel.NONE)
  private double trust;

  /** Levels bought of the Thought-Binding Ring. */
  @Setter(AccessLevel.NONE)
  private int ringLevel;

  /** Villagers in the Spore Cellar. */
  @Setter(AccessLevel.NONE)
  private int sporewardens;

  /** Dragon Spores in the Stockroom. */
  @Setter(AccessLevel.NONE)
  private double spores;

  /** The Count: Villagers lost to Starvation this Campaign. */
  @Setter(AccessLevel.NONE)
  private int villagersLost;

  /** The Count: Reserves lost to Starvation this Campaign. */
  @Setter(AccessLevel.NONE)
  private int reservesLost;

  @JsonIgnore private double villagerFoodProduction;
  @JsonIgnore private double unitFoodConsumption;
  @JsonIgnore private GameProperties.Trades trades;
  @JsonIgnore private GameProperties.Salt saltRules = new GameProperties.Salt();
  @JsonIgnore private GameProperties.Trust trustRules = new GameProperties.Trust();
  @JsonIgnore private GameProperties.Spores sporeRules = new GameProperties.Spores();

  /** What Emergency Tilling did this tick. {@code moved} is null when no one could be moved. */
  public record EmergencyTillingResult(boolean active, boolean started, Trade moved) {}

  /** The margin after one more or one fewer hand in a Trade; {@code blocked} says why not. */
  public record MovePreview(
      Trade trade,
      int delta,
      double fields,
      double upkeep,
      boolean fieldsCoverUpkeep,
      Integer pantryHoldSeconds,
      String blocked) {}

  public Village(GameProperties.Village config) {
    this(config, new GameProperties.Trades());
  }

  public Village(GameProperties.Village config, GameProperties.Trades trades) {
    this.food = config.getStartingFood();
    this.villagersCount = config.getStartingVillagers();
    this.villagerFoodProduction = config.getVillagerFoodProduction();
    this.unitFoodConsumption = config.getUnitFoodConsumption();
    this.trades = trades;
    for (var trade : Trade.values()) {
      settling.put(trade, new ArrayList<>());
    }
  }

  /** Restores the persisted facts; the production and upkeep rates stay as configured. */
  public void restore(double food, int villagers) {
    setFood(food);
    setVillagersCount(villagers);
    clampTrades();
  }

  /** Restores the Trades, the stores and The Count. Call after {@link #restore(double, int)}. */
  public void restoreTrades(
      int breakers,
      int scribes,
      Map<Trade, List<Integer>> settlingTicks,
      double knowledge,
      double quarryHeap,
      boolean emergencyTilling,
      int villagersLost,
      int reservesLost) {
    this.breakers = Math.max(0, breakers);
    this.scribes = Math.max(0, scribes);
    settlingTicks.forEach((trade, ticks) -> settling.put(trade, new ArrayList<>(ticks)));
    this.knowledge = Math.max(0, knowledge);
    this.quarryHeap = Math.max(0, quarryHeap);
    this.emergencyTilling = emergencyTilling;
    this.villagersLost = Math.max(0, villagersLost);
    this.reservesLost = Math.max(0, reservesLost);
    clampTrades();
  }

  /** Restores the Tome levels and Post upgrades bought. Missing entries mean none. */
  public void restoreUpgrades(Map<Tome, Integer> tomeLevels, Map<Trade, Integer> postUpgrades) {
    tomes = new EnumMap<>(Tome.class);
    postLevels = new EnumMap<>(Trade.class);
    if (tomeLevels != null) {
      tomeLevels.forEach((tome, level) -> tomes.put(tome, Math.max(0, level)));
    }
    if (postUpgrades != null) {
      postUpgrades.forEach(
          (trade, level) -> {
            if (trade != Trade.TILLER) {
              postLevels.put(trade, Math.max(0, level));
            }
          });
    }
    clampTrades();
  }

  /** Restores the Salt Pans. Call after {@link #restoreTrades}, which clamps the Trades. */
  public void restoreSalt(int salters, double salt, List<Integer> settlingSalters) {
    this.salters = Math.max(0, salters);
    this.salt = Math.max(0, salt);
    settling.put(
        Trade.SALTER, new ArrayList<>(settlingSalters == null ? List.of() : settlingSalters));
    clampTrades();
  }

  /** Restores the Salt Pans' hands and the Stockroom's Salt, leaving any Settling as it is. */
  public void restoreSalt(int salters, double salt) {
    restoreSalt(salters, salt, settling.get(Trade.SALTER));
  }

  /** Restores the Trust held and the Ring's level, which cannot pass its cap. */
  public void restoreTrust(double trust, int ringLevel) {
    this.trust = Math.max(0, trust);
    this.ringLevel = Math.max(0, Math.min(ringLevel, trustRules.getRingMaxLevel()));
  }

  /**
   * Earns Trust for an Extraction: one per Floor cleared, scaled by the share of the Party that
   * came home, so each death lowers it. Returns what was earned.
   */
  public double earnTrust(int floorsCleared, int survivors, int partySize) {
    if (floorsCleared <= 0 || survivors <= 0 || partySize <= 0) {
      return 0d;
    }
    var earned = floorsCleared * (double) Math.min(survivors, partySize) / partySize;
    trust += earned;
    return earned;
  }

  /** Trust the next Ring level costs, whole and rounded up. */
  public long ringCost() {
    return (long)
        Math.ceil(
            trustRules.getRingBaseCost() * Math.pow(trustRules.getRingCostGrowth(), ringLevel)
                - 1e-9);
  }

  public boolean ringMaxed() {
    return ringLevel >= trustRules.getRingMaxLevel();
  }

  /** Why the next Ring level cannot be bought now, or empty. */
  public Optional<String> ringBlocked() {
    if (ringMaxed()) {
      return Optional.of("The Ring holds all it can.");
    }
    var cost = ringCost();
    if (trust < cost) {
      return Optional.of(
          "Ring " + (ringLevel + 1) + " costs " + cost + ". Trust " + (long) trust + ".");
    }
    return Optional.empty();
  }

  /** Spends Trust on the next Ring level. Returns why not, or empty when bought. */
  public Optional<String> buyRing() {
    var blocked = ringBlocked();
    if (blocked.isPresent()) {
      return blocked;
    }
    trust -= ringCost();
    ringLevel++;
    return Optional.empty();
  }

  /** Floors per tick the Ring adds to a Wave's climb. */
  public double waveSpeedBonus() {
    return ringLevel * trustRules.getWaveSpeedPerLevel();
  }

  /** Carrying Capacity the Ring and Survivors' Notes add to every Wave, and to nothing else. */
  public double waveCapacityBonus() {
    return ringLevel * trustRules.getWaveCapacityPerLevel()
        + tomeLevel(Tome.SURVIVORS_NOTES) * trustRules.getSurvivorsNotesCapacityPerLevel();
  }

  /** Restores the Spore Cellar. Call after {@link #restoreTrades}, which clamps the Trades. */
  public void restoreSpores(int sporewardens, double spores, List<Integer> settlingSporewardens) {
    this.sporewardens = Math.max(0, sporewardens);
    this.spores = Math.max(0, spores);
    settling.put(
        Trade.SPOREWARDEN,
        new ArrayList<>(settlingSporewardens == null ? List.of() : settlingSporewardens));
    clampTrades();
  }

  /** Restores the Cellar's hands and the Stockroom's Spores, leaving any Settling as it is. */
  public void restoreSpores(int sporewardens, double spores) {
    restoreSpores(sporewardens, spores, settling.get(Trade.SPOREWARDEN));
  }

  private void clampTrades() {
    scribes = Math.min(scribes, villagersCount);
    breakers = Math.min(breakers, villagersCount - scribes);
    salters = Math.min(salters, villagersCount - scribes - breakers);
    sporewardens = Math.min(sporewardens, villagersCount - scribes - breakers - salters);
    for (var trade : Trade.values()) {
      var ticks = settling.get(trade);
      while (ticks.size() > hands(trade)) {
        ticks.removeLast();
      }
    }
  }

  private void setFood(Double food) {
    this.food = Math.max(0, food);
  }

  private void setVillagersCount(Integer villagersCount) {
    this.villagersCount = Math.max(0, villagersCount);
  }

  /** The tick without a Highest Floor: the Trades yield their base. */
  public boolean triggerLifecycle(Integer unitCount) {
    return triggerLifecycle(unitCount, 0);
  }

  public boolean triggerLifecycle(Integer unitCount, int highestFloor) {
    this.setFood(Math.max(0, this.food + getProductionRate() - upkeepFor(unitCount)));
    this.quarryHeap = filled(quarryHeap, materialsPerTick(highestFloor), heapCap(highestFloor));
    this.knowledge = filled(knowledge, knowledgePerTick(highestFloor), stockroomCap(highestFloor));
    this.salt = filled(salt, saltPerTick(), stockroomCap(highestFloor));
    this.spores = filled(spores, sporesPerTick(), stockroomCap(highestFloor));
    settling.values().forEach(ticks -> ticks.replaceAll(left -> left - 1));
    settling.values().forEach(ticks -> ticks.removeIf(left -> left <= 0));
    return checkStarvation();
  }

  /** Output beyond the cap is wasted; a store already above it is left as it is. */
  private static double filled(double held, double output, double cap) {
    return Math.max(held, Math.min(cap, held + output));
  }

  /** Villagers working {@code trade}; Tillers are whoever is not a Breaker or a Scribe. */
  public int hands(Trade trade) {
    return switch (trade) {
      case TILLER -> villagersCount - breakers - scribes - salters - sporewardens;
      case BREAKER -> breakers;
      case SCRIBE -> scribes;
      case SALTER -> salters;
      case SPOREWARDEN -> sporewardens;
    };
  }

  /** Hands of {@code trade} that are Settling. */
  public int settlingHands(Trade trade) {
    return settling.get(trade).size();
  }

  /** Whole ticks until the next Settling hand of {@code trade} is free, or 0 if none settles. */
  public int settlingSeconds(Trade trade) {
    return settling.get(trade).stream().min(Comparator.naturalOrder()).orElse(0);
  }

  /** Posts of {@code trade}; null for the Fields, which has no cap. */
  public Integer posts(Trade trade) {
    return trade == Trade.TILLER
        ? null
        : trades.getStartingPosts() + trades.getPostsPerLevel() * postLevel(trade);
  }

  public int postLevel(Trade trade) {
    return postLevels.getOrDefault(trade, 0);
  }

  /** Materials the next Post upgrade of {@code trade} costs, whole. */
  public long postCost(Trade trade) {
    return grown(trades.getPostBaseCost(), trades.getPostCostGrowth(), postLevel(trade));
  }

  /** Why the Post upgrade cannot be bought with {@code materials} in the Vault, or empty. */
  public Optional<String> postUpgradeBlocked(Trade trade, double materials) {
    if (trade == Trade.TILLER) {
      return Optional.of("The Fields have no Posts to raise.");
    }
    var cost = postCost(trade);
    if (materials < cost) {
      return Optional.of(
          "Posts cost " + cost + " Materials. The Vault holds " + (long) materials + ".");
    }
    return Optional.empty();
  }

  /** Adds the Posts of one upgrade. The caller has taken the Materials. */
  public void raisePosts(Trade trade) {
    postLevels.merge(trade, 1, Integer::sum);
  }

  public int tomeLevel(Tome tome) {
    return tomes.getOrDefault(tome, 0);
  }

  /** Knowledge the next level of {@code tome} costs, whole. */
  public long tomeCost(Tome tome) {
    return grown(trades.getTomeBaseCost(), trades.getTomeCostGrowth(), tomeLevel(tome));
  }

  private static long grown(double base, double growth, int level) {
    return (long) Math.floor(base * Math.pow(growth, level) + 1e-9);
  }

  /**
   * The reason the Stockroom cannot hold the next level of {@code tome}, or null when it can. The
   * floor named is the first Highest Floor whose Stockroom holds the cost.
   */
  public String tomeGate(Tome tome, int highestFloor) {
    var cost = tomeCost(tome);
    var holds = stockroomCap(highestFloor);
    if (cost <= holds) {
      return null;
    }
    var floor = (int) Math.ceil((cost - trades.getStockroomBase()) / trades.getStockroomPerFloor());
    return "Tome "
        + (tomeLevel(tome) + 1)
        + " costs "
        + cost
        + ". Stockroom holds "
        + (long) holds
        + ". Needs Highest Floor "
        + floor
        + ".";
  }

  /** Why the next level of {@code tome} cannot be bought now, or empty. The gate comes first. */
  public Optional<String> tomeBlocked(Tome tome, int highestFloor) {
    var gate = tomeGate(tome, highestFloor);
    if (gate != null) {
      return Optional.of(gate);
    }
    var cost = tomeCost(tome);
    if (knowledge < cost) {
      return Optional.of(
          "Tome "
              + (tomeLevel(tome) + 1)
              + " costs "
              + cost
              + ". Knowledge "
              + (long) knowledge
              + ".");
    }
    return Optional.empty();
  }

  /** Spends Knowledge on the next level of {@code tome}. Returns why not, or empty when bought. */
  public Optional<String> buyTome(Tome tome, int highestFloor) {
    var blocked = tomeBlocked(tome, highestFloor);
    if (blocked.isPresent()) {
      return blocked;
    }
    knowledge -= tomeCost(tome);
    tomes.merge(tome, 1, Integer::sum);
    return Optional.empty();
  }

  /** Output of the Fields over the base: Tillers' Record adds a share per level. */
  private double fieldsMultiplier() {
    return 1 + trades.getTomeBonusPerLevel() * tomeLevel(Tome.TILLERS_RECORD);
  }

  /** Food the Tillers grow each tick. */
  public double getProductionRate() {
    return hands(Trade.TILLER) * villagerFoodProduction * fieldsMultiplier();
  }

  /** The Quarry and the Study yield more from ground the Village already knows. */
  public double yieldMultiplier(int highestFloor) {
    return 1 + trades.getYieldPerHighestFloor() * Math.max(0, highestFloor);
  }

  public double materialsPerTick(int highestFloor) {
    var seams = 1 + trades.getTomeBonusPerLevel() * tomeLevel(Tome.SEAM_CHARTS);
    return breakers * trades.getBreakerMaterialsPerTick() * yieldMultiplier(highestFloor) * seams;
  }

  public double knowledgePerTick(int highestFloor) {
    return scribes * trades.getScribeKnowledgePerTick() * yieldMultiplier(highestFloor);
  }

  /** Salt the Salt Pans make each tick; it does not grow with the Highest Floor. */
  public double saltPerTick() {
    return salters * saltRules.getPerSalterPerTick();
  }

  /** Takes up to {@code amount} whole Salt from the Stockroom; returns what was taken. */
  /** Puts Salt the Party did not use back in the Stockroom. */
  public void returnSalt(int amount) {
    salt += Math.max(0, amount);
  }

  public int takeSalt(int amount) {
    var taken = (int) Math.min(Math.floor(salt), Math.max(0, amount));
    salt -= taken;
    return taken;
  }

  /** Spores the Spore Cellar makes each tick; it does not grow with the Highest Floor. */
  public double sporesPerTick() {
    return sporewardens * sporeRules.getPerSporewardenPerTick();
  }

  /** Takes up to {@code amount} whole Spores from the Stockroom; returns what was taken. */
  public int takeSpores(int amount) {
    var taken = (int) Math.min(Math.floor(spores), Math.max(0, amount));
    spores -= taken;
    return taken;
  }

  /** Puts Spores into the Stockroom, as an Extraction banks them; the cap does not apply. */
  public void addSpores(double amount) {
    spores += Math.max(0d, amount);
  }

  public double stockroomCap(int highestFloor) {
    return trades.getStockroomBase() + trades.getStockroomPerFloor() * Math.max(0, highestFloor);
  }

  public double heapCap(int highestFloor) {
    return trades.getHeapPerFloor() * Math.max(0, highestFloor);
  }

  /**
   * Hands over what an Extraction carries down from the Heap: up to the configured Materials for
   * each Floor cleared. The rest stays. Returns what was taken.
   */
  public double deliverHeap(int floorsCleared) {
    var limit = trades.getHeapDeliveryPerFloorCleared() * Math.max(0, floorsCleared);
    var delivered = Math.min(quarryHeap, limit);
    quarryHeap -= delivered;
    return delivered;
  }

  /**
   * Moves one Villager between the Fields and {@code trade}: {@code delta > 0} takes a Tiller to
   * the Trade, {@code delta < 0} sends one back. Returns why not, or empty when it was done.
   */
  public Optional<String> assign(Trade trade, int delta) {
    return assign(trade, delta, _ -> null);
  }

  /** As {@link #assign(Trade, int)}, with {@code gate} naming why a Trade is not yet open. */
  public Optional<String> assign(Trade trade, int delta, Function<Trade, String> gate) {
    var blocked = blockedReason(trade, delta, gate);
    if (blocked != null) {
      return Optional.of(blocked);
    }
    if (delta > 0) {
      shift(Trade.TILLER, trade);
    } else {
      shift(trade, Trade.TILLER);
    }
    return Optional.empty();
  }

  /** The reason a move is not allowed, or null. Warnings about Food are not reasons. */
  private String blockedReason(Trade trade, int delta, Function<Trade, String> gate) {
    if (trade == Trade.TILLER) {
      return "The Fields take whoever is not assigned.";
    }
    if (delta > 0) {
      var closed = gate.apply(trade);
      if (closed != null) {
        return closed;
      }
      if (emergencyTilling) {
        return "Held at the Fields until the Pantry passes "
            + whole(trades.getEmergencyTillingAbove())
            + ".";
      }
      if (hands(Trade.TILLER) - 1 < trades.getTillerMinimum()) {
        return "Cannot assign. " + trades.getTillerMinimum() + " Tillers must stay.";
      }
      if (hands(trade) >= posts(trade)) {
        return trade.workplace() + ": " + hands(trade) + " of " + posts(trade) + " Posts.";
      }
      if (settlingHands(Trade.TILLER) >= hands(Trade.TILLER)) {
        return "Settling " + settlingSeconds(Trade.TILLER) + " s.";
      }
      return null;
    }
    if (hands(trade) == 0) {
      return "No " + trade.plural() + " to move.";
    }
    if (settlingHands(trade) >= hands(trade)) {
      return "Settling " + settlingSeconds(trade) + " s.";
    }
    return null;
  }

  private static String whole(double value) {
    return String.valueOf((long) value);
  }

  /** Moves one hand, who then settles in its new Trade. No checks. */
  private void shift(Trade from, Trade to) {
    adjust(from, -1);
    adjust(to, 1);
    settling.get(to).add(trades.getSettlingTicks());
  }

  private void adjust(Trade trade, int by) {
    switch (trade) {
      case BREAKER -> breakers += by;
      case SCRIBE -> scribes += by;
      case SALTER -> salters += by;
      case SPOREWARDEN -> sporewardens += by;
      case TILLER -> {
        // Tillers are the remainder.
      }
    }
  }

  /**
   * Runs Emergency Tilling for this tick. It starts when the Pantry is below its lower mark and
   * Upkeep for {@code unitCount} Reserves exceeds the Fields output, and ends once the Pantry is
   * above its upper mark. While it runs it moves one Villager to the Fields, from the Trade with
   * the most hands, skipping anyone Settling.
   */
  public EmergencyTillingResult emergencyTilling(int unitCount) {
    var upkeep = upkeepFor(unitCount);
    var started = false;
    if (!emergencyTilling) {
      if (food < trades.getEmergencyTillingBelow() && upkeep > getProductionRate()) {
        emergencyTilling = true;
        started = true;
      }
    } else if (food > trades.getEmergencyTillingAbove()) {
      emergencyTilling = false;
    }
    if (!emergencyTilling) {
      return new EmergencyTillingResult(false, false, null);
    }
    Trade moved = null;
    if (upkeep > getProductionRate()) {
      moved =
          List.of(Trade.BREAKER, Trade.SCRIBE, Trade.SALTER, Trade.SPOREWARDEN).stream()
              .filter(trade -> hands(trade) > settlingHands(trade))
              .max(Comparator.comparingInt(this::hands))
              .orElse(null);
      if (moved != null) {
        shift(moved, Trade.TILLER);
      }
    }
    return new EmergencyTillingResult(true, started, moved);
  }

  /** Food eaten each tick by {@code unitCount} idle (standby) units back home. */
  public double upkeepFor(int unitCount) {
    return unitCount * unitFoodConsumption;
  }

  /** Removes up to {@code amount} of food from the pantry for an expedition to carry. */
  public double takeFood(double amount) {
    var taken = Math.min(this.food, Math.max(0d, amount));
    setFood(this.food - taken);
    return taken;
  }

  /** Pours food (e.g. an expedition's leftovers) back into the pantry. */
  public void addFood(double amount) {
    setFood(this.food + Math.max(0d, amount));
  }

  public Double buyVillager(Double credits) {
    this.villagersCount++;
    credits -= this.villagersCount;
    return credits;
  }

  public boolean sellFood() {
    if (food > 1) {
      setFood(food - 1);
      return true;
    }
    return false;
  }

  /**
   * Whole seconds (lifecycle ticks) the pantry holds before Starvation begins, at current rates:
   * {@code (pantry - gap) / (gap + recoverySpend)} rounded down, where {@code gap} is the Upkeep
   * for {@code unitCount} idle units that the Fields output does not cover. {@code recoverySpend}
   * is what Recovery draws each tick; pass its ceiling for the worst case. Null means the Fields
   * cover Upkeep and the pantry never runs the village into Starvation. Zero means Starvation
   * begins on the next tick.
   */
  public Integer secondsUntilStarvation(int unitCount, double recoverySpend) {
    return secondsUntilStarvation(getProductionRate(), unitCount, recoverySpend);
  }

  private Integer secondsUntilStarvation(double production, int unitCount, double recoverySpend) {
    return secondsUntilStarvation(food, production, unitCount, recoverySpend);
  }

  private Integer secondsUntilStarvation(
      double pantry, double production, int unitCount, double recoverySpend) {
    var gap = upkeepFor(unitCount) - production;
    if (gap <= 0) {
      return pantry + -gap <= 0 ? 0 : null;
    }
    return (int) Math.max(0, Math.floor((pantry - gap) / (gap + Math.max(0d, recoverySpend))));
  }

  /**
   * How long the Pantry would hold after {@code taken} Food leaves it with a Party, with {@code
   * staying} Reserves left behind to feed. Null means the Fields cover Upkeep.
   */
  public Integer secondsUntilStarvationAfter(double taken, int staying, double recoverySpend) {
    return secondsUntilStarvation(
        Math.max(0, food - taken), getProductionRate(), staying, recoverySpend);
  }

  /**
   * The margin after one more ({@code delta > 0}) or one fewer hand in {@code trade}, as the
   * assignment row states it. The margin is shown even when the move is blocked.
   */
  public MovePreview preview(Trade trade, int delta, int unitCount, double recoverySpend) {
    return preview(trade, delta, unitCount, recoverySpend, _ -> null);
  }

  public MovePreview preview(
      Trade trade, int delta, int unitCount, double recoverySpend, Function<Trade, String> gate) {
    var tillers = hands(Trade.TILLER) - Integer.signum(delta);
    var fields = Math.max(0, tillers) * villagerFoodProduction * fieldsMultiplier();
    var upkeep = upkeepFor(unitCount);
    return new MovePreview(
        trade,
        delta,
        fields,
        upkeep,
        fields >= upkeep,
        secondsUntilStarvation(fields, unitCount, recoverySpend),
        blockedReason(trade, delta, gate));
  }

  private boolean checkStarvation() {
    return this.food <= 0;
  }

  /** Starvation takes a Villager at random, weighted by headcount; the Trade is returned. */
  public Optional<Trade> starve(RandomGenerator random) {
    if (villagersCount <= 0) {
      return Optional.empty();
    }
    var draw = random.nextInt(villagersCount);
    Trade lost = null;
    for (var trade : Trade.values()) {
      if (draw < hands(trade)) {
        lost = trade;
        break;
      }
      draw -= hands(trade);
    }
    adjust(lost, -1);
    setVillagersCount(villagersCount - 1);
    villagersLost++;
    clampTrades();
    return Optional.of(lost);
  }

  public void starve() {
    starve(ThreadLocalRandom.current());
  }

  /** Records a Reserve lost to Starvation, for The Count. */
  public void recordReserveLost() {
    reservesLost++;
  }
}
