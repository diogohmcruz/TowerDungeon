package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.config.ThreadLocalRandomGenerator;
import io.github.diogohmcruz.towerdungeon.domain.items.FloorGearStore;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.FoodDistributionStrategy;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.FoodHealingConfig;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.RoundRobinFoodDistributionStrategy;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneOffer;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutOffer;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.CampaignSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.EnemySnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.ExpeditionSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.PartSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.PhobiaSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.TowerSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.TradesSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.UnitSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.WaveSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeOffer;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class GameState {
  private Double credit;
  private Map<UnitStats, List<Unit>> units = new HashMap<>();
  private Map<UnitStats, List<Unit>> unitsOnTower = new HashMap<>();
  private Tower tower;
  private boolean expeditionActive = false;
  private GameOutcome gameOutcome = GameOutcome.PLAYING;
  private Village village;
  private Map<ResourceType, Double> resources = ResourceType.emptyWallet();
  private Map<ResourceType, Double> carriedLoot = ResourceType.emptyWallet();
  private Double carriedCredits = 0d;
  private Map<ItemTier, Integer> surplus = new EnumMap<>(ItemTier.class);

  /** Ids of the Artifacts taken so far in this generation; they are not offered again. */
  @JsonIgnore private Set<String> artifactsTaken = new TreeSet<>();

  private Double supplies = 0d;
  private Double maxSupplies;
  private Double lastFoodReturned = 0d;
  private List<Reinforcement> reinforcements = new ArrayList<>();

  @JsonIgnore
  private Set<UnitStats> unlockedUnits =
      EnumSet.of(UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.PORTER);

  @JsonIgnore
  private Map<UpgradeType, Integer> purchasedUpgrades = new EnumMap<>(UpgradeType.class);

  private int highestFloor = 0;
  private int expeditionsCompleted = 0;
  private int enemiesDefeated = 0;

  /** The Tally: totals that spending never reduces. */
  private CampaignTally tally;

  /** The Village Tier reached; a ratchet, so a rebalance of the gates never takes one away. */
  private VillageTier villageTier = VillageTier.HUDDLED;

  /** Food Recovery spent on the home party during the last lifecycle tick. */
  private double recoverySpend = 0d;

  private int runStartFloor = 0;
  private int runEnemiesDefeated = 0;
  private int runUnitsLost = 0;
  private RunSummary lastRunSummary;

  /** The Units that fell on the current Expedition, in the order they fell. */
  private List<Fallen> runFallen = new ArrayList<>();

  /** The Wall of Names: every Unit that fell on the Tower or starved at home, oldest first. */
  private List<Fallen> wallOfNames = new ArrayList<>();

  @JsonIgnore private Set<MilestoneType> achievedMilestones = EnumSet.noneOf(MilestoneType.class);

  /** Shortcuts ever opened; a ratchet, so a rebalance of the thresholds never closes one. */
  @JsonIgnore private Set<ShortcutType> openedShortcuts = EnumSet.noneOf(ShortcutType.class);

  /** The Ledger: the Village's plain record of what happened, newest last. Not persisted. */
  private List<LedgerLine> ledger = new ArrayList<>();

  /** One line of the Ledger; {@code loss} lines are written to be read as losses. */
  public record LedgerLine(String text, boolean loss) {}

  private static final int LEDGER_LIMIT = 50;

  @JsonIgnore @ToString.Exclude @EqualsAndHashCode.Exclude
  private FoodDistributionStrategy foodDistributionStrategy =
      new RoundRobinFoodDistributionStrategy();

  @JsonIgnore @ToString.Exclude @EqualsAndHashCode.Exclude private GameProperties config;

  @FunctionalInterface
  public interface LockedCall<T> {
    T call() throws Exception;
  }

  /**
   * Runs {@code action} while holding this state's intrinsic monitor and returns its result. All
   * mutations (scheduled game loops and user actions) and the outbound serialization funnel through
   * this single method, so the reactive send thread never observes a half-mutated state.
   */
  public synchronized <T> T callLocked(LockedCall<T> action) throws Exception {
    return action.call();
  }

  public GameState(GameProperties config) {
    this.config = config;
    this.credit = config.getStart().getCredit();
    this.maxSupplies = config.getSupply().getBaseMax();
    this.village = new Village(config.getVillage(), config.getTrades());
    this.tally = new CampaignTally(village.getVillagersCount());
  }

  /**
   * Starts an expedition. Food is a single shared resource: the party draws food out of the village
   * pantry (up to its carrying capacity, which the PORTER unit increases) and carries it as
   * supplies. On extraction the leftovers are poured back into the pantry and the survivors are
   * then healed using the village's food.
   */
  public void startRun() {
    this.expeditionActive = true;
    this.maxSupplies = computeSupplyCapacity();
    this.supplies = village.takeFood(this.maxSupplies);
    this.carriedLoot = ResourceType.emptyWallet();
    this.carriedCredits = 0d;
    this.lastFoodReturned = 0d;
    this.runEnemiesDefeated = 0;
    this.runUnitsLost = 0;
    this.runFallen = new ArrayList<>();
    partyMembers().forEach(Unit::forgetFearFloor);
  }

  /**
   * Records the floor the current expedition set out from (base or a shortcut), for its summary.
   */
  public void setRunStartFloor(int startFloor) {
    this.runStartFloor = Math.max(0, startFloor);
  }

  /**
   * Carrying capacity of the party currently on the tower: a base amount plus the supply bonus of
   * every PORTER in the party. Because it is derived from the live party, capacity shrinks as soon
   * as a porter dies.
   */
  private double computeSupplyCapacity() {
    var supplyBonus =
        unitsOnTower.entrySet().stream()
            .mapToDouble(entry -> entry.getKey().getSupplyBonus() * entry.getValue().size())
            .sum();
    return config.getSupply().getBaseMax() + getSupplyCapacityBonus() + supplyBonus;
  }

  /**
   * Recomputes the carrying capacity from the surviving party (e.g. after a porter dies) and spills
   * any food that can no longer be carried — the dead porter's load is lost with them.
   */
  public void recalculateSupplyCapacity() {
    if (!expeditionActive) {
      return;
    }
    this.maxSupplies = computeSupplyCapacity();
    if (this.supplies > this.maxSupplies) {
      this.supplies = this.maxSupplies;
    }
  }

  /** Number of units in the party currently on the tower. */
  public int getTowerPartySize() {
    return (int) unitsOnTower.values().stream().flatMap(List::stream).count();
  }

  /** Pours any food the party still carries back into the village pantry after a successful run. */
  public void returnLeftoverSuppliesToVillage() {
    this.lastFoodReturned = this.supplies;
    village.addFood(this.supplies);
    this.supplies = 0d;
  }

  /**
   * Heals the wounded units resting at home, spending the village's food. Runs every lifecycle tick
   * and delegates the actual "who gets fed" decision to the configured {@link
   * FoodDistributionStrategy}, so different policies (round-robin, proportional,
   * most-wounded-first, …) can be swapped in without changing the game loop.
   */
  public void healHomePartyWithFood() {
    var homeUnits = units.values().stream().flatMap(List::stream).toList();
    this.recoverySpend =
        foodDistributionStrategy.distribute(
            homeUnits,
            village,
            new FoodHealingConfig(
                config.getHealing().getCostPerHp(), config.getHealing().getHpPerTick()));
  }

  /**
   * Seconds until Starvation begins at current rates, counting Recovery at its ceiling while any
   * home unit is wounded; null while the Fields cover Upkeep.
   */
  public Integer getPantryHoldSeconds() {
    return village.secondsUntilStarvation(getHomeUnitCount(), recoveryCeiling());
  }

  private double recoveryCeiling() {
    return getHomePartyWounds() > 0
        ? config.getHealing().getHpPerTick() * config.getHealing().getCostPerHp()
        : 0d;
  }

  /** Total missing health across the units resting at home (the ones the village feeds/heals). */
  public double getHomePartyWounds() {
    double total = 0d;
    for (var entry : units.entrySet()) {
      for (Unit unit : entry.getValue()) {
        var missing = unit.getMaxHealth() - unit.getCurrentHealth();
        if (missing > 0) {
          total += missing;
        }
      }
    }
    return total;
  }

  /** Health restored per lifecycle tick when the village has food to spare. */
  public double getFoodHealRate() {
    return config.getHealing().getHpPerTick();
  }

  /** Number of idle units back home (excludes those on an expedition). */
  public int getHomeUnitCount() {
    return (int) units.values().stream().flatMap(List::stream).count();
  }

  /** Whether the player has any soldier left at all, whether idle at home or up on the tower. */
  public boolean hasAnyUnits() {
    return getHomeUnitCount() > 0 || getTowerPartySize() > 0;
  }

  /**
   * Whether the player still has any economic means to field another soldier: coin to recruit or
   * hire a villager, sellable loot, or spare food to sell. When this is false and every unit and
   * villager is gone, the campaign is a genuine dead end.
   */
  private boolean canRecover() {
    return credit > 0
        || resources.getOrDefault(ResourceType.MATERIALS, 0d) > 0
        || resources.getOrDefault(ResourceType.RELICS, 0d) > 0
        || village.getFood() > 1;
  }

  /**
   * Ends the campaign in defeat once the tower has consumed everything: no units remain anywhere,
   * the village is depopulated, and there is no coin, loot, or food left to raise another party.
   * Returns true if the game has (now or already) ended in defeat.
   */
  public boolean checkGameOver() {
    if (gameOutcome != GameOutcome.PLAYING) {
      return gameOutcome == GameOutcome.DEFEAT;
    }
    if (!hasAnyUnits() && village.getVillagersCount() <= 0 && !canRecover()) {
      gameOutcome = GameOutcome.DEFEAT;
      log.error("GAME OVER — the village is spent and the tower has taken everyone.");
      return true;
    }
    return false;
  }

  /** Records that the party conquered the tower's summit — the campaign is won. */
  public void winCampaign() {
    this.gameOutcome = GameOutcome.VICTORY;
  }

  /** Food eaten per tick by idle units standing by at home. */
  public double getFoodUpkeep() {
    return village.upkeepFor(getHomeUnitCount());
  }

  /** Net food change per tick: villager production minus standby unit upkeep. */
  public double getNetFoodPerSecond() {
    return village.getProductionRate() - getFoodUpkeep();
  }

  public void gatherLoot(ResourceType type, Double amount) {
    carriedLoot.merge(type, amount, Double::sum);
  }

  /**
   * Adds credits found on a cleared floor to the party's carried purse. Like other loot these
   * credits are only kept if the party extracts; a wipe forfeits them.
   */
  public void gatherCredits(Double amount) {
    carriedCredits += amount;
  }

  /**
   * Adds supplies (food) found on a cleared floor to what the party is carrying, capped at its
   * current carrying capacity. Unlike banked loot these are immediately usable to keep the party
   * alive deeper into the current expedition.
   */
  public void gatherSupplies(Double amount) {
    this.supplies = Math.min(this.maxSupplies, this.supplies + Math.max(0d, amount));
  }

  public void drainSupplies(Double amount) {
    this.supplies = Math.max(0d, this.supplies - amount);
  }

  public boolean hasSupplies() {
    return supplies > 0;
  }

  /** The ids of the Artifacts already taken this generation. */
  public Set<String> getArtifactsTaken() {
    return Collections.unmodifiableSet(artifactsTaken);
  }

  /** Notes that an Artifact was taken: its home floor will not offer it again this generation. */
  public void recordArtifactTaken(String artifactId) {
    artifactsTaken.add(artifactId);
  }

  /** Counts one item that did not fit on anyone: kept per tier, banked like loot on extraction. */
  public void addSurplus(ItemTier tier) {
    surplus.merge(tier, 1, Integer::sum);
  }

  /** The Relics the counted surplus will bank at extraction: each item by its tier. */
  private double surplusRelics() {
    var relics = config.getItems().getSurplusRelics();
    return surplus.entrySet().stream()
        .mapToDouble(entry -> (double) entry.getValue() * relics.of(entry.getKey()))
        .sum();
  }

  /** Banks the carried loot, and every surplus item as Relics by tier. */
  public void bankLoot() {
    var surplusRelics = surplusRelics();
    if (surplusRelics > 0) {
      resources.merge(ResourceType.RELICS, surplusRelics, Double::sum);
    }
    this.surplus = new EnumMap<>(ItemTier.class);
    carriedLoot.forEach((type, amount) -> resources.merge(type, amount, Double::sum));
    tally.addMaterialsBanked(carriedLoot.getOrDefault(ResourceType.MATERIALS, 0d));
    tally.addRelicsBanked(carriedLoot.getOrDefault(ResourceType.RELICS, 0d));
    tally.addCreditsBanked(carriedCredits);
    this.carriedLoot = ResourceType.emptyWallet();
    this.credit += this.carriedCredits;
    this.carriedCredits = 0d;
    refreshVillageTier();
  }

  public void forfeitLoot() {
    this.surplus = new EnumMap<>(ItemTier.class);
    this.carriedLoot = ResourceType.emptyWallet();
    this.carriedCredits = 0d;
  }

  /**
   * Sells every banked unit of {@code type} at {@code unitPrice} credits each. Returns the take.
   */
  private double sellAll(ResourceType type, double unitPrice) {
    var stock = resources.getOrDefault(type, 0d);
    var profit = stock * unitPrice;
    resources.put(type, 0d);
    this.credit += profit;
    return profit;
  }

  public double sellAllMaterials() {
    return sellAll(ResourceType.MATERIALS, config.getReward().getMaterialsSellPrice());
  }

  public double sellAllRelics() {
    return sellAll(ResourceType.RELICS, config.getReward().getRelicsSellPrice());
  }

  public void returnPartyHome() {
    unitsOnTower.forEach(
        (stats, survivors) ->
            units.computeIfAbsent(stats, _ -> new ArrayList<>()).addAll(survivors));
    unitsOnTower.clear();
  }

  /**
   * Musters a follow-up wave from the units resting at home and sends it up after the active party.
   * An empty selection sends everyone currently home; otherwise the requested counts are pulled
   * (and clamped to what is actually available). The wave draws its own rations from the village
   * pantry up to its carrying capacity, then sets out from the base to climb after the party.
   */
  public void sendReinforcement(Map<UnitStats, Integer> selection) {
    Map<UnitStats, List<Unit>> waveUnits = new HashMap<>();
    if (selection == null || selection.isEmpty()) {
      units.forEach(
          (stats, homeList) -> {
            if (!homeList.isEmpty()) {
              waveUnits.put(stats, new ArrayList<>(homeList));
              homeList.clear();
            }
          });
    } else {
      selection.forEach(
          (stats, requested) -> {
            var homeList = units.getOrDefault(stats, new ArrayList<>());
            var take = Math.min(requested == null ? 0 : requested, homeList.size());
            if (take > 0) {
              var moved = new ArrayList<>(homeList.subList(0, take));
              homeList.removeAll(moved);
              waveUnits.put(stats, moved);
            }
          });
    }
    if (waveUnits.values().stream().allMatch(List::isEmpty)) {
      return;
    }
    var porterBonus =
        waveUnits.entrySet().stream()
            .mapToDouble(entry -> entry.getKey().getSupplyBonus() * entry.getValue().size())
            .sum();
    var capacity = config.getSupply().getBaseMax() + getSupplyCapacityBonus() + porterBonus;
    waveUnits.values().stream().flatMap(List::stream).forEach(Unit::forgetFearFloor);
    var food = village.takeFood(capacity);
    reinforcements.add(new Reinforcement(waveUnits, food, capacity));
    log.info(
        "Mustered a reinforcement wave of {} carrying {} food.",
        waveUnits.values().stream().mapToInt(List::size).sum(),
        food);
  }

  /**
   * Advances every in-transit reinforcement wave one tick: each drains rations for its size
   * (starving climbers if the reserve is dry), climbs toward the party, and — once it reaches the
   * party's current floor — merges its survivors and any leftover food into the delving party.
   */
  public void advanceReinforcements() {
    if (reinforcements.isEmpty()) {
      return;
    }
    var partyFloor = tower == null ? 0 : tower.getCurrentFloor();
    var drainPerUnit = config.getLoop().getSupplyDrainPerUnit();
    var starvationDamage = config.getLoop().getStarvationDamage();
    var climb = config.getReinforcements().getClimbFloorsPerTick();
    var iterator = reinforcements.iterator();
    while (iterator.hasNext()) {
      var wave = iterator.next();
      wave.drainSupplies(wave.size() * drainPerUnit);
      if (!wave.hasSupplies()) {
        starveWave(wave, starvationDamage);
      }
      if (wave.isEmpty()) {
        log.warn("A reinforcement wave starved to death on the stairs before reaching the party.");
        iterator.remove();
        continue;
      }
      wave.climb(climb);
      if (wave.getCurrentFloor() >= partyFloor) {
        mergeReinforcement(wave, partyFloor);
        iterator.remove();
      }
    }
  }

  private void starveWave(Reinforcement wave, double damage) {
    var climbers = wave.getUnits().values().stream().flatMap(List::stream).toList();
    var floor = (int) wave.getCurrentFloor();
    climbers.forEach(climber -> dropGear(floor, climber.receiveAttack(damage, null)));
    climbers.stream()
        .filter(BaseUnit::isDead)
        .forEach(
            dead -> {
              var list = wave.getUnits().get(dead.getStats());
              if (list != null && list.remove(dead)) {
                runUnitsLost++;
                dropGear(floor, dead.dropAll());
              }
            });
  }

  private void mergeReinforcement(Reinforcement wave, int partyFloor) {
    wave.getUnits()
        .forEach(
            (stats, survivors) ->
                unitsOnTower.computeIfAbsent(stats, _ -> new ArrayList<>()).addAll(survivors));
    this.supplies += wave.getSupplies();
    recalculateSupplyCapacity();
    log.info("A reinforcement wave of {} reached the party at floor {}.", wave.size(), partyFloor);
  }

  /**
   * Recalls any waves still climbing when the run ends — they were not at the deadly front, so
   * their survivors march back home and their leftover rations are poured back into the pantry.
   */
  public void returnReinforcementsHome() {
    reinforcements.forEach(
        wave -> {
          wave.getUnits()
              .forEach(
                  (stats, survivors) ->
                      units.computeIfAbsent(stats, _ -> new ArrayList<>()).addAll(survivors));
          village.addFood(wave.getSupplies());
        });
    reinforcements.clear();
  }

  public void triggerLifecycle() {
    triggerLifecycle(ThreadLocalRandom.current());
  }

  /** One tick of the village: Emergency Tilling, production and Upkeep, Starvation, Recovery. */
  public void triggerLifecycle(RandomGenerator random) {
    if (checkGameOver()) {
      return;
    }
    var allUnits = units.values().stream().flatMap(List::stream).toList();
    var tilling = village.emergencyTilling(allUnits.size());
    if (tilling.started()) {
      writeLedger(
          "Emergency Tilling. Pantry below "
              + (long) config.getTrades().getEmergencyTillingBelow()
              + ". "
              + (tilling.moved() == null
                  ? "No one could be moved."
                  : tilling.moved().plural() + " moved to the Fields."),
          false);
    }
    tally.notePeakVillagers(village.getVillagersCount());
    tally.addFoodGrown(village.getProductionRate());
    var isStarving = village.triggerLifecycle(allUnits.size(), highestFloor);
    if (isStarving) {
      var lostTrade = village.starve(random);
      Unit lostReserve = null;
      if (!allUnits.isEmpty()) {
        lostReserve = allUnits.get(random.nextInt(allUnits.size()));
        units.get(lostReserve.getStats()).remove(lostReserve);
        village.recordReserveLost();
        log.warn("Village is starving! Unit {} starved!", lostReserve);
        wallOfNames.add(fallenAt(lostReserve, 0));
      }
      writeStarvation(lostTrade, lostReserve);
    }
    healHomePartyWithFood();
    recoverHomeSanity();
    rechargeHomeCasters();
    refreshVillageTier();
  }

  private void writeStarvation(Optional<Trade> lostTrade, Unit lostReserve) {
    if (lostTrade.isEmpty() && lostReserve == null) {
      return;
    }
    var line = new StringBuilder("Starvation.");
    lostTrade.ifPresent(trade -> line.append(" 1 ").append(trade.noun()).append(" lost."));
    if (lostReserve != null) {
      line.append(" ")
          .append(lostReserve.getName())
          .append(", ")
          .append(typeName(lostReserve.getStats()))
          .append(", lost.");
    }
    writeLedger(line.toString(), true);
  }

  private static String typeName(UnitStats stats) {
    var name = stats.name().replace('_', ' ').toLowerCase();
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private void writeLedger(String text, boolean loss) {
    ledger.add(new LedgerLine(text, loss));
    while (ledger.size() > LEDGER_LIMIT) {
      ledger.removeFirst();
    }
  }

  /**
   * Moves one Villager between the Fields and {@code trade} ({@code delta} of +1 or -1). Returns
   * why it was refused, or empty when it was done.
   */
  public Optional<String> assignTrade(Trade trade, int delta) {
    if (delta == 0 || gameOutcome != GameOutcome.PLAYING) {
      return Optional.of("Nothing moved.");
    }
    return village.assign(trade, delta);
  }

  /**
   * Carries the Quarry Heap down with a Party that came home: up to the configured Materials for
   * each Floor cleared in the Expedition just summed up, into the Vault. A Wipe never calls this.
   */
  public void deliverQuarryHeap() {
    var cleared = lastRunSummary == null ? 0 : lastRunSummary.floorsCleared();
    var delivered = village.deliverHeap(cleared);
    if (delivered > 0) {
      resources.merge(ResourceType.MATERIALS, delivered, Double::sum);
      tally.addMaterialsBanked(delivered);
      refreshVillageTier();
    }
  }

  /** Quarry output per tick, before it reaches the Heap. */
  public double getQuarryRate() {
    return village.materialsPerTick(highestFloor);
  }

  /** Scribe output per tick. */
  public double getStudyRate() {
    return village.knowledgePerTick(highestFloor);
  }

  /** Yield of the Quarry and the Study over the base, from the Floors the Village knows. */
  public double getYieldBonus() {
    return village.yieldMultiplier(highestFloor) - 1;
  }

  public double getStockroomCap() {
    return village.stockroomCap(highestFloor);
  }

  public double getQuarryHeapCap() {
    return village.heapCap(highestFloor);
  }

  public double getHeapDeliveryPerFloor() {
    return config.getTrades().getHeapDeliveryPerFloorCleared();
  }

  /** What each Trade holds, its Posts and any Settling, for the assignment rows. */
  public List<TradeView> getTradeViews() {
    return Arrays.stream(Trade.values())
        .map(
            trade ->
                new TradeView(
                    trade.name(),
                    trade.plural(),
                    trade.workplace(),
                    village.hands(trade),
                    village.posts(trade),
                    village.settlingHands(trade),
                    village.settlingSeconds(trade)))
        .toList();
  }

  /** The margin after one more or one fewer hand in each non-Fields Trade. */
  public List<TradePreview> getTradePreviews() {
    var ceiling = recoveryCeiling();
    var previews = new ArrayList<TradePreview>();
    for (var trade : Trade.values()) {
      if (trade == Trade.TILLER) {
        continue;
      }
      for (var delta : new int[] {1, -1}) {
        var p = village.preview(trade, delta, getHomeUnitCount(), ceiling);
        previews.add(
            new TradePreview(
                trade.name(),
                delta,
                p.fields(),
                p.upkeep(),
                p.fieldsCoverUpkeep(),
                p.pantryHoldSeconds(),
                p.blocked()));
      }
    }
    return previews;
  }

  /** Buys the next level of {@code tome} with Knowledge. Returns why not, or empty when bought. */
  public Optional<String> buyTome(Tome tome) {
    if (gameOutcome != GameOutcome.PLAYING) {
      return Optional.of("Nothing bought.");
    }
    return village.buyTome(tome, highestFloor);
  }

  /** Raises the Posts of {@code trade} for Materials from the Vault. Returns why not, or empty. */
  public Optional<String> buyPosts(Trade trade) {
    if (gameOutcome != GameOutcome.PLAYING) {
      return Optional.of("Nothing bought.");
    }
    var blocked = village.postUpgradeBlocked(trade, vaultMaterials());
    if (blocked.isPresent()) {
      return blocked;
    }
    resources.merge(ResourceType.MATERIALS, -(double) village.postCost(trade), Double::sum);
    village.raisePosts(trade);
    return Optional.empty();
  }

  private double vaultMaterials() {
    return resources.getOrDefault(ResourceType.MATERIALS, 0d);
  }

  /** The Tomes as the Village panel lists them; {@code blocked} says why one cannot be bought. */
  public List<TomeView> getTomeViews() {
    return Arrays.stream(Tome.values())
        .map(
            tome ->
                new TomeView(
                    tome.name(),
                    tome.title(),
                    tome.effect(),
                    (int) Math.round(config.getTrades().getTomeBonusPerLevel() * 100),
                    village.tomeLevel(tome),
                    village.tomeCost(tome),
                    village.tomeGate(tome, highestFloor) != null,
                    village.tomeBlocked(tome, highestFloor).orElse(null)))
        .toList();
  }

  /** The Post upgrades of the Quarry and the Scribe's Study. */
  public List<PostUpgradeView> getPostUpgrades() {
    return Arrays.stream(Trade.values())
        .filter(trade -> trade != Trade.TILLER)
        .map(
            trade ->
                new PostUpgradeView(
                    trade.name(),
                    trade.workplace(),
                    village.postLevel(trade),
                    village.posts(trade),
                    village.posts(trade) + config.getTrades().getPostsPerLevel(),
                    village.postCost(trade),
                    village.postUpgradeBlocked(trade, vaultMaterials()).orElse(null)))
        .toList();
  }

  /** The Count: what the Campaign has lost to Starvation. */
  public TheCount getTheCount() {
    return new TheCount(village.getVillagersLost(), village.getReservesLost());
  }

  /** A Trade as the assignment row shows it; {@code posts} is null for the Fields. */
  public record TradeView(
      String trade,
      String plural,
      String workplace,
      int hands,
      Integer posts,
      int settling,
      int settlingSeconds) {}

  /** The margin after a move of {@code delta} hands; {@code blocked} is why it cannot be made. */
  public record TradePreview(
      String trade,
      int delta,
      double fields,
      double upkeep,
      boolean fieldsCoverUpkeep,
      Integer pantryHoldSeconds,
      String blocked) {}

  public record TheCount(int villagers, int reserves) {}

  /** A Tome: {@code gated} when the Stockroom cannot hold its cost; {@code blocked} says why. */
  public record TomeView(
      String tome,
      String title,
      String effect,
      int bonusPercent,
      int level,
      long cost,
      boolean gated,
      String blocked) {}

  /** The next Post upgrade of a workplace and what it costs in Materials. */
  public record PostUpgradeView(
      String trade,
      String workplace,
      int level,
      int posts,
      int nextPosts,
      long cost,
      String blocked) {}

  /**
   * Refills the mana of casters standing by at home, a little each tick. This is the fast, safe
   * refill — the reason spent mages are rotated back to the village.
   */
  private void rechargeHomeCasters() {
    var rate = config.getMagic().getRechargePerTick();
    units.values().stream().flatMap(List::stream).forEach(unit -> unit.rechargeMana(rate));
  }

  /**
   * Slowly trickles mana back into casters currently delving on the tower — an innate regeneration,
   * far weaker than the village refill, so a hard-pressed party still burns mana faster than it
   * recovers but is never permanently dry.
   */
  public void rechargeTowerCasters() {
    var rate = config.getMagic().getTowerRechargePerTick();
    unitsOnTower.values().stream().flatMap(List::stream).forEach(unit -> unit.rechargeMana(rate));
  }

  /**
   * Raises a fresh unit at the village. Casters march out with a full mana reserve (drawn from
   * config); other units carry none. Health and damage strengths are rolled with {@code random}.
   */
  public Unit recruit(UnitStats unitStats, RandomGenerator random) {
    var unit = new Unit(unitStats);
    unit.setBodyWeights(config.getBody().getWeights());
    unit.rollStrengths(random, config.getRecruit().getStrengthSpread());
    unit.setSanityRules(config.getSanity());
    unit.setItemEffects(config.getItems().getEffects());
    unit.rollPhobias(random);
    unit.rollAffinity(random, config.getItems().getAffinity().getChance());
    if (unit.isCaster()) {
      unit.chargeToFull(config.getMagic().getMaxMana());
    }
    return unit;
  }

  /**
   * Raises a Founder at the village: a named unit whose {@code statMultiplier} scales its health
   * and damage factors. Founders flagged {@code fixedStrengths} skip the recruit roll; the rest are
   * rolled like any recruit.
   */
  public Unit recruitFounder(GameProperties.Founder founder, RandomGenerator random) {
    var unit = new Unit(founder.getUnitType(), founder.getName());
    unit.setFixedStrengths(founder.isFixedStrengths());
    if (founder.getStatMultiplier() != 1.0) {
      unit.setHealthFactor(founder.getStatMultiplier());
      unit.setDamageFactor(founder.getStatMultiplier());
    }
    unit.rollStrengths(random, config.getRecruit().getStrengthSpread());
    unit.setSanityRules(config.getSanity());
    unit.setItemEffects(config.getItems().getEffects());
    if (unit.isCaster()) {
      unit.chargeToFull(config.getMagic().getMaxMana());
    }
    return unit;
  }

  public void addCredit(Double amount) {
    credit += amount;
  }

  /**
   * Credits the player can spend right now: the treasury plus whatever the party is carrying on the
   * tower. Carried credits are only banked on extract, but they are still "in the party's hands"
   * and can fund recruiting village reserves mid-expedition (spent before they could be lost to a
   * wipe).
   */
  public double getSpendableCredit() {
    return credit + carriedCredits;
  }

  /** Spends {@code amount} of credit, drawing from carried loot first, then the treasury. */
  public void spendCredit(double amount) {
    var fromCarried = Math.min(carriedCredits, amount);
    carriedCredits -= fromCarried;
    credit -= (amount - fromCarried);
  }

  public void addUnits(UnitStats unitStats, List<Unit> newUnits) {
    units.computeIfAbsent(unitStats, _ -> new ArrayList<>()).addAll(newUnits);
  }

  /** Whether the given unit type has been unlocked and can be recruited. */
  public boolean isUnitUnlocked(UnitStats unitStats) {
    return unlockedUnits.contains(unitStats);
  }

  /** Names of the unit types the player has unlocked so far, for the frontend hire list. */
  public List<String> getUnlockedUnitTypes() {
    return unlockedUnits.stream().map(UnitStats::name).sorted().toList();
  }

  private int upgradeLevel(UpgradeType type) {
    return purchasedUpgrades.getOrDefault(type, 0);
  }

  /** Extra carrying capacity granted by the SUPPLY_LINES upgrade and any capacity milestones. */
  public double getSupplyCapacityBonus() {
    var upgradeBonus =
        upgradeLevel(UpgradeType.SUPPLY_LINES) * UpgradeType.SUPPLY_LINES.getCapacityPerLevel();
    var milestoneBonus =
        achievedMilestones.stream().mapToDouble(MilestoneType::getCapacityBonus).sum();
    return upgradeBonus + milestoneBonus;
  }

  /**
   * Multiplier applied to party damage, from the SHARPENED_ARMS upgrade and any damage milestones
   * (1.0 = no bonus).
   */
  public double getDamageMultiplier() {
    var upgradeBonus =
        upgradeLevel(UpgradeType.SHARPENED_ARMS)
            * UpgradeType.SHARPENED_ARMS.getDamageBonusPerLevel();
    var milestoneBonus =
        achievedMilestones.stream().mapToDouble(MilestoneType::getDamageBonus).sum();
    return 1.0 + upgradeBonus + milestoneBonus;
  }

  private boolean canAfford(Map<ResourceType, Double> cost) {
    return cost.entrySet().stream()
        .allMatch(entry -> resources.getOrDefault(entry.getKey(), 0d) >= entry.getValue());
  }

  private void spend(Map<ResourceType, Double> cost) {
    cost.forEach((type, amount) -> resources.merge(type, -amount, Double::sum));
  }

  /**
   * Attempts to buy the next level of an upgrade using banked resources. Returns {@code true} on
   * success. Unit-unlock upgrades add the unit to the roster; repeatable upgrades bump their level
   * (and their bonuses) and grow more expensive.
   */
  public boolean applyUpgrade(UpgradeType type) {
    var level = upgradeLevel(type);
    if (!type.isAvailableAt(level)) {
      return false;
    }
    var cost = type.costAtLevel(level);
    if (!canAfford(cost)) {
      return false;
    }
    spend(cost);
    purchasedUpgrades.put(type, level + 1);
    if (type.getUnlockUnit() != null) {
      unlockedUnits.add(type.getUnlockUnit());
    }
    if (expeditionActive) {
      recalculateSupplyCapacity();
    } else {
      this.maxSupplies = computeSupplyCapacity();
    }
    return true;
  }

  /** Live catalog of upgrades with current level and next-purchase cost, streamed to the client. */
  public List<UpgradeOffer> getAvailableUpgrades() {
    var offers = new ArrayList<UpgradeOffer>();
    for (UpgradeType type : UpgradeType.values()) {
      var level = upgradeLevel(type);
      var maxed = !type.isAvailableAt(level);
      offers.add(
          new UpgradeOffer(
              type.name(),
              type.getDisplayName(),
              type.getDescription(),
              level,
              maxed,
              type.isRepeatable(),
              type.getUnlockUnit() == null ? null : type.getUnlockUnit().name(),
              maxed ? Map.of() : type.costAtLevel(level)));
    }
    return offers;
  }

  /** Records the highest floor the party has reached and grants any newly earned milestones. */
  public void recordFloorReached(int floor) {
    if (floor > highestFloor) {
      highestFloor = floor;
    }
    refreshVillageTier();
    checkMilestones();
  }

  /** Marks an expedition as finished (extract or wipe) and grants any newly earned milestones. */
  public void completeExpedition() {
    expeditionsCompleted++;
    refreshVillageTier();
    refreshShortcuts();
    checkMilestones();
  }

  /**
   * Snapshots how the current run went into {@link #lastRunSummary}. Must be called while the run
   * data is still intact — before survivors are sent home and before loot is banked (extract) or
   * forfeited (wipe) — so the carried haul and party losses are reported accurately.
   */
  public void buildRunSummary(String outcome) {
    var highest = tower == null ? runStartFloor : tower.getCurrentFloor();
    var floorsCleared = Math.max(0, highest - runStartFloor);
    var survivors = RunSummary.WIPED.equals(outcome) ? 0 : getTowerPartySize();
    this.lastRunSummary =
        new RunSummary(
            outcome,
            runStartFloor,
            highest,
            floorsCleared,
            runEnemiesDefeated,
            carriedCredits,
            carriedLoot.getOrDefault(ResourceType.MATERIALS, 0d),
            carriedLoot.getOrDefault(ResourceType.RELICS, 0d)
                + (RunSummary.WIPED.equals(outcome) ? 0d : surplusRelics()),
            runUnitsLost,
            survivors,
            List.copyOf(runFallen));
    if (!RunSummary.WIPED.equals(outcome)) {
      tally.addFloorsCleared(floorsCleared);
    }
  }

  private static Fallen fallenAt(Unit unit, int floor) {
    return new Fallen(unit.getName(), unit.getStats().name(), floor);
  }

  /**
   * A Wipe ends on the floor where the last Unit fell, and that floor gains Risen: Dead Soldiers
   * carrying the names of the Units that fell there, one third of them rounded up and never fewer
   * than one, capped by {@code risen-cap-max} and by the share {@code risen-cap-fraction} of the
   * floor's eased base budget. Nothing is raised on a guardian floor, above the Highest Floor, or
   * on a floor already holding its cap, so repeated Wipes never compound. Call it before the
   * Expedition is torn down.
   */
  public void raiseRisenOnWipe() {
    if (tower == null) {
      return;
    }
    var floor = tower.getCurrentFloor();
    var here = tower.getFloors().get(floor);
    if (floor < 1 || floor > highestFloor || here == null || here.isBoss()) {
      return;
    }
    var names = runFallen.stream().filter(f -> f.floor() == floor).map(Fallen::name).toList();
    if (names.isEmpty()) {
      return;
    }
    var rules = config.getWipe();
    var cap =
        Math.max(
            1,
            Math.min(
                rules.getRisenCapMax(),
                (int) Math.floor(rules.getRisenCapFraction() * tower.easedBaseBudget(floor))));
    var wanted = (names.size() + 2) / 3;
    var memory = tower.getFloorMemory();
    var raised = memory.raiseRisen(floor, names.subList(0, wanted), cap);
    if (raised > 0) {
      writeLedger("They stay on the Floor.", true);
      writeLedger(
          "Floor "
              + floor
              + " holds "
              + raised
              + (raised == 1 ? " more Dead Soldier" : " more Dead Soldiers")
              + " until cleared.",
          true);
    }
  }

  /** Lays the Risen of a floor to rest: the party has cleared it. */
  public void layRisenToRest(int floor) {
    if (tower != null) {
      tower.getFloorMemory().layRisenToRest(floor);
    }
  }

  /**
   * Raises the Village Tier to the highest whose gate the Village now meets, writing one Ledger
   * line for each Tier that opens. Never lowers it.
   */
  private void refreshVillageTier() {
    var reached = VillageTier.reached(config.getTiers(), tally, highestFloor, expeditionsCompleted);
    for (var tier : VillageTier.values()) {
      if (tier.getLevel() > villageTier.getLevel() && tier.getLevel() <= reached.getLevel()) {
        villageTier = tier;
        writeLedger(tier.getLedgerLine(), false);
      }
    }
  }

  public int getVillageTierLevel() {
    return villageTier.getLevel();
  }

  public String getVillageTierName() {
    return villageTier.getDisplayName();
  }

  /** What the Village Tier has opened so far: the Trades and buildings, comma-separated. */
  public String getVillageTierOpens() {
    return java.util.Arrays.stream(VillageTier.values())
        .filter(tier -> tier.getLevel() <= villageTier.getLevel())
        .map(VillageTier::getOpens)
        .filter(opens -> !opens.isEmpty())
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /** The Tally as the Prestige Toll reads it; the caller supplies the Guardians slain. */
  public io.github.diogohmcruz.towerdungeon.domain.prestige.Tally tollTally(int guardiansSlain) {
    return new io.github.diogohmcruz.towerdungeon.domain.prestige.Tally(
        tally.getFoodGrown(),
        tally.getMaterialsBanked(),
        tally.getCreditsBanked(),
        tally.getRelicsBanked(),
        expeditionsCompleted,
        highestFloor,
        guardiansSlain);
  }

  /**
   * Grants every milestone whose condition is now satisfied. Story-unit milestones add the
   * companion to the roster; buff milestones take effect immediately via the capacity/damage
   * getters.
   */
  private void checkMilestones() {
    for (MilestoneType milestone : MilestoneType.values()) {
      if (achievedMilestones.contains(milestone)) {
        continue;
      }
      if (milestone.isSatisfied(highestFloor, expeditionsCompleted)) {
        achievedMilestones.add(milestone);
        if (milestone.getUnlockUnit() != null) {
          unlockedUnits.add(milestone.getUnlockUnit());
        }
        log.info("Milestone reached: {}", milestone.getDisplayName());
      }
    }
    if (expeditionActive) {
      recalculateSupplyCapacity();
    }
  }

  /** Story milestones with their unlock condition and whether they have been achieved. */
  public List<MilestoneOffer> getMilestones() {
    var offers = new ArrayList<MilestoneOffer>();
    for (MilestoneType milestone : MilestoneType.values()) {
      offers.add(
          new MilestoneOffer(
              milestone.name(),
              milestone.getDisplayName(),
              milestone.getDescription(),
              milestone.getTriggerLabel(),
              achievedMilestones.contains(milestone),
              milestone.getUnlockUnit() != null));
    }
    return offers;
  }

  /** Records a defeated tower enemy; drives the kill-based shortcut unlocks. */
  public void recordEnemyKill() {
    enemiesDefeated++;
    runEnemiesDefeated++;
    refreshShortcuts();
  }

  private boolean isShortcutOpen(ShortcutType shortcut) {
    return openedShortcuts.contains(shortcut)
        || shortcut.isUnlocked(expeditionsCompleted, enemiesDefeated);
  }

  /** Records every shortcut the campaign's progress now opens, so none can close again. */
  private void refreshShortcuts() {
    for (ShortcutType shortcut : ShortcutType.values()) {
      if (shortcut.isUnlocked(expeditionsCompleted, enemiesDefeated)) {
        openedShortcuts.add(shortcut);
      }
    }
  }

  /** Tower shortcuts with their target floor, lore, unlock condition and whether they are open. */
  public List<ShortcutOffer> getShortcuts() {
    var offers = new ArrayList<ShortcutOffer>();
    for (ShortcutType shortcut : ShortcutType.values()) {
      offers.add(
          new ShortcutOffer(
              shortcut.name(),
              shortcut.getDisplayName(),
              shortcut.getDescription(),
              shortcut.getTriggerLabel(),
              shortcut.getFloor(),
              isShortcutOpen(shortcut)));
    }
    return offers;
  }

  /**
   * Whether the party may legally begin an expedition on {@code requestedFloor}: either the base of
   * the tower (a non-positive floor) or a floor whose shortcut the campaign has already unlocked.
   */
  public boolean isStartFloorUnlocked(int requestedFloor) {
    if (requestedFloor <= 0) {
      return true;
    }
    return Arrays.stream(ShortcutType.values())
        .anyMatch(shortcut -> shortcut.getFloor() == requestedFloor && isShortcutOpen(shortcut));
  }

  public Integer buyVillager() {
    var villagersCount = village.getVillagersCount();
    while (village.getVillagersCount() < credit) {
      credit = village.buyVillager(credit);
    }
    tally.notePeakVillagers(village.getVillagersCount());
    return villagersCount;
  }

  public Double sellFood() {
    var profit = 0d;
    while (village.sellFood()) {
      profit += config.getReward().getFoodSellPrice();
    }
    this.credit += profit;
    return credit;
  }

  public void enlistRandom(UnitStats unitStats) {
    var currentUnits = this.units.getOrDefault(unitStats, new ArrayList<>());
    var randomIndex = ThreadLocalRandom.current().nextInt(currentUnits.size());
    var unitToEnlist = currentUnits.get(randomIndex);
    currentUnits.remove(unitToEnlist);
    units.put(unitStats, currentUnits);
    var currentUnitsOnTower = unitsOnTower.getOrDefault(unitStats, new ArrayList<>());
    currentUnitsOnTower.add(unitToEnlist);
    unitsOnTower.put(unitStats, currentUnitsOnTower);
  }

  public void removeUnit(Unit targetUnit) {
    var currentUnits = unitsOnTower.get(targetUnit.getStats());
    var isRemoved = currentUnits.remove(targetUnit);
    if (isRemoved) {
      unitsOnTower.put(targetUnit.getStats(), currentUnits);
      if (currentUnits.isEmpty()) {
        unitsOnTower.remove(targetUnit.getStats());
      }
      runUnitsLost++;
      var floor = tower == null ? 0 : tower.getCurrentFloor();
      runFallen.add(fallenAt(targetUnit, floor));
      wallOfNames.add(fallenAt(targetUnit, floor));
      dropGearOnCurrentFloor(targetUnit.dropAll());
      recalculateSupplyCapacity();
      partyMembers().forEach(unit -> unit.changeSanity(-config.getSanity().getAllyDeathDrain()));
    } else {
      log.warn("Unit {} not found on tower. Failed to remove.", targetUnit);
    }
  }

  /** Leaves gear on the floor the party stands on; see {@link #dropGear}. */
  public void dropGearOnCurrentFloor(Collection<Item> items) {
    dropGear(tower == null ? 0 : tower.getCurrentFloor(), items);
  }

  /**
   * Leaves gear on a floor of the tower, to be found again in that floor's Bundle. The base (floor
   * 0) has no Bundle, so gear dropped there is lost, as is anything over the floor's cap.
   */
  public void dropGear(int floor, Collection<Item> items) {
    if (tower == null || floor < 1 || items.isEmpty()) {
      return;
    }
    var items0 = config.getItems();
    tower
        .getFloorMemory()
        .leaveGear(floor, items, new ItemRating(items0.getWeights()), items0.getFloorGearCap());
  }

  /** The gear waiting on each floor, as the Bundle of a cleared floor reads and refills it. */
  public FloorGearStore floorGear() {
    return new FloorGearStore() {
      @Override
      public List<Item> takeAll(int floor) {
        return tower == null ? List.of() : tower.getFloorMemory().takeGear(floor);
      }

      @Override
      public void putBack(int floor, Collection<Item> items) {
        dropGear(floor, items);
      }
    };
  }

  private List<Unit> partyMembers() {
    return unitsOnTower.values().stream().flatMap(List::stream).toList();
  }

  /** Lifts every party member's spirits for having climbed to the next floor. */
  public void rewardFloorClimbed() {
    var gain = config.getSanity().getFloorClimbedGain();
    partyMembers().forEach(unit -> unit.changeSanity(gain + unit.floorClimbedSanityBonus()));
  }

  /** The tower wears on the party: every member loses a little sanity, more the deeper it is. */
  public void drainDepthSanity(int floor) {
    var drain = config.getSanity().getDepthDrainPerFloor() * floor;
    partyMembers().forEach(unit -> unit.changeSanity(-drain));
  }

  /**
   * Rests the minds of the reserves: each one below its maximum regains the configured sanity for
   * the configured food, until the pantry runs dry. A reserve the pantry can only part-pay for
   * recovers the matching share.
   */
  public void recoverHomeSanity() {
    var rules = config.getSanity();
    units.values().stream()
        .flatMap(List::stream)
        .filter(unit -> unit.getSanity() < unit.getMaxSanity())
        .forEach(
            unit -> {
              var cost = rules.getRecoveryFoodPerTick();
              var paid = cost > 0 ? village.takeFood(cost) : 0d;
              var share = cost > 0 ? paid / cost : 0d;
              unit.changeSanity(rules.getRecoveryPerTick() * share);
            });
  }

  public boolean hasUnitsOnTower() {
    return unitsOnTower.values().stream().anyMatch(Predicate.not(List::isEmpty));
  }

  public void passingTime(Long interval) {
    Double attack = Double.valueOf(interval) / 1000;
    var unitsToRemove =
        unitsOnTower.values().stream()
            .flatMap(List::stream)
            .peek(unit -> dropGearOnCurrentFloor(unit.receiveAttrition(attack)))
            .filter(BaseUnit::isDead)
            .toList();
    unitsToRemove.forEach(this::removeUnit);
  }

  /**
   * Whether nothing has been done yet: no expedition and no change to the treasury, vault,
   * upgrades, village or progress since the campaign began, so there is nothing worth writing to
   * disk. The reserves are ignored, as a new campaign starts with its Founders already in them.
   */
  @JsonIgnore
  public boolean isPristine() {
    var fresh = new GameState(config).toSnapshot().campaign().withoutReserves();
    return !expeditionActive && toSnapshot().campaign().withoutReserves().equals(fresh);
  }

  /**
   * The persisted record: always the {@code campaign} facts, plus the in-flight {@code expedition}
   * while one is active. Derived values are not stored. Call it under the state's lock.
   */
  public GameSnapshot toSnapshot() {
    var campaign =
        new CampaignSnapshot(
            credit,
            unitSnapshots(units),
            new EnumMap<>(resources),
            new EnumMap<>(purchasedUpgrades),
            achievedMilestones.stream().sorted().toList(),
            openedShortcuts.stream().sorted().toList(),
            tower == null
                ? null
                : new TowerSnapshot(
                    tower.getCurrentFloor(),
                    tower.getSlainGuardians(),
                    tower.getFloorMemory().gearByFloor(),
                    tower.getFloorMemory().risenByFloor()),
            highestFloor,
            expeditionsCompleted,
            enemiesDefeated,
            gameOutcome,
            lastRunSummary,
            village.getFood(),
            village.getVillagersCount(),
            new TradesSnapshot(
                village.getBreakers(),
                village.getScribes(),
                List.copyOf(village.getSettling().get(Trade.TILLER)),
                List.copyOf(village.getSettling().get(Trade.BREAKER)),
                List.copyOf(village.getSettling().get(Trade.SCRIBE)),
                village.getKnowledge(),
                village.getQuarryHeap(),
                village.isEmergencyTilling(),
                village.getVillagersLost(),
                village.getReservesLost(),
                new EnumMap<>(village.getTomes()),
                new EnumMap<>(village.getPostLevels())),
            List.copyOf(artifactsTaken),
            tally.toSnapshot(),
            villageTier.getLevel(),
            List.copyOf(wallOfNames));
    return new GameSnapshot(campaign, expeditionActive ? expeditionSnapshot() : null);
  }

  private ExpeditionSnapshot expeditionSnapshot() {
    var garrison =
        tower == null || tower.getCurrentTowerFloor() == null
            ? List.<EnemySnapshot>of()
            : tower.getCurrentTowerFloor().getEnemies().stream()
                .map(
                    enemy ->
                        new EnemySnapshot(
                            enemy.getId(),
                            enemy.getName(),
                            enemy.getStats(),
                            enemy.healthMultiplier(),
                            enemy.damageMultiplier(),
                            enemy.isBoss(),
                            partSnapshots(enemy.getParts())))
                .toList();
    var waves =
        reinforcements.stream()
            .map(
                wave ->
                    new WaveSnapshot(
                        unitSnapshots(wave.getUnits()),
                        wave.getCurrentFloor(),
                        wave.getSupplies(),
                        wave.getMaxSupplies()))
            .toList();
    return new ExpeditionSnapshot(
        unitSnapshots(unitsOnTower),
        supplies,
        new EnumMap<>(carriedLoot),
        carriedCredits,
        new EnumMap<>(surplus),
        lastFoodReturned,
        runStartFloor,
        runEnemiesDefeated,
        runUnitsLost,
        List.copyOf(runFallen),
        garrison,
        waves);
  }

  /** Units in a stable order: by type, then in list order. */
  private static List<UnitSnapshot> unitSnapshots(Map<UnitStats, List<Unit>> byType) {
    return byType.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .flatMap(entry -> entry.getValue().stream())
        .map(
            unit ->
                new UnitSnapshot(
                    unit.getId(),
                    unit.getName(),
                    unit.getStats(),
                    unit.getHealthFactor(),
                    unit.getDamageFactor(),
                    unit.isFixedStrengths(),
                    unit.getBaseMaxMana(),
                    unit.getCurrentMana(),
                    partSnapshots(unit.getParts()),
                    unit.getSanity(),
                    unit.getBaseMaxSanity(),
                    unit.getPhobias().stream()
                        .map(phobia -> new PhobiaSnapshot(phobia.getFeared(), phobia.isKnown()))
                        .toList(),
                    unit.getFearFloor(),
                    Map.copyOf(unit.getGear().getSlots()),
                    unit.getAffinity()))
        .toList();
  }

  private static List<PartSnapshot> partSnapshots(List<Part> parts) {
    return parts.stream()
        .map(
            part ->
                new PartSnapshot(
                    part.getKind(), part.getMaxHealth(), part.getCurrentHealth(), part.isLost()))
        .toList();
  }

  private static List<Part> partsFrom(List<PartSnapshot> snapshots) {
    return snapshots.stream()
        .map(
            p -> {
              var part = new Part(p.kind(), p.maxHealth());
              part.setCurrentHealth(p.currentHealth());
              part.setLost(p.lost());
              return part;
            })
        .toList();
  }

  private static Map<UnitStats, List<Unit>> unitsFrom(
      List<UnitSnapshot> snapshots, GameProperties config) {
    Map<UnitStats, List<Unit>> byType = new HashMap<>();
    for (var u : snapshots) {
      var unit =
          Unit.restore(
              u.type(),
              u.id(),
              u.name(),
              u.healthFactor(),
              u.damageFactor(),
              u.fixedStrengths(),
              u.maxMana(),
              u.currentMana(),
              partsFrom(u.parts()),
              u.gear(),
              u.affinity(),
              config.getBody().getWeights());
      var maxSanity = u.maxSanity() == null ? config.getSanity().getMax() : u.maxSanity();
      unit.setItemEffects(config.getItems().getEffects());
      unit.restoreMind(
          u.sanity() == null ? maxSanity : u.sanity(),
          maxSanity,
          u.phobias().stream().map(p -> new Phobia(p.enemyType(), p.known())).toList(),
          u.fearFloor(),
          config.getSanity());
      byType.computeIfAbsent(u.type(), _ -> new ArrayList<>()).add(unit);
    }
    return byType;
  }

  /**
   * Rebuilds a state with a production-style random source and the Tower's Architecture plan
   * generated from {@code config}.
   */
  public static GameState restore(GameSnapshot snapshot, GameProperties config) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return restore(snapshot, config, new ThreadLocalRandomGenerator(), plan);
  }

  /**
   * Rebuilds a state from its persisted record. Derived values (unlocked unit types, carrying
   * capacity) are recomputed; unit strengths are reapplied as saved, never re-rolled; the Tower
   * gets its own copy of the Boss and Enemies tuning.
   */
  public static GameState restore(
      GameSnapshot snapshot,
      GameProperties config,
      RandomGenerator random,
      ArchitecturePlan architecturePlan) {
    var c = snapshot.campaign();
    var state = new GameState(config);
    state.credit = c.credit();
    state.units = unitsFrom(c.reserves(), config);
    state.resources.putAll(c.vault());
    state.purchasedUpgrades.putAll(c.upgrades());
    state.achievedMilestones.addAll(c.milestones());
    state.openedShortcuts.addAll(c.shortcutsOpened());
    state.highestFloor = c.highestFloor();
    state.expeditionsCompleted = c.expeditionsCompleted();
    state.enemiesDefeated = c.enemiesDefeated();
    state.gameOutcome = c.outcome();
    state.lastRunSummary = c.lastExpeditionSummary();
    state.wallOfNames = new ArrayList<>(c.wallOfNames());
    state.village.restore(c.villageFood(), c.villagers());
    var t = c.trades();
    if (t != null) {
      state.village.restoreTrades(
          t.breakers(),
          t.scribes(),
          Map.of(
              Trade.TILLER, t.settlingTillers(),
              Trade.BREAKER, t.settlingBreakers(),
              Trade.SCRIBE, t.settlingScribes()),
          t.knowledge(),
          t.quarryHeap(),
          t.emergencyTilling(),
          t.villagersLost(),
          t.reservesLost());
      state.village.restoreUpgrades(t.tomes(), t.postLevels());
    }
    state.tally = new CampaignTally(c.villagers());
    if (c.tally() != null) {
      state.tally.restore(c.tally());
    }
    state.villageTier = VillageTier.ofLevel(c.villageTier());
    state.recomputeUnlockedUnits();

    state.artifactsTaken.addAll(c.artifactsTaken());
    var e = snapshot.expedition();
    if (e != null) {
      state.expeditionActive = true;
      state.unitsOnTower = unitsFrom(e.party(), config);
      state.supplies = e.supplies();
      state.carriedLoot.putAll(e.carriedLoot());
      state.carriedCredits = e.carriedCredits();
      state.surplus.putAll(e.surplus());
      state.lastFoodReturned = e.lastFoodReturned();
      state.runStartFloor = e.runStartFloor();
      state.runEnemiesDefeated = e.runEnemiesDefeated();
      state.runUnitsLost = e.runUnitsLost();
      state.runFallen = new ArrayList<>(e.runFallen());
      e.waves()
          .forEach(
              w ->
                  state.reinforcements.add(
                      Reinforcement.restore(
                          unitsFrom(w.units(), config),
                          w.currentFloor(),
                          w.supplies(),
                          w.maxSupplies())));
    }
    if (c.tower() != null) {
      var garrison =
          e == null
              ? List.<Enemy>of()
              : e.garrison().stream()
                  .map(
                      en ->
                          Enemy.restore(
                              en.id(),
                              en.name(),
                              en.type(),
                              en.healthMultiplier(),
                              en.damageMultiplier(),
                              en.boss(),
                              partsFrom(en.parts())))
                  .toList();
      state.tower =
          Tower.restore(
              config.getTower().getMaxFloor(),
              config.getBoss().copy(),
              config.getEnemies().copy(),
              random,
              architecturePlan,
              c.tower().currentFloor(),
              c.highestFloor(),
              new HashSet<>(c.tower().clearedBosses()),
              garrison);
      state.tower.getFloorMemory().restoreGear(c.tower().floorGear());
      state.tower.getFloorMemory().restoreRisen(c.tower().risen());
    }
    state.maxSupplies = state.computeSupplyCapacity();
    return state;
  }

  /** Unit types open to recruit: the starting three plus whatever upgrades and milestones grant. */
  private void recomputeUnlockedUnits() {
    unlockedUnits = EnumSet.of(UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.PORTER);
    purchasedUpgrades.keySet().stream()
        .map(UpgradeType::getUnlockUnit)
        .filter(Objects::nonNull)
        .forEach(unlockedUnits::add);
    achievedMilestones.stream()
        .map(MilestoneType::getUnlockUnit)
        .filter(Objects::nonNull)
        .forEach(unlockedUnits::add);
  }
}
