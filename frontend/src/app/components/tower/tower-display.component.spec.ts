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

  it('draws the pillar mark only on a Finish Floor the server lists', () => {
    const mark = () => (fixture.nativeElement as HTMLElement).querySelector('.pillar-mark');
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
