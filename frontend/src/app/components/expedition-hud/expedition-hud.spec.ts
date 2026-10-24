import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExpeditionHud } from './expedition-hud';

describe('ExpeditionHud', () => {
  let fixture: ComponentFixture<ExpeditionHud>;
  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ExpeditionHud] }).compileComponents();
    fixture = TestBed.createComponent(ExpeditionHud);
    fixture.detectChanges();
  });

  it('is absent outside an Expedition', () => {
    expect(root().querySelector('.expedition-hud')).toBeNull();
  });

  it('shows the Floor, Party and the enemy groups during an Expedition', () => {
    const enemy = { id: 1, name: 'Slime', currentHealth: 3, stats: { type: 'SLIME', health: 3, damage: 1 } } as any;
    fixture.componentRef.setInput('active', true);
    fixture.componentRef.setInput('tower', {
      currentFloor: 4,
      floors: { 4: { boss: true, enemies: [enemy, { ...enemy, id: 2 }] } },
    } as any);
    fixture.detectChanges();
    const text = root().textContent ?? '';
    expect(text).toContain('Floor 4');
    expect(text).toContain('GUARDIAN');
    expect(text).toContain('Party');
    expect(text).toContain('×2');
    expect(root().querySelectorAll('app-enemy-card').length).toBe(2);
  });

  it('says the Floor is clear when no enemy remains', () => {
    fixture.componentRef.setInput('active', true);
    fixture.componentRef.setInput('tower', { currentFloor: 2, floors: { 2: { enemies: [] } } } as any);
    fixture.detectChanges();
    expect(root().textContent).toContain('Floor clear. Advancing.');
  });

  it('draws the pillar mark only on a Finish Floor the server lists', () => {
    const mark = () => root().querySelector('.pillar-mark');
    fixture.componentRef.setInput('active', true);
    fixture.componentRef.setInput('tower', { currentFloor: 10, floors: {} } as any);
    fixture.componentRef.setInput('pillarMarks', []);
    fixture.detectChanges();
    expect(mark()).toBeNull();
    fixture.componentRef.setInput('pillarMarks', [10]);
    fixture.detectChanges();
    expect(mark()?.textContent).toContain('Finish Floor');
    fixture.componentRef.setInput('tower', { currentFloor: 9, floors: {} } as any);
    fixture.detectChanges();
    expect(mark()).toBeNull();
  });
});
