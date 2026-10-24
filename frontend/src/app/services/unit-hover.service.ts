import { Injectable, signal } from '@angular/core';

import { UnitHover } from '../three/figures/unit-hover';

/**
 * The figure under the pointer in the Tower scene, for the caption beside the pointer.
 * The scene sets it from the same hit-test a click uses; nothing here holds time.
 */
@Injectable({ providedIn: 'root' })
export class UnitHoverService {
  private readonly _hover = signal<UnitHover | null>(null);
  readonly hover = this._hover.asReadonly();

  set(next: UnitHover | null): void {
    const now = this._hover();
    if (
      now === next ||
      (now &&
        next &&
        now.id === next.id &&
        now.caption === next.caption &&
        now.x === next.x &&
        now.y === next.y)
    ) {
      return;
    }
    this._hover.set(next);
  }
}
