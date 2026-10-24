package io.github.diogohmcruz.towerdungeon.agent.balance;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.playtest.PlaytestRunner;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.services.DuelBench;
import io.github.diogohmcruz.towerdungeon.domain.services.FloorBundle;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.PartyCombat;
import tools.jackson.databind.json.JsonMapper;

/**
 * The floor duel harness: runs a {@link PartyPlan} against one Floor of the Tower, many times, on
 * the real combat, Attrition, Supplies-drain and Starvation code, and reports what happened, with
 * no playthrough. It is a tool of the scaling pass (#8), not a player feature.
 *
 * <p>Each duel is a Campaign of its own (a {@link GameState} on its own copy of the configuration,
 * with Tower Memory applied as a real Campaign applies it), its Party set out on the Floor with the
 * plan's Supplies and its Garrison built by the real {@code Tower} from the per-floor stream of the
 * Campaign's seed, then ticked a second at a time, as the manual driver does, until the Party
 * clears the Floor (the Floor changes under it) or is Wiped. A run is fixed by its seed alone: the
 * same plan, Floor and seed give the same report in any JVM.
 *
 * <p>Two choices stand in for what a duel leaves out. The Party arrives on the Floor at full health
 * and with full sanity, as an Expedition that sets out from it would, and the Year is started at a
 * random tick drawn from the run's seed, so the Quarter's effect (Summer's lighter drain, Winter's
 * heavier Attrition) averages out as it does over a Campaign.
 */
@AgentEnabled
@Component
public class FloorDuel {
  /** A duel that has run this many ticks without a result is stopped and counted as a timeout. */
  public static final int MAX_TICKS = 2000;

  /** One run of a duel, before it is averaged. */
  public record Run(
      long seed,
      boolean guardian,
      boolean surge,
      boolean cleared,
      boolean wiped,
      int ticks,
      double garrisonHealth,
      int partyStart,
      int partyEnd,
      double damageDealt,
      double suppliesSpent,
      int unitsLost,
      double takenCombat,
      double takenAttrition,
      double takenStarvation) {}

  private static final long RECRUIT_STREAM = 0x5DEECE66DL;
  private static final long YEAR_STREAM = 0x2545F4914F6CDD1DL;

  private final GameProperties base;
  private final DuelBench bench;

  /** The harness on a configuration and a running game service (Spring wires these). */
  public FloorDuel(GameProperties base, GameService service, ArchitecturePlan plan) {
    this.base = base;
    this.bench = new DuelBench(service, plan);
  }

  /**
   * A harness that needs no Spring context: the game's own {@link GameService} on {@code config},
   * with the Tower's plan generated from its architecture seed, as a server's is.
   */
  public static FloorDuel standalone(GameProperties config) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var service =
        new GameService(
            config,
            JsonMapper.builder().build(),
            SeedSource.fixed(1),
            plan,
            new PartyCombat(),
            null,
            new FloorBundle(config, new LeftoverGenerator(config)),
            null);
    return new FloorDuel(config, service, plan);
  }

  /**
   * Runs {@code runs} duels of {@code plan} on {@code floor} and averages them. Run {@code i} is on
   * the seed {@link PlaytestRunner#seedFor(long, int) seedFor(seed, i)}.
   */
  public DuelReport duel(PartyPlan plan, int floor, int runs, long seed) {
    return report(plan, floor, seed, fight(plan, floor, runs, seed));
  }

  /** The runs themselves, in order: for a test that wants to look at one. */
  public List<Run> fight(PartyPlan plan, int floor, int runs, long seed) {
    if (floor < 1 || floor > base.getTower().getMaxFloor()) {
      throw new IllegalArgumentException(
          "No Floor " + floor + " in a Tower of " + base.getTower().getMaxFloor());
    }
    if (runs < 1) {
      throw new IllegalArgumentException("A duel needs at least one run");
    }
    var out = new ArrayList<Run>(runs);
    for (var index = 0; index < runs; index++) {
      out.add(runOnce(plan, floor, PlaytestRunner.seedFor(seed, index)));
    }
    return out;
  }

  private Run runOnce(PartyPlan plan, int floor, long seed) {
    var config = base.copy().withTowerMemory(plan.towerMemoryStep() + 1);
    plan.aim().forEach((type, weights) -> config.getAiming().getUnitProfiles().put(type, weights));
    if (plan.carryingCapacity() != null) {
      // Carried by what the Party adds, so a Porter that falls still takes its share: the base is
      // moved after the Expedition has set out and counted what the rest gives.
      config.getSupply().setBaseMax(0d);
    }

    var state = bench.open(config, seed);
    var recruits = new SplittableRandom(seed ^ RECRUIT_STREAM);
    var party = new ArrayList<Unit>();
    var counts = new java.util.EnumMap<>(plan.units());
    if (plan.founders()) {
      for (var founder : config.getStart().getFounders()) {
        var left = counts.getOrDefault(founder.getUnitType(), 0);
        if (left > 0) {
          counts.put(founder.getUnitType(), left - 1);
          party.add(state.recruitFounder(strengthOf(founder, plan), recruits));
        }
      }
    }
    counts.forEach((type, count) -> recruit(state, recruits, party, type, count));
    startUpgrades(state, plan);

    var year = new SplittableRandom(seed ^ YEAR_STREAM);
    for (var tick = year.nextInt(config.getYear().ticksPerYear()); tick > 0; tick--) {
      state.advanceYear();
    }

    var tower = bench.muster(state, party);
    plan.easing().forEach(tower::markBossCleared);
    if (plan.risen() > 0) {
      var names = java.util.Collections.nCopies(plan.risen(), "Risen");
      tower.getFloorMemory().raiseRisen(floor, names, plan.risen());
    }
    bench.setOut(state, floor);
    carry(state, plan);

    var open = tower.getCurrentTowerFloor();
    var garrison = open.getEnemies().stream().mapToDouble(enemy -> enemy.getCurrentHealth()).sum();
    var surgeEvery = config.getEnemies().getSurgeInterval();
    var partyStart = state.getTowerPartySize();
    var before = state.getMeter().snapshot();

    var ticks = 0;
    var cleared = false;
    while (ticks < MAX_TICKS) {
      bench.second(state);
      ticks++;
      if (!state.isExpeditionActive()) {
        cleared =
            state.getLastRunSummary() != null
                && RunSummary.VICTORY.equals(state.getLastRunSummary().outcome());
        break;
      }
      if (tower.getCurrentFloor() > floor || state.isDoorSeen()) {
        cleared = true;
        break;
      }
    }
    var wiped = !cleared && !state.isExpeditionActive();
    var after = state.getMeter().snapshot();
    return new Run(
        seed,
        open.isBoss(),
        !open.isBoss() && surgeEvery > 0 && floor % surgeEvery == 0,
        cleared,
        wiped,
        ticks,
        garrison,
        partyStart,
        state.getTowerPartySize(),
        after.damageDealt() - before.damageDealt(),
        after.suppliesDrained() - before.suppliesDrained(),
        after.unitsLost() - before.unitsLost(),
        after.takenCombat() - before.takenCombat(),
        after.takenAttrition() - before.takenAttrition(),
        after.takenStarvation() - before.takenStarvation());
  }

  /** A Founder as the Campaign's Generation makes it: Generation 1's stronger kind only then. */
  private static GameProperties.Founder strengthOf(GameProperties.Founder founder, PartyPlan plan) {
    return plan.towerMemoryStep() > 0 && founder.isFirstGenerationOnly()
        ? new GameProperties.Founder(founder.getName(), founder.getUnitType(), 1.0, false)
        : founder;
  }

  private static void recruit(
      GameState state, SplittableRandom random, List<Unit> party, UnitStats type, int count) {
    for (var made = 0; made < count; made++) {
      party.add(state.recruit(type, random));
    }
  }

  /** Buys the plan's Upgrade levels outright, out of a treasury that cannot run dry. */
  private static void startUpgrades(GameState state, PartyPlan plan) {
    for (var type : ResourceType.values()) {
      state.grant(type, 1e15);
    }
    plan.upgrades()
        .forEach(
            (upgrade, level) -> {
              for (var bought = 0; bought < level; bought++) {
                if (!state.applyUpgrade(upgrade)) {
                  break;
                }
              }
            });
  }

  /** Sets the Carrying Capacity and the Supplies the plan asks for, once the Party has set out. */
  private static void carry(GameState state, PartyPlan plan) {
    if (plan.carryingCapacity() != null) {
      var given = state.getMaxSupplies();
      state.getConfig().getSupply().setBaseMax(Math.max(0d, plan.carryingCapacity() - given));
      state.recalculateSupplyCapacity();
    }
    var capacity = state.getMaxSupplies();
    state.setSupplies(Math.min(plan.supplies() == null ? capacity : plan.supplies(), capacity));
    state.setSaltedSupplies(Math.min(plan.saltedSupplies(), state.getSupplies()));
  }

  private static DuelReport report(PartyPlan plan, int floor, long seed, List<Run> runs) {
    var n = runs.size();
    var ticks = runs.stream().mapToInt(Run::ticks).summaryStatistics();
    var mean = ticks.getAverage();
    var variance =
        runs.stream().mapToDouble(run -> Math.pow(run.ticks() - mean, 2)).sum()
            / Math.max(1, n - 1);
    var damage = runs.stream().mapToDouble(Run::damageDealt).sum();
    var unitTicks =
        runs.stream()
            .mapToDouble(run -> run.ticks() * (run.partyStart() + run.partyEnd()) / 2d)
            .sum();
    var d = unitTicks > 0 ? damage / unitTicks : 0d;
    var health = runs.stream().mapToDouble(Run::garrisonHealth).average().orElse(0d);
    var wins = (int) runs.stream().filter(Run::cleared).count();
    var wipes = (int) runs.stream().filter(Run::wiped).count();
    return new DuelReport(
        floor,
        runs.getFirst().guardian(),
        runs.getFirst().surge(),
        seed,
        n,
        (double) wins / n,
        mean,
        Math.sqrt(variance),
        ticks.getMin(),
        ticks.getMax(),
        mean(runs, Run::suppliesSpent),
        mean(runs, run -> run.unitsLost()),
        mean(runs, Run::takenCombat),
        mean(runs, Run::takenAttrition),
        mean(runs, Run::takenStarvation),
        mean(runs, run -> run.partyStart()),
        health,
        d,
        d > 0 ? Math.sqrt(health / (1.25 * d)) : Double.NaN,
        wipes,
        n - wins - wipes);
  }

  private static double mean(List<Run> runs, java.util.function.ToDoubleFunction<Run> of) {
    return runs.stream().mapToDouble(of).average().orElse(0d);
  }
}
