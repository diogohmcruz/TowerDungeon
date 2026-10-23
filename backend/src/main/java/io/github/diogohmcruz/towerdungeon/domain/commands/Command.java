package io.github.diogohmcruz.towerdungeon.domain.commands;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.Tome;
import io.github.diogohmcruz.towerdungeon.domain.models.Trade;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * Something a player (or an agent acting for one) asks the game to do. These are domain records, so
 * no front door (the WebSocket handler today, an agent surface tomorrow) has to build another
 * door's DTOs to reach the {@code GameService}.
 */
public sealed interface Command {

  /** A stable upper-case name for logs and events; never derived from caller-supplied text. */
  String kind();

  /** Recruit up to {@code quantity} units of a type, as far as the spendable Credits stretch. */
  record Recruit(UnitStats unitStats, Integer quantity) implements Command {
    @Override
    public String kind() {
      return "RECRUIT";
    }
  }

  /**
   * Hire villagers until the Vault can no longer pay for another, or {@code count} have been hired.
   * A null count hires as many as the Vault can pay for (what the screen does).
   */
  record BuyVillagers(Integer count) implements Command {
    public BuyVillagers() {
      this(null);
    }

    @Override
    public String kind() {
      return "BUY_VILLAGERS";
    }
  }

  /**
   * Sell the Pantry's food above the keep-back. A null {@code amount} sells all of it (what the
   * screen does). With an amount, up to that many whole Food are sold, and the sale is refused with
   * {@code WOULD_STARVE} when it would leave the Pantry under a safe floor for the Reserves'
   * Upkeep, unless {@code force} is set.
   */
  record SellFood(Integer amount, boolean force) implements Command {
    public SellFood() {
      this(null, false);
    }

    @Override
    public String kind() {
      return "SELL_FOOD";
    }
  }

  /** Sell the banked Materials: all of them for a null {@code amount}, else up to that many. */
  record SellMaterials(Double amount) implements Command {
    public SellMaterials() {
      this(null);
    }

    @Override
    public String kind() {
      return "SELL_MATERIALS";
    }
  }

  /** Sell the Salt in the Stockroom. */
  record SellSalt() implements Command {
    @Override
    public String kind() {
      return "SELL_SALT";
    }
  }

  /** Sell the banked Relics: all of them for a null {@code amount}, else up to that many. */
  record SellRelics(Double amount) implements Command {
    public SellRelics() {
      this(null);
    }

    @Override
    public String kind() {
      return "SELL_RELICS";
    }
  }

  /**
   * Buy the next level of an upgrade. The id is caller-supplied text: it is never logged, and its
   * {@code toString} does not show it.
   */
  record BuyUpgrade(String upgradeId) implements Command {
    @Override
    public String kind() {
      return "BUY_UPGRADE";
    }

    @Override
    public String toString() {
      return "BuyUpgrade[upgradeId=<" + (upgradeId == null ? "null" : upgradeId.length()) + ">]";
    }
  }

  /**
   * Send reserves up the tower. An empty selection sends everyone; a null one is not supported
   * (today it fails with a {@link NullPointerException}). {@code salt} is the Salt to salt Supplies
   * with; null is none. {@code saltPack} is the Salt of a Salt Pack against Chymus Insects, carried
   * by the first Unit of type {@code saltBearer} in the Party; null or 0 is no Pack. {@code
   * panacea} asks the Loadout to carry a Panacea. {@code overreach} sets the Expedition to
   * Overreach, which only a Roll that holds it allows.
   */
  record StartExpedition(
      Map<UnitStats, Integer> units,
      Integer startFloor,
      Integer salt,
      Integer saltPack,
      UnitStats saltBearer,
      boolean panacea,
      boolean overreach)
      implements Command {
    public StartExpedition(
        Map<UnitStats, Integer> units,
        Integer startFloor,
        Integer salt,
        Integer saltPack,
        UnitStats saltBearer,
        boolean panacea) {
      this(units, startFloor, salt, saltPack, saltBearer, panacea, false);
    }

    public StartExpedition(Map<UnitStats, Integer> units, Integer startFloor, Integer salt) {
      this(units, startFloor, salt, null, null);
    }

    public StartExpedition(Map<UnitStats, Integer> units, Integer startFloor) {
      this(units, startFloor, null, null, null, false);
    }

    public StartExpedition(
        Map<UnitStats, Integer> units,
        Integer startFloor,
        Integer salt,
        Integer saltPack,
        UnitStats saltBearer) {
      this(units, startFloor, salt, saltPack, saltBearer, false);
    }

    @Override
    public String kind() {
      return "START_EXPEDITION";
    }
  }

  /**
   * Save the Loadout in a slot, under Quartermaster's Tally: the Salt, the Salt Pack and its
   * bearer, the Panacea and the Overreach switch, not the Party.
   */
  record SaveLoadout(
      Integer slot,
      Integer salt,
      Integer saltPack,
      UnitStats saltBearer,
      boolean panacea,
      boolean overreach)
      implements Command {
    @Override
    public String kind() {
      return "SAVE_LOADOUT";
    }
  }

  /** Forget the Loadout saved in a slot. */
  record ClearLoadout(Integer slot) implements Command {
    @Override
    public String kind() {
      return "CLEAR_LOADOUT";
    }
  }

  /** Move one villager into ({@code delta} above zero) or out of a Trade. */
  record AssignTrade(Trade trade, Integer delta) implements Command {
    @Override
    public String kind() {
      return "ASSIGN_TRADE";
    }
  }

  /** Buy the next level of a Tome with Knowledge. */
  record BuyTome(Tome tome) implements Command {
    @Override
    public String kind() {
      return "BUY_TOME";
    }
  }

  /** Buy the next level of the Thought-Binding Ring with Trust. */
  record BuyRing() implements Command {
    @Override
    public String kind() {
      return "BUY_RING";
    }
  }

  /** Raise the Posts of a Trade for Materials from the Vault. */
  record BuyPosts(Trade trade) implements Command {
    @Override
    public String kind() {
      return "BUY_POSTS";
    }
  }

  /**
   * Finish the Port with Materials and Credits from the Vault. Refused until the footing is open,
   * once built, in a Trial, and when the Vault cannot pay.
   */
  record BuildPort() implements Command {
    @Override
    public String kind() {
      return "BUILD_PORT";
    }
  }

  /** Spend one Spore on a Remedy: a Unit at home recovers 20 health at once. */
  record GiveRemedy(Integer unitId) implements Command {
    @Override
    public String kind() {
      return "GIVE_REMEDY";
    }
  }

  /** Spend one Spore on reattaching a lost arm or leg of a Unit at home. */
  record ReattachPart(Integer unitId, BodyPart part) implements Command {
    @Override
    public String kind() {
      return "REATTACH_PART";
    }
  }

  /** End the active expedition and bring the party home with what it carries. */
  record Extract() implements Command {
    @Override
    public String kind() {
      return "EXTRACT";
    }
  }

  /**
   * Stop the Party and the Village clock where they stand, as a lost connection does. Repeating it
   * changes nothing. With no Expedition out it still stops the Village clock.
   */
  record Hold() implements Command {
    @Override
    public String kind() {
      return "HOLD";
    }
  }

  /** Let a Held Party march on. */
  record Resume() implements Command {
    @Override
    public String kind() {
      return "RESUME";
    }
  }

  /** Send a follow-up wave from the reserves. An empty or null selection sends everyone home. */
  record SendReinforcements(Map<UnitStats, Integer> units) implements Command {
    @Override
    public String kind() {
      return "SEND_REINFORCEMENTS";
    }
  }

  /**
   * Lead the village away from the tower: the Campaign ends with its Toll. Refused while an
   * Expedition is out or Held, and while the Toll forecast is under the minimum.
   */
  record Exodus() implements Command {
    @Override
    public String kind() {
      return "EXODUS";
    }
  }

  /**
   * Muster Roll: send the last Party and Loadout out again with one command. Needs the Inheritance
   * in the Roll and a Party that has set out this Generation; Extraction stays manual.
   */
  record MusterRoll() implements Command {
    @Override
    public String kind() {
      return "MUSTER_ROLL";
    }
  }

  /** Buy an Inheritance of the Roll with Legacy, at Succession. The id is caller-supplied. */
  record RollBuy(String inheritanceId) implements Command {
    @Override
    public String kind() {
      return "ROLL_BUY";
    }

    @Override
    public String toString() {
      return "RollBuy[inheritanceId=<"
          + (inheritanceId == null ? "null" : inheritanceId.length())
          + ">]";
    }
  }

  /**
   * Give an Inheritance back for what was paid for it, at Succession. The id is caller-supplied.
   */
  record RollRefund(String inheritanceId) implements Command {
    @Override
    public String kind() {
      return "ROLL_REFUND";
    }

    @Override
    public String toString() {
      return "RollRefund[inheritanceId=<"
          + (inheritanceId == null ? "null" : inheritanceId.length())
          + ">]";
    }
  }

  /** Make one of the Succession's choices for the next Generation. The id is caller-supplied. */
  record Choose(String choiceId) implements Command {
    @Override
    public String kind() {
      return "CHOOSE";
    }

    @Override
    public String toString() {
      return "Choose[choiceId=<" + (choiceId == null ? "null" : choiceId.length()) + ">]";
    }
  }

  /** Begin the next Generation from a Campaign that has ended. A no-op once it has begun. */
  record BeginGeneration() implements Command {
    @Override
    public String kind() {
      return "BEGIN_GENERATION";
    }
  }
}
