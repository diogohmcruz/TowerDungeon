import { Component, inject } from '@angular/core';

import {
  EMPTY_VILLAGE,
  VillageInput,
} from '../../three/village/village-layout';
import type { GameState } from '../../interfaces/game-state';
import type { Member } from '../../interfaces/member';
import type { UnitStats } from '../../interfaces/unit.enum';
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
    game.update({
      village: { ...game.state().village, food, villagersCount: 20, productionRate: 7.5 },
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
      villageTierLevel: 2,
      villageTierName: 'Sheltered',
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
        villageTierLevel: 2,
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
        saltPansOpen: true,
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
        still: false,
      };
}
