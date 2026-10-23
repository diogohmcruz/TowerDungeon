import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Toll, TollForecast } from '../../interfaces/game-state';
import { TollBar } from './toll-bar';

describe('TollBar', () => {
  let fixture: ComponentFixture<TollBar>;
  let leaves: number;

  const toll = (overrides: Partial<Toll> = {}): Toll => ({
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
    ...overrides,
  });

  const forecast = (overrides: Partial<TollForecast> = {}): TollForecast => ({
    toll: toll(),
    pointsDivisor: 9,
    nextLegacyAt: 27,
    exodusMinimum: 3,
    canExodus: false,
    ...overrides,
  });

  const show = (f: TollForecast) => {
    fixture.componentRef.setInput('forecast', f);
    fixture.detectChanges();
  };
  const root = () => fixture.nativeElement as HTMLElement;
  const button = () =>
    root().querySelector('.toll-exodus') as HTMLButtonElement | null;
  const text = () => root().textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TollBar] });
    fixture = TestBed.createComponent(TollBar);
    leaves = 0;
    fixture.componentInstance.leave.subscribe(() => leaves++);
  });

  it('reads the Toll and the next Legacy as plain numbers', () => {
    show(forecast());

    expect(text()).toContain('Toll 24.0 / next Legacy at 27.0');
    expect(text()).toContain('If you left now: about 2 Legacy.');
  });

  it('fills the bar by the share between the last and the next point', () => {
    show(forecast());

    const fill = root().querySelector('.toll-fill') as HTMLElement;
    expect(fill.style.width).toBe('67%');
    expect(
      root().querySelector('[role=progressbar]')?.getAttribute('aria-valuenow'),
    ).toBe('67');
  });

  it('breaks the Toll down by counter with New Ground and the residue', () => {
    show(forecast());

    const lines = Array.from(root().querySelectorAll('.toll-line')).map((li) =>
      Array.from(li.children)
        .map((c) => c.textContent?.trim())
        .join(' '),
    );
    expect(lines).toContain('Food grown 2.0');
    expect(lines).toContain('Ascent 10.0');
    expect(lines).toContain('New Ground +2');
    expect(lines).toContain('Residue carried on 6.0');
  });

  it('closes Exodus below the minimum and says so in words', () => {
    show(forecast());

    expect(button()?.disabled).toBeTrue();
    expect(text()).toContain('Exodus opens at 3 Legacy.');
  });

  it('closes Exodus while a Party is out and says to Extract first', () => {
    show(forecast({ toll: toll({ legacy: 5 }), canExodus: false }));

    expect(button()?.disabled).toBeTrue();
    expect(text()).toContain('Extract first.');
  });

  it('asks before leaving, and Stay changes nothing', () => {
    show(forecast({ toll: toll({ legacy: 5 }), canExodus: true }));

    button()!.click();
    fixture.detectChanges();

    expect(text()).toContain('The village leaves in the night.');
    expect(text()).toContain('pays about 5 Legacy');
    expect(leaves).toBe(0);

    (root().querySelectorAll('.toll-confirm button')[1] as HTMLElement).click();
    fixture.detectChanges();

    expect(leaves).toBe(0);
    expect(root().querySelector('.toll-confirm')).toBeNull();
  });

  it('leaves only once confirmed', () => {
    show(forecast({ toll: toll({ legacy: 5 }), canExodus: true }));

    button()!.click();
    fixture.detectChanges();
    (root().querySelectorAll('.toll-confirm button')[0] as HTMLElement).click();

    expect(leaves).toBe(1);
  });
});
