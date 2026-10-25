import * as THREE from 'three';

import {
  ANNEX_X,
  BASE_GAP,
  BELL_SPOT,
  buildVillage,
  EMPTY_VILLAGE,
  figureCounts,
  FRAME_WIDTH,
  growthLevels,
  INVISIBLE_UPGRADES,
  heapBlocks,
  LANES,
  litLamps,
  litSlits,
  MAX_RESERVE_FIGURES,
  MAX_VILLAGE_HEIGHT,
  MAX_VILLAGE_SHARE,
  MAX_VILLAGER_FIGURES,
  QUARTER_LOOK,
  SEA_REACH,
  seaUnrest,
  SHORE_X0,
  shoreX,
  SHORTCUT_MARK_ID,
  shortcutMarkHeight,
  siloFill,
  STAIR,
  UPGRADE_MODULES,
  VILLAGE_CAPACITY,
  VILLAGE_TONE,
  VillageInput,
  villageBounds,
  vaultCrates,
  Volume,
} from './village-layout';
import { applyStageView } from '../stage-view';
import { closeupFrame, fitVillageCamera, VILLAGE_FOV, villagePose } from './village-camera';
import { subjectOfVolume } from './village-subjects';

const BUSY: VillageInput = {
  ...EMPTY_VILLAGE,
  food: 900,
  reserves: 45,
  hands: { TILLER: 60, BREAKER: 30, SCRIBE: 12, SALTER: 20, SPOREWARDEN: 0 },
  posts: { SCRIBE: 12 },
  quarryHeap: 40,
  quarryHeapCap: 40,
  materials: 5000,
  relics: 30,
  villageTierLevel: 4,
  saltPansOpen: true,
  salt: 500,
  tick: 3,
  still: false,
};

const VILLAGE_DARK = 0.12;
const ids = (volumes: Volume[]) => volumes.map((v) => v.id);

describe('village layout', () => {
  describe('frame', () => {
    for (const [name, input] of [
      ['an empty village', EMPTY_VILLAGE],
      ['a busy village', BUSY],
    ] as const) {
      it(`keeps ${name} within 60% of the width and 2 units tall`, () => {
        const b = villageBounds(buildVillage(input));
        expect(b.maxX - b.minX).toBeLessThanOrEqual(
          MAX_VILLAGE_SHARE * FRAME_WIDTH,
        );
        expect(b.maxY - b.minY).toBeLessThan(MAX_VILLAGE_HEIGHT);
        // The outer 20% of the frame on each side stays empty.
        expect(b.minX).toBeGreaterThanOrEqual(-FRAME_WIDTH * 0.3);
        expect(b.maxX).toBeLessThanOrEqual(FRAME_WIDTH * 0.3);
        expect(b.minY).toBeGreaterThanOrEqual(0);
      });
    }

    it('stays within bounds as villagers pace', () => {
      for (let tick = 0; tick < 16; tick++) {
        const b = villageBounds(buildVillage({ ...BUSY, tick }));
        expect(b.maxX - b.minX).toBeLessThanOrEqual(
          MAX_VILLAGE_SHARE * FRAME_WIDTH,
        );
      }
    });

    it('floats the Base slab above a black gap, with the Tower rising behind', () => {
      const v = buildVillage(EMPTY_VILLAGE);
      const slab = v.find((x) => x.id === 'base-slab')!;
      expect(slab.y - slab.h / 2).toBeCloseTo(BASE_GAP, 5);
      // Smaller than the Tower: the Fort is lower than the slab's top.
      const fort = v.find((x) => x.id === 'fort-merlon-0')!;
      expect(fort.y + fort.h / 2).toBeLessThan(slab.y + slab.h / 2);
      // The top of the Tower is dithered away.
      const top = v.find((x) => x.id === 'tower-13')!;
      expect(top.alpha).toBeLessThan(0.2);
    });

    it('uses rectangular volumes only: every volume is a box with positive size', () => {
      for (const v of buildVillage(BUSY)) {
        expect(v.w).toBeGreaterThan(0);
        expect(v.h).toBeGreaterThan(0);
        expect(v.d).toBeGreaterThan(0);
      }
    });

    it('fits the budget: well under the instance capacity, unique ids', () => {
      const v = buildVillage(BUSY);
      expect(v.length).toBeLessThan(VILLAGE_CAPACITY);
      expect(new Set(ids(v)).size).toBe(v.length);
    });
  });

  describe('Annexes', () => {
    it('keeps the Annexes apart along x', () => {
      const ranges = Object.entries(ANNEX_X)
        // the Pans stand before the Quarry, the Cellar before the Fort; from Tier IV the Vault is
        // the Strongbox and the Reliquary, and the Heap building stands behind the Quarry
        .filter(([k]) => !['pans', 'cellar', 'strongbox', 'reliquary', 'stack'].includes(k))
        .map(([, r]) => r)
        .sort((a, b) => a[0] - b[0]);
      for (let i = 1; i < ranges.length; i++) {
        expect(ranges[i][0]).toBeGreaterThanOrEqual(ranges[i - 1][1]);
      }
    });

    it('builds the Fort, stair, Fields, silo, Quarry, Study and Vault Annex always', () => {
      const names = ids(buildVillage(EMPTY_VILLAGE));
      for (const id of [
        'fort-plinth',
        'stair-0',
        'field-0',
        'silo',
        'quarry-cut-0',
        'gantry-beam',
        'study-cell',
        'vault',
        'vault-door',
      ]) {
        expect(names).toContain(id);
      }
    });

    it('shows the Salt Pans as a footing until they open', () => {
      const locked = ids(buildVillage(EMPTY_VILLAGE));
      expect(locked).toContain('footing-n');
      expect(locked).not.toContain('pan-0');
      const open = ids(buildVillage({ ...EMPTY_VILLAGE, saltPansOpen: true }));
      expect(open).toContain('pan-0');
      expect(open).not.toContain('footing-n');
    });

    it('gives each Annex a shape from its Trade: stepped Fields, a gantry, a stilted cell', () => {
      const names = ids(buildVillage(EMPTY_VILLAGE));
      expect(names.filter((n) => n.startsWith('field-')).length).toBe(3);
      expect(names.filter((n) => n.startsWith('gantry-pier')).length).toBe(2);
      expect(names.filter((n) => n.startsWith('stilt-')).length).toBe(4);
    });

    it('reads data off the Annexes: silo fill, Vault crates, Heap blocks, Study slits', () => {
      expect(siloFill(0)).toBe(0);
      expect(siloFill(999)).toBeCloseTo(1, 5);
      expect(siloFill(1e9)).toBe(1);
      expect(vaultCrates(0, 0)).toBe(0);
      expect(vaultCrates(7, 0)).toBe(3);
      expect(vaultCrates(1e9, 1e9)).toBe(12);
      expect(heapBlocks(0, 40)).toBe(0);
      expect(heapBlocks(20, 40)).toBe(6);
      expect(heapBlocks(40, 40)).toBe(12);
      expect(heapBlocks(5, 0)).toBe(0);
      expect(litSlits(0, 12)).toBe(0);
      expect(litSlits(1, 12)).toBe(1);
      expect(litSlits(12, 12)).toBe(3);

      const v = buildVillage({ ...BUSY, villageTierLevel: 3 });
      expect(ids(v).filter((n) => n.startsWith('crate-')).length).toBe(12);
      expect(ids(v).filter((n) => /^heap-\d+$/.test(n)).length).toBe(12);
      const slits = v.filter((x) => x.id.startsWith('slit-'));
      expect(slits.filter((s) => s.tone === 1).length).toBe(3);
      const empty = buildVillage(EMPTY_VILLAGE);
      expect(empty.find((x) => x.id === 'silo-fill')).toBeUndefined();
      expect(empty.filter((x) => x.id.startsWith('crate-')).length).toBe(0);
    });

    it('lights one lamp per Village Tier along the stair, at most four', () => {
      expect(litLamps(0)).toBe(0);
      expect(litLamps(2)).toBe(2);
      expect(litLamps(9)).toBe(4);
      const lit = (tier: number) =>
        buildVillage({ ...EMPTY_VILLAGE, villageTierLevel: tier }).filter(
          (x) => /^lamp-\d$/.test(x.id) && x.tone > 0.5,
        ).length;
      expect(lit(0)).toBe(0);
      expect(lit(1)).toBe(1);
      expect(lit(3)).toBe(3);
      expect(lit(4)).toBe(4);
    });

    it('flickers a lit lamp by at most 8% and freezes it when still', () => {
      const tones = new Set<number>();
      for (let f = 0; f < 40; f++) {
        const lamp = buildVillage({
          ...EMPTY_VILLAGE,
          villageTierLevel: 1,
          still: false,
          flicker: f * 0.25,
        }).find((x) => x.id === 'lamp-0')!;
        expect(lamp.tone).toBeLessThanOrEqual(1);
        expect(lamp.tone).toBeGreaterThanOrEqual(0.92);
        tones.add(lamp.tone);
      }
      expect(tones.size).toBeGreaterThan(1);
      const still = new Set(
        [0, 1, 2, 3].map(
          (f) =>
            buildVillage({
              ...EMPTY_VILLAGE,
              villageTierLevel: 1,
              still: true,
              flicker: f,
            }).find((x) => x.id === 'lamp-0')!.tone,
        ),
      );
      expect(still.size).toBe(1);
    });
  });

  describe('villagers and Reserves', () => {
    it('shows one figure per two Villagers, capped at 40 in all', () => {
      expect(
        figureCounts({
          TILLER: 5,
          BREAKER: 2,
          SCRIBE: 0,
          SALTER: 1,
          SPOREWARDEN: 0,
        }),
      ).toEqual({
        TILLER: 3,
        BREAKER: 1,
        SCRIBE: 0,
        SALTER: 1,
        SPOREWARDEN: 0,
      });
      const capped = figureCounts({
        TILLER: 400,
        BREAKER: 100,
        SCRIBE: 40,
        SALTER: 40,
        SPOREWARDEN: 0,
      });
      const total = Object.values(capped).reduce((a, b) => a + b, 0);
      expect(total).toBe(MAX_VILLAGER_FIGURES);
    });

    it('draws no more than 40 villager figures and 30 Reserves', () => {
      const v = buildVillage({
        ...BUSY,
        reserves: 500,
        hands: {
          TILLER: 900,
          BREAKER: 90,
          SCRIBE: 90,
          SALTER: 90,
          SPOREWARDEN: 0,
        },
      });
      expect(v.filter((x) => /^villager-[A-Z]+-\d+$/.test(x.id)).length).toBe(
        MAX_VILLAGER_FIGURES,
      );
      expect(v.filter((x) => x.id.startsWith('reserve-')).length).toBe(
        MAX_RESERVE_FIGURES,
      );
    });

    it('walks each Trade in its own lane, in single file, within its lane', () => {
      const v = buildVillage(BUSY);
      const homes = buildVillage({ ...BUSY, still: true });
      for (const trade of ['TILLER', 'BREAKER', 'SCRIBE', 'SALTER'] as const) {
        const lane = LANES[trade];
        const walkers = v.filter((x) =>
          new RegExp(`^villager-${trade}-\\d+$`).test(x.id),
        );
        expect(walkers.length).toBeGreaterThan(0);
        for (const w of walkers) {
          expect(w.z).toBe(lane.z);
          expect(w.x).toBeGreaterThanOrEqual(lane.x0 - 0.1);
          expect(w.x).toBeLessThanOrEqual(lane.x1 + 0.1);
        }
        const xs = homes
          .filter((x) => new RegExp(`^villager-${trade}-\\d+$`).test(x.id))
          .map((w) => w.x)
          .sort((a, b) => a - b);
        for (let i = 1; i < xs.length; i++) {
          expect(xs[i] - xs[i - 1]).toBeGreaterThan(0.04);
        }
      }
    });

    it('moves by single virtual pixels and not at all when reduced motion is on', () => {
      const x = (tick: number, still: boolean) =>
        buildVillage({ ...BUSY, tick, still }).find(
          (v) => v.id === 'villager-TILLER-0',
        )!.x;
      expect(Math.abs(x(1, false) - x(0, false))).toBeLessThan(0.06);
      expect(x(1, false)).not.toBe(x(0, false));
      expect(x(1, true)).toBe(x(0, true));
    });

    it('shows the Trade by what is carried, never by tone', () => {
      const v = buildVillage(BUSY);
      const body = (id: string) => v.find((x) => x.id === id)!;
      for (const id of [
        'villager-TILLER-0',
        'villager-BREAKER-0',
        'villager-SCRIBE-0',
        'villager-SALTER-0',
      ]) {
        expect(body(id).tone).toBe(body('villager-TILLER-0').tone);
        expect(ids(v)).toContain(`${id}-load`);
      }
    });
  });

  describe('Shortcut marks', () => {
    const marks = (v: Volume[]) =>
      v.filter((x) => x.id.startsWith(SHORTCUT_MARK_ID));
    const openings = (v: Volume[]) =>
      marks(v).filter((x) => x.id.endsWith(':opening'));

    it('draws nothing when no Shortcut is open', () => {
      expect(marks(buildVillage(EMPTY_VILLAGE))).toEqual([]);
    });

    it('draws one lit doorway per open Shortcut at its Floor relative height', () => {
      const v = openings(
        buildVillage({
          ...EMPTY_VILLAGE,
          shortcuts: [
            { id: 'a', floor: 3 },
            { id: 'b', floor: 21 },
            { id: 'c', floor: 50 },
          ],
        }),
      );
      expect(v.length).toBe(3);
      const ys = v.map((x) => x.y);
      expect(ys).toEqual([...ys].sort((p, q) => p - q));
      expect(new Set(ys).size).toBe(3);
      for (const m of v) {
        expect(m.tone).toBe(1);
        expect(m.group).toBe('tower');
      }
      expect(shortcutMarkHeight(50)).toBeGreaterThan(shortcutMarkHeight(21));
    });

    it('leaves the Village bounds and every other volume untouched', () => {
      const base = buildVillage(BUSY);
      const withMarks = buildVillage({
        ...BUSY,
        shortcuts: [{ id: 'a', floor: 10 }],
      });
      expect(villageBounds(withMarks)).toEqual(villageBounds(base));
      expect(
        ids(withMarks).filter((i) => !i.startsWith(SHORTCUT_MARK_ID)),
      ).toEqual(ids(base));
    });

    it('adds a mark for any open Shortcut, such as the Narrow Stair on Floor 21', () => {
      const v = marks(
        buildVillage({
          ...EMPTY_VILLAGE,
          shortcuts: [{ id: 'NARROW_STAIR', floor: 21 }],
        }),
      );
      expect(new Set(marks(v).map((x) => x.id.split(':')[0]))).toEqual(
        new Set([`${SHORTCUT_MARK_ID}NARROW_STAIR`]),
      );
    });

    it('draws each mark as a doorway: opening, jambs, lintel, ledge and a stair stub', () => {
      const v = buildVillage({
        ...EMPTY_VILLAGE,
        shortcuts: [{ id: 'a', floor: 10 }],
      });
      const parts = marks(v).map((x) => x.id.split(':')[1]);
      expect(parts.sort()).toEqual(
        ['jamb-e', 'jamb-w', 'ledge', 'lintel', 'opening', 'step'].sort(),
      );
      const part = (n: string) =>
        v.find((x) => x.id === `${SHORTCUT_MARK_ID}a:${n}`)!;
      const opening = part('opening');
      expect(opening.tone).toBe(1);
      // At pixel scale 2 a virtual pixel is 0.05 world units: at least 5 by 8 pixels.
      expect(opening.w).toBeGreaterThanOrEqual(0.25);
      expect(opening.h).toBeGreaterThanOrEqual(0.4);
      expect(part('lintel').y).toBeGreaterThan(opening.y);
      expect(part('lintel').w).toBeGreaterThan(opening.w);
      expect(part('ledge').y).toBeLessThan(opening.y);
      expect(part('step').y).toBeLessThan(part('ledge').y);
      expect(marks(v).every((x) => x.w >= 0.05 && x.h >= 0.05)).toBeTrue();
    });

    it('draws nothing for a Shortcut that is not passed in (locked)', () => {
      const v = buildVillage({ ...BUSY, shortcuts: [] });
      expect(marks(v)).toEqual([]);
    });

    it('stays on the Tower face, clear of the Port, the Upgrade modules and the Annexes', () => {
      const floors = [1, 3, 10, 21, 50, 100];
      const all = buildVillage({
        ...BUSY,
        port: 'BUILT',
        boatDocked: true,
        upgrades: Object.fromEntries(
          Object.entries(UPGRADE_MODULES).map(([id, m]) => [id, m.cap]),
        ),
        shortcuts: floors.map((f) => ({ id: `f${f}`, floor: f })),
      });
      const doors = marks(all);
      const others = all.filter(
        (x) => x.group !== 'tower' && !x.id.startsWith('outline-'),
      );
      const tower = all.find((x) => x.id === 'tower-0')!;
      for (const m of doors) {
        expect(m.x - m.w / 2).toBeGreaterThanOrEqual(tower.x - tower.w / 2);
        expect(m.x + m.w / 2).toBeLessThanOrEqual(tower.x + tower.w / 2);
        expect(m.x + m.w / 2).toBeLessThan(SHORE_X0);
        for (const o of others) {
          const apartX = Math.abs(m.x - o.x) >= (m.w + o.w) / 2;
          const apartY = Math.abs(m.y - o.y) >= (m.h + o.h) / 2;
          expect(apartX || apartY).withContext(`${m.id} vs ${o.id}`).toBeTrue();
        }
      }
    });

    it('outlines the whole doorway when hovered, and only that Shortcut', () => {
      const v = buildVillage({
        ...EMPTY_VILLAGE,
        shortcuts: [
          { id: 'a', floor: 3 },
          { id: 'b', floor: 30 },
        ],
        hovered: 'b',
      });
      const outline = v.filter((x) => x.id.startsWith('outline-b:hover:shortcut'));
      expect(outline.length).toBeGreaterThanOrEqual(12);
      expect(v.some((x) => x.id.startsWith('outline-a'))).toBeFalse();
      const step = v.find((x) => x.id === `${SHORTCUT_MARK_ID}b:step`)!;
      const lintel = v.find((x) => x.id === `${SHORTCUT_MARK_ID}b:lintel`)!;
      const top = Math.max(...outline.map((x) => x.y + x.h / 2));
      const bottom = Math.min(...outline.map((x) => x.y - x.h / 2));
      expect(top).toBeGreaterThanOrEqual(lintel.y + lintel.h / 2);
      expect(bottom).toBeLessThanOrEqual(step.y - step.h / 2);
    });
  });

  describe('camera', () => {
    it('frames the same width at any aspect and looks up about 15 degrees', () => {
      for (const aspect of [0.49, 0.8, 1.6]) {
        const pose = villagePose(aspect);
        const cam = new THREE.PerspectiveCamera(VILLAGE_FOV, aspect);
        cam.position.set(...pose.position);
        cam.lookAt(...pose.target);
        cam.updateMatrixWorld();
        cam.updateProjectionMatrix();
        // Width visible at the Village's depth equals FRAME_WIDTH.
        const visible =
          2 *
          pose.distance *
          Math.tan(THREE.MathUtils.degToRad(VILLAGE_FOV / 2)) *
          aspect;
        expect(visible).toBeCloseTo(FRAME_WIDTH, 5);
        const dir = cam.getWorldDirection(new THREE.Vector3());
        expect(THREE.MathUtils.radToDeg(Math.asin(dir.y))).toBeCloseTo(15, 1);
        // The ground at the Village's depth sits inside the frame.
        const ground = new THREE.Vector3(0, 0, 1).project(cam);
        expect(ground.y).toBeGreaterThan(-1);
        expect(ground.y).toBeLessThan(-0.5);
        // The Village's top stays in the lower 40% of the frame.
        const top = new THREE.Vector3(0, MAX_VILLAGE_HEIGHT, 1).project(cam);
        expect(top.y).toBeLessThan(-0.2);
      }
    });
  });

  describe('growth', () => {
    /** The backend's UpgradeType names (the catalogue). */
    const CATALOGUE = [
      'UNLOCK_HEALER',
      'UNLOCK_TANK',
      'UNLOCK_ROGUE',
      'UNLOCK_NECROMANCER',
      'UNLOCK_DRACO_METAMORPH',
      'SUPPLY_LINES',
      'SHARPENED_ARMS',
    ];
    const modules = (volumes: Volume[]) =>
      new Set(
        volumes
          .filter((v) => v.id.startsWith('grow:'))
          .map((v) => v.id.split(':').slice(0, 3).join(':')),
      );
    const full = Object.fromEntries(
      Object.entries(UPGRADE_MODULES).map(([id, m]) => [id, m.cap]),
    );

    it('has a module entry, or an explicit invisible mark, for every Upgrade', () => {
      for (const id of CATALOGUE) {
        expect(id in UPGRADE_MODULES || INVISIBLE_UPGRADES.includes(id)).toBe(true);
      }
      expect(Object.keys(UPGRADE_MODULES).every((id) => CATALOGUE.includes(id))).toBe(true);
    });

    it('matches today\'s picture with no Upgrades', () => {
      const base = buildVillage(EMPTY_VILLAGE);
      expect(ids(base).some((i) => i.startsWith('grow:'))).toBe(false);
      // An Upgrade not offered, or unknown, shows nothing.
      expect(buildVillage({ ...EMPTY_VILLAGE, upgrades: { NOPE: 3 } })).toEqual(base);
      // One offered and not bought shows only its footing and its pad, never a module.
      const offered = buildVillage({ ...EMPTY_VILLAGE, upgrades: { SUPPLY_LINES: 0 } });
      expect(ids(offered).some((i) => i.startsWith('grow:'))).toBe(false);
      expect(ids(offered).filter((i) => !ids(base).includes(i)).sort()).toEqual([
        'upgrade-footing:SUPPLY_LINES:e',
        'upgrade-footing:SUPPLY_LINES:n',
        'upgrade-footing:SUPPLY_LINES:s',
        'upgrade-footing:SUPPLY_LINES:w',
        'upgrade-hit:SUPPLY_LINES',
      ]);
      expect(buildVillage({ ...EMPTY_VILLAGE, upgrades: { SHARPENED_ARMS: -1 } })).toEqual(
        buildVillage({ ...EMPTY_VILLAGE, upgrades: { SHARPENED_ARMS: 0 } }),
      );
    });

    it('adds exactly one module per level bought, up to the cap', () => {
      for (const [id, m] of Object.entries(UPGRADE_MODULES)) {
        let before = buildVillage({ ...BUSY, upgrades: { [id]: 0 } });
        for (let level = 1; level <= m.cap + 2; level++) {
          const after = buildVillage({ ...BUSY, upgrades: { [id]: level } });
          const added = modules(after).size - modules(before).size;
          expect(added).toBe(level <= m.cap ? 1 : 0);
          // Everything else in the picture is untouched.
          const rest = (vs: Volume[]) =>
            vs.filter((v) => !/^(grow:|upgrade-)/.test(v.id));
          expect(rest(after)).toEqual(rest(before));
          before = after;
        }
      }
    });

    it('is a pure, deterministic function of the state', () => {
      const a = buildVillage({ ...BUSY, upgrades: full });
      const b = buildVillage({
        ...BUSY,
        upgrades: Object.fromEntries(Object.entries(full).reverse()),
      });
      expect(b).toEqual(a);
      expect(buildVillage({ ...BUSY, upgrades: full })).toEqual(a);
      expect(growthLevels('SUPPLY_LINES', 99)).toBe(UPGRADE_MODULES['SUPPLY_LINES'].cap);
      expect(growthLevels('SUPPLY_LINES', 2.9)).toBe(2);
    });

    for (const [name, base] of [
      ['an empty village', EMPTY_VILLAGE],
      ['a busy village', BUSY],
    ] as const) {
      it(`keeps ${name} with every Upgrade inside the bounds`, () => {
        const b = villageBounds(buildVillage({ ...base, upgrades: full }));
        expect(b.maxX - b.minX).toBeLessThanOrEqual(MAX_VILLAGE_SHARE * FRAME_WIDTH);
        expect(b.maxY - b.minY).toBeLessThan(MAX_VILLAGE_HEIGHT);
        expect(b.minX).toBeGreaterThanOrEqual(-FRAME_WIDTH * 0.3);
        expect(b.maxX).toBeLessThanOrEqual(FRAME_WIDTH * 0.3);
        expect(b.minY).toBeGreaterThanOrEqual(0);
        // Growth never makes the Village wider or taller than it already is.
        const plain = villageBounds(buildVillage(base));
        expect(b.maxY).toBeLessThanOrEqual(Math.max(plain.maxY, 1.8) + 1e-9);
      });
    }

    it('leaves Village Tier lamps and the Pans footing alone', () => {
      const plain = buildVillage({ ...BUSY, saltPansOpen: false });
      const grown = buildVillage({ ...BUSY, saltPansOpen: false, upgrades: full });
      for (const id of ids(plain).filter((i) => /^(lamp|footing)/.test(i))) {
        expect(grown.find((v) => v.id === id)).toEqual(plain.find((v) => v.id === id));
      }
    });

    it('stays within the renderer capacity', () => {
      expect(buildVillage({ ...BUSY, upgrades: full }).length).toBeLessThanOrEqual(VILLAGE_CAPACITY);
    });
  });

  describe('Port', () => {
    const shore = (v: Volume[]) => v.filter((x) => x.group === 'port');
    const hasSea = (v: Volume[]) => v.some((x) => x.id.startsWith('sea-'));
    const FOOTING = { ...BUSY, port: 'FOOTING' as const };
    const BUILT = { ...BUSY, port: 'BUILT' as const };

    it('draws no sea and nothing of the Port before the footing opens', () => {
      const v = buildVillage(BUSY);

      expect(shore(v)).toEqual([]);
      expect(hasSea(v)).toBe(false);
      expect(buildVillage(EMPTY_VILLAGE)).toEqual(
        buildVillage({ ...EMPTY_VILLAGE, port: 'HIDDEN', boatDocked: true }),
      );
    });

    it('shows a hairline footing, a short pier and a strip of sea at the footing', () => {
      const v = buildVillage(FOOTING);

      expect(ids(v)).toContain('port-footing-n');
      expect(ids(v)).toContain('port-pier-deck');
      expect(hasSea(v)).toBe(true);
      expect(ids(v)).not.toContain('port-pier-bollard');
      const hair = v.find((x) => x.id === 'port-footing-n')!;
      expect(hair.h).toBeLessThan(0.02);
    });

    it('shows the full pier once finished, longer than the footing pier', () => {
      const deck = (input: VillageInput) =>
        buildVillage(input).find((x) => x.id === 'port-pier-deck')!;

      expect(deck(BUILT).w).toBeGreaterThan(deck(FOOTING).w);
      expect(ids(buildVillage(BUILT))).toContain('port-pier-bollard');
    });

    describe('the sea (#147)', () => {
      const seaOf = (v: Volume[]) => v.filter((x) => x.group === 'sea');

      it('is scenery of its own group, drawn only once the Port is there', () => {
        expect(seaOf(buildVillage(BUSY))).toEqual([]);
        for (const input of [FOOTING, BUILT]) {
          const sea = seaOf(buildVillage(input));
          expect(sea.length).toBeGreaterThan(10);
          expect(sea.every((x) => x.id.startsWith('sea-'))).toBeTrue();
        }
      });

      it('runs to the right edge of the screen, far past the frame', () => {
        expect(SEA_REACH).toBeGreaterThan(FRAME_WIDTH * 2);
        for (const input of [FOOTING, BUILT]) {
          const reach = Math.max(...seaOf(buildVillage(input)).map((x) => x.x + x.w / 2));
          expect(reach).toBeGreaterThanOrEqual(SEA_REACH - 1e-9);
        }
      });

      it('recedes to a horizon: the water runs back to the far distance and ends in a light line', () => {
        const sea = seaOf(buildVillage(BUILT));
        expect(Math.min(...sea.map((x) => x.z - x.d / 2))).toBeLessThanOrEqual(-40);
        const horizon = sea.find((x) => x.id === 'sea-horizon')!;
        expect(horizon).toBeDefined();
        expect(horizon.w).toBeGreaterThan(FRAME_WIDTH);
        expect(horizon.tone).toBeGreaterThan(0.4);
      });

      it('keeps the Village bounds clear of the water: the water starts at the shore line of its depth', () => {
        const v = buildVillage({ ...BUILT, boatDocked: true });
        expect(villageBounds(v).maxX).toBeLessThanOrEqual(SHORE_X0);
        const water = seaOf(v).filter((x) => x.id.startsWith('sea-water-'));
        expect(water.length).toBeGreaterThan(5);
        for (const x of water) {
          expect(x.x - x.w / 2).toBeGreaterThanOrEqual(shoreX(x.z) - 1e-9);
        }
        // The ground stops where the shore starts, so no land lies under the water.
        const ground = v.find((x) => x.id === 'ground')!;
        expect(ground.x + ground.w / 2).toBeLessThanOrEqual(SHORE_X0 + 1e-9);
        // Landlocked, the ground runs the full width.
        const dry = buildVillage(BUSY).find((x) => x.id === 'ground')!;
        expect(dry.x + dry.w / 2).toBeGreaterThanOrEqual(FRAME_WIDTH);
      });

      it('has a curved shore, and the pier meets it', () => {
        const edges = [-1.5, 0, 0.9, 2, 3.5].map(shoreX);
        expect(new Set(edges.map((e) => e.toFixed(3))).size).toBeGreaterThan(3);
        expect(Math.min(...edges)).toBeGreaterThanOrEqual(SHORE_X0);
        const v = buildVillage(BUILT);
        const deck = v.find((x) => x.id === 'port-pier-deck')!;
        expect(Math.abs(deck.x - deck.w / 2 - shoreX(deck.z))).toBeLessThan(0.1);
        expect(seaOf(v).filter((x) => x.id.startsWith('sea-shore-')).length).toBeGreaterThan(3);
      });

      it('is left out of the closeup frame, which fits the built Village and the Port alone', () => {
        const v = buildVillage({ ...BUILT, boatDocked: true });
        expect(closeupFrame(v)).toEqual(closeupFrame(v.filter((x) => x.group !== 'sea')));
        const frame = closeupFrame(v);
        expect(frame.centerX + frame.span / 2).toBeLessThan(FRAME_WIDTH);
      });

      it('drifts its dashes slowly with the clock and holds them still under reduced motion', () => {
        const dashes = (flicker: number, still: boolean) =>
          seaOf(buildVillage({ ...BUILT, flicker, still }))
            .filter((x) => x.id.startsWith('sea-band-'))
            .map((x) => x.x);
        const a = dashes(0, false);
        const b = dashes(12, false);
        expect(a.length).toBeGreaterThan(20);
        expect(b).not.toEqual(a);
        // Slow: over a second the dashes move by well under 0.08 units.
        const next = dashes(1, false);
        a.forEach((x, i) => expect(Math.abs(next[i] - x)).toBeLessThan(0.08));
        a.forEach((x, i) => expect(Math.abs(b[i] - x)).toBeLessThan(1.5));
        expect(dashes(12, true)).toEqual(a);
        expect(dashes(0, true)).toEqual(a);
      });

      it('keeps the Port hit pad over the pier and the Boat', () => {
        const hit = buildVillage({ ...BUILT, boatDocked: true }).find((x) => x.id === 'port-hit')!;
        expect(hit.alpha).toBe(0);
        expect(hit.x - hit.w / 2).toBeGreaterThanOrEqual(SHORE_X0 - 1e-9);
        expect(hit.x + hit.w / 2).toBeLessThanOrEqual(FRAME_WIDTH / 2);
        const boat = buildVillage({ ...BUILT, boatDocked: true }).find((x) => x.id === 'port-boat-hull')!;
        // The pad (a near-miss margin over the pier and the water beside it) reaches the Boat's depth.
        expect(boat.z - boat.d / 2).toBeLessThan(hit.z + hit.d / 2);
        expect(boat.z + boat.d / 2).toBeGreaterThan(hit.z - hit.d / 2);
      });
    });

    it('draws a larger Boat with two masts and, lit, one window', () => {
      const v = buildVillage({ ...BUILT, boatDocked: true });
      const hull = v.find((x) => x.id === 'port-boat-hull')!;
      expect(hull.w).toBeGreaterThanOrEqual(1.7);
      const masts = v.filter((x) => x.id.startsWith('port-boat-mast'));
      expect(masts.length).toBe(2);
      for (const m of masts) expect(m.h).toBeGreaterThan(hull.h * 3);
      const windows = v.filter((x) => x.id.startsWith('port-boat-window'));
      expect(windows.length).toBe(1);
      expect(windows[0].tone).toBe(1);
      for (const x of v.filter((q) => q.id.startsWith('port-boat'))) {
        expect(x.group).toBe('port');
        expect(x.x + x.w / 2).toBeLessThanOrEqual(FRAME_WIDTH / 2 + 1e-9);
      }
    });

    it('keeps the Port itself (footing, pier, Boat) in the outer right-hand 20% of the frame', () => {
      for (const input of [FOOTING, BUILT, { ...BUILT, boatDocked: true }]) {
        const v = buildVillage(input);
        for (const x of shore(v)) {
          expect(x.x - x.w / 2).toBeGreaterThanOrEqual(SHORE_X0 - 1e-9);
          expect(x.x + x.w / 2).toBeLessThanOrEqual(FRAME_WIDTH / 2);
        }
        const b = villageBounds(v);
        expect(b.maxX).toBeLessThanOrEqual(FRAME_WIDTH * 0.3);
      }
      expect(SHORE_X0).toBeCloseTo(FRAME_WIDTH * 0.3);
    });

    it('draws sea as horizontal bands, no waves: every band is wider than tall', () => {
      const bands = buildVillage(BUILT).filter((x) =>
        x.id.startsWith('sea-band-'),
      );

      expect(bands.length).toBeGreaterThan(3);
      for (const b of bands) expect(b.w).toBeGreaterThan(b.h * 5);
    });

    it('docks a dark hull only once built and docked, and removes it on a Voyage', () => {
      const hull = (input: VillageInput) =>
        buildVillage(input).find((x) => x.id === 'port-boat-hull');

      expect(hull(BUILT)).toBeUndefined();
      const docked = hull({ ...BUILT, boatDocked: true })!;
      expect(docked.tone).toBeLessThanOrEqual(VILLAGE_DARK);
      expect(hull({ ...BUILT, boatDocked: false })).toBeUndefined();
      expect(hull({ ...FOOTING, boatDocked: true })).toBeUndefined();
    });

    it('is the same hull whether motion is reduced or not', () => {
      const a = buildVillage({ ...BUILT, boatDocked: true, still: true });
      const b = buildVillage({ ...BUILT, boatDocked: true, still: false, tick: 0 });

      expect(ids(a).filter((i) => i.startsWith('port-boat'))).toEqual(
        ids(b).filter((i) => i.startsWith('port-boat')),
      );
    });

    it('stays within the renderer capacity', () => {
      const v = buildVillage({ ...BUSY, port: 'BUILT', boatDocked: true });

      expect(v.length).toBeLessThanOrEqual(VILLAGE_CAPACITY);
      expect(new Set(ids(v)).size).toBe(v.length);
    });
  });
  describe('Quarter', () => {
    const QUARTERS = ['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'] as const;
    const withQ = (q: (typeof QUARTERS)[number] | null, over: Partial<VillageInput> = {}) =>
      buildVillage({ ...BUSY, quarter: q, ...over });
    const find = (v: Volume[], id: string) => v.find((x) => x.id === id)!;
    const marks = (v: Volume[]) => v.filter((x) => x.id.startsWith('season-'));

    it('draws the plain Village with no Quarter (a Trial has no Year)', () => {
      expect(marks(withQ(null))).toEqual([]);
      expect(buildVillage(BUSY)).toEqual(withQ(null));
    });

    it('changes the ground tone, the rows and the marks for each Quarter', () => {
      const grounds = QUARTERS.map((q) => find(withQ(q), 'ground').tone);
      expect(new Set(grounds).size).toBe(4);
      const rows = QUARTERS.map((q) => find(withQ(q), 'furrow-0-0').tone);
      expect(rows[0]).toBeGreaterThan(rows[2]);
      expect(rows[2]).toBeGreaterThan(rows[3]);
      for (const q of QUARTERS) {
        expect(marks(withQ(q)).length).toBeGreaterThan(0);
      }
      expect(marks(withQ('WINTER')).some((m) => m.id.startsWith('season-snow'))).toBe(true);
      expect(marks(withQ('AUTUMN')).some((m) => m.id.startsWith('season-stubble'))).toBe(true);
    });

    it('uses only tones the ramp can draw, no new colour', () => {
      for (const q of QUARTERS) {
        for (const v of withQ(q)) {
          expect(v.tone).toBeGreaterThanOrEqual(0);
          expect(v.tone).toBeLessThanOrEqual(1);
        }
        expect(QUARTER_LOOK[q].ground).toBeLessThan(0.5);
      }
    });

    it('leaves the Tower exactly as it is in every Quarter', () => {
      const tower = (v: Volume[]) => v.filter((x) => x.group === 'tower');
      for (const q of QUARTERS) {
        expect(tower(withQ(q))).toEqual(tower(withQ(null)));
      }
    });

    it('keeps the Village within its width and height and the capacity', () => {
      for (const q of QUARTERS) {
        const v = withQ(q, { port: 'BUILT', boatDocked: true });
        const b = villageBounds(v);
        expect(b.maxX - b.minX).toBeLessThanOrEqual(FRAME_WIDTH * MAX_VILLAGE_SHARE);
        expect(b.maxY).toBeLessThanOrEqual(MAX_VILLAGE_HEIGHT);
        expect(v.length).toBeLessThanOrEqual(VILLAGE_CAPACITY);
        expect(new Set(v.map((x) => x.id)).size).toBe(v.length);
      }
    });

    it('does not depend on the tick or the flicker (nothing animates for the Quarter)', () => {
      for (const q of QUARTERS) {
        const still = marks(withQ(q, { still: true, tick: 0 }));
        const moving = marks(withQ(q, { still: false, tick: 37, flicker: 5 }));
        expect(moving).toEqual(still);
      }
    });

    it('tints the sea with the danger at sea once the Port is there', () => {
      const sea = (risk: number | null) =>
        buildVillage({ ...BUSY, port: 'FOOTING', seaRisk: risk }).filter((x) =>
          x.id.startsWith('sea-'),
        );
      expect(seaUnrest(0.01)).toBe(0);
      expect(seaUnrest(0.2)).toBe(1);
      expect(seaUnrest(null)).toBe(0);
      expect(sea(0.2)).not.toEqual(sea(0.01));
      expect(sea(null)).toEqual(sea(0.01));
      expect(find(sea(0.2), 'sea-body').tone).toBeLessThan(find(sea(0.01), 'sea-body').tone);
      expect(find(sea(0.2), 'sea-band-1').tone).toBeGreaterThan(find(sea(0.01), 'sea-band-1').tone);
    });
  });

  describe('the road (#148)', () => {
    const isRoad = (v: Volume) => v.id.startsWith('road-');
    const paving = (v: Volume[]) => v.filter((x) => x.id.startsWith('road-paving'));
    const walls = (v: Volume[]) => v.filter((x) => x.id.startsWith('road-wall'));
    const FULL = Object.fromEntries(Object.entries(UPGRADE_MODULES).map(([id, m]) => [id, m.cap]));
    const OFFERED = Object.fromEntries(Object.keys(UPGRADE_MODULES).map((id) => [id, 0]));
    const SPORES = { ...BUSY.hands, SPOREWARDEN: 6 };
    const SCENES: Array<[string, VillageInput]> = [
      ['an empty Village', EMPTY_VILLAGE],
      ['Tier I', { ...BUSY, villageTierLevel: 1, upgrades: FULL, saltPansOpen: false }],
      ['Tier II', { ...BUSY, villageTierLevel: 2, upgrades: FULL }],
      ['Tier III', { ...BUSY, villageTierLevel: 3, upgrades: FULL, sporeCellarOpen: true, spores: 7, hands: SPORES }],
      [
        'Tier IV with the Port and every Upgrade',
        { ...BUSY, upgrades: FULL, sporeCellarOpen: true, spores: 7, hands: SPORES, port: 'BUILT', boatDocked: true, offer: true },
      ],
      ['Tier IV with the Upgrades offered, not bought', { ...BUSY, upgrades: OFFERED, sporeCellarOpen: true }],
    ];
    const overlaps = (a: Volume, b: Volume, eps = 1e-6) =>
      Math.abs(a.x - b.x) < (a.w + b.w) / 2 - eps &&
      Math.abs(a.y - b.y) < (a.h + b.h) / 2 - eps &&
      Math.abs(a.z - b.z) < (a.d + b.d) / 2 - eps;

    it('is drawn at every Village Tier, as boxes of the Village in the ink ramp', () => {
      for (const [name, input] of SCENES) {
        const road = buildVillage(input).filter(isRoad);
        expect(paving(road).length).withContext(name).toBeGreaterThan(8);
        expect(walls(road).length).withContext(name).toBeGreaterThan(10);
        for (const v of road) {
          expect(v.group).withContext(v.id).toBe('village');
          expect(v.alpha).toBe(1);
          expect(v.tone).toBeGreaterThanOrEqual(0);
          expect(v.tone).toBeLessThanOrEqual(1);
        }
      }
    });

    it('does not depend on the state: the same road everywhere, still or moving', () => {
      const roads = SCENES.map(([, input]) =>
        buildVillage({ ...input, tick: 5, still: false, flicker: 2 }).filter(isRoad),
      );
      for (const r of roads) expect(r).toEqual(roads[0]);
    });

    it('is paved lighter than the ground and bounded by low walls', () => {
      const v = buildVillage(BUSY);
      const ground = v.find((x) => x.id === 'ground')!;
      for (const p of paving(v)) {
        expect(p.tone).toBeGreaterThan(ground.tone + 0.2);
        expect(p.y + p.h / 2).toBeLessThanOrEqual(0.1);
      }
      for (const w of walls(v)) {
        expect(w.y + w.h / 2).toBeLessThanOrEqual(0.2);
        expect(Math.min(w.w, w.d)).toBeLessThanOrEqual(0.08);
      }
    });

    it("runs unbroken from the front of the ground to the stair's foot", () => {
      const tiles = paving(buildVillage(BUSY));
      const touches = (a: Volume, b: Volume) =>
        Math.abs(a.x - b.x) <= (a.w + b.w) / 2 + 1e-6 && Math.abs(a.z - b.z) <= (a.d + b.d) / 2 + 1e-6;
      const front = tiles.filter((t) => t.z + t.d / 2 >= 3.4);
      expect(front.length).toBeGreaterThan(0);
      const foot = tiles.filter(
        (t) =>
          t.z - t.d / 2 <= STAIR.z + STAIR.d / 2 + 0.1 &&
          t.x - t.w / 2 <= STAIR.x0 &&
          t.x + t.w / 2 >= STAIR.x0,
      );
      expect(foot.length).toBeGreaterThan(0);
      const seen = new Set<Volume>(front);
      const queue = [...front];
      while (queue.length) {
        const t = queue.pop()!;
        for (const n of tiles) {
          if (!seen.has(n) && touches(t, n)) {
            seen.add(n);
            queue.push(n);
          }
        }
      }
      expect(foot.every((t) => seen.has(t))).toBeTrue();
    });

    it('winds: it crosses the front, climbs the middle and bends toward the Gate', () => {
      const tiles = paving(buildVillage(BUSY));
      const xs = tiles.map((t) => t.x);
      const zs = tiles.map((t) => t.z);
      expect(Math.max(...xs) - Math.min(...xs)).toBeGreaterThan(2);
      expect(Math.max(...zs) - Math.min(...zs)).toBeGreaterThan(2.5);
      // The climb to the Gate drifts sideways band by band, never in a ruler-straight line.
      const climb = tiles.filter((t) => t.z > 0.9 && t.z < 2.1 && t.w < 0.4).map((t) => t.x);
      expect(new Set(climb.map((x) => x.toFixed(2))).size).toBeGreaterThan(3);
    });

    it('steps up toward the stair in small terraces, never higher than the first tread', () => {
      const tiles = paving(buildVillage(BUSY)).sort((a, b) => b.z - a.z);
      const tops = tiles.map((t) => t.y + t.h / 2);
      expect(new Set(tops.map((t) => t.toFixed(3))).size).toBeGreaterThanOrEqual(3);
      expect(Math.max(...tops)).toBeLessThan(STAIR.rise / STAIR.steps);
    });

    it('stands clear of every building, module, figure and object it passes', () => {
      for (const [name, input] of SCENES) {
        for (const tick of [0, 3, 5, 8]) {
          const v = buildVillage({ ...input, tick, still: false });
          const road = v.filter(isRoad);
          const others = v.filter(
            (x) =>
              !isRoad(x) &&
              x.alpha > 0 &&
              x.group !== 'sea' &&
              x.id !== 'ground' &&
              !x.id.startsWith('season-') &&
              !x.id.startsWith('chasm-') &&
              !x.id.startsWith('outline-'),
          );
          for (const r of road) {
            for (const o of others) {
              expect(overlaps(r, o)).withContext(`${name} tick ${tick}: ${r.id} meets ${o.id}`).toBeFalse();
            }
          }
        }
      }
    });

    it('leaves the Gate, bell and stair where they are, and goes through the Gate between its piers', () => {
      const v = buildVillage(BUSY);
      const find = (id: string) => v.find((x) => x.id === id)!;
      expect(find('bell-beam').x).toBe(BELL_SPOT.x);
      expect(find('stair-0').x).toBeCloseTo(STAIR.x0 + STAIR.run / STAIR.steps / 2, 9);
      const pierW = find('gate-pier-w');
      const pierE = find('gate-pier-e');
      const through = paving(v).filter((t) => Math.abs(t.z - BELL_SPOT.z) <= t.d / 2);
      expect(through.length).toBeGreaterThan(0);
      for (const t of through) {
        expect(t.x - t.w / 2).toBeGreaterThan(pierW.x + pierW.w / 2);
        expect(t.x + t.w / 2).toBeLessThan(pierE.x - pierE.w / 2);
      }
    });

    it('stands for nothing the player can point at: no subject, so no hover region', () => {
      for (const v of buildVillage(BUSY).filter(isRoad)) {
        expect(subjectOfVolume(v.id)).withContext(v.id).toBeNull();
      }
    });

    it('does not widen or heighten the Village, nor move the closeup frame', () => {
      for (const [name, input] of SCENES) {
        const all = buildVillage(input);
        const without = all.filter((x) => !isRoad(x));
        expect(villageBounds(all)).withContext(name).toEqual(villageBounds(without));
        expect(closeupFrame(all)).withContext(name).toEqual(closeupFrame(without));
        expect(all.length).withContext(name).toBeLessThanOrEqual(VILLAGE_CAPACITY);
      }
    });

    it('keeps ids unique, and the paving and the walls on the ground', () => {
      const v = buildVillage(BUSY);
      expect(new Set(ids(v)).size).toBe(v.length);
      for (const r of [...paving(v), ...walls(v)]) expect(r.y - r.h / 2).toBeCloseTo(0, 9);
    });
  });

  describe('framing and the cliff (#149)', () => {
    const isFrame = (v: Volume) => v.group === 'frame';
    const cliff = (v: Volume[]) => v.filter((x) => x.id.startsWith('cliff-'));
    const fog = (v: Volume[]) => v.filter((x) => x.id.startsWith('fog-'));
    const foliage = (v: Volume[]) => v.filter((x) => x.id.startsWith('foliage-'));
    const FULL = Object.fromEntries(Object.entries(UPGRADE_MODULES).map(([id, m]) => [id, m.cap]));
    const SCENES: Array<[string, VillageInput]> = [
      ['an empty Village', EMPTY_VILLAGE],
      ['Tier I', { ...BUSY, villageTierLevel: 1, upgrades: FULL, saltPansOpen: false }],
      ['Tier II', { ...BUSY, villageTierLevel: 2, upgrades: FULL }],
      ['Tier III', { ...BUSY, villageTierLevel: 3, upgrades: FULL, sporeCellarOpen: true, spores: 7 }],
      ['Tier IV with the Port', { ...BUSY, upgrades: FULL, sporeCellarOpen: true, spores: 7, port: 'BUILT', boatDocked: true, offer: true }],
      ['Tier IV with a footing', { ...BUSY, port: 'FOOTING' }],
    ];
    const hasBay = (input: VillageInput) => (input.port ?? 'HIDDEN') !== 'HIDDEN';

    it('is scenery of its own group, in every scene, and no id is shared', () => {
      for (const [name, input] of SCENES) {
        const v = buildVillage(input);
        const frame = v.filter(isFrame);
        expect(cliff(v).length).withContext(name).toBeGreaterThan(20);
        expect(fog(v).length).withContext(name).toBeGreaterThan(5);
        expect(foliage(v).length).withContext(name).toBeGreaterThan(10);
        expect(frame.length).withContext(name).toBe(cliff(v).length + fog(v).length + foliage(v).length);
        expect(new Set(ids(v)).size).withContext(name).toBe(v.length);
        // No frame volume stands for anything the player can point at.
        for (const f of frame) expect(subjectOfVolume(f.id)).withContext(f.id).toBeNull();
      }
    });

    it('leaves the Village bounds and the closeup frame as they were', () => {
      for (const [name, input] of SCENES) {
        const all = buildVillage(input);
        const without = all.filter((x) => !isFrame(x));
        expect(villageBounds(all)).withContext(name).toEqual(villageBounds(without));
        expect(closeupFrame(all)).withContext(name).toEqual(closeupFrame(without));
      }
    });

    it('stays clear of every other volume, the road, the Gate and the stair included', () => {
      for (const [name, input] of SCENES) {
        for (const tick of [0, 5]) {
          const v = buildVillage({ ...input, tick, still: false });
          const frame = v.filter(isFrame);
          const others = v.filter((x) => !isFrame(x) && !x.id.startsWith('outline-'));
          for (const f of frame) {
            for (const o of others) {
              const meet =
                Math.abs(f.x - o.x) < (f.w + o.w) / 2 - 1e-6 &&
                Math.abs(f.y - o.y) < (f.h + o.h) / 2 - 1e-6 &&
                Math.abs(f.z - o.z) < (f.d + o.d) / 2 - 1e-6;
              expect(meet).withContext(`${name}: ${f.id} meets ${o.id}`).toBeFalse();
            }
          }
        }
      }
    });

    describe('the cliff', () => {
      it('is a stone face under the ground, in courses, from the ground top down into the dark', () => {
        for (const [name, input] of SCENES) {
          const c = cliff(buildVillage(input));
          // Under the ground's top, in front of its edge.
          expect(Math.max(...c.map((x) => x.y + x.h / 2))).withContext(name).toBeLessThanOrEqual(1e-9);
          expect(Math.min(...c.map((x) => x.y - x.h / 2))).withContext(name).toBeLessThanOrEqual(-4);
          expect(Math.min(...c.map((x) => x.z - x.d / 2))).withContext(name).toBeGreaterThanOrEqual(3.5 - 1e-9);
          // Courses: blocks of a few distinct bottoms, many of them stone, not one slab.
          const bottoms = new Set(c.filter((x) => x.id.startsWith('cliff-block-')).map((x) => (x.y - x.h / 2).toFixed(3)));
          expect(bottoms.size).withContext(name).toBeGreaterThanOrEqual(4);
          // Lighter than the ground at the top, fading to dark with depth.
          const tone = (x: Volume) => x.tone;
          const top = c.filter((x) => x.id.startsWith('cliff-block-') && x.y + x.h / 2 > -0.45);
          const low = c.filter((x) => x.id.startsWith('cliff-block-') && x.y + x.h / 2 < -1.5);
          const mean = (a: Volume[]) => a.map(tone).reduce((s, t) => s + t, 0) / a.length;
          expect(mean(top)).withContext(name).toBeGreaterThan(VILLAGE_TONE.ground);
          expect(mean(low)).withContext(name).toBeLessThan(mean(top));
        }
      });

      it('runs along the whole bottom of the view, from far left to the sea or the far right', () => {
        for (const [name, input] of SCENES) {
          const c = cliff(buildVillage(input));
          const left = Math.min(...c.map((x) => x.x - x.w / 2));
          const right = Math.max(...c.map((x) => x.x + x.w / 2));
          expect(left).withContext(name).toBeLessThanOrEqual(-36);
          if (hasBay(input)) {
            // The land ends at the shore; the sea is not under a cliff.
            expect(right).withContext(name).toBeLessThanOrEqual(shoreX(3.5) + 1e-9);
            expect(right).withContext(name).toBeGreaterThanOrEqual(shoreX(3.5) - 0.3);
          } else {
            expect(right).withContext(name).toBeGreaterThanOrEqual(FRAME_WIDTH);
          }
        }
      });

      it('lets the road go on down the face, in three paler steps at its own x', () => {
        const c = cliff(buildVillage(BUSY));
        const steps = c.filter((x) => x.id.startsWith('cliff-road-'));
        expect(steps.length).toBe(3);
        const road = buildVillage(BUSY).find((x) => x.id === 'road-paving-0')!;
        for (const s of steps) {
          expect(Math.abs(s.x - road.x)).toBeLessThanOrEqual(0.1);
          expect(s.tone).toBeGreaterThan(0.5);
        }
        // No block of the face stands over the steps.
        const blocks = c.filter((x) => x.id.startsWith('cliff-block-'));
        for (const s of steps) {
          for (const b of blocks) {
            const meet =
              Math.abs(b.x - s.x) < (b.w + s.w) / 2 - 1e-6 &&
              Math.abs(b.y - s.y) < (b.h + s.h) / 2 - 1e-6 &&
              Math.abs(b.z - s.z) < (b.d + s.d) / 2 - 1e-6;
            expect(meet).withContext(`${s.id} meets ${b.id}`).toBeFalse();
          }
        }
      });
    });

    describe('the ground fog', () => {
      it('is dithered translucency, light, in banks of nested slabs along the cliff, under the ground top', () => {
        const f = fog(buildVillage(BUSY));
        expect(f.every((x) => x.alpha > 0 && x.alpha < 0.6)).toBeTrue();
        expect(f.every((x) => x.tone >= 0.4)).toBeTrue();
        // Never above the ground, so it cannot veil the road's front or a building.
        expect(Math.max(...f.map((x) => x.y + x.h / 2))).toBeLessThanOrEqual(-0.04);
        // A bank thins out in steps: more than one alpha in use.
        expect(new Set(f.map((x) => x.alpha.toFixed(3))).size).toBeGreaterThanOrEqual(3);
        expect(Math.min(...f.map((x) => x.z - x.d / 2))).toBeGreaterThanOrEqual(3.5 - 1e-9);
      });

      it('drifts slowly with the clock and holds still under reduced motion', () => {
        const at = (flicker: number, still: boolean) =>
          fog(buildVillage({ ...BUSY, flicker, still })).map((x) => x.x);
        expect(at(0, false)).not.toEqual(at(30, false));
        expect(at(30, true)).toEqual(at(0, true));
        expect(at(30, true)).toEqual(at(0, false));
        const a = at(0, false);
        const b = at(2, false);
        // Slow: two ticks move a bank by well under a tenth of a unit.
        a.forEach((x, i) => expect(Math.abs(b[i] - x)).toBeLessThan(0.1));
      });
    });

    describe('the foliage', () => {
      it('is low, black, stepped masses: silhouette only', () => {
        for (const [name, input] of SCENES) {
          const f = foliage(buildVillage(input));
          expect(Math.max(...f.map((x) => x.tone))).withContext(name).toBeLessThanOrEqual(0.06);
          expect(f.every((x) => x.alpha === 1)).withContext(name).toBeTrue();
          // Black in both themes: flagged for the darkest ink, and nothing else is.
          expect(f.every((x) => x.black === true)).withContext(name).toBeTrue();
          expect(buildVillage(input).filter((x) => x.black).length).withContext(name).toBe(f.length);
          expect(Math.max(...f.map((x) => x.y + x.h / 2))).withContext(name).toBeLessThanOrEqual(1.8);
          expect(Math.min(...f.map((x) => x.y - x.h / 2))).withContext(name).toBeGreaterThanOrEqual(-0.5);
          // Ragged: many distinct top heights and widths.
          expect(new Set(f.map((x) => (x.y + x.h / 2).toFixed(2))).size).withContext(name).toBeGreaterThan(5);
          expect(new Set(f.map((x) => x.w.toFixed(2))).size).withContext(name).toBeGreaterThan(5);
        }
      });

      it('stands outside the Village bounds and the closeup frame, to the left, and to the right when landlocked', () => {
        for (const [name, input] of SCENES) {
          const v = buildVillage(input);
          const frame = closeupFrame(v);
          const lo = frame.centerX - frame.span / 2;
          const hi = frame.centerX + frame.span / 2;
          const b = villageBounds(v);
          const f = foliage(v);
          const left = f.filter((x) => x.x < 0);
          const right = f.filter((x) => x.x >= 0);
          expect(left.length).withContext(name).toBeGreaterThan(5);
          for (const x of left) {
            expect(x.x + x.w / 2).withContext(`${name} ${x.id}`).toBeLessThanOrEqual(Math.min(lo, b.minX) + 1e-9);
          }
          if (hasBay(input)) {
            // The sea is the right-hand edge: no black trees on it.
            expect(right.length).withContext(name).toBe(0);
          } else {
            expect(right.length).withContext(name).toBeGreaterThan(5);
            for (const x of right) {
              expect(x.x - x.w / 2).withContext(`${name} ${x.id}`).toBeGreaterThanOrEqual(Math.max(hi, b.maxX) - 1e-9);
            }
          }
        }
      });

      it('is behind the Village, so it never stands in front of a building, a pad or the road', () => {
        for (const [name, input] of SCENES) {
          const v = buildVillage(input);
          for (const f of foliage(v)) {
            // Behind the Village's middle, outside its x range, so it only ever frames it from behind.
            expect(f.z + f.d / 2).withContext(`${name} ${f.id}`).toBeLessThanOrEqual(0.6 + 1e-9);
          }
        }
      });

      it('holds still: no volume of the frame moves with the clock but the fog', () => {
        const at = (flicker: number) => buildVillage({ ...BUSY, flicker, still: false }).filter((x) => isFrame(x) && !x.id.startsWith('fog-'));
        expect(at(40)).toEqual(at(0));
      });
    });

    it('does not take the pointer or the sight from any building: no frame volume is nearer the camera than a building it overlaps on screen', () => {
      for (const [name, input] of SCENES) {
        const v = buildVillage(input);
        const targets = v.filter(
          (x) => (x.group === 'village' || x.group === 'port') && x.id !== 'ground' && !x.id.startsWith('outline-'),
        );
        for (const [w, h] of [[1440, 900], [1920, 1080]] as const) {
          for (const closeness of [0, 1]) {
            const cam = new THREE.PerspectiveCamera();
            applyStageView(cam, w, h, false, closeness);
            fitVillageCamera(cam, closeness, closeupFrame(v));
            cam.updateMatrixWorld();
            const rect = (x: Volume) => {
              let l = Infinity, r = -Infinity, t = Infinity, b = -Infinity;
              for (const sx of [-1, 1]) for (const sy of [-1, 1]) for (const sz of [-1, 1]) {
                const p = new THREE.Vector3(x.x + (sx * x.w) / 2, x.y + (sy * x.h) / 2, x.z + (sz * x.d) / 2).project(cam);
                l = Math.min(l, p.x); r = Math.max(r, p.x); t = Math.min(t, p.y); b = Math.max(b, p.y);
              }
              return { l, r, t, b };
            };
            const dist = (x: Volume) => cam.position.distanceTo(new THREE.Vector3(x.x, x.y, x.z));
            for (const f of v.filter((x) => x.id.startsWith('foliage-'))) {
              const fr = rect(f);
              for (const o of targets) {
                const or = rect(o);
                const overlap = fr.l < or.r && fr.r > or.l && fr.t < or.b && fr.b > or.t;
                if (overlap) {
                  expect(dist(f)).withContext(`${name} ${w}x${h} closeness ${closeness}: ${f.id} over ${o.id}`).toBeGreaterThan(dist(o));
                }
              }
            }
          }
        }
      }
    });

    it('stays inside the instance capacity, unique, with every Upgrade and the Port', () => {
      for (const [name, input] of SCENES) {
        expect(buildVillage(input).length).withContext(name).toBeLessThanOrEqual(VILLAGE_CAPACITY);
      }
    });
  });
});
