import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RaceRow, RaceTableView } from '../../interfaces/race';
import { RaceService } from '../../services/race.service';
import { RaceTable, timelineLines } from './race-table';

const row = (over: Partial<RaceRow> = {}): RaceRow => ({
  place: 1,
  tied: false,
  alias: 'Anaxagoras',
  finished: true,
  status: 'FINISHED',
  seconds: 2468,
  highestFloor: 10,
  arrivalSeconds: 2468,
  attempt: 1,
  cost: { expeditions: 3, wipes: 1, unitsLost: 12, lootBanked: 40 },
  timeline: [
    { floor: 1, seconds: 30 },
    { floor: 15, seconds: 1100 },
  ],
  you: false,
  ...over,
});

describe('timelineLines', () => {
  const me = row({
    alias: 'Ada',
    you: true,
    place: 2,
    finished: false,
    status: 'TIMED_OUT',
    timeline: [{ floor: 15, seconds: 1361 }],
  });
  const other = row({ alias: 'Anaxagoras' });
  const absent = row({ alias: 'Zeno', place: 3, timeline: [{ floor: 1, seconds: 10 }] });

  it('writes who reached the Floor when, and the viewer after them', () => {
    expect(timelineLines([other, me, absent], 15)).toEqual([
      'Anaxagoras reached Floor 15 at 18:20, you at 22:41.',
      'You reached Floor 15 at 22:41.',
      'Zeno did not reach Floor 15.',
    ]);
  });

  it('says so when the viewer did not reach it', () => {
    expect(timelineLines([other, me], 1)).toEqual([
      'Anaxagoras reached Floor 1 at 0:30, you did not reach it.',
      'You did not reach Floor 1.',
    ]);
  });
});

describe('RaceTable', () => {
  let fixture: ComponentFixture<RaceTable>;
  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent!.replace(/\s+/g, ' ');

  async function open(read: () => Promise<RaceTableView>) {
    TestBed.configureTestingModule({
      providers: [{ provide: RaceService, useValue: { table: read } }],
    });
    fixture = TestBed.createComponent(RaceTable);
    fixture.componentRef.setInput('raceId', 'season-1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const view: RaceTableView = {
    raceId: 'season-1',
    hands: [
      row({ place: 1, tied: true, alias: 'Anaxagoras' }),
      row({
        place: 1,
        tied: true,
        alias: 'Ada',
        you: true,
        cost: { expeditions: 9, wipes: 4, unitsLost: 112, lootBanked: 7.5 },
        timeline: [{ floor: 15, seconds: 1361 }],
      }),
      row({
        place: 3,
        alias: 'Zeno',
        finished: false,
        status: 'DEFEATED',
        highestFloor: 6,
        arrivalSeconds: 900,
        seconds: 1500,
      }),
    ],
    agents: [row({ place: 1, alias: 'Probe', timeline: [{ floor: 1, seconds: 5 }] })],
  };

  it('shows Order of Arrival and Cost as separate columns, with ties as ties', async () => {
    await open(() => Promise.resolve(view));
    const heads = [...el().querySelectorAll('thead th')].map((th) => th.textContent!.trim());
    expect(heads).toContain('Order of Arrival');
    expect(heads).toContain('Cost');
    expect(heads).toEqual(
      jasmine.arrayContaining(['Place', 'Result', 'Expeditions', 'Wipes', 'Units lost', 'Loot banked']),
    );
    const places = [...el().querySelectorAll('td.place')].map((td) => td.textContent!.trim());
    expect(places.slice(0, 3)).toEqual(['1st, tied', '1st, tied', '3rd']);
    expect(text()).toContain('Ada (you)');
    expect(text()).toContain('Finished in 41:08');
    expect(text()).toContain('Floor 6 at 15:00. No one was left to climb');
    expect(text()).toContain('112');
    expect(text()).toContain('7.5');
  });

  it('adds nothing up and ends on the Tower keeping count', async () => {
    await open(() => Promise.resolve(view));
    expect(text()).not.toMatch(/total|score|rank|congrat|well done/i);
    expect(text()).toContain('It does not change it, and it does not break a tie.');
    expect(text().trim().endsWith('The Tower keeps count.')).toBeTrue();
  });

  it('tables agents apart from hands', async () => {
    await open(() => Promise.resolve(view));
    const headings = [...el().querySelectorAll('h3')].map((h) => h.textContent!.trim());
    expect(headings).toEqual(['Hands', 'Agents']);
    expect(el().querySelectorAll('table').length).toBe(2);
  });

  it('writes the timeline for the Floor the viewer last reached', async () => {
    await open(() => Promise.resolve(view));
    expect(text()).toContain('Anaxagoras reached Floor 15 at 18:20, you at 22:41.');
  });

  it('says the table is shown once an attempt is over, on a 403', async () => {
    await open(() => Promise.reject({ status: 403 }));
    expect(text()).toContain('The table is shown once an attempt is over.');
    expect(el().querySelector('table')).toBeNull();
  });
});
