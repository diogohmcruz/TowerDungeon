import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VillageManagement } from './village-management';

describe('VillageManagement', () => {
  let component: VillageManagement;
  let fixture: ComponentFixture<VillageManagement>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VillageManagement],
    }).compileComponents();

    fixture = TestBed.createComponent(VillageManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
