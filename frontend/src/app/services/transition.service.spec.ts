import { TestBed } from '@angular/core/testing';

import { TOTAL_MS, WIPE_HOLD_MS } from '../three/transition';
import { GameStateService } from './game-state.service';
import { TransitionService } from './transition.service';

class Still extends TransitionService {
  protected override schedule = () => 0;
}

describe('TransitionService', () => {
  let service: TransitionService;
  let state: GameStateService;
  let reduced: boolean;

  beforeEach(() => {
    reduced = false;
    spyOn(window, 'matchMedia').and.callFake(
      (q: string) =>
        ({ matches: reduced && q.includes('reduce') }) as MediaQueryList,
    );
    TestBed.configureTestingModule({
      providers: [{ provide: TransitionService, useClass: Still }],
    });
    state = TestBed.inject(GameStateService);
    service = TestBed.inject(TransitionService);
    TestBed.flushEffects();
  });

  it('starts on the Village and plays Set out: dissolve, swap, rise, then rests', () => {
    expect(service.towerShown()).toBeFalse();
    state.update({ expeditionActive: true });
    TestBed.flushEffects();
    expect(service.running).toBeTrue();
    service.advance(200);
    expect(service.towerShown()).toBeFalse();
    expect(service.cover()).toBeGreaterThan(0);
    service.advance(600);
    expect(service.towerShown()).toBeTrue();
    expect(service.drop).toBeGreaterThan(0);
    service.advance(TOTAL_MS + 1);
    expect(service.running).toBeFalse();
    expect(service.towerShown()).toBeTrue();
    expect(service.cover()).toBe(0);
    expect(service.drop).toBe(0);
  });

  it('keeps the Tower on screen while Extraction drops the camera, then shows the Village', () => {
    state.update({ expeditionActive: true });
    TestBed.flushEffects();
    service.finish();
    state.update({ expeditionActive: false });
    TestBed.flushEffects();
    service.advance(300);
    expect(service.towerShown()).toBeTrue();
    expect(service.drop).toBeGreaterThan(0);
    service.advance(TOTAL_MS + 1);
    expect(service.towerShown()).toBeFalse();
  });

  it('holds a Wipe on the empty stair before the drop', () => {
    state.update({ expeditionActive: true });
    TestBed.flushEffects();
    service.finish();
    state.update({
      expeditionActive: false,
      lastRunSummary: { outcome: 'WIPED' } as never,
    });
    TestBed.flushEffects();
    service.advance(WIPE_HOLD_MS - 1);
    expect(service.drop).toBe(0);
    expect(service.cover()).toBe(0);
    service.advance(WIPE_HOLD_MS + 800);
    expect(service.drop).toBeGreaterThan(0);
  });

  it('is an instant cut under reduced motion', () => {
    reduced = true;
    state.update({ expeditionActive: true });
    TestBed.flushEffects();
    expect(service.running).toBeFalse();
    expect(service.towerShown()).toBeTrue();
    expect(service.cover()).toBe(0);
    expect(service.drop).toBe(0);
  });

  it('a new transition finishes the previous one first', () => {
    state.update({ expeditionActive: true });
    TestBed.flushEffects();
    service.advance(100);
    state.update({ expeditionActive: false });
    TestBed.flushEffects();
    service.advance(TOTAL_MS + 1);
    expect(service.towerShown()).toBeFalse();
    expect(service.running).toBeFalse();
  });
});
