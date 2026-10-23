import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { SuccessionReport } from '../../interfaces/game-state';
import { RaceRecord } from '../../interfaces/race';
import {
  ChronicleEntry,
  SuccessionCourse,
  SuccessionNode,
  SuccessionOption,
  SuccessionView,
} from '../../interfaces/succession';

interface TollLine {
  label: string;
  marks: number;
}

interface CourseRow {
  course: SuccessionCourse;
  nodes: SuccessionNode[];
}

interface ChoiceGroup {
  group: string;
  title: string;
  options: SuccessionOption[];
}

const OUTCOMES: Record<string, string> = {
  VICTORY: 'The Heirs',
  DEFEAT: 'The Second Company',
  EXODUS: 'The Flight',
};

const CHOICE_LABELS: Record<string, string> = {
  DOWRY_CREDITS: 'Credits',
  DOWRY_MATERIALS: 'Materials',
  DOWRY_RELICS: 'Relics',
  HEIR_WARRIOR: 'Warrior',
  HEIR_ARCHER: 'Archer',
  HEIR_PORTER: 'Porter',
};

const GROUP_TITLES: Record<string, string> = {
  DOWRY: 'The Dowry. What the village carries into the next Generation.',
  HEIR: 'The Heir. The unit type the next Generation starts with.',
};

/** How long a started Generation is held before a second Begin is allowed again. */
const BEGIN_HOLD_MS = 3000;

/**
 * The Succession screen, shown only while the Campaign waits in OFFER: the Toll
 * as plain numbers, the Roll tree where the Legacy is spent, the choices open
 * for the next Generation, and the Chronicle. It never congratulates. State is
 * always spelled out in words, never by colour alone.
 */
@Component({
  selector: 'app-succession-screen',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './succession-screen.html',
  styleUrl: './succession-screen.scss',
})
export class SuccessionScreen {
  view = input.required<SuccessionView>();
  report = input<SuccessionReport | null>(null);
  /** The player's Race records, newest first as sent; a plain line each, no effect on play. */
  raceRecords = input<RaceRecord[]>([]);

  buy = output<string>();
  refund = output<string>();
  choose = output<string>();
  begin = output<void>();

  readonly confirming = signal(false);
  /** The Generation whose start has been sent; a second Begin for it is a no-op. */
  private readonly begunFor = signal<number | null>(null);
  readonly begun = computed(() => this.begunFor() === this.view().generation);

  readonly courseRows = computed<CourseRow[]>(() =>
    this.view().courses.map((course) => ({
      course,
      nodes: this.view().inheritances.filter((n) => n.course === course.course),
    })),
  );

  readonly choiceGroups = computed<ChoiceGroup[]>(() => {
    const groups: ChoiceGroup[] = [];
    for (const option of this.view().choices) {
      let group = groups.find((g) => g.group === option.group);
      if (!group) {
        group = {
          group: option.group,
          title: GROUP_TITLES[option.group] ?? option.group,
          options: [],
        };
        groups.push(group);
      }
      group.options.push(option);
    }
    return groups;
  });

  readonly tollLines = computed<TollLine[]>(() => {
    const toll = this.report()?.toll;
    if (!toll) {
      return [];
    }
    return [
      { label: 'Food grown', marks: toll.foodMarks },
      { label: 'Materials banked', marks: toll.materialsMarks },
      { label: 'Credits banked', marks: toll.creditsMarks },
      { label: 'Relics banked', marks: toll.relicsMarks },
      { label: 'Expeditions completed', marks: toll.expeditionsMarks },
      { label: 'Ascent', marks: toll.ascentMarks },
      { label: 'Guardians slain', marks: toll.guardianMarks },
    ];
  });

  /** Newest first. */
  readonly chronicle = computed<ChronicleEntry[]>(() =>
    [...(this.view().chronicle ?? [])].reverse(),
  );

  readonly nextGeneration = computed(() => this.view().generation + 1);

  outcomeName(outcome: string): string {
    return OUTCOMES[outcome] ?? outcome;
  }

  choiceLabel(id: string): string {
    return CHOICE_LABELS[id] ?? id;
  }

  /** Whether the option is the answer now, or the default while none is given. */
  isChosen(group: ChoiceGroup, option: SuccessionOption): boolean {
    return group.options.some((o) => o.selected)
      ? option.selected
      : option.isDefault;
  }

  courseState(course: SuccessionCourse): string {
    return course.open
      ? 'Open'
      : `Closed. Spend ${course.gate} Legacy in the Courses below (${course.spentBelow} spent).`;
  }

  /** The node's state in words. */
  stateOf(node: SuccessionNode): string {
    if (node.held) {
      return 'Held';
    }
    if (!node.available) {
      return 'Sealed';
    }
    return node.whyNot ? 'Not yet' : 'Open';
  }

  /** Why the node cannot be bought, in words; empty while it can. */
  whyNot(node: SuccessionNode): string {
    if (node.held) {
      return '';
    }
    if (!node.available) {
      return 'Sealed. The Tower has not opened this.';
    }
    switch (node.whyNot) {
      case null:
        return '';
      case 'COURSE_CLOSED':
        return 'Its Course is closed.';
      case 'INSUFFICIENT_LEGACY':
        return `It costs ${node.cost} Legacy. You hold ${this.view().legacy}.`;
      case 'ALREADY_HELD':
        return 'Already held.';
      default:
        return 'It cannot be bought now.';
    }
  }

  canBuy(node: SuccessionNode): boolean {
    return node.available && !node.held && node.whyNot === null;
  }

  buyNode(node: SuccessionNode) {
    if (this.canBuy(node)) {
      this.buy.emit(node.id);
    }
  }

  refundNode(node: SuccessionNode) {
    if (node.held && node.refundable) {
      this.refund.emit(node.id);
    }
  }

  pick(option: SuccessionOption) {
    this.choose.emit(option.id);
  }

  ask() {
    if (!this.begun()) {
      this.confirming.set(true);
    }
  }

  stay() {
    this.confirming.set(false);
  }

  /** Begins the next Generation once; a double click does not start it twice. */
  confirm() {
    this.confirming.set(false);
    const generation = this.view().generation;
    if (this.begunFor() === generation) {
      return;
    }
    this.begunFor.set(generation);
    this.begin.emit();
    // If the server refused and the Campaign still waits, allow another try.
    setTimeout(() => {
      if (this.begunFor() === generation) {
        this.begunFor.set(null);
      }
    }, BEGIN_HOLD_MS);
  }
}
