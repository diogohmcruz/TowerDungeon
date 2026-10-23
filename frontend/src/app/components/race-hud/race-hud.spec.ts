import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TrialRun } from '../../interfaces/race';
import { RaceHud } from './race-hud';

describe('RaceHud', () => {
  let fixture: ComponentFixture<RaceHud>;
  let leaves: number;

  const trial = (over: Partial<TrialRun> = {}): TrialRun => ({
    raceId: 'season-1',
    attempt: 1,
    finishFloor: 10,
    limitTicks: 5400,
    ticksSinceStart: 754,
    status: 'RACING',
    expeditions: 2,
    wipes: 1,
    arrivals: {},
    ...over,
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');

  beforeEach(() => {
    leaves = 0;
    fixture = TestBed.createComponent(RaceHud);
    fixture.componentInstance.leave.subscribe(() => leaves++);
  });

  it('draws nothing outside a Trial', () => {
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.race-hud')).toBeNull();
    expect(text().trim()).toBe('');
  });

  it('shows the clock, the Finish Floor and the Cost so far inside one', () => {
    fixture.componentRef.setInput('trial', trial());
    fixture.componentRef.setInput('highestFloor', 4);
    fixture.componentRef.setInput('cost', {
      expeditions: 2,
      wipes: 1,
      unitsLost: 12,
      lootBanked: 40,
    });
    fixture.detectChanges();
    const part = (selector: string) =>
      (fixture.nativeElement as HTMLElement).querySelector(selector)!.textContent!.trim();
    expect(part('.hud-clock')).toBe('12:34');
    expect(text()).toContain('of 90:00');
    expect(part('.hud-finish')).toBe('10');
    expect(text()).toContain('Highest Floor 4');
    expect(text()).toContain('Expeditions 2. Wipes 1. Units lost 12. Loot banked 40.');
    expect(text()).toContain('Only the hours spent here are counted.');
  });

  it('adds nothing up: there is no total, score or rank', () => {
    fixture.componentRef.setInput('trial', trial());
    fixture.componentRef.setInput('cost', { expeditions: 2, wipes: 1, unitsLost: 12, lootBanked: 40 });
    fixture.detectChanges();
    expect(text()).not.toMatch(/total|score|rank|win/i);
  });

  it('says in words that the attempt is over', () => {
    fixture.componentRef.setInput('trial', trial({ status: 'TIMED_OUT' }));
    fixture.detectChanges();
    expect(text()).toContain('The clock ran out.');
    expect(text()).not.toContain('Only the hours');
  });

  it('leaves the Trial on request', () => {
    fixture.componentRef.setInput('trial', trial());
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.hud-leave')!.click();
    expect(leaves).toBe(1);
  });
});
