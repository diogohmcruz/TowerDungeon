package io.github.diogohmcruz.towerdungeon.agent.playtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * The three starting policies (design 5.7). They share one way of running the Village, so what
 * separates them is when the Party comes home.
 *
 * <ul>
 *   <li>{@link #GREEDY}: recruits the cheapest affordable Archer or Warrior, goes up, and Extracts
 *       when the Supplies have fallen to 30% of what it left with.
 *   <li>{@link #CAUTIOUS}: the same Village, and Extracts as soon as a Guardian Floor is cleared.
 *       It never Extracts for any other reason, so it shows what that rule alone is worth.
 *   <li>{@link #RANDOM}: picks among what is legal at each step, and Extracts at random.
 * </ul>
 */
public final class Policies {
  private Policies() {}

  /** Fewest Units the bots send up; fewer than this go only after a long wait. */
  static final int MIN_PARTY = 3;

  /** Most Units the bots keep in the Reserves: more is a Pantry the Village cannot feed. */
  static final int MAX_RESERVES = 8;

  /** Food the bots keep for the Supplies of the next Expedition. */
  static final int PROVISION = 100;

  /**
   * Food that must stay behind when a Party sets out, which takes the Pantry down to nothing
   * otherwise and starves the Villagers while it is away.
   */
  static final int HOME_KEEP = 100;

  /** Ticks at home a bot waits at a time. */
  static final int HOME_WAIT = 10;

  /** Ticks at home after which a bot goes with whatever it has. */
  static final int PATIENCE_TICKS = 120;

  public static final Policy GREEDY =
      new Policy() {
        @Override
        public String name() {
          return "greedy";
        }

        @Override
        public Move next(Sight sight, RandomGenerator dice) {
          if (!sight.expedition()) {
            return atHome(sight);
          }
          return sight.suppliesLeft() <= 0.30 ? new Move.Extract() : new Move.Wait(1);
        }
      };

  public static final Policy CAUTIOUS =
      new Policy() {
        @Override
        public String name() {
          return "cautious";
        }

        @Override
        public Move next(Sight sight, RandomGenerator dice) {
          if (!sight.expedition()) {
            return atHome(sight);
          }
          return sight.guardianBelowSlain() ? new Move.Extract() : new Move.Wait(1);
        }
      };

  public static final Policy RANDOM =
      new Policy() {
        @Override
        public String name() {
          return "random";
        }

        @Override
        public Move next(Sight sight, RandomGenerator dice) {
          if (sight.expedition()) {
            return dice.nextInt(30) == 0 ? new Move.Extract() : new Move.Wait(1);
          }
          var options = new ArrayList<Move>();
          for (var type : sight.unlocked()) {
            if (sight.reservesTotal() < MAX_RESERVES && sight.spendable() > type.getCost()) {
              options.add(new Move.Recruit(type, 1));
            }
          }
          if (sight.reservesTotal() > 0) {
            options.add(new Move.StartExpedition());
            options.add(new Move.StartExpedition());
          }
          if (sight.pantry() > 2) {
            options.add(
                new Move.SellFood(1 + dice.nextInt((int) Math.min(50, sight.pantry() - 1))));
          }
          options.add(new Move.Wait(HOME_WAIT));
          return options.get(dice.nextInt(options.size()));
        }
      };

  /**
   * Not one of the three of the design: an exploratory player that spends what a Village offers
   * (Porters for carrying, Healers and Tanks once unlocked, Upgrades) and Extracts on low Supplies
   * or a badly hurt Party. It always sets out from the foot of the Tower. It exists to reach
   * further than the three, so the baseline shows more of the curve, and is reported apart.
   */
  public static final Policy DELVER = new Delver("delver", false);

  /**
   * As {@link #DELVER}, but each Expedition sets out from the next open Shortcut in turn (the foot,
   * then each Shortcut Floor, then the foot again). It is how the baseline samples Floors the other
   * policies never reach alive, and it is not a way to play well.
   */
  public static final Policy LADDER = new Delver("ladder", true);

  private static final class Delver implements Policy {
    private static final List<String> UPGRADES =
        List.of("UNLOCK_HEALER", "SUPPLY_LINES", "UNLOCK_TANK", "SHARPENED_ARMS");

    private final String name;
    private final boolean ladder;

    private Delver(String name, boolean ladder) {
      this.name = name;
      this.ladder = ladder;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Move next(Sight sight, RandomGenerator dice) {
      if (sight.expedition()) {
        return sight.suppliesLeft() <= 0.30 || sight.partyHealth() <= 0.20
            ? new Move.Extract()
            : new Move.Wait(1);
      }
      for (var id : UPGRADES) {
        if (sight.upgrades().contains(id)) {
          return new Move.BuyUpgrade(id);
        }
      }
      if (sight.reservesTotal() < DELVER_RESERVES) {
        var pick = wanted(sight);
        if (pick != null) {
          return new Move.Recruit(pick, 1);
        }
        if (sight.pantry() > PROVISION + HOME_KEEP + 20) {
          return new Move.SellFood((int) (sight.pantry() - PROVISION - HOME_KEEP));
        }
      }
      var patient = sight.homeTicks() >= PATIENCE_TICKS;
      var enough = sight.reservesTotal() >= DELVER_PARTY || (sight.reservesTotal() > 0 && patient);
      var provisioned =
          sight.pantry() >= PROVISION + HOME_KEEP || (patient && sight.pantry() >= PROVISION / 2);
      return enough && provisioned
          ? new Move.StartExpedition(startFloor(sight))
          : new Move.Wait(HOME_WAIT);
    }

    private int startFloor(Sight sight) {
      if (!ladder || sight.shortcuts().isEmpty()) {
        return 0;
      }
      var steps = sight.shortcuts().size() + 1;
      var turn = sight.expeditions() % steps;
      return turn == 0 ? 0 : sight.shortcuts().get(turn - 1);
    }

    /** Two Porters, two Tanks, two Healers as they unlock, then Warriors and Archers evenly. */
    private UnitStats wanted(Sight sight) {
      var order =
          List.of(
              Map.entry(UnitStats.PORTER, 2),
              Map.entry(UnitStats.TANK, 2),
              Map.entry(UnitStats.HEALER, 2));
      for (var want : order) {
        var type = want.getKey();
        if (sight.unlocked().contains(type)
            && sight.reserve(type) < want.getValue()
            && sight.spendable() > type.getCost()) {
          return type;
        }
      }
      return recruitable(sight);
    }
  }

  /**
   * Most Units the Delver keeps at home, and how many send it up. Upkeep is 1 Food per Unit per
   * tick and 20 Villagers grow 10, so more than 10 at home starves the Village.
   */
  static final int DELVER_RESERVES = 9;

  static final int DELVER_PARTY = 6;

  /** Every policy, by name: the three of the design and the two exploratory ones. */
  public static Map<String, Policy> all() {
    return Map.of(
        "greedy",
        GREEDY,
        "cautious",
        CAUTIOUS,
        "random",
        RANDOM,
        "delver",
        DELVER,
        "ladder",
        LADDER);
  }

  public static List<String> names() {
    return all().keySet().stream().sorted().toList();
  }

  /**
   * How the Greedy and Cautious bots run the Village: recruit the Warrior or Archer the Reserves
   * have fewer of while the Credits allow, up to {@link #MAX_RESERVES}; sell Food that the next
   * Expedition's Supplies do not need, to pay for them; go up when provisioned and strong enough;
   * otherwise wait. Waiting at home is Village-only time, and the telemetry keeps it apart.
   */
  static Move atHome(Sight sight) {
    var room = sight.reservesTotal() < MAX_RESERVES;
    if (room) {
      var pick = recruitable(sight);
      if (pick != null) {
        return new Move.Recruit(pick, 1);
      }
      if (sight.pantry() > PROVISION + HOME_KEEP + 20) {
        return new Move.SellFood((int) (sight.pantry() - PROVISION - HOME_KEEP));
      }
    }
    var patient = sight.homeTicks() >= PATIENCE_TICKS;
    var enough = sight.reservesTotal() >= MIN_PARTY || (sight.reservesTotal() > 0 && patient);
    var provisioned =
        sight.pantry() >= PROVISION + HOME_KEEP || (patient && sight.pantry() >= PROVISION / 2);
    if (enough && provisioned) {
      return new Move.StartExpedition();
    }
    return new Move.Wait(HOME_WAIT);
  }

  /** The Warrior or Archer with fewer in the Reserves that the Credits can pay for, else null. */
  private static UnitStats recruitable(Sight sight) {
    var first = sight.reserve(UnitStats.WARRIOR) <= sight.reserve(UnitStats.ARCHER);
    var order =
        first
            ? List.of(UnitStats.WARRIOR, UnitStats.ARCHER)
            : List.of(UnitStats.ARCHER, UnitStats.WARRIOR);
    for (var type : order) {
      if (sight.unlocked().contains(type) && sight.spendable() > type.getCost()) {
        return type;
      }
    }
    return null;
  }
}
