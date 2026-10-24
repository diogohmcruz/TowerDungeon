import { Component } from '@angular/core';

import {
  EMPTY_VILLAGE,
  VillageInput,
} from '../../three/village/village-layout';
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

@Component({
  selector: 'app-village-fixture',
  imports: [VillageScene],
  template: `<app-village-scene [fixture]="state" />`,
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
  protected readonly state: VillageInput = new URLSearchParams(
    location.search,
  ).has('empty')
    ? EMPTY_VILLAGE
    : {
        ...EMPTY_VILLAGE,
        food: 400,
        reserves: 18,
        hands: { TILLER: 14, BREAKER: 8, SCRIBE: 4, SALTER: 6, SPOREWARDEN: 0 },
        posts: { SCRIBE: 6 },
        quarryHeap: 24,
        quarryHeapCap: 40,
        materials: 300,
        relics: 3,
        villageTierLevel: 2,
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
        still: false,
      };
}
