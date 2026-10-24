import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState, SuccessionReport } from '../../interfaces/game-state';
import type {
  SuccessionNode,
  SuccessionOption,
  SuccessionView,
} from '../../interfaces/succession';
import { BuildingPopover } from '../../components/building-popover/building-popover';
import { ListView } from '../../components/list-view/list-view';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { ListViewService } from '../../services/list-view.service';
import { PopoverService } from '../../services/popover.service';
import { buildingView, type BuildingView } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import {
  beginFlow,
  SUCCESSION,
  SUCCESSION_ID,
  tollLines,
} from './succession-building';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE } from './village-layout';
import { subjectOfVolume } from './village-subjects';

const node = (id: string, over: Partial<SuccessionNode> = {}): SuccessionNode => ({
  id,
  name: id,
  course: 1,
  kind: 'POWER',
  cost: 3,
  available: true,
  held: false,
  paid: 0,
  whyNot: null,
  refundable: false,
  ...over,
});

const option = (
  id: string,
  group: string,
  over: Partial<SuccessionOption> = {},
): SuccessionOption => ({ id, group, selected: false, isDefault: false, ...over });

const view = (over: Partial<SuccessionView> = {}): SuccessionView => ({
  generation: 1,
  legacy: 5,
  spent: 0,
  courses: [
    { course: 1, gate: 0, spentBelow: 0, open: true },
    { course: 2, gate: 8, spentBelow: 0, open: false },
  ],
  inheritances: [
    node('SEED_GRAIN', { name: 'Seed Grain' }),
    node('HARDY_STOCK', { name: 'Hardy Stock', held: true, paid: 3, refundable: true }),
    node('THE_DOWRY', { name: 'The Dowry', course: 2, whyNot: 'COURSE_CLOSED' }),
    node('NARROW_STAIR', { name: 'Narrow Stair', course: 2, available: false, whyNot: 'UNAVAILABLE' }),
  ],
  choices: [],
  chronicle: [],
  ...over,
});

const report: SuccessionReport = {
  outcome: 'EXODUS',
  generation: 1,
  toll: {
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
    legacy: 5,
    residueNext: 6,
  },
  legacy: 5,
  pity: false,
  guardiansSlain: 1,
  highestFloor: 12,
  expeditions: 4,
};

describe('the Succession at the Tower foot', () => {
  let sendAction: jasmine.Spy;
  let game: GameStateService;
  let popover: PopoverService;

  const offer = (v: SuccessionView = view(), over: Partial<GameState> = {}) => {
    game.setSession({
      resumed: false,
      savedAt: null,
      schema: 1,
      notices: [],
      held: false,
      succession: v,
    });
    game.update({ phase: 'OFFER', gameOutcome: 'EXODUS', pending: report, ...over });
    TestBed.flushEffects();
  };
  const current = (): BuildingView =>
    buildingView(SUCCESSION, game.registryState());
  const action = (id: string) => current().actions.find((a) => a.id === id)!;
  const line = (id: string) => current().lines.find((l) => l.id === id)!;

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    popover = TestBed.inject(PopoverService);
    beginFlow.begunFor.set(null);
  });

  it('is registered, and the Roll stone is its subject', () => {
    expect(buildingDefinition(SUCCESSION_ID)).toBe(SUCCESSION);
    expect(BUILDINGS).toContain(SUCCESSION);
    for (const id of ['roll-slab', 'roll-hit', 'roll-notch-2']) {
      expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: SUCCESSION_ID });
    }
  });

  it('stands only while the Campaign waits in OFFER', () => {
    const plain = buildVillage({ ...EMPTY_VILLAGE });
    const waiting = buildVillage({ ...EMPTY_VILLAGE, offer: true });
    expect(plain.some((v) => v.id.startsWith('roll-'))).toBeFalse();
    expect(waiting.some((v) => v.id === 'roll-slab')).toBeTrue();
    expect(waiting.some((v) => v.id === 'roll-hit' && v.alpha === 0)).toBeTrue();

    expect(popover.buildings().some((b) => b.id === SUCCESSION_ID)).toBeFalse();
    offer();
    expect(popover.buildings().some((b) => b.id === SUCCESSION_ID)).toBeTrue();
    game.update({ phase: 'ACTIVE' });
    expect(popover.buildings().some((b) => b.id === SUCCESSION_ID)).toBeFalse();
  });

  it('opens its popover by itself when the Campaign enters OFFER', () => {
    expect(popover.openId()).toBeNull();
    offer();
    expect(popover.openId()).toBe(SUCCESSION_ID);

    // Closed by the player, it stays closed until the next Succession.
    popover.close();
    game.update({ gameOutcome: 'EXODUS' });
    TestBed.flushEffects();
    expect(popover.openId()).toBeNull();
    game.update({ phase: 'ACTIVE' });
    TestBed.flushEffects();
    offer();
    expect(popover.openId()).toBe(SUCCESSION_ID);
  });

  it('shows the Toll as plain numbers, in a part of its own', () => {
    offer();
    expect(line('toll-total').value).toBe('24.0');
    expect(line('toll-total').heading).toBe('Toll');
    expect(line('toll-total').detail).toContain('Legacy 5');
    expect(line('toll-materials').value).toBe('3.0');
    expect(line('toll-new-ground').value).toBe('+2');
    expect(line('toll-residue').value).toBe('6.0');
    expect(tollLines(game.registryState()).map((l) => l.label)).toEqual([
      'Toll',
      'Food grown',
      'Materials banked',
      'Credits banked',
      'Relics banked',
      'Expeditions completed',
      'Ascent',
      'Guardians slain',
      'New Ground',
      'Residue carried on',
    ]);
  });

  it('says the pity floor after a Defeat', () => {
    offer(view(), { pending: { ...report, pity: true } });
    expect(line('toll-total').detail).toContain('the floor paid after a Defeat');
  });

  it('lists the Courses with their gates in words', () => {
    offer();
    expect(line('course-1').value).toBe('Open');
    expect(line('course-2').value).toBe('Closed');
    expect(line('course-2').detail).toBe(
      'Closed. Spend 8 Legacy in the Courses below (0 spent).',
    );
  });

  it('takes an open node and sends its id', () => {
    offer();
    expect(action('roll-SEED_GRAIN').label).toContain('Take Seed Grain');
    expect(popover.perform(SUCCESSION_ID, 'roll-SEED_GRAIN')).toBeTrue();
    expect(sendAction).toHaveBeenCalledWith(GameAction.ROLL_BUY, { id: 'SEED_GRAIN' });
  });

  it('cannot take a sealed node, or one whose Course is closed, and says why', () => {
    offer();
    for (const id of ['NARROW_STAIR', 'THE_DOWRY']) {
      expect(action(`roll-${id}`).usable).toBeFalse();
      expect(popover.perform(SUCCESSION_ID, `roll-${id}`)).toBeFalse();
    }
    expect(action('roll-NARROW_STAIR').reason).toContain('Sealed.');
    expect(action('roll-THE_DOWRY').reason).toBe('Its Course is closed.');
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('names the shortfall when the Legacy is not enough', () => {
    offer(view({ inheritances: [node('SEED_GRAIN', { whyNot: 'INSUFFICIENT_LEGACY' })] }));
    expect(action('roll-SEED_GRAIN').reason).toBe('It costs 3 Legacy. You hold 5.');
  });

  it('gives back a held node, and not one others stand on', () => {
    offer(
      view({
        inheritances: [
          node('HARDY_STOCK', { held: true, paid: 3, refundable: true }),
          node('SEED_GRAIN', { held: true, paid: 3, refundable: false }),
        ],
      }),
    );
    expect(action('roll-HARDY_STOCK').label).toContain('Give back HARDY_STOCK');
    popover.perform(SUCCESSION_ID, 'roll-HARDY_STOCK');
    popover.perform(SUCCESSION_ID, 'roll-SEED_GRAIN');
    expect(sendAction.calls.allArgs()).toEqual([
      [GameAction.ROLL_REFUND, { id: 'HARDY_STOCK' }],
    ]);
    expect(action('roll-SEED_GRAIN').reason).toBe('Others stand on it.');
  });

  it('shows the Dowry and Heir only when the server sends them, and answers a choice', () => {
    offer();
    expect(current().actions.some((a) => a.id.startsWith('choose-'))).toBeFalse();

    offer(
      view({
        choices: [
          option('DOWRY_CREDITS', 'DOWRY', { isDefault: true }),
          option('DOWRY_RELICS', 'DOWRY'),
          option('HEIR_WARRIOR', 'HEIR', { isDefault: true }),
          option('HEIR_ARCHER', 'HEIR', { selected: true }),
        ],
      }),
    );
    expect(line('choice-DOWRY').value).toBe('Credits');
    expect(line('choice-HEIR').value).toBe('Archer');
    expect(action('choose-DOWRY_CREDITS').label).toContain('(chosen)');
    expect(action('choose-HEIR_ARCHER').label).toContain('(chosen)');
    expect(action('choose-HEIR_WARRIOR').label).not.toContain('(chosen)');
    expect(action('choose-HEIR_ARCHER').usable).toBeFalse();

    popover.perform(SUCCESSION_ID, 'choose-DOWRY_RELICS');
    expect(sendAction).toHaveBeenCalledWith(GameAction.CHOOSE, { id: 'DOWRY_RELICS' });
  });

  it('asks before beginning, with the same words, and starts the Generation once', () => {
    offer();
    expect(action('begin').label).toBe('Begin Generation 2');
    expect(action('begin').confirm).toEqual({
      prompt: 'Generation 2 begins and the Roll is fixed. 5 Legacy goes unspent and is kept.',
      confirmLabel: 'Begin',
      cancelLabel: 'Not yet',
    });

    popover.perform(SUCCESSION_ID, 'begin');
    popover.perform(SUCCESSION_ID, 'begin');
    expect(sendAction.calls.allArgs()).toEqual([[GameAction.BEGIN_GENERATION, null]]);
    expect(action('begin').usable).toBeFalse();
    expect(action('begin').reason).toBe('The village is on the road.');
    expect(popover.perform(SUCCESSION_ID, 'begin')).toBeFalse();
  });

  it('lists the Chronicle newest first with outcome, Toll and fallen names', () => {
    offer(
      view({
        chronicle: [
          { generation: 1, outcome: 'DEFEAT', toll: 9, legacy: 1, highestFloor: 3, guardiansSlain: 0, expeditions: 1, fallen: [] },
          {
            generation: 2,
            outcome: 'EXODUS',
            toll: 24,
            legacy: 5,
            highestFloor: 12,
            guardiansSlain: 1,
            expeditions: 4,
            fallen: Array.from({ length: 8 }, (_, i) => ({ name: `Name${i}`, type: 'WARRIOR' })),
          },
        ],
      }),
    );
    const ids = current().lines.map((l) => l.id).filter((id) => id.startsWith('chronicle'));
    expect(ids).toEqual(['chronicle-2', 'chronicle-1']);
    expect(line('chronicle-2').value).toBe('The Flight. Toll 24.0');
    expect(line('chronicle-2').detail).toContain('Name4 (WARRIOR).');
    expect(line('chronicle-2').detail).not.toContain('Name5');
    expect(line('chronicle-1').value).toBe('The Second Company. Toll 9.0');
    expect(line('chronicle-1').detail).toContain('No names were kept.');
  });

  it('says when the Chronicle is empty, and lists Race records when there are some', () => {
    offer();
    expect(line('chronicle').label).toBe('Nothing is written yet.');
    expect(current().lines.some((l) => l.heading === 'Race record')).toBeFalse();
    offer(view(), {
      raceRecords: [
        {
          raceId: 'season-1',
          attempt: 1,
          finishFloor: 10,
          floor: 10,
          place: 3,
          tied: false,
          of: 17,
          seconds: 2468,
          fallen: 112,
          at: '2026-10-24T10:00:00Z',
          line: 'Race of 2026-10-24. Floor 10. 3rd of 17. 41:08. 112 fallen.',
        },
      ],
    });
    expect(current().lines.find((l) => l.heading === 'Race record')!.label).toBe(
      'Race of 2026-10-24. Floor 10. 3rd of 17. 41:08. 112 fallen.',
    );
  });

  it('never congratulates and never says Prestige', () => {
    offer();
    const v = current();
    const words = [
      v.title,
      ...v.gauges.flatMap((g) => [g.label, g.figure, g.leaves ?? '']),
      ...v.lines.flatMap((l) => [l.heading ?? '', l.label, l.value, l.detail ?? '']),
      ...v.actions.flatMap((a) => [a.label, a.reason ?? '', a.preview ?? '']),
    ].join(' ');
    expect(words).not.toMatch(/prestige|congrat/i);
  });

  it('is read on hover as the Generation and the Legacy left, not the whole card', () => {
    offer();
    const text = captionText(captionFor({ kind: 'building', id: SUCCESSION_ID }, game.registryState(), { startFloor: 0, held: false })!);
    expect(text).toContain('Generation 1 has ended');
    expect(text).toContain('Legacy to spend 5');
    expect(text).not.toContain('Guardians');
  });

  describe('in the popover and in the list view', () => {
    let fixture: ComponentFixture<BuildingPopover>;
    const el = () => fixture.nativeElement as HTMLElement;
    const show = () => {
      TestBed.flushEffects();
      fixture.detectChanges();
    };

    it('holds every action in the popover, scrolling inside it', () => {
      fixture = TestBed.createComponent(BuildingPopover);
      offer();
      show();
      const card = el().querySelector('.popover') as HTMLElement;
      expect(card).not.toBeNull();
      const text = card.textContent!;
      expect(text).toContain('The Succession');
      expect(text).toContain('Take Seed Grain');
      expect(text).toContain('Give back Hardy Stock');
      expect(text).toContain('Begin Generation 2');
      expect(text).toContain('The Tower keeps count.');
      expect(getComputedStyle(card).overflowY).toBe('auto');
      expect(card.style.maxHeight).not.toBe('');

      (Array.from(card.querySelectorAll('button')).find((b) =>
        b.textContent!.includes('Take Seed Grain'),
      ) as HTMLButtonElement).click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.ROLL_BUY, { id: 'SEED_GRAIN' });
    });

    it('keeps the same route in the list view', () => {
      const listFixture = TestBed.createComponent(ListView);
      TestBed.inject(ListViewService).show();
      offer();
      TestBed.flushEffects();
      listFixture.detectChanges();
      const root = listFixture.nativeElement as HTMLElement;
      const button = Array.from(root.querySelectorAll('button')).find((b) =>
        b.textContent!.includes('Begin Generation 2'),
      ) as HTMLButtonElement;
      expect(button).toBeDefined();
      button.click();
      listFixture.detectChanges();
      expect(root.textContent).toContain('Generation 2 begins and the Roll is fixed.');
      const yes = Array.from(root.querySelectorAll('button')).find(
        (b) => b.textContent!.trim() === 'Begin',
      ) as HTMLButtonElement;
      yes.click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.BEGIN_GENERATION, null);
    });
  });
});
