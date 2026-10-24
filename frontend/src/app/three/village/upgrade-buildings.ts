/**
 * The Upgrades as buildings (architecture doc 5.2 and 5.6): each is bought at the
 * module it adds to the Village, the roof, the stockade, the watchtower, the obelisk,
 * the brazier, the silo and crates, the spears. An unbought Upgrade stands as its
 * footing, and is a building all the same: its hover and popover state the level,
 * the cost and the effect in words, and refuse in words. The subject id is the
 * Upgrade id, so the module, its footing and the list view's row are one thing.
 * Pure: no Angular, no three.js.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState, UpgradeOffer } from '../../interfaces/game-state';
import { defineBuilding, type BuildingDefinition } from './building-registry';
import { UPGRADE_MODULES } from './village-layout';

/** What each module is called, by the Upgrade whose module it is. */
const TITLES: Record<string, string> = {
  UNLOCK_HEALER: "The Field Medic's roof",
  UNLOCK_TANK: "The Ironclad's stockade",
  UNLOCK_ROGUE: "The Shadow's watchtower",
  UNLOCK_NECROMANCER: "The Necromancer's obelisk",
  UNLOCK_DRACO_METAMORPH: "The Draco-Metamorph's brazier",
  SUPPLY_LINES: 'The supply store',
  SHARPENED_ARMS: 'The spear rack',
};

export function offerOf(state: GameState, id: string): UpgradeOffer | undefined {
  return (state.availableUpgrades ?? []).find((u) => u.id === id);
}

/** A cost in words: "40 Materials", "5 Relics", or both. */
export function costText(cost: UpgradeOffer['nextCost']): string {
  const parts = [
    cost.MATERIALS ? `${Math.round(cost.MATERIALS)} Materials` : '',
    cost.RELICS ? `${Math.round(cost.RELICS)} Relics` : '',
  ].filter((t) => t);
  return parts.length ? parts.join(' and ') : 'nothing';
}

/** What the Vault holds, in the two goods an Upgrade costs. */
function vaultText(state: GameState): string {
  const w = state.resources;
  return `${Math.floor(w.MATERIALS)} Materials and ${Math.floor(w.RELICS)} Relics`;
}

/** What the Vault would hold after paying; null when it cannot pay. */
function shortOf(state: GameState, offer: UpgradeOffer): string | null {
  const w = state.resources;
  const missing: string[] = [];
  if ((offer.nextCost.MATERIALS ?? 0) > w.MATERIALS) missing.push('Materials');
  if ((offer.nextCost.RELICS ?? 0) > w.RELICS) missing.push('Relics');
  return missing.length ? missing.join(' and ') : null;
}

function upgradeBuilding(id: string): BuildingDefinition {
  const oneTime = id.startsWith('UNLOCK_');
  return defineBuilding({
    id,
    title: TITLES[id] ?? id,
    visible: (s: GameState) => offerOf(s, id) !== undefined,
    gauges: [
      {
        id: 'level',
        label: oneTime ? 'Bought' : 'Level',
        value: (s: GameState) => offerOf(s, id)?.level ?? 0,
        format: (v: number) => (oneTime ? (v > 0 ? 'yes' : 'no') : String(Math.floor(v))),
        fill: (s: GameState) => {
          const o = offerOf(s, id);
          if (!o) return 0;
          if (o.maxed) return 1;
          return Math.min(1, o.level / UPGRADE_MODULES[id].cap);
        },
        leaves: (s: GameState) => {
          const o = offerOf(s, id);
          if (!o) return null;
          if (o.maxed) return `${o.name}. ${o.description} Owned in full.`;
          return (
            `${o.name}. ${o.description} The next level costs ${costText(o.nextCost)}. ` +
            `The Vault holds ${vaultText(s)}.`
          );
        },
      },
    ],
    actions: [
      {
        id: 'buy-upgrade',
        label: (s: GameState) => {
          const o = offerOf(s, id);
          if (!o) return 'Upgrade';
          const verb = o.unlockUnit ? 'Recruit' : 'Upgrade';
          return o.maxed ? `${o.name}: owned` : `${o.name}: ${verb}, ${costText(o.nextCost)}`;
        },
        availability: (s: GameState) => {
          const o = offerOf(s, id);
          if (!o) return { usable: false, reason: 'Nothing is offered here.' };
          if (o.maxed) return { usable: false, reason: 'Owned. There is no further level.' };
          const missing = shortOf(s, o);
          return missing
            ? {
                usable: false,
                reason: `Costs ${costText(o.nextCost)}. The Vault holds ${vaultText(s)}, short in ${missing}.`,
              }
            : { usable: true, reason: null };
        },
        preview: (s: GameState) => {
          const o = offerOf(s, id);
          if (!o || o.maxed || shortOf(s, o)) return null;
          const left = {
            m: Math.floor(s.resources.MATERIALS - (o.nextCost.MATERIALS ?? 0)),
            r: Math.floor(s.resources.RELICS - (o.nextCost.RELICS ?? 0)),
          };
          return `${o.description} Costs ${costText(o.nextCost)} and leaves ${left.m} Materials and ${left.r} Relics in the Vault.`;
        },
        run: (commands) => commands.send(GameAction.UPGRADE, { upgradeId: id }),
      },
    ],
  });
}

/** One building per Upgrade that has a module, in the module table's order. */
export const UPGRADE_BUILDINGS: readonly BuildingDefinition[] =
  Object.keys(UPGRADE_MODULES).map(upgradeBuilding);
