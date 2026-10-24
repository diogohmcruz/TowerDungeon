import { Injectable, signal } from '@angular/core';

/**
 * The hook for the unit view (a later issue, the closeup zoom): choosing a unit in an army
 * list lands here. Nothing reads `chosen` yet; the zoom will, and will call `release`.
 */
@Injectable({ providedIn: 'root' })
export class UnitViewService {
  private readonly _chosen = signal<number | null>(null);
  /** The id of the unit last chosen from an army list, or null. */
  readonly chosen = this._chosen.asReadonly();

  choose(unitId: number): void {
    this._chosen.set(unitId);
  }

  release(): void {
    this._chosen.set(null);
  }
}
