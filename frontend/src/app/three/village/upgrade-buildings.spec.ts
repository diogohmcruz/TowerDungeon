import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState, UpgradeOffer } from '../../interfaces/game-state';
import { PostUpgradeView, TomeView } from '../../interfaces/village';
import { GameStateService } from '../../services/game-state.service';
import { buildingView } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import { RING } from './ring-building';
import { UPGRADE_BUILDINGS } from './upgrade-buildings';
import { captionFor, captionText } from './village-captions';
import {
  buildVillage,
  EMPTY_VILLAGE,
  litRingSides,
  UPGRADE_MODULES,
} from './village-layout';
import { outlineVolumes, subjectOfVolume } from './village-subjects';

const offer = (over: Partial<UpgradeOffer> = {}): UpgradeOffer => ({
  id: 'UNLOCK_HEALER',
  name: 'Field Medic joins',
  description: 'Recruit the Healer to mend the party mid-delve.',
  level: 0,
  maxed: false,
  repeatable: false,
  unlockUnit: 'HEALER',
  nextCost: { MATERIALS: 40 },
  ...over,
});

describe('the Upgrades, Tomes, Posts and the Ring in the world', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };
  const sent: unknown[][] = [];
  const commands = {
    send: (a: GameAction, p?: unknown) => sent.push([a, p]),
    launch: () => undefined,
  };

  beforeEach(() => {
    sent.length = 0;
    load({
      resources: { MATERIALS: 100, RELICS: 0 },
      availableUpgrades: [
        offer(),
        offer({
          id: 'SUPPLY_LINES',
          name: 'Supply Lines',
          description: 'Carry more.',
          level: 1,
          repeatable: true,
          unlockUnit: null,
          nextCost: { MATERIALS: 120, RELICS: 2 },
        }),
      ],
    });
  });

  describe('Upgrades', () => {
    it('registers one building per module, on the module itself', () => {
      expect(UPGRADE_BUILDINGS.map((b) => b.id)).toEqual(Object.keys(UPGRADE_MODULES));
      for (const b of UPGRADE_BUILDINGS) {
        expect(BUILDINGS).toContain(b);
        expect(buildingDefinition(b.id)).toBe(b);
        expect(subjectOfVolume(`grow:${b.id}:0:roof`)).toEqual({ kind: 'building', id: b.id });
        expect(subjectOfVolume(`upgrade-footing:${b.id}:n`)?.id).toBe(b.id);
        expect(subjectOfVolume(`upgrade-hit:${b.id}`)?.id).toBe(b.id);
      }
    });

    it('is shown only while the catalogue offers it', () => {
      const tank = UPGRADE_BUILDINGS.find((b) => b.id === 'UNLOCK_TANK')!;
      expect(tank.visible!(state)).toBeFalse();
      expect(UPGRADE_BUILDINGS.find((b) => b.id === 'UNLOCK_HEALER')!.visible!(state)).toBeTrue();
    });

    it('states the level, cost and effect in words on hover, before buying', () => {
      const text = captionText(captionFor({ kind: 'building', id: 'SUPPLY_LINES' }, state)!);
      expect(text).toContain('Level 1');
      expect(text).toContain('Carry more.');
      expect(text).toContain('120 Materials and 2 Relics');
    });

    it('buys through the Upgrade command, and says what it leaves', () => {
      const def = UPGRADE_BUILDINGS.find((b) => b.id === 'UNLOCK_HEALER')!;
      const view = buildingView(def, state).actions[0];
      expect(view.label).toBe('Field Medic joins: Recruit, 40 Materials');
      expect(view.usable).toBeTrue();
      expect(view.preview).toContain('leaves 60 Materials');
      def.actions[0].run(commands, state);
      expect(sent).toEqual([[GameAction.UPGRADE, { upgradeId: 'UNLOCK_HEALER' }]]);
    });

    it('refuses in words when the Vault cannot pay, or the Upgrade is owned', () => {
      const def = UPGRADE_BUILDINGS.find((b) => b.id === 'SUPPLY_LINES')!;
      const short = buildingView(def, state).actions[0];
      expect(short.usable).toBeFalse();
      expect(short.reason).toContain('short in Materials and Relics');
      load({ availableUpgrades: [offer({ maxed: true, level: 1 })] });
      const owned = buildingView(
        UPGRADE_BUILDINGS.find((b) => b.id === 'UNLOCK_HEALER')!,
        state,
      ).actions[0];
      expect(owned.usable).toBeFalse();
      expect(owned.reason).toContain('Owned');
    });

    it('stands as a footing until bought, then as its module', () => {
      const footing = buildVillage({ ...EMPTY_VILLAGE, upgrades: { UNLOCK_HEALER: 0 } });
      const edge = footing.find((v) => v.id === 'upgrade-footing:UNLOCK_HEALER:n')!;
      expect(edge.h).toBeLessThanOrEqual(0.02);
      expect(footing.some((v) => v.id.startsWith('grow:UNLOCK_HEALER'))).toBeFalse();
      expect(footing.find((v) => v.id === 'upgrade-hit:UNLOCK_HEALER')!.alpha).toBe(0);
      // Its frame hugs the footing, not the invisible pad.
      const frame = outlineVolumes(footing, 'UNLOCK_HEALER', 1, 'hover');
      expect(frame.length).toBeGreaterThan(0);

      const bought = buildVillage({ ...EMPTY_VILLAGE, upgrades: { UNLOCK_HEALER: 1 } });
      expect(bought.some((v) => v.id.startsWith('grow:UNLOCK_HEALER:0'))).toBeTrue();
      expect(bought.some((v) => v.id.startsWith('upgrade-footing:'))).toBeFalse();
    });
  });

  describe("Tomes at the Scribe's Study", () => {
    const tomes: TomeView[] = [
      {
        tome: 'SEAM_CHARTS',
        title: 'Seam Charts',
        effect: 'Breaker output',
        bonusPercent: 25,
        bonusFlat: 0,
        level: 1,
        cost: 60,
        gated: false,
        blocked: null,
      },
      {
        tome: 'SURVIVORS_NOTES',
        title: "Survivors' Notes",
        effect: 'Wave Carrying Capacity',
        bonusPercent: 0,
        bonusFlat: 10,
        level: 2,
        cost: 135,
        gated: true,
        blocked: 'Tome 3 costs 135. Stockroom holds 80.',
      },
    ];
    const study = () => buildingDefinition('SCRIBE')!;

    it('lists each Tome as an action, and sends the same command as the card did', () => {
      load({ tomeViews: tomes });
      const actions = buildingView(study(), state).actions.filter((a) => a.id.startsWith('tome-'));
      expect(actions.map((a) => a.id)).toEqual(['tome-SEAM_CHARTS', 'tome-SURVIVORS_NOTES']);
      expect(actions[0].label).toBe('Buy Seam Charts, 60 Knowledge (level 1)');
      expect(actions[0].usable).toBeTrue();
      expect(actions[0].preview).toContain('Breaker output +50%');
      expect(actions[1].usable).toBeFalse();
      expect(actions[1].reason).toBe('Tome 3 costs 135. Stockroom holds 80.');
      study().moreActions!(state)[0].run(commands, state);
      expect(sent).toEqual([[GameAction.BUY_TOME, { tome: 'SEAM_CHARTS' }]]);
    });

    it('states every Tome level on hover, a flat bonus as a number', () => {
      load({ tomeViews: tomes });
      const gauge = buildingView(study(), state).gauges.find((g) => g.id === 'tomes')!;
      expect(gauge.leaves).toContain("Survivors' Notes level 2: Wave Carrying Capacity +20.");
    });
  });

  describe('Posts at the Annex of their Trade', () => {
    const posts: PostUpgradeView = {
      trade: 'BREAKER',
      workplace: 'Quarry',
      level: 1,
      posts: 15,
      nextPosts: 20,
      cost: 40,
      blocked: null,
    };

    it('raises the Posts at the Quarry, and only where a Post upgrade exists', () => {
      load({ postUpgrades: [posts] });
      const quarry = buildingDefinition('BREAKER')!;
      const action = buildingView(quarry, state).actions.find((a) => a.id === 'raise-posts')!;
      expect(action.label).toBe('Raise Posts, 40 Materials (15 now, 20 after)');
      expect(action.preview).toContain('from 15 to 20');
      quarry.actions.find((a) => a.id === 'raise-posts')!.run(commands, state);
      expect(sent).toEqual([[GameAction.BUY_POSTS, { trade: 'BREAKER' }]]);
      const study = buildingDefinition('SCRIBE')!;
      expect(buildingView(study, state).actions.some((a) => a.id === 'raise-posts')).toBeFalse();
    });

    it('refuses in the words the game gives', () => {
      load({ postUpgrades: [{ ...posts, blocked: 'Posts cost 40 Materials. The Vault holds 3.' }] });
      const action = buildingView(buildingDefinition('BREAKER')!, state).actions.find(
        (a) => a.id === 'raise-posts',
      )!;
      expect(action.usable).toBeFalse();
      expect(action.reason).toBe('Posts cost 40 Materials. The Vault holds 3.');
    });
  });

  describe('the Thought-Binding Ring on the stair', () => {
    const ring = {
      level: 2,
      maxLevel: 5,
      cost: 23,
      maxed: false,
      speedBonus: 0.5,
      speedPerLevel: 0.25,
      capacityBonus: 10,
      capacityPerLevel: 5,
      blocked: null,
    };

    it('is registered, and its parts are its subject', () => {
      expect(BUILDINGS).toContain(RING);
      for (const id of ['ring-post', 'ring-band-n', 'ring-hit']) {
        expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: 'RING' });
      }
    });

    it('states level, bonus and cost on hover, and binds with the Ring command', () => {
      load({ ring, trustHeld: 30 });
      const text = captionText(captionFor({ kind: 'building', id: 'RING' }, state)!);
      expect(text).toContain('Level 2 of 5');
      expect(text).toContain('+0.5 Floor/s');
      expect(text).toContain('23 Trust');
      const action = buildingView(RING, state).actions[0];
      expect(action.label).toBe('Bind the Ring, 23 Trust');
      expect(action.usable).toBeTrue();
      RING.actions[0].run(commands, state);
      expect(sent).toEqual([[GameAction.BUY_RING, undefined]]);
    });

    it('refuses in words when blocked or full', () => {
      load({ ring: { ...ring, blocked: 'Ring 3 costs 23. Trust 12.' } });
      expect(buildingView(RING, state).actions[0].reason).toBe('Ring 3 costs 23. Trust 12.');
      load({ ring: { ...ring, level: 5, maxed: true } });
      const full = buildingView(RING, state).actions[0];
      expect(full.label).toBe('The Ring is full');
      expect(full.usable).toBeFalse();
    });

    it('lights its sides with the level', () => {
      expect(litRingSides(0, 5)).toBe(0);
      expect(litRingSides(2, 5)).toBe(2);
      expect(litRingSides(5, 5)).toBe(4);
      const lit = (v: ReturnType<typeof buildVillage>) =>
        v.filter((x) => x.id.startsWith('ring-band-') && x.tone === 1).length;
      expect(lit(buildVillage({ ...EMPTY_VILLAGE, ringLevel: 2, ringMax: 5 }))).toBe(2);
      expect(lit(buildVillage(EMPTY_VILLAGE))).toBe(0);
    });
  });
});
