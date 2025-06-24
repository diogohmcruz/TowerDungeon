import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Army } from './army';

describe('Army', () => {
  let component: Army;
  let fixture: ComponentFixture<Army>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Army]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Army);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
