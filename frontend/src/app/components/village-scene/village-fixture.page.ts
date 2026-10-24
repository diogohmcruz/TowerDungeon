import { Component, inject } from '@angular/core';

import {
  EMPTY_VILLAGE,
  VillageInput,
} from '../../three/village/village-layout';
import type { GameState, RunSummary } from '../../interfaces/game-state';
import type { Member } from '../../interfaces/member';
import type { UnitStats } from '../../interfaces/unit.enum';
import type { SessionMeta } from '../../interfaces/game-frame';
import type { SuccessionNode } from '../../interfaces/succession';
import { loadCargo } from '../../three/village/voyage-building';
import { PopoverService } from '../../services/popover.service';
import { GameStateService } from '../../services/game-state.service';
import { BuildingPopover } from '../building-popover/building-popover';
import { ListView } from '../list-view/list-view';
import { ListViewService } from '../../services/list-view.service';
import { ZoomService } from '../../services/zoom.service';
import { closeupKey } from '../game-component/closeup-key';
import { VillageScene } from './village-scene.component';

/**
 * Dev tooling (route `/village-fixture`, dev mode only): the Village Elevation
 * without a backend. `?empty=1` shows a new Campaign instead of a busy Village.
 */
function portParam(): VillageInput['port'] {
  const v = new URLSearchParams(location.search).get('port');
  return v === 'footing' ? 'FOOTING' : v === 'built' ? 'BUILT' : 'HIDDEN';
}

function quarterParam(): VillageInput['quarter'] {
  const v = new URLSearchParams(location.search).get('quarter')?.toUpperCase();
  return v === 'SPRING' || v === 'SUMMER' || v === 'AUTUMN' || v === 'WINTER'
    ? v
    : null;
}

const FIXTURE_LEDGER = [
  { text: 'Emergency Tilling. Pantry below 20.', loss: false },
  { text: 'The Boat arrived. 2 Arrivals came ashore.', loss: false },
  { text: 'Starvation. 1 Breaker lost.', loss: true },
  { text: 'Quarry Heap full. Breaking stops until it is carried.', loss: false },
  { text: 'The Floor 10 Guardian was slain. The stair eases.', loss: false },
  { text: 'Wipe. They stay on the Floor.', loss: true },
  { text: 'The Study gained a Post.', loss: false },
  { text: 'The Boat sails with 10 Relics, for Salt.', loss: false },
  { text: 'The sea took the cargo of the Voyage sailed in Autumn. The Boat comes home empty.', loss: true },
];

function fixtureSummary(wiped: boolean): RunSummary {
  return {
    outcome: wiped ? 'WIPED' : 'EXTRACTED',
    startFloor: 0,
    highestFloor: 12,
    floorsCleared: 11,
    enemiesDefeated: 38,
    creditsGained: 1240,
    materialsGained: 86,
    relicsGained: wiped ? 2 : 1,
    unitsLost: wiped ? 3 : 1,
    survivors: wiped ? 0 : 5,
    fallen: wiped
      ? [
          { name: 'Tomas', type: 'ARCHER', floor: 12 },
          { name: 'Brann', type: 'TANK', floor: 12 },
          { name: 'Ilse', type: 'HEALER', floor: 11 },
        ]
      : [{ name: 'Tomas', type: 'ARCHER', floor: 9 }],
  };
}

/** `?fallen=N` names N Units on the Wall of Names (default 5); the last starved at home. */
function fixtureFallen(): NonNullable<GameState['wallOfNames']> {
  const n = Number(new URLSearchParams(location.search).get('fallen') ?? 5);
  const roster = [
    ['Aldric', 'WARRIOR'], ['Brisa', 'ARCHER'], ['Tomas', 'ARCHER'], ['Brann', 'TANK'],
    ['Ilse', 'HEALER'], ['Corwin', 'WARRIOR'], ['Maren', 'PORTER'],
  ];
  return Array.from({ length: Number.isFinite(n) ? Math.max(0, n) : 0 }, (_, i) => {
    const [name, type] = roster[i % roster.length];
    return { name, type, floor: i === n - 1 ? 0 : 3 + i * 2 };
  });
}

function fixtureReserves(): GameState['units'] {
  const member = (id: number, hurt: boolean) =>
    ({
      id,
      name: `Reserve ${id}`,
      currentHealth: hurt ? 4 : 10,
      maxHealth: 10,
    }) as unknown as Member;
  let id = 0;
  const list = (n: number, hurt: number) =>
    Array.from({ length: n }, (_, i) => member(id++, i < hurt));
  return {
    ...game0().units,
    WARRIOR: list(9, 2),
    ARCHER: list(6, 1),
    PORTER: list(3, 0),
  };
}

function game0(): GameState {
  return inject(GameStateService).state();
}

function fixtureCatalog(): Record<string, UnitStats> {
  const cost: Record<string, number> = {
    WARRIOR: 3, ARCHER: 5, PORTER: 2, HEALER: 12, ROGUE: 20, TANK: 25,
  };
  return Object.fromEntries(
    Object.entries(cost).map(([k, v]) => [k, { cost: v } as UnitStats]),
  );
}

/** `?tier=1|2|3|4` sets the Village Tier (default 2). */
function tierParam(): number {
  const v = Number(new URLSearchParams(location.search).get('tier') ?? 2);
  return Number.isFinite(v) ? Math.min(4, Math.max(1, Math.round(v))) : 2;
}

function fixtureReport(): NonNullable<GameState['pending']> {
  return {
    outcome: 'EXODUS',
    generation: 3,
    toll: {
      foodMarks: 12.4, materialsMarks: 8.2, creditsMarks: 3.5, relicsMarks: 1, expeditionsMarks: 6,
      ascentMarks: 18, guardianMarks: 3, marks: 52.1, toll: 52.1, newGround: 2, legacy: 9, residueNext: 2.3,
    },
    legacy: 9,
    pity: false,
    guardiansSlain: 1,
    highestFloor: 12,
    expeditions: 6,
  };
}

function fixtureSuccession(): SessionMeta {
  const node = (id: string, name: string, course: number, kind: string, cost: number, over: Partial<SuccessionNode> = {}): SuccessionNode => ({
    id, name, course, kind, cost, available: true, held: false, paid: 0, whyNot: null, refundable: false, ...over,
  });
  return {
    resumed: false,
    savedAt: null,
    schema: 1,
    notices: [],
    held: false,
    succession: {
      generation: 3,
      legacy: 9,
      spent: 3,
      courses: [
        { course: 1, gate: 0, spentBelow: 0, open: true },
        { course: 2, gate: 8, spentBelow: 3, open: false },
        { course: 3, gate: 20, spentBelow: 3, open: false },
        { course: 4, gate: 40, spentBelow: 3, open: false },
      ],
      inheritances: [
        node('SEED_GRAIN', 'Seed Grain', 1, 'POWER', 2),
        node('HARDY_STOCK', 'Hardy Stock', 1, 'POWER', 3, { held: true, paid: 3, refundable: true }),
        node('LONG_MEMORY', 'Long Memory', 1, 'REACH', 4),
        node('THE_DOWRY', 'The Dowry', 2, 'POWER', 5, { whyNot: 'COURSE_CLOSED' }),
        node('NARROW_STAIR', 'Narrow Stair', 3, 'REACH', 8, { available: false, whyNot: 'UNAVAILABLE' }),
        node('SECOND_PACT', 'Second Pact', 4, 'POWER', 13, { available: false, whyNot: 'UNAVAILABLE' }),
      ],
      choices: [
        { id: 'DOWRY_CREDITS', group: 'DOWRY', selected: false, isDefault: true },
        { id: 'DOWRY_MATERIALS', group: 'DOWRY', selected: false, isDefault: false },
        { id: 'DOWRY_RELICS', group: 'DOWRY', selected: false, isDefault: false },
        { id: 'HEIR_WARRIOR', group: 'HEIR', selected: false, isDefault: true },
        { id: 'HEIR_ARCHER', group: 'HEIR', selected: true, isDefault: false },
        { id: 'HEIR_PORTER', group: 'HEIR', selected: false, isDefault: false },
      ],
      chronicle: [
        { generation: 1, outcome: 'DEFEAT', toll: 14, legacy: 3, highestFloor: 5, guardiansSlain: 0, expeditions: 2, fallen: [] },
        { generation: 2, outcome: 'VICTORY', toll: 61.3, legacy: 11, highestFloor: 20, guardiansSlain: 2, expeditions: 7, fallen: [{ name: 'Aldric', type: 'WARRIOR' }, { name: 'Brisa', type: 'ARCHER' }] },
      ],
    },
  };
}

@Component({
  selector: 'app-village-fixture',
  imports: [VillageScene, BuildingPopover, ListView],
  host: { '(document:keydown)': 'onKey($event)' },
  template: `
    <app-village-scene [fixture]="state" />
    <app-building-popover />
    <app-list-view />
  `,
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 10;
      display: block;
    }
  `,
})
export class VillageFixturePage {
  private readonly zoom = inject(ZoomService);
  private readonly popoverService = inject(PopoverService);
  private readonly listView = inject(ListViewService);

  /** The game page's keys for the zoom ladder: C and Escape (#130). */
  protected onKey(event: KeyboardEvent): void {
    closeupKey(event, this.zoom, this.popoverService, this.listView);
  }

  constructor() {
    // `?closeup=1` starts in the Village closeup.
    if (new URLSearchParams(location.search).has('closeup')) {
      setTimeout(() => this.zoom.toggleVillageCloseup(), 1000);
    }
    // The popover and the list read the game's state, which the fixture has no backend to fill.
    const food = Number(new URLSearchParams(location.search).get('food') ?? 400);
    const game = inject(GameStateService);
    const toll = {
      foodMarks: 2,
      materialsMarks: 3,
      creditsMarks: 1.5,
      relicsMarks: 0.5,
      expeditionsMarks: 4,
      ascentMarks: 10,
      guardianMarks: 3,
      marks: 24,
      toll: 24,
      newGround: 2,
      legacy: 2,
      residueNext: 6,
    };
    const milestone = (id: string, name: string, achieved: boolean) => ({
      id,
      name,
      description: `${name}: the Tower remembers.`,
      trigger: `Reach ${name}`,
      achieved,
      companion: false,
    });
    // `?exodus=1` opens Exodus (the forecast reaches its minimum).
    const exodusOpen = new URLSearchParams(location.search).has('exodus');
    // `?open=FORT` opens that building's popover, for a capture with no pointer to click with.
    const query = new URLSearchParams(location.search);
    const open = query.get('open');
    if (open) {
      const popover = inject(PopoverService);
      setTimeout(() => popover.openFor({ kind: 'building', id: open }), 1500);
    }
    // `?port=footing|built`, `&boat=docked|due`, `&voyage=sailing|home` (at sea; on the way home),
    // `&load=2` (lots in the cargo), `&goods=spores`, `&held=1` (time held).
    const portState = portParam();
    const voyageMode = query.get('voyage');
    const seaNow = Number(query.get('risk') ?? 0.01);
    const boatState =
      query.get('boat') === 'docked' || voyageMode ? 'DOCKED' : query.get('boat') === 'due' ? 'DUE' : 'NOT_BUILT';
    if (query.has('load')) {
      loadCargo(Number(query.get('load')), query.get('goods') === 'spores' ? 'SPORES' : 'SALT');
    }
    if (query.has('held')) {
      game.setSession({ resumed: false, savedAt: null, schema: 1, notices: [], held: true });
    }
    const sailing = voyageMode === 'sailing' || voyageMode === 'home';
    // `?offer=1` shows the Campaign ended and waiting in OFFER: the Roll stone and the Succession popover.
    const waiting = new URLSearchParams(location.search).has('offer');
    if (waiting) {
      game.setSession(fixtureSuccession());
    }
    game.update({
      ...(waiting ? { phase: 'OFFER', gameOutcome: 'EXODUS', pending: fixtureReport(), raceRecords: [] } : {}),
      village: {
        ...game.state().village,
        food,
        villagersCount: 20,
        productionRate: 7.5,
        quarryHeap: 24,
        salt: 40,
        spores: tierParam() >= 3 ? 7 : 0,
      },
      resources: { ...game.state().resources, MATERIALS: 300, RELICS: query.has('relics') ? Number(query.get('relics')) : 23 },
      quarryHeapCap: 40,
      saltRate: 0.3,
      sporeRate: 0.1,
      stockroomCap: 500,
      heapDeliveryPerFloor: 4,
      netFoodPerSecond: 1.2,
      // The Gate's popover reads the Loadout: a Party of six with Salt, Reserves to carry the Pack.
      loadout: {
        ...game.state().loadout,
        carryingCapacity: 160,
        suppliesTaken: 100,
        pantryAfter: Math.max(0, food - 100),
        partySize: 6,
        pantryHoldSeconds: 90,
        saltUseful: 10,
        saltHeld: 4,
        saltPansOpen: true,
        trustHeld: 12,
        bearerTypes: ['TANK', 'WARRIOR'],
        // `?roll=1`: the Roll holds Overreach, the Quartermaster's Tally and Muster Roll.
        ...(query.has('roll')
          ? {
              overreach: {
                supplyDrainMultiplier: 1.3,
                starvationDamageMultiplier: 2,
                lootMultiplier: 1.5,
              },
              tally: {
                slots: 3,
                saved: [
                  { slot: 0, salt: 3, saltPack: 2, saltBearer: 'TANK', panacea: true, overreach: false },
                  { slot: 2, salt: 0, saltPack: 0, saltBearer: null, panacea: false, overreach: true },
                ],
                runwayTicks: 11,
                saltedRunwayTicks: 13,
                overreachRunwayTicks: 8,
              },
              musterRoll: {
                ready: true,
                startFloor: 5,
                partyUnits: 0,
                salt: 2,
                saltPack: 0,
                saltBearer: null,
                panacea: false,
                overreach: true,
              },
            }
          : {}),
      },
      // The Fort: some Reserves, a few wounded, Credits to hire with and the Types' costs.
      credit: Number(new URLSearchParams(location.search).get('credit') ?? 40),
      units: fixtureReserves(),
      unlockedUnitTypes: ['WARRIOR', 'ARCHER', 'PORTER', 'HEALER', 'ROGUE'],
      unitCatalog: fixtureCatalog(),
      // The Upgrades: bought at their modules (an unbought one stands as a footing), as the scene draws them.
      availableUpgrades: [
        ['UNLOCK_HEALER', 'Field Medic joins', 1, false, 'HEALER', 40],
        ['UNLOCK_TANK', 'Ironclad joins', 0, false, 'TANK', 85],
        ['UNLOCK_ROGUE', 'Shadow joins', 1, false, 'ROGUE', 110],
        ['UNLOCK_NECROMANCER', 'Necromancer joins', 0, false, 'NECROMANCER', 200],
        ['UNLOCK_DRACO_METAMORPH', 'Draco-Metamorph joins', 0, false, 'DRACO_METAMORPH', 400],
        ['SUPPLY_LINES', 'Supply Lines', 3, true, null, 150],
        ['SHARPENED_ARMS', 'Sharpened Arms', 0, true, null, 90],
      ].map(([id, name, level, repeatable, unlockUnit, cost]) => ({
        id: id as string,
        name: name as string,
        description: `${name}: the fixture's description.`,
        level: level as number,
        maxed: !repeatable && (level as number) > 0,
        repeatable: repeatable as boolean,
        unlockUnit: unlockUnit as string | null,
        nextCost: { MATERIALS: cost as number },
      })),
      tomeViews: [
        { tome: 'TILLERS_RECORD', title: "Tillers' Record", effect: 'Fields output', bonusPercent: 25, bonusFlat: 0, level: 1, cost: 90, gated: true, blocked: 'Tome 2 costs 90. Stockroom holds 80. Needs Highest Floor 8.' },
        { tome: 'SEAM_CHARTS', title: 'Seam Charts', effect: 'Breaker output', bonusPercent: 25, bonusFlat: 0, level: 0, cost: 60, gated: false, blocked: null },
      ],
      postUpgrades: [
        { trade: 'BREAKER', workplace: 'Quarry', level: 1, posts: 10, nextPosts: 15, cost: 40, blocked: null },
        { trade: 'SCRIBE', workplace: "Scribe's Study", level: 0, posts: 10, nextPosts: 15, cost: 250, blocked: 'Posts cost 250 Materials. The Vault holds 120.' },
      ],
      trustHeld: 30,
      ring: { level: 2, maxLevel: 5, cost: 23, maxed: false, speedBonus: 0.5, speedPerLevel: 0.25, capacityBonus: 10, capacityPerLevel: 5, blocked: null },
      // The Port, the Boat and the Voyage; the sea and the Quarter as the Village draws them.
      port: {
        state: portState,
        footingExpeditions: 12,
        expeditionsCompleted: 14,
        materialsCost: 600,
        creditsCost: 300,
      },
      boat: { state: boatState, dockTicks: 600, ticksToDock: boatState === 'DUE' ? 300 : 0 },
      quarter: quarterParam() ?? 'AUTUMN',
      nextQuarter: 'WINTER',
      seaRisk: seaNow,
      nextSeaRisk: 0.12,
      voyage: {
        state: sailing ? 'SAILING' : boatState === 'DOCKED' ? 'READY' : 'UNAVAILABLE',
        relics: sailing ? 10 : null,
        goods: sailing ? 'SALT' : null,
        ticksLeft: sailing ? (voyageMode === 'home' ? 50 : 120) : 0,
        ticksTotal: 150,
        riskTaken: sailing ? seaNow * 1 : null,
        seaRisk: seaNow,
        nextSeaRisk: 0.12,
        relicsPerLot: 5,
        saltPerLot: 10,
        sporesPerLot: 3,
        maxRelics: 50,
        riskReferenceRelics: 10,
        riskCap: 0.2,
      },
      // The Trades, so the Annexes and the Silo have something to offer.
      foodUpkeep: 8,
      pantryHoldSeconds: 99,
      quarryRate: 0.75,
      yieldBonus: 0.24,
      villageTierLevel: tierParam(),
      villageTierName: ['', 'Huddled', 'Sheltered', 'Fortified', 'Established'][tierParam()],
      tradeViews: [
        { trade: 'TILLER', plural: 'Tillers', workplace: 'Fields', hands: 16, posts: null, settling: 0, settlingSeconds: 0 },
        { trade: 'BREAKER', plural: 'Breakers', workplace: 'Quarry', hands: 3, posts: 10, settling: 1, settlingSeconds: 7 },
        { trade: 'SCRIBE', plural: 'Scribes', workplace: "Scribe's Study", hands: 1, posts: 10, settling: 0, settlingSeconds: 0 },
        { trade: 'SALTER', plural: 'Salters', workplace: 'Salt Pans', hands: 0, posts: 10, settling: 0, settlingSeconds: 0 },
        { trade: 'SPOREWARDEN', plural: 'Sporewardens', workplace: 'Spore Cellar', hands: 0, posts: 10, settling: 0, settlingSeconds: 0 },
      ],
      tradePreviews: (['BREAKER', 'SCRIBE', 'SALTER', 'SPOREWARDEN'] as const).flatMap((trade) => [
        { trade, delta: 1 as const, fields: 7.5, upkeep: 8, fieldsCoverUpkeep: false, pantryHoldSeconds: 99, blocked: null },
        { trade, delta: -1 as const, fields: 8, upkeep: 8, fieldsCoverUpkeep: true, pantryHoldSeconds: null, blocked: trade === 'BREAKER' ? 'Settling 7 s.' : null },
      ]),
      // The reference pillar: the Highest Floor, the Milestones and the Toll with Exodus.
      highestFloor: 37,
      milestones: [
        milestone('a', 'First Floor', true),
        milestone('b', 'Floor Ten', true),
        milestone('c', 'Floor Twenty', false),
        milestone('d', 'First Guardian', false),
      ],
      forecast: {
        toll,
        pointsDivisor: 9,
        nextLegacyAt: 27,
        exodusMinimum: exodusOpen ? 2 : 3,
        canExodus: exodusOpen,
      },
      // `?ledger=1` writes a Ledger (read at the Study); `?summary=wiped|extracted` a Summary (read at the Gate).
      wallOfNames: fixtureFallen(),
      ...(query.has('ledger') ? { ledger: FIXTURE_LEDGER } : {}),
      ...(query.has('summary')
        ? { lastRunSummary: fixtureSummary(query.get('summary') === 'wiped') }
        : {}),
    });
  }

  protected readonly state: VillageInput = new URLSearchParams(
    location.search,
  ).has('empty')
    ? EMPTY_VILLAGE
    : {
        ...EMPTY_VILLAGE,
        food: 400,
        reserves: 18,
        wounded: 3,
        hands: { TILLER: 14, BREAKER: 8, SCRIBE: 4, SALTER: 6, SPOREWARDEN: 0 },
        posts: { SCRIBE: 6 },
        quarryHeap: 24,
        quarryHeapCap: 40,
        materials: 300,
        relics: 3,
        credits: Number(new URLSearchParams(location.search).get('credit') ?? 40),
        villageTierLevel: tierParam(),
        sporeCellarOpen: tierParam() >= 3,
        spores: tierParam() >= 3 ? 7 : 0,
        pillar: {
          highestFloor: 37,
          milestonesEarned: 2,
          milestonesTotal: 4,
          tollShare: 0.67,
        },
        upgrades: {
          UNLOCK_HEALER: 1,
          UNLOCK_TANK: 0,
          UNLOCK_ROGUE: 1,
          UNLOCK_NECROMANCER: 0,
          UNLOCK_DRACO_METAMORPH: 0,
          SUPPLY_LINES: 3,
          SHARPENED_ARMS: 0,
        },
        fallenCount: Number(new URLSearchParams(location.search).get('fallen') ?? 5),
        ringLevel: 2,
        ringMax: 5,
        saltPansOpen: tierParam() >= 2,
        salt: 40,
        shortcuts: [
          { id: 'a', floor: 3 },
          { id: 'b', floor: 10 },
          { id: 'c', floor: 21 },
        ],
        // `?port=footing` or `?port=built`, with `&boat=docked` for a docked Boat (any other value, or none, is no hull).
        port: portParam(),
        // `?quarter=winter` (spring, summer, autumn, winter) and `&risk=0.2` for the sea.
        quarter: quarterParam(),
        seaRisk: Number(
          new URLSearchParams(location.search).get('risk') ?? 0.01,
        ),
        boatDocked:
          new URLSearchParams(location.search).get('boat') === 'docked' ||
          new URLSearchParams(location.search).has('voyage'),
        // `?bell=dark` draws the bell unlit; `?start=10` chooses the Shortcut at Floor 10.
        bellUsable: new URLSearchParams(location.search).get('bell') !== 'dark',
        startFloor: Number(new URLSearchParams(location.search).get('start') ?? 0),
        offer: new URLSearchParams(location.search).has('offer'),
        still: false,
      };
}
