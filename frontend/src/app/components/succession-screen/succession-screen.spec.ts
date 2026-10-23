import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuccessionReport } from '../../interfaces/game-state';
import {
  SuccessionNode,
  SuccessionOption,
  SuccessionView,
} from '../../interfaces/succession';
import { SuccessionScreen } from './succession-screen';

describe('SuccessionScreen', () => {
  let fixture: ComponentFixture<SuccessionScreen>;
  let bought: string[];
  let refunded: string[];
  let chosen: string[];
  let begins: number;

  const node = (
    id: string,
    over: Partial<SuccessionNode> = {},
  ): SuccessionNode => ({
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

  const view = (over: Partial<SuccessionView> = {}): SuccessionView => ({
    generation: 1,
    legacy: 5,
    spent: 0,
    courses: [
      { course: 1, gate: 0, spentBelow: 0, open: true },
      { course: 2, gate: 8, spentBelow: 0, open: false },
      { course: 3, gate: 20, spentBelow: 0, open: false },
      { course: 4, gate: 40, spentBelow: 0, open: false },
    ],
    inheritances: [
      node('SEED_GRAIN', { name: 'Seed Grain' }),
      node('HARDY_STOCK', {
        name: 'Hardy Stock',
        held: true,
        paid: 3,
        refundable: true,
      }),
      node('THE_DOWRY', {
        name: 'The Dowry',
        course: 2,
        whyNot: 'COURSE_CLOSED',
      }),
      node('NARROW_STAIR', {
        name: 'Narrow Stair',
        course: 3,
        available: false,
        whyNot: 'UNAVAILABLE',
      }),
      node('SECOND_PACT', {
        name: 'Second Pact',
        course: 4,
        available: false,
        whyNot: 'UNAVAILABLE',
      }),
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

  const show = (v: SuccessionView, r: SuccessionReport | null = report) => {
    fixture.componentRef.setInput('view', v);
    fixture.componentRef.setInput('report', r);
    fixture.detectChanges();
  };
  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => (root().textContent ?? '').replace(/\s+/g, ' ');
  const nodeEl = (id: string) =>
    root().querySelector(`[data-node="${id}"]`) as HTMLElement;
  const click = (el: Element | null) => {
    (el as HTMLElement).click();
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuccessionScreen] });
    fixture = TestBed.createComponent(SuccessionScreen);
    bought = [];
    refunded = [];
    chosen = [];
    begins = 0;
    const c = fixture.componentInstance;
    c.buy.subscribe((id) => bought.push(id));
    c.refund.subscribe((id) => refunded.push(id));
    c.choose.subscribe((id) => chosen.push(id));
    c.begin.subscribe(() => begins++);
  });

  it('shows the Toll as plain numbers and ends on the Tower keeping count', () => {
    show(view());

    expect(text()).toContain('Toll 24.0. Legacy 5');
    expect(text()).toContain('Guardians slain');
    expect(text()).toContain('Residue carried on');
    expect(text().trim().endsWith('The Tower keeps count.')).toBeTrue();
    expect(text()).not.toMatch(/prestige|congrat/i);
  });

  it('lists all four Courses with their gates and states in words', () => {
    show(view());

    expect(root().querySelectorAll('.course').length).toBe(4);
    expect(text()).toContain('Closed. Spend 8 Legacy');
    expect(nodeEl('NARROW_STAIR').textContent).toContain('Sealed');
    expect(nodeEl('HARDY_STOCK').textContent).toContain('Held');
  });

  it('buys an open node and sends its id', () => {
    show(view());

    click(nodeEl('SEED_GRAIN').querySelector('.node-buy'));

    expect(bought).toEqual(['SEED_GRAIN']);
  });

  it('cannot buy a sealed node or one whose Course is closed', () => {
    show(view());

    for (const id of ['NARROW_STAIR', 'SECOND_PACT', 'THE_DOWRY']) {
      const button = nodeEl(id).querySelector(
        '.node-buy',
      ) as HTMLButtonElement;
      expect(button.disabled).toBeTrue();
      button.click();
    }
    expect(bought).toEqual([]);
    expect(nodeEl('THE_DOWRY').textContent).toContain('Its Course is closed.');
    expect(nodeEl('NARROW_STAIR').textContent).toContain('Sealed.');
  });

  it('names the shortfall when the Legacy is not enough', () => {
    show(
      view({
        inheritances: [node('SEED_GRAIN', { whyNot: 'INSUFFICIENT_LEGACY' })],
      }),
    );

    expect(nodeEl('SEED_GRAIN').textContent).toContain(
      'It costs 3 Legacy. You hold 5.',
    );
  });

  it('gives back a held node, and not one others stand on', () => {
    show(
      view({
        inheritances: [
          node('HARDY_STOCK', { held: true, paid: 3, refundable: true }),
          node('SEED_GRAIN', { held: true, paid: 3, refundable: false }),
        ],
      }),
    );

    click(nodeEl('HARDY_STOCK').querySelector('.node-refund'));
    const held = nodeEl('SEED_GRAIN').querySelector(
      '.node-refund',
    ) as HTMLButtonElement;
    expect(held.disabled).toBeTrue();
    held.click();

    expect(refunded).toEqual(['HARDY_STOCK']);
  });

  it('shows the Dowry and Heir choices only when the server sends them', () => {
    show(view());
    expect(root().querySelector('.choices')).toBeNull();

    const option = (
      id: string,
      group: string,
      over: Partial<SuccessionOption> = {},
    ): SuccessionOption => ({
      id,
      group,
      selected: false,
      isDefault: false,
      ...over,
    });
    show(
      view({
        choices: [
          option('DOWRY_CREDITS', 'DOWRY', { isDefault: true }),
          option('DOWRY_RELICS', 'DOWRY'),
          option('HEIR_WARRIOR', 'HEIR', { isDefault: true }),
          option('HEIR_ARCHER', 'HEIR', { selected: true }),
        ],
      }),
    );

    expect(root().querySelectorAll('.choices').length).toBe(2);
    expect(text()).toContain('Credits (chosen)');
    expect(text()).toContain('Archer (chosen)');
    expect(text()).not.toContain('Warrior (chosen)');

    click(root().querySelectorAll('.choice input')[1]);
    expect(chosen).toEqual(['DOWRY_RELICS']);
  });

  it('asks before beginning, and a double click starts the Generation once', () => {
    show(view());

    click(root().querySelector('.begin'));
    expect(begins).toBe(0);
    expect(root().querySelector('.begin-confirm')).not.toBeNull();

    const yes = root().querySelector('.begin-yes') as HTMLButtonElement;
    yes.click();
    yes.click();
    fixture.detectChanges();

    expect(begins).toBe(1);
    expect((root().querySelector('.begin') as HTMLButtonElement).disabled).toBeTrue();
    click(root().querySelector('.begin'));
    expect(root().querySelector('.begin-confirm')).toBeNull();
    expect(begins).toBe(1);
  });

  it('can be declined', () => {
    show(view());

    click(root().querySelector('.begin'));
    click(root().querySelector('.begin-no'));

    expect(begins).toBe(0);
    expect(root().querySelector('.begin-confirm')).toBeNull();
  });

  it('lists the Chronicle newest first with outcome, Toll and fallen names', () => {
    show(
      view({
        chronicle: [
          {
            generation: 1,
            outcome: 'DEFEAT',
            toll: 9,
            legacy: 1,
            highestFloor: 3,
            guardiansSlain: 0,
            expeditions: 1,
            fallen: [],
          },
          {
            generation: 2,
            outcome: 'EXODUS',
            toll: 24,
            legacy: 5,
            highestFloor: 12,
            guardiansSlain: 1,
            expeditions: 4,
            fallen: [
              { name: 'Aldric', type: 'WARRIOR' },
              { name: 'Brisa', type: 'ARCHER' },
            ],
          },
        ],
      }),
    );

    const entries = root().querySelectorAll('.chronicle-entry');
    expect(entries.length).toBe(2);
    expect(entries[0].textContent).toContain('Generation 2');
    expect(entries[0].textContent).toContain('The Flight');
    expect(entries[0].textContent).toContain('Toll 24.0');
    expect(entries[0].textContent).toContain('Aldric (WARRIOR), Brisa (ARCHER)');
    expect(entries[1].textContent).toContain('The Second Company');
    expect(entries[1].textContent).toContain('No names were kept.');
  });

  it('caps the fallen names shown at five', () => {
    const fallen = Array.from({ length: 8 }, (_, i) => ({
      name: `Name${i}`,
      type: 'WARRIOR',
    }));
    show(
      view({
        chronicle: [
          {
            generation: 1,
            outcome: 'DEFEAT',
            toll: 1,
            legacy: 1,
            highestFloor: 1,
            guardiansSlain: 0,
            expeditions: 0,
            fallen,
          },
        ],
      }),
    );

    expect(root().querySelectorAll('.fallen').length).toBe(5);
  });
});
