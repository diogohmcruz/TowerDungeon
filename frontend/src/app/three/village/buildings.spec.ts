import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState } from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import { BuildingCommands, buildingViews } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import { captionFor, captionText } from './village-captions';
import {
  buildVillage,
  creditsFill,
  EMPTY_VILLAGE,
  litRelicNotches,
  litSaltNotches,
  VillageInput,
} from './village-layout';
import { outlineVolumes, subjectOfVolume } from './village-subjects';

describe('the Village Tier buildings', () => {
  let state: GameState;
  const base = () => TestBed.inject(GameStateService).state();
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };
  const stock = (tier: number, over: Partial<GameState> = {}): Partial<GameState> => ({
    villageTierLevel: tier,
    credit: 1840,
    resources: { ...base().resources, MATERIALS: 300, RELICS: 3 },
    village: { ...base().village, salt: 40, spores: 7, quarryHeap: 24 },
    quarryHeapCap: 40,
    stockroomCap: 500,
    heapDeliveryPerFloor: 4,
    tradeViews: (['TILLER', 'BREAKER', 'SCRIBE', 'SALTER', 'SPOREWARDEN'] as const).map((trade) => ({
      trade,
      plural: `${trade}s`,
      workplace: trade,
      hands: 2,
      posts: 10,
      settling: 0,
      settlingSeconds: 0,
    })),
    ...over,
  });
  const views = () => buildingViews(BUILDINGS, state);
  const gaugeLabels = (id: string) =>
    views().find((b) => b.id === id)?.gauges.map((g) => g.label);
  const actionIds = (id: string) =>
    views().find((b) => b.id === id)?.actions.map((a) => a.id);

  it('Tier I: the Vault shows Credits, Materials and Relics, and the Quarry its Heap', () => {
    load(stock(1, { village: { ...base().village, salt: 0, spores: 0, quarryHeap: 24 } }));
    expect(views().map((b) => b.id)).toContain('BREAKER');
    expect(views().map((b) => b.id)).not.toContain('SALTER');
    expect(gaugeLabels('VAULT')).toEqual(['Credits', 'Materials', 'Relics']);
    expect(actionIds('VAULT')).toEqual(['sell-materials', 'sell-relics']);
    expect(gaugeLabels('BREAKER')).toContain('Materials in the Heap');
  });

  it('Tier I: Salt held below the Pans is still shown and sold at the Vault', () => {
    load(stock(1));
    expect(gaugeLabels('VAULT')).toContain('Salt');
    expect(actionIds('VAULT')).toContain('sell-salt');
  });

  it('Tier II: the Vault no longer shows or sells Salt and the Pans do', () => {
    load(stock(2));
    expect(views().map((b) => b.id)).toContain('SALTER');
    expect(gaugeLabels('VAULT')).not.toContain('Salt');
    expect(actionIds('VAULT')).not.toContain('sell-salt');
    expect(gaugeLabels('SALTER')).toContain('Salt');
    expect(actionIds('SALTER')).toContain('sell-salt');
  });

  it('Tier III: Spores are at the Cellar and no longer at the Vault', () => {
    load(stock(3));
    expect(views().map((b) => b.id)).toContain('SPOREWARDEN');
    expect(gaugeLabels('SPOREWARDEN')).toContain('Dragon Spores');
    expect(gaugeLabels('VAULT')).not.toContain('Dragon Spores');
    load(stock(2));
    expect(gaugeLabels('VAULT')).toContain('Dragon Spores');
    expect(views().map((b) => b.id)).not.toContain('SPOREWARDEN');
  });

  it('sends the existing commands for each sale', () => {
    load(stock(1));
    const send = jasmine.createSpy('send');
    const commands = { send } as BuildingCommands;
    for (const action of ['sell-materials', 'sell-relics', 'sell-salt']) {
      buildingDefinition('VAULT')!.actions.find((a) => a.id === action)!.run(commands, state);
    }
    load(stock(2));
    buildingDefinition('SALTER')!.actions.find((a) => a.id === 'sell-salt')!.run(commands, state);
    expect(send.calls.allArgs()).toEqual([
      [GameAction.SELL_MATERIALS],
      [GameAction.SELL_RELICS],
      [GameAction.SELL_SALT],
      [GameAction.SELL_SALT],
    ]);
  });

  it('says in words why a sale cannot be made', () => {
    load(
      stock(2, {
        resources: { ...base().resources, MATERIALS: 0, RELICS: 0 },
        village: { ...base().village, salt: 0.4 },
      }),
    );
    const vault = views().find((b) => b.id === 'VAULT')!;
    for (const a of vault.actions) {
      expect(a.usable).toBeFalse();
      expect(a.reason).toContain('Nothing is sold');
    }
    expect(views().find((b) => b.id === 'SALTER')!.actions.find((a) => a.id === 'sell-salt')!.reason).toContain(
      'less than one Salt',
    );
  });

  it('gives exact figures, rates and what a sale leaves in the captions', () => {
    load(
      stock(2, {
        saltRate: 0.3,
        quarryRate: 0.8,
        village: { ...base().village, salt: 12.4, quarryHeap: 24 },
      }),
    );
    const pans = captionText(captionFor({ kind: 'annex', id: 'SALTER' }, state)!);
    expect(pans).toContain('Salt held 12.40.');
    expect(pans).toContain('+0.30/s');
    expect(pans).toContain('leaves 0.40');
    const heap = captionText(captionFor({ kind: 'annex', id: 'BREAKER' }, state)!);
    expect(heap).toContain('Materials in the Heap held 24.');
    const vault = captionText(captionFor({ kind: 'building', id: 'VAULT' }, state)!);
    expect(vault).toContain('Credits 1,840');
    expect(heap).toContain('Carried down on Extraction, 4 per Floor cleared');
  });

  describe('as drawn', () => {
    const drawn = (over: Partial<VillageInput> = {}) =>
      buildVillage({ ...EMPTY_VILLAGE, ...over });
    const ids = (v: { id: string }[]) => v.map((x) => x.id);

    it('maps every part of each building to its subject, pad included', () => {
      const v = drawn({
        materials: 300,
        relics: 3,
        credits: 100,
        quarryHeap: 12,
        quarryHeapCap: 24,
        saltPansOpen: true,
        salt: 40,
        sporeCellarOpen: true,
        spores: 7,
      });
      const expected: Record<string, [string, string[]]> = {
        VAULT: ['building', ['vault', 'vault-door', 'vault-credits-bar', 'vault-relics-notch-0', 'crate-0', 'vault-hit']],
        BREAKER: ['annex', ['heap-0', 'heap-scale-bar', 'heap-hit']],
        SALTER: ['annex', ['pan-0', 'pan-crust-0', 'pan-scale-notch-0', 'pan-hit']],
        SPOREWARDEN: ['annex', ['cellar', 'cellar-slit', 'cellar-fill', 'cellar-hit']],
      };
      for (const [building, [kind, parts]] of Object.entries(expected)) {
        for (const part of parts) {
          expect(ids(v)).toContain(part);
          expect(subjectOfVolume(part)).toEqual({ kind, id: building } as never);
        }
      }
    });

    it('draws the Pans and the Cellar only when open', () => {
      expect(ids(drawn()).some((i) => i.startsWith('pan-'))).toBeFalse();
      expect(ids(drawn()).some((i) => i.startsWith('cellar'))).toBeFalse();
      expect(ids(drawn({ saltPansOpen: true })).some((i) => i.startsWith('pan-'))).toBeTrue();
      expect(
        ids(drawn({ sporeCellarOpen: true })).some((i) => i.startsWith('cellar')),
      ).toBeTrue();
    });

    it('lights the gauges in proportion', () => {
      const lit = (v: { id: string; tone: number }[], prefix: string) =>
        v.filter((x) => x.id.startsWith(prefix) && x.tone === 1).length;
      expect(lit(drawn({ relics: 0 }), 'vault-relics-notch-')).toBe(0);
      expect(lit(drawn({ relics: 3 }), 'vault-relics-notch-')).toBe(3);
      expect(lit(drawn({ relics: 40 }), 'vault-relics-notch-')).toBe(5);
      expect(ids(drawn({ credits: 0 }))).not.toContain('vault-credits-fill');
      expect(ids(drawn({ credits: 1 }))).toContain('vault-credits-fill');
      expect(lit(drawn({ saltPansOpen: true, salt: 0 }), 'pan-scale-notch-')).toBe(0);
      expect(
        lit(drawn({ saltPansOpen: true, salt: 1 }), 'pan-scale-notch-'),
      ).toBeGreaterThanOrEqual(1);
      expect(ids(drawn({ sporeCellarOpen: true, spores: 0 }))).not.toContain('cellar-fill');
      expect(ids(drawn({ sporeCellarOpen: true, spores: 2 }))).toContain('cellar-fill');
      expect(ids(drawn({ quarryHeapCap: 40, quarryHeap: 40 }))).toContain('heap-scale-fill');
    });

    it('has pure scales', () => {
      expect(creditsFill(0)).toBe(0);
      expect(creditsFill(1e9)).toBe(1);
      expect(litRelicNotches(2.9)).toBe(2);
      expect(litSaltNotches(0)).toBe(0);
      expect(litSaltNotches(1e6)).toBe(5);
    });

    it('is outlined when hovered, from the drawn parts and not the pad', () => {
      const v = drawn({
        saltPansOpen: true,
        salt: 5,
        sporeCellarOpen: true,
        spores: 1,
        materials: 9,
        quarryHeap: 5,
        quarryHeapCap: 10,
      });
      for (const id of ['VAULT', 'BREAKER', 'SALTER', 'SPOREWARDEN']) {
        expect(outlineVolumes(v, id, 1, 'hover').length).toBe(4);
      }
    });
  });
});
