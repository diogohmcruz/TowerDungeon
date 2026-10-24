import { Component, inject } from '@angular/core';

import {
  EMPTY_VILLAGE,
  VillageInput,
} from '../../three/village/village-layout';
import type { GameState } from '../../interfaces/game-state';
import type { Member } from '../../interfaces/member';
import type { UnitStats } from '../../interfaces/unit.enum';
import type { SessionMeta } from '../../interfaces/game-frame';
import type { SuccessionNode } from '../../interfaces/succession';
import { PopoverService } from '../../services/popover.service';
import { GameStateService } from '../../services/game-state.service';
import { BuildingPopover } from '../building-popover/building-popover';
import { ListView } from '../list-view/list-view';
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
  constructor() {
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
    const open = new URLSearchParams(location.search).get('open');
    if (open) {
      const popover = inject(PopoverService);
      setTimeout(() => popover.openFor({ kind: 'building', id: open }), 1500);
    }
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
      resources: { ...game.state().resources, MATERIALS: 300, RELICS: 3 },
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
      },
      // The Fort: some Reserves, a few wounded, Credits to hire with and the Types' costs.
      credit: Number(new URLSearchParams(location.search).get('credit') ?? 40),
      units: fixtureReserves(),
      unlockedUnitTypes: ['WARRIOR', 'ARCHER', 'PORTER', 'HEALER', 'ROGUE'],
      unitCatalog: fixtureCatalog(),
      availableUpgrades: [
        {
          id: 'UNLOCK_TANK',
          name: 'Recruit the Tank',
          description: '',
          level: 0,
          maxed: false,
          repeatable: false,
          unlockUnit: 'TANK',
          nextCost: { MATERIALS: 30 },
        },
      ],
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
          UNLOCK_ROGUE: 1,
          SUPPLY_LINES: 3,
          SHARPENED_ARMS: 2,
        },
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
          new URLSearchParams(location.search).get('boat') === 'docked',
        // `?bell=dark` draws the bell unlit; `?start=10` chooses the Shortcut at Floor 10.
        bellUsable: new URLSearchParams(location.search).get('bell') !== 'dark',
        startFloor: Number(new URLSearchParams(location.search).get('start') ?? 0),
        offer: new URLSearchParams(location.search).has('offer'),
        still: false,
      };
}
