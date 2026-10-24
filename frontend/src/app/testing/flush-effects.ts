import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * `TestBed.flushEffects()` for a spec. `TestBed.flushEffects()` ticks the ApplicationRef from outside
 * the Angular zone; the effect scheduler then runs its effects in a zone turn that, on leaving,
 * makes the zone tick again while the first tick still runs: NG0101 "ApplicationRef.tick is called
 * recursively". Run inside the zone, as the app's own tick runs, the nested leave raises nothing.
 */
export function flushEffects(): void {
  TestBed.inject(NgZone).run(() => TestBed.tick());
}
