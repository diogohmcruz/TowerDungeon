import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Army } from './army';
import { UnitViewService } from '../../services/unit-view.service';

describe('Army', () => {
  let component: Army;
  let fixture: ComponentFixture<Army>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Army],
    }).compileComponents();

    fixture = TestBed.createComponent(Army);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('hands a unit chosen by its name button to the unit view hook', () => {
    const unit = {
      id: 7,
      name: 'Maren',
      currentHealth: 5,
      maxHealth: 10,
      stats: { type: 'KNIGHT', health: 10, damage: 2 },
    } as any;
    fixture.componentRef.setInput('army', { KNIGHT: [unit] });
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'button.toggle',
    ) as HTMLButtonElement;
    button.click();
    expect(TestBed.inject(UnitViewService).chosen()).toBe(7);
  });
});
