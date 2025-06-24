import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TowerDisplay } from './tower-display.component';

describe('Tower', () => {
  let component: TowerDisplay;
  let fixture: ComponentFixture<TowerDisplay>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TowerDisplay]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TowerDisplay);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
