import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TowerDisplay } from './tower-display.component';

describe('Tower', () => {
  let component: TowerDisplay;
  let fixture: ComponentFixture<TowerDisplay>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TowerDisplay],
    }).compileComponents();

    fixture = TestBed.createComponent(TowerDisplay);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('no longer carries the Expedition HUD, which lives in the menu column', () => {
    fixture.componentRef.setInput('active', true);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-enemy-card')).toBeNull();
    expect(el.textContent).not.toContain('Floor');
  });
});
