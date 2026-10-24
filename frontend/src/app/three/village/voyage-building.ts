/**
 * The Port (architecture doc 5.2 and 5.6): the pier, and the Boat's hull when it is
 * docked. Its popover is the Voyage panel: the Boat's state in words, the cargo of
 * Relics to load in lots, what the cargo returns, the danger at sea now and next
 * Quarter as a gauge with the exact figure, and Send. Every refusal is written in
 * words. A lost cargo is one plain Ledger line, written by the backend and read at
 * the Scribe's Study. The same definition is a section of the list view, so nothing
 * here is pointer-only. Pure but for the cargo draft, a signal.
 */
import { signal } from '@angular/core';

import { GameAction } from '../../interfaces/game-action.enum';
import type {
  GameState,
  Quarter,
  VoyageGoods,
  VoyageView,
} from '../../interfaces/game-state';
import {
  defineBuilding,
  type ActionSpec,
  type LineSpec,
} from './building-registry';

export const PORT_ID = 'PORT';

/** The cargo being loaded: whole lots of Relics, and what the Boat should bring back. */
interface Cargo {
  lots: number;
  goods: VoyageGoods;
}

const cargo = signal<Cargo>({ lots: 0, goods: 'SALT' });

/** What is chosen now. Read inside a computed, it tracks the draft. */
export function cargoDraft(): Readonly<Cargo> {
  return cargo();
}

/** Puts the draft back to nothing loaded (a new Campaign, a test). */
export function resetCargo(): void {
  cargo.set({ lots: 0, goods: 'SALT' });
}

/** Loads a cargo outright, as the controls would (the capture fixture's `?load=`). */
export function loadCargo(lots: number, goods: VoyageGoods = 'SALT'): void {
  cargo.set({ lots: Math.max(0, Math.floor(lots)), goods });
}

const NO_VOYAGE: VoyageView = {
  state: 'UNAVAILABLE',
  relics: null,
  goods: null,
  ticksLeft: 0,
  ticksTotal: 0,
  riskTaken: null,
  seaRisk: null,
  nextSeaRisk: null,
  relicsPerLot: 5,
  saltPerLot: 10,
  sporesPerLot: 3,
  maxRelics: 50,
  riskReferenceRelics: 10,
  riskCap: 0.2,
};

function voyageOf(state: GameState): VoyageView {
  return state.voyage ?? NO_VOYAGE;
}

/** Time left in plain words; one lifecycle tick is one second. */
export function ticksInWords(ticks: number): string {
  if (ticks < 60) return 'less than a minute';
  const minutes = Math.ceil(ticks / 60);
  return minutes === 1 ? 'about a minute' : `about ${minutes} minutes`;
}

/** A chance as a share, one decimal when it has one: "4%", "2.5%". */
export function percent(share: number | null | undefined): string {
  const v = Number.isFinite(share) ? (share as number) : 0;
  return `${Math.round(v * 1000) / 10}%`;
}

function quarterName(q: Quarter | null | undefined): string {
  return q ? q.charAt(0) + q.slice(1).toLowerCase() : 'the next Quarter';
}

function goodsName(goods: VoyageGoods): string {
  return goods === 'SALT' ? 'Salt' : 'Spores';
}

/** How many lots the Vault could still load into one Voyage, whatever the draft. */
function lotsLimit(state: GameState): number {
  const v = voyageOf(state);
  const per = Math.max(1, v.relicsPerLot);
  const byCap = Math.floor(v.maxRelics / per);
  const byVault = Math.floor((state.resources?.RELICS ?? 0) / per);
  return Math.max(0, Math.min(byCap, byVault));
}

/** The lots loaded, never more than the Vault holds or the Boat carries. */
export function loadedLots(state: GameState): number {
  return Math.min(cargo().lots, lotsLimit(state));
}

export function loadedRelics(state: GameState): number {
  return loadedLots(state) * voyageOf(state).relicsPerLot;
}

/** What the cargo would return, in the goods chosen. */
export function expectedReturn(state: GameState, lots = loadedLots(state)): number {
  const v = voyageOf(state);
  return lots * (cargo().goods === 'SALT' ? v.saltPerLot : v.sporesPerLot);
}

/**
 * The chance the sea takes a cargo of `relics`: the Year's sea risk scaled by the
 * cargo's value over the reference, never above the cap (as the backend draws it).
 */
export function cargoRisk(state: GameState, relics: number): number {
  const v = voyageOf(state);
  const sea = v.seaRisk ?? state.seaRisk ?? 0;
  const scaled = (sea * relics) / Math.max(1, v.riskReferenceRelics);
  return Math.min(v.riskCap, Math.max(0, scaled));
}

function halfway(v: VoyageView): boolean {
  return v.ticksLeft * 2 <= v.ticksTotal;
}

/** The Boat's state in words: not built, due, docked, sailing out, or on its way home. */
export function boatWords(state: GameState): string {
  const port = state.port;
  if (!port || port.state === 'HIDDEN') return 'There is no Port.';
  if (port.state === 'FOOTING') return 'The Port is not finished. A footing and a short pier.';
  const v = voyageOf(state);
  if (v.state === 'SAILING') {
    const cargoWords = `${v.relics ?? 0} Relics, for ${goodsName(v.goods ?? 'SALT')}`;
    return halfway(v)
      ? `The Boat is on its way home with ${cargoWords}. It is due in ${ticksInWords(v.ticksLeft)}.`
      : `The Boat is at sea with ${cargoWords}. It is due back in ${ticksInWords(v.ticksLeft)}.`;
  }
  if (state.boat?.state === 'DOCKED') return 'The Boat lies at the pier, empty.';
  if (state.boat?.state === 'DUE') {
    return `The Boat has not come. It is due in ${ticksInWords(state.boat.ticksToDock)}.`;
  }
  return 'There is no Boat.';
}

/** Why the Boat cannot be sent now, in words; null when it can. */
export function sendRefusal(state: GameState): string | null {
  const port = state.port;
  if (!port || port.state !== 'BUILT') return 'The Port is not finished. No Boat can come.';
  if (state.session?.held === true) return 'Time is held. Nothing sails. Resume first.';
  if (state.gameOutcome !== 'PLAYING' || state.phase === 'OFFER') {
    return 'The Campaign has ended. Nothing sails.';
  }
  const v = voyageOf(state);
  if (v.state === 'SAILING') {
    return `The Boat is away. It returns in ${ticksInWords(v.ticksLeft)}.`;
  }
  if (state.boat?.state === 'DUE') {
    return `The Boat has not come. It is due in ${ticksInWords(state.boat.ticksToDock)}.`;
  }
  if (v.state !== 'READY') return 'There is no Boat at the pier.';
  if (loadedLots(state) <= 0) return 'Nothing is loaded. Load Relics first.';
  return null;
}

/** Why cargo cannot be changed now, in words; null when it can. */
function cargoRefusal(state: GameState): string | null {
  const port = state.port;
  if (!port || port.state !== 'BUILT') return 'The Port is not finished. No Boat can come.';
  const v = voyageOf(state);
  if (v.state === 'SAILING') return `The Boat is away. It returns in ${ticksInWords(v.ticksLeft)}.`;
  if (v.state !== 'READY') return 'There is no Boat at the pier to load.';
  return null;
}

const USABLE = { usable: true, reason: null } as const;
const refused = (reason: string) => ({ usable: false, reason });

function sendLabel(state: GameState): string {
  const lots = loadedLots(state);
  if (lots <= 0) return 'Send the Boat';
  return `Send the Boat with ${loadedRelics(state)} Relics, for ${goodsName(cargo().goods)}`;
}

function sendPreview(state: GameState): string | null {
  if (sendRefusal(state)) return null;
  const v = voyageOf(state);
  const relics = loadedRelics(state);
  return (
    `Takes ${relics} Relics from the Vault. In ${ticksInWords(v.ticksTotal)} the Boat returns ` +
    `${expectedReturn(state)} ${goodsName(cargo().goods)} to the Stockroom, ` +
    `unless the sea takes the cargo: ${percent(cargoRisk(state, relics))} chance, drawn once, now. ` +
    'The Boat itself is never lost.'
  );
}

function portActions(): ActionSpec[] {
  return [
    {
      id: 'finish-port',
      label: (s) =>
        s.port
          ? `Finish the Port, ${s.port.materialsCost} Materials and ${s.port.creditsCost} Credits`
          : 'Finish the Port',
      visible: (s) => s.port?.state === 'FOOTING',
      availability: (s) => {
        const p = s.port;
        if (!p || p.state !== 'FOOTING') return refused('The Port is already finished.');
        if (s.session?.held === true) return refused('Time is held. Nothing is built. Resume first.');
        const materials = s.resources?.MATERIALS ?? 0;
        if (materials < p.materialsCost || s.credit < p.creditsCost) {
          return refused(
            `The Vault cannot pay for the Port. It costs ${p.materialsCost} Materials and ` +
              `${p.creditsCost} Credits; the Vault holds ${Math.floor(materials)} Materials and ` +
              `${Math.floor(s.credit)} Credits.`,
          );
        }
        return USABLE;
      },
      preview: (s) =>
        s.port?.state === 'FOOTING'
          ? 'Spends the Materials and Credits from the Vault. The Boat docks some time after.'
          : null,
      run: (commands) => commands.send(GameAction.BUILD_PORT),
    },
  ];
}

const loadAction: ActionSpec = {
  id: 'load-cargo',
  label: (s) => `Load ${voyageOf(s).relicsPerLot} Relics`,
  visible: (s) => s.port?.state === 'BUILT',
  availability: (s) => {
    const why = cargoRefusal(s);
    if (why) return refused(why);
    const v = voyageOf(s);
    if (loadedRelics(s) + v.relicsPerLot > v.maxRelics) {
      return refused(`The Boat carries at most ${v.maxRelics} Relics.`);
    }
    if (loadedRelics(s) + v.relicsPerLot > (s.resources?.RELICS ?? 0)) {
      return refused(
        `The Vault holds ${Math.floor(s.resources?.RELICS ?? 0)} Relics. No more can be loaded.`,
      );
    }
    return USABLE;
  },
  preview: (s) => {
    const v = voyageOf(s);
    const next = loadedRelics(s) + v.relicsPerLot;
    return `The cargo would be ${next} Relics: ${percent(cargoRisk(s, next))} chance the sea takes it.`;
  },
  run: (_commands, s) =>
    cargo.update((c) => ({ ...c, lots: loadedLots(s) + 1 })),
};

const unloadAction: ActionSpec = {
  id: 'unload-cargo',
  label: (s) => `Unload ${voyageOf(s).relicsPerLot} Relics`,
  visible: (s) => s.port?.state === 'BUILT',
  availability: (s) => {
    const why = cargoRefusal(s);
    if (why) return refused(why);
    return loadedLots(s) > 0 ? USABLE : refused('Nothing is loaded.');
  },
  preview: (s) => {
    const left = loadedRelics(s) - voyageOf(s).relicsPerLot;
    return left > 0
      ? `The cargo would be ${left} Relics: ${percent(cargoRisk(s, left))} chance the sea takes it.`
      : 'The Boat would carry nothing.';
  },
  run: (_commands, s) =>
    cargo.update((c) => ({ ...c, lots: Math.max(0, loadedLots(s) - 1) })),
};

function goodsAction(goods: VoyageGoods): ActionSpec {
  const name = goodsName(goods);
  return {
    id: `bring-${goods.toLowerCase()}`,
    label: () => (cargo().goods === goods ? `Bring back ${name} (chosen)` : `Bring back ${name}`),
    visible: (s) => s.port?.state === 'BUILT',
    availability: (s) => {
      const why = cargoRefusal(s);
      if (why) return refused(why);
      return cargo().goods === goods ? refused(`${name} is already chosen.`) : USABLE;
    },
    preview: (s) => {
      const v = voyageOf(s);
      const per = goods === 'SALT' ? v.saltPerLot : v.sporesPerLot;
      return `${per} ${name} for each ${v.relicsPerLot} Relics.`;
    },
    run: () => cargo.update((c) => ({ ...c, goods })),
  };
}

const sendAction: ActionSpec = {
  id: 'send-voyage',
  label: sendLabel,
  visible: (s) => s.port?.state === 'BUILT',
  availability: (s) => {
    const why = sendRefusal(s);
    return why ? refused(why) : USABLE;
  },
  preview: sendPreview,
  run: (commands, s) => {
    const relics = loadedRelics(s);
    commands.send(GameAction.SEND_VOYAGE, { relics, goods: cargo().goods });
    // The Relics leave the Vault; the next cargo starts from nothing.
    cargo.update((c) => ({ ...c, lots: 0 }));
  },
};

function lines(s: GameState): readonly LineSpec[] {
  const v = voyageOf(s);
  const out: LineSpec[] = [{ id: 'boat', label: 'The Boat', value: boatWords(s) }];
  if (s.port?.state !== 'BUILT') return out;
  out.push({
    id: 'rates',
    label: 'Returns',
    value: `${v.saltPerLot} Salt, or ${v.sporesPerLot} Spores, for each ${v.relicsPerLot} Relics sent.`,
    detail: `It carries at most ${v.maxRelics} Relics, in lots of ${v.relicsPerLot}, and sails for ${ticksInWords(v.ticksTotal)}. Returns go into the Stockroom by themselves.`,
  });
  if (v.state === 'SAILING') {
    const goods = v.goods ?? 'SALT';
    const lots = (v.relics ?? 0) / Math.max(1, v.relicsPerLot);
    const per = goods === 'SALT' ? v.saltPerLot : v.sporesPerLot;
    out.push({
      id: 'at-sea',
      label: 'Cargo at sea',
      value: `${v.relics ?? 0} Relics, to return ${Math.round(lots * per)} ${goodsName(goods)}.`,
      detail:
        v.riskTaken === null
          ? undefined
          : `The sea's chance of taking it was ${percent(v.riskTaken)}, drawn once when the Boat sailed. A lost cargo is written in the Ledger.`,
    });
  } else if (v.state === 'READY') {
    const relics = loadedRelics(s);
    out.push({
      id: 'loaded',
      label: 'Loaded',
      value:
        relics > 0
          ? `${relics} Relics, for ${goodsName(cargo().goods)}: ${expectedReturn(s)} ${goodsName(cargo().goods)} on return.`
          : 'Nothing is loaded.',
    });
  }
  out.push({
    id: 'quarters',
    label: 'The sea',
    value:
      `Now, in ${quarterName(s.quarter)}: ${percent(v.seaRisk ?? s.seaRisk)}. ` +
      `In ${quarterName(s.nextQuarter)}: ${percent(v.nextSeaRisk ?? s.nextSeaRisk)}.`,
    detail:
      `The chance the sea takes a cargo is the danger at sea scaled by the cargo's value ` +
      `(Relics sent over ${v.riskReferenceRelics}), and never above ${percent(v.riskCap)}.`,
  });
  return out;
}

export const PORT = defineBuilding({
  id: PORT_ID,
  title: 'The Port',
  // A Trial has no Port; neither does a Campaign whose footing is not yet open.
  visible: (s) => !s.trial && !!s.port && s.port.state !== 'HIDDEN',
  gauges: [
    {
      id: 'sea-now',
      label: 'Danger at sea now',
      visible: (s) => s.port?.state === 'BUILT',
      value: (s) => voyageOf(s).seaRisk ?? s.seaRisk ?? 0,
      format: percent,
      fill: (s) => (voyageOf(s).seaRisk ?? s.seaRisk ?? 0) / Math.max(0.01, voyageOf(s).riskCap),
      leaves: (s) =>
        `In ${quarterName(s.quarter)}. The danger rises with the cargo's value, up to ${percent(voyageOf(s).riskCap)}.`,
    },
    {
      id: 'sea-next',
      label: 'Danger at sea next Quarter',
      visible: (s) => s.port?.state === 'BUILT',
      value: (s) => voyageOf(s).nextSeaRisk ?? s.nextSeaRisk ?? 0,
      format: percent,
      fill: (s) =>
        (voyageOf(s).nextSeaRisk ?? s.nextSeaRisk ?? 0) / Math.max(0.01, voyageOf(s).riskCap),
      leaves: (s) => `In ${quarterName(s.nextQuarter)}.`,
    },
    {
      id: 'cargo',
      label: 'Cargo',
      visible: (s) => s.port?.state === 'BUILT' && voyageOf(s).state !== 'SAILING',
      value: loadedRelics,
      figure: (s) => `${loadedRelics(s)} of ${voyageOf(s).maxRelics} Relics`,
      fill: (s) => loadedRelics(s) / Math.max(1, voyageOf(s).maxRelics),
      leaves: (s) =>
        `The Vault holds ${Math.floor(s.resources?.RELICS ?? 0)} Relics. ` +
        `Chance the sea takes this cargo: ${percent(cargoRisk(s, loadedRelics(s)))}.`,
    },
    {
      id: 'time',
      label: 'Voyage time left',
      visible: (s) => voyageOf(s).state === 'SAILING',
      value: (s) => voyageOf(s).ticksLeft,
      figure: (s) => ticksInWords(voyageOf(s).ticksLeft),
      fill: (s) => {
        const v = voyageOf(s);
        return 1 - v.ticksLeft / Math.max(1, v.ticksTotal);
      },
      leaves: (s) => `${voyageOf(s).ticksLeft} of ${voyageOf(s).ticksTotal} seconds remain, counted while connected.`,
    },
  ],
  lines,
  actions: [
    ...portActions(),
    loadAction,
    unloadAction,
    goodsAction('SALT'),
    goodsAction('SPORES'),
    sendAction,
  ],
});
