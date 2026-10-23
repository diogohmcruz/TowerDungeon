import { Component, computed, DestroyRef, inject, input, OnInit, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RaceRow, RaceTableView } from '../../interfaces/race';
import { clock, ordinal, statusText } from '../../interfaces/race-copy';
import { RaceService } from '../../services/race.service';

/** The second a row first reached a Floor, or null if it never did. */
export function arrivalAt(row: RaceRow, floor: number): number | null {
  return row.timeline.find((a) => a.floor === floor)?.seconds ?? null;
}

/**
 * The timeline of one Floor, in the words of the design: "Anaxagoras reached Floor 15 at 18:20,
 * you at 22:41." Those who reached it come first, by time; the rest follow.
 */
export function timelineLines(rows: RaceRow[], floor: number): string[] {
  const you = rows.find((r) => r.you);
  const yours = you ? arrivalAt(you, floor) : null;
  const reached = rows
    .map((row) => ({ row, at: arrivalAt(row, floor) }))
    .filter((e): e is { row: RaceRow; at: number } => e.at !== null)
    .sort((a, b) => a.at - b.at || a.row.alias.localeCompare(b.row.alias));
  const missed = rows.filter((r) => arrivalAt(r, floor) === null);
  const lines: string[] = [];
  for (const { row, at } of reached) {
    if (row.you) {
      lines.push(`You reached Floor ${floor} at ${clock(at)}.`);
    } else if (!you) {
      lines.push(`${row.alias} reached Floor ${floor} at ${clock(at)}.`);
    } else if (yours !== null) {
      lines.push(`${row.alias} reached Floor ${floor} at ${clock(at)}, you at ${clock(yours)}.`);
    } else {
      lines.push(`${row.alias} reached Floor ${floor} at ${clock(at)}, you did not reach it.`);
    }
  }
  for (const row of missed) {
    lines.push(
      row.you ? `You did not reach Floor ${floor}.` : `${row.alias} did not reach Floor ${floor}.`,
    );
  }
  return lines;
}

export interface TableSection {
  title: string;
  rows: RaceRow[];
  /** Floors anyone in this table reached, lowest first. */
  floors: number[];
}

/** How a row's attempt ended, in words. */
export function resultText(row: RaceRow): string {
  return row.finished
    ? `Finished in ${clock(row.seconds)}`
    : `Floor ${row.highestFloor} at ${clock(row.arrivalSeconds)}. ${statusText(row.status)}`;
}

/**
 * The finished table of a race: Order of Arrival and Cost side by side as separate columns, never
 * summed, hands and agents in separate tables, and a per-Floor timeline. Ties are shown as ties.
 */
@Component({
  selector: 'app-race-table',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './race-table.html',
  styleUrl: './race-table.scss',
})
export class RaceTable implements OnInit {
  private readonly races = inject(RaceService);

  raceId = input.required<string>();
  /** The panel asks to be closed. */
  closed = output<void>();

  protected readonly view = signal<RaceTableView | null>(null);
  protected readonly loading = signal(true);
  /** The server refuses the table until an attempt of this Key is over. */
  protected readonly notYet = signal(false);
  protected readonly failed = signal(false);
  protected readonly floorChoice = signal<Record<string, number>>({});

  protected readonly sections = computed<TableSection[]>(() => {
    const view = this.view();
    if (!view) {
      return [];
    }
    return [
      { title: 'Hands', rows: view.hands },
      { title: 'Agents', rows: view.agents },
    ]
      .filter((s) => s.rows.length > 0)
      .map((s) => ({
        ...s,
        floors: [...new Set(s.rows.flatMap((r) => r.timeline.map((a) => a.floor)))].sort(
          (a, b) => a - b,
        ),
      }));
  });

  /** Reads made again after the server answered 403: the result is written behind the close. */
  private retries = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private static readonly RETRY_MS = 1500;
  private static readonly MAX_RETRIES = 3;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.timer !== null) {
        clearTimeout(this.timer);
      }
    });
  }

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(this.view() === null && !this.notYet());
    this.notYet.set(false);
    this.failed.set(false);
    try {
      this.view.set(await this.races.table(this.raceId()));
      this.retries = 0;
    } catch (e) {
      const status = (e as { status?: number }).status;
      if (status === 403) {
        this.notYet.set(true);
        if (this.retries < RaceTable.MAX_RETRIES) {
          this.retries += 1;
          this.timer = setTimeout(() => void this.load(), RaceTable.RETRY_MS);
        }
      } else {
        this.failed.set(true);
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected place(row: RaceRow): string {
    return row.tied ? `${ordinal(row.place)}, tied` : ordinal(row.place);
  }

  protected result(row: RaceRow): string {
    return resultText(row);
  }

  /** The Floor chosen for a section's timeline; the highest the viewer reached by default. */
  protected floorOf(section: TableSection): number {
    const chosen = this.floorChoice()[section.title];
    if (chosen !== undefined) {
      return chosen;
    }
    const you = section.rows.find((r) => r.you);
    const yours = you?.timeline.map((a) => a.floor) ?? [];
    return yours.length > 0
      ? Math.max(...yours)
      : (section.floors[section.floors.length - 1] ?? 0);
  }

  protected chooseFloor(section: TableSection, event: Event): void {
    const floor = Number((event.target as HTMLSelectElement).value);
    this.floorChoice.update((c) => ({ ...c, [section.title]: floor }));
  }

  protected lines(section: TableSection): string[] {
    return timelineLines(section.rows, this.floorOf(section));
  }
}
