import { Component, effect, inject, OnDestroy, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { GameStateService } from '../../services/game-state.service';
import { LaunchService } from '../../services/launch.service';
import { StageService } from '../../services/stage.service';
import { UnitStatsService } from '../../services/unit-stats.service';
import { ZoomService } from '../../services/zoom.service';
import { Unit, UnitStats } from '../../interfaces/unit.enum';
import { TowerDisplay } from '../tower/tower-display.component';
import { ListView } from '../list-view/list-view';

/**
 * The game page: the full-screen scene, the top bar's own buttons (hide, closeup,
 * Resume for a Held Party), the narrow screen's launch bar and the list view. The
 * card layer is gone (3.0.0): every number is a gauge on a building and every action
 * a click on one, and the list view is the one DOM route to them all.
 */
@Component({
  selector: 'app-game-component',
  standalone: true,
  imports: [TowerDisplay, ListView],
  templateUrl: './game-component.html',
  styleUrls: ['./game-component.scss'],
  host: {
    '[class.narrow]': 'stage.narrow()',
    '(document:keydown)': 'onKey($event)',
  },
})
export class GameComponent implements OnDestroy {
  protected readonly stage = inject(StageService);
  protected readonly zoom = inject(ZoomService);

  ngOnDestroy(): void {
    // Leaving the page releases a closeup's hold.
    this.zoom.release();
  }

  /**
   * H hides and restores the menus (the list view, the popovers and the narrow
   * launch bar); Escape steps the zoom back. Not while typing, and not with a modifier.
   */
  protected onKey(event: KeyboardEvent): void {
    const key = event.key.toLowerCase();
    if (
      (key !== 'h' && key !== 'escape') ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.defaultPrevented
    ) {
      return;
    }
    const target = event.target as HTMLElement | null;
    if (
      target &&
      (target.isContentEditable ||
        ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
    ) {
      return;
    }
    if (key === 'escape') {
      if (this.zoom.level() !== 'battle') {
        event.preventDefault();
        this.zoom.back();
      }
      return;
    }
    this.stage.toggleMenus();
  }

  private readonly stateService = inject(GameStateService);
  readonly gameState = this.stateService.state;
  /** The session facts the socket strips from the state: a Held Party, the Succession. */
  readonly session = this.stateService.session;
  private readonly unitStatsService = inject(UnitStatsService);

  /** The launch commands; shared with the scene's bell, lamp and doorways and the Gate's actions. */
  protected readonly launch = inject(LaunchService);

  unitStats: Signal<Map<Unit, UnitStats>> = toSignal(
    this.unitStatsService.getUnitStats(),
    { initialValue: new Map<Unit, UnitStats>() },
  );

  constructor() {
    // The Fort's popover and the list view read costs from the state: hand them the Unit Types' stats.
    effect(() => {
      const stats = this.unitStats();
      if (stats.size > 0) {
        this.stateService.setCatalog(Object.fromEntries(stats));
      }
    });
  }
}
