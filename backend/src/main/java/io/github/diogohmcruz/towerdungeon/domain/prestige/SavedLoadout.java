package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * A Loadout the player saved under Quartermaster's Tally: the supplies and consumables chosen
 * before setting out, not the Party. Kept in the player's meta, so it outlives the Campaign.
 *
 * @param slot which of the saved places it is in, from 0
 * @param salt Salt to salt Supplies with
 * @param saltPack Salt of a Salt Pack against Chymus Insects
 * @param saltBearer the Unit type that carries the Pack ({@code UnitStats} name); null for none
 * @param panacea whether the Party carries a Panacea
 * @param overreach whether the Expedition is set to Overreach
 */
public record SavedLoadout(
    int slot, int salt, int saltPack, String saltBearer, boolean panacea, boolean overreach) {}
