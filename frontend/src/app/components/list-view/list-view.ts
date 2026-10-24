import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  untracked,
  viewChild,
} from '@angular/core';

import { computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { GameAction } from '../../interfaces/game-action.enum';
import { BodyPartKind } from '../../interfaces/member';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { LaunchService } from '../../services/launch.service';
import { ListViewService } from '../../services/list-view.service';
import { PopoverService } from '../../services/popover.service';
import { ZoomService } from '../../services/zoom.service';
import { endingOf, SUCCESSION_ID } from '../../three/village/succession-building';
import { villageFacts } from '../../three/village/village-facts';
import { ActionRow } from '../action-row/action-row';
import { Army } from '../army/army';
import { ExpeditionHud } from '../expedition-hud/expedition-hud';
import { ShortcutCard } from '../shortcut-card/shortcut-card';

/** Keys that open the list and close it. Not while typing, and never with a modifier. */
export function listKeyIntent(event: KeyboardEvent): 'toggle' | 'close' | null {
  const key = event.key.toLowerCase();
  if (
    (key !== 'l' && key !== 'escape') ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    event.defaultPrevented
  ) {
    return null;
  }
  const target = event.target as HTMLElement | null;
  if (
    target &&
    (target.isContentEditable ||
      ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  ) {
    return null;
  }
  return key === 'l' ? 'toggle' : 'close';
}

/**
 * The list view (architecture doc 5.6): every registered building with its
 * gauges and actions, as plain DOM for the keyboard and screen readers. Hidden
 * until `L` (or the skip button, or a stored choice). Its actions are the
 * popover's: both call `PopoverService.perform`, so the command, the refusal
 * and the reason in words are the same.
 */
@Component({
  selector: 'app-list-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionRow, ExpeditionHud, Army, ShortcutCard, DecimalPipe],
  template: `
    <button
      type="button"
      class="skip"
      aria-keyshortcuts="L"
      (click)="list.show()"
    >
      Open the list view of buildings and actions (L)
    </button>
    @if (list.open()) {
      <section
        class="list"
        role="region"
        aria-label="List view of buildings"
        [hidden]="zoom.level() !== 'battle'"
      >
        <header>
          <h2>Buildings</h2>
          <button
            #close
            type="button"
            class="btn"
            aria-keyshortcuts="L Escape"
            (click)="list.hide()"
          >
            Close the list (L)
          </button>
        </header>
        <label class="remember">
          <input
            type="checkbox"
            [checked]="remembered"
            (change)="remember($event)"
          />
          Open this list when the game starts
        </label>
        <!-- Time: a Held Party waits for the player; the top bar carries the same button. -->
        @if (held()) {
          <section class="building" aria-label="The Party">
            <h3>The Party</h3>
            <p class="line">The Party has not moved.</p>
            <button type="button" class="btn" (click)="launch.resume()">Resume</button>
          </section>
        }
        <!-- How the Campaign ended, when the Succession is not open to carry the words. -->
        @if (ending(); as end) {
          <section class="building" aria-label="The Campaign has ended">
            <h3>{{ end.heading }}</h3>
            <p class="line">{{ end.text }}</p>
            @if (game().pending; as report) {
              <p class="line">
                Toll {{ report.toll.toll | number: '1.1-1' }}. Legacy {{ report.legacy }}.
              </p>
            }
          </section>
        }
        @for (b of popover.buildings(); track b.id) {
          <section class="building" [attr.aria-labelledby]="'list-' + b.id">
            <h3 [id]="'list-' + b.id">{{ b.title }}</h3>
            <dl>
              @for (g of b.gauges; track g.id) {
                <dt>{{ g.label }}</dt>
                <dd>
                  {{ g.figure }}
                  @if (g.rate) {
                    <span class="rate">({{ g.rate }})</span>
                  }
                  @if (g.leaves) {
                    <span class="leaves">{{ g.leaves }}</span>
                  }
                </dd>
              }
            </dl>
            @for (l of b.lines; track l.id) {
              @if (l.heading) {
                <h4>{{ l.heading }}</h4>
              }
              <p class="line">
                <span class="name">{{ l.label }}</span>
                {{ l.value }}
                @if (l.detail) {
                  <span class="leaves">{{ l.detail }}</span>
                }
              </p>
            }
            @for (s of b.sections; track s.id) {
              <section class="written" [attr.aria-label]="s.heading ?? b.title">
                @if (s.heading) {
                  <h4>{{ s.heading }}</h4>
                }
                @if (s.lines.length > 0) {
                  <ul class="written-lines">
                    @for (l of s.lines; track $index) {
                      <li [class.loss]="l.loss">{{ l.mark }} {{ l.text }}</li>
                    }
                  </ul>
                } @else {
                  <p class="leaves">{{ s.empty }}</p>
                }
              </section>
            }
            @for (a of b.actions; track a.id) {
              <app-action-row
                [idPrefix]="'list-reason-' + b.id"
                [action]="a"
                [alwaysPreview]="true"
                (perform)="run(b.id, $event)"
              />
            }
          </section>
        } @empty {
          <p>No building has anything to show yet.</p>
        }
        <!-- What the Stock card said that no building states. -->
        @for (g of facts(); track g.id) {
          <section class="building facts" [attr.aria-labelledby]="'facts-' + g.id">
            <h3 [id]="'facts-' + g.id">{{ g.heading }}</h3>
            @for (line of g.lines; track $index) {
              <p class="line">{{ line }}</p>
            }
          </section>
        }
        <!-- The Shortcuts: open ones are doorways in the Village; the Start Floor is chosen at the Gate. -->
        <section class="building shortcuts" aria-labelledby="list-shortcuts">
          <h3 id="list-shortcuts">Shortcuts</h3>
          <p class="line leaves">
            Shortcuts open as the Campaign slays enemies and completes Expeditions.
            Choose an open Shortcut as the Start Floor at the Gate before setting out.
          </p>
          @for (sc of game().shortcuts; track sc.id) {
            <app-shortcut-card [shortcut]="sc" />
          }
        </section>
        <!-- The Reserves at home: their cards, with the Spore Doses. Choosing a name opens the unit view. -->
        <section class="building reserves" aria-labelledby="list-reserves">
          <h3 id="list-reserves">Army</h3>
          <app-army
            [army]="game().units"
            [atHome]="!over()"
            [sporesHeld]="sporesHeld()"
            (remedy)="giveRemedy($event)"
            (reattach)="reattachPart($event)"
          />
        </section>
        <!-- The Expedition's objects in the picture (Supplies post, crates, formations), as plain DOM. -->
        <app-expedition-hud
          [tower]="game().tower"
          [active]="game().expeditionActive"
          [party]="game().unitsOnTower"
          [supplies]="game().supplies"
          [maxSupplies]="game().maxSupplies"
          [carriedLoot]="game().carriedLoot"
          [carriedCredits]="game().carriedCredits"
          [pillarMarks]="game().pillarMarks ?? []"
        />
      </section>
    }
  `,
  host: { '(document:keydown)': 'onKey($event)' },
  styles: `
    .skip {
      position: fixed;
      top: 8px;
      left: -10000px;
      z-index: 1100;
      padding: 6px 12px;
      color: var(--bone);
      background: var(--panel);
      border: 1px solid var(--bone);
      pointer-events: auto;
    }
    .skip:focus {
      left: 8px;
    }
    .list {
      position: fixed;
      inset: 0;
      z-index: 1050;
      overflow-y: auto;
      box-sizing: border-box;
      /* The top bar's own buttons stand over the list: the first line clears them. */
      padding: 52px 24px 16px;
      color: var(--text);
      background: var(--panel);
      /* The game page's host is pointer-events: none and that is inherited: the list is a fixed
         overlay over the canvas, and without this every press in it falls through to the scene. */
      pointer-events: auto;
    }
    header {
      display: flex;
      gap: 16px;
      align-items: baseline;
    }
    h2,
    h3 {
      margin: 0 0 6px;
      color: var(--bone);
    }
    .remember {
      display: block;
      margin: 8px 0 16px;
      color: var(--muted);
    }
    .building {
      max-width: 640px;
      margin-bottom: 16px;
      padding: 8px 12px;
      border: 1px solid var(--rule);
    }
    .facts .line,
    .shortcuts .line {
      margin: 0 0 4px;
    }
    .written-lines {
      margin: 0 0 8px;
      padding: 0;
      list-style: none;
      line-height: 1.5;
    }
    .written-lines li.loss {
      color: var(--ember-text);
    }
    dl {
      margin: 0 0 8px;
    }
    dt {
      display: inline;
      color: var(--muted);
    }
    dd {
      display: inline;
      margin: 0 0 0 6px;
      color: var(--bone);
    }
    dd::after {
      content: '';
      display: block;
    }
    .rate,
    .leaves,
    .reason {
      color: var(--muted);
    }
    .leaves {
      display: block;
      margin-left: 6px;
    }
    .reason {
      margin-left: 8px;
    }
    h4 {
      margin: 8px 0 2px;
      color: var(--bone);
    }
    .line {
      margin: 0;
      color: var(--bone);
    }
    .name {
      margin-right: 6px;
      color: var(--muted);
    }
  `,
})
export class ListView {
  protected readonly list = inject(ListViewService);
  protected readonly popover = inject(PopoverService);
  private readonly stateService = inject(GameStateService);
  protected readonly game = this.stateService.state;
  protected readonly launch = inject(LaunchService);
  private readonly ws = inject(GameWebSocketService);
  protected readonly zoom = inject(ZoomService);

  /** The Party has not moved, and not because the closeup took the hold. */
  protected readonly held = computed(
    () => this.stateService.session()?.held === true && !this.zoom.holding(),
  );
  /** The ending in words, only when the Succession is not open to carry it. */
  protected readonly ending = computed(() => {
    const carried = this.popover.buildings().some((b) => b.id === SUCCESSION_ID);
    return carried ? null : endingOf(this.game());
  });
  protected readonly over = computed(() => endingOf(this.game()) !== null);
  protected readonly facts = computed(() => villageFacts(this.game()));
  protected readonly sporesHeld = computed(() =>
    Math.floor(this.game().village.spores),
  );

  protected giveRemedy(unitId: number): void {
    this.ws.sendAction(GameAction.REMEDY, { unitId });
  }

  protected reattachPart(event: { unitId: number; part: BodyPartKind }): void {
    this.ws.sendAction(GameAction.REATTACH, event);
  }
  private readonly closeButton = viewChild<ElementRef<HTMLElement>>('close');
  private opener: HTMLElement | null = null;

  protected remembered = false;

  constructor() {
    try {
      this.remembered = localStorage.getItem('td-list-view') === 'on';
    } catch {
      /* no stored choice */
    }
    // Opening moves focus into the list; closing gives it back to where it came from.
    effect(() => {
      const open = this.list.open();
      untracked(() => {
        if (open) {
          this.opener = document.activeElement as HTMLElement | null;
          queueMicrotask(() => this.closeButton()?.nativeElement.focus());
        } else {
          this.opener?.focus?.();
          this.opener = null;
        }
      });
    });
  }

  /** The same route as the popover; a refused action does nothing and its reason is in the row. */
  protected run(buildingId: string, actionId: string): void {
    this.popover.perform(buildingId, actionId);
  }

  protected remember(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.remembered = on;
    this.list.remember(on);
  }

  protected onKey(event: KeyboardEvent): void {
    const intent = listKeyIntent(event);
    if (intent === 'toggle') {
      event.preventDefault();
      this.list.toggle();
    } else if (
      intent === 'close' &&
      this.list.open() &&
      // Escape steps a zoomed view back first; the list underneath stays as the player left it.
      this.zoom.level() === 'battle'
    ) {
      event.preventDefault();
      this.list.hide();
    }
  }
}
