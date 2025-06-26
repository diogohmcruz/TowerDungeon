import { TestBed } from '@angular/core/testing';

import { UnitStatsService } from './unit-stats.service';

describe('UnitStatsService', () => {
  let service: UnitStatsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(UnitStatsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
