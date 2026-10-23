package io.github.diogohmcruz.towerdungeon.domain.commands;

import java.util.Map;

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

  /** Hire villagers until the Vault can no longer pay for another. */
  record BuyVillagers() implements Command {
    @Override
    public String kind() {
      return "BUY_VILLAGERS";
    }
  }

  /** Sell the Pantry's food above the keep-back. */
  record SellFood() implements Command {
    @Override
    public String kind() {
      return "SELL_FOOD";
    }
  }

  /** Sell every banked Material. */
  record SellMaterials() implements Command {
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

  /** Sell every banked Relic. */
  record SellRelics() implements Command {
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
   * by the first Unit of type {@code saltBearer} in the Party; null or 0 is no Pack.
   */
  record StartExpedition(
      Map<UnitStats, Integer> units,
      Integer startFloor,
      Integer salt,
      Integer saltPack,
      UnitStats saltBearer)
      implements Command {
    public StartExpedition(Map<UnitStats, Integer> units, Integer startFloor, Integer salt) {
      this(units, startFloor, salt, null, null);
    }

    public StartExpedition(Map<UnitStats, Integer> units, Integer startFloor) {
      this(units, startFloor, null, null, null);
    }

    @Override
    public String kind() {
      return "START_EXPEDITION";
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

  /** End the active expedition and bring the party home with what it carries. */
  record Extract() implements Command {
    @Override
    public String kind() {
      return "EXTRACT";
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
}
