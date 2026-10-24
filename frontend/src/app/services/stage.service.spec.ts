import { TestBed } from '@angular/core/testing';

import { QUALITY_STORAGE, StageService } from './stage.service';

describe('StageService', () => {
  beforeEach(() => localStorage.removeItem(QUALITY_STORAGE));
  afterEach(() => localStorage.removeItem(QUALITY_STORAGE));

  function make(): StageService {
    TestBed.resetTestingModule();
    return TestBed.inject(StageService);
  }

  it('chooses the pixel scale from the viewport until the quality setting overrides it', () => {
    const stage = make();
    expect(stage.quality()).toBe('auto');
    expect(stage.scale()).toBeGreaterThanOrEqual(1);
    expect(stage.dpr()).toBeCloseTo(1 / stage.scale(), 10);

    stage.setQuality(1);
    expect(stage.scale()).toBe(1);
    expect(stage.dpr()).toBe(1);
    stage.setQuality(3);
    expect(stage.scale()).toBe(3);
    stage.setQuality('auto');
    expect(stage.quality()).toBe('auto');
  });

  it('remembers the quality across a reload', () => {
    make().setQuality(3);
    expect(make().quality()).toBe(3);
  });

  it('hides and restores the menus', () => {
    const stage = make();
    expect(stage.menusHidden()).toBeFalse();
    stage.toggleMenus();
    expect(stage.menusHidden()).toBeTrue();
    stage.showMenus();
    expect(stage.menusHidden()).toBeFalse();
  });
});
