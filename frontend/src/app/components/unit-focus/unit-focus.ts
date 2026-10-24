import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';

import { Squad } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { Tower } from '../../interfaces/tower';
import { StageService } from '../../services/stage.service';
import { ZoomService } from '../../services/zoom.service';
import { UnitFocus } from '../../three/figures/figure-pick';
import { CharacterCard } from '../character-card/character-card';
import { EnemyCard } from '../enemy-card/enemy-card';
import { CardFit } from './card-fit';
import { placeCard } from './card-placement';

/** The member a unit view is on, found among the Party or the current Floor's garrison. */
export function focusedMember(
  focus: UnitFocus | null,
  party: Squad | undefined,
  tower: Tower | undefined,
): Member | null {
  if (!focus) {
    return null;
  }
  const pool: Member[] =
    focus.kind === 'unit'
      ? (Object.values(party ?? {})
          .flat()
          .filter(Boolean) as Member[])
      : (tower?.floors?.[tower.currentFloor]?.enemies ?? []);
  return pool.find((member) => member.id === focus.id) ?? null;
}

/**
 * The floating card of the unit view (#95): the character card for the Party,
 * the enemy card for the garrison, beside the figure and following it. Time is
 * held while it shows, so nothing in it dies or ends.
 */
@Component({
  selector: 'app-unit-focus',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CharacterCard, EnemyCard, CardFit],
  template: `
    @if (view(); as v) {
      <aside
        class="unit-focus"
        appCardFit
        [style.left.px]="place().left"
        [style.top.px]="place().top"
        [style.width.px]="place().width"
        [style.max-height.px]="place().maxHeight"
        [attr.aria-label]="v.member.name"
      >
        @if (v.kind === 'unit') {
          <app-character-card [member]="v.member" [detailed]="true" />
        } @else {
          <app-enemy-card [enemy]="v.member" [detailed]="true" />
        }
      </aside>
    }
  `,
  styles: `
    .unit-focus {
      position: absolute;
      z-index: 2;
      overflow-x: hidden;
      overflow-y: auto;
      padding: 8px;
      box-sizing: border-box;
      /* The same hard-edged panel as the rest: ink on ink, no rounding. */
      border: 1px solid var(--bone);
      background: var(--panel);
      scrollbar-width: thin;
      scrollbar-color: var(--bone) transparent;
    }
    /* More below: a bone edge at the foot, until the end is scrolled to. */
    .unit-focus.more-below {
      box-shadow: inset 0 -14px 10px -10px
        color-mix(in srgb, var(--bone) 55%, transparent);
    }
  `,
})
export class UnitFocusCard {
  party = input<Squad>();
  tower = input<Tower | undefined>();

  private readonly zoom = inject(ZoomService);
  private readonly stage = inject(StageService);

  /** Which card, and for whom. */
  protected readonly view = computed(() => {
    const focus = this.zoom.focus();
    const member = focusedMember(focus, this.party(), this.tower());
    return focus && member ? { kind: focus.kind, member } : null;
  });

  private readonly fit = viewChild(CardFit);

  protected readonly place = computed(() => {
    const { width, height } = this.stage.viewport();
    return placeCard(
      this.zoom.anchor(),
      width,
      height,
      this.fit()?.height() || undefined,
    );
  });
}
