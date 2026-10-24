import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe, LowerCasePipe } from '@angular/common';
import {
  BodyPart,
  BodyPartKind,
  Item,
  Member,
  SlotName,
} from '../../interfaces/member';
import { Card } from '../card/card';
import { effectText } from './item-effects';

interface Region {
  kind: BodyPartKind;
  label: string;
  /** Rectangle of the region in the 60 x 100 silhouette. */
  x: number;
  y: number;
  w: number;
  h: number;
}

interface SlotView {
  name: SlotName;
  label: string;
  /** Placement in the six-column slot grid, mirroring the body. */
  column: string;
  row: number;
  item?: Item;
}

const REGIONS: Region[] = [
  { kind: 'HEAD', label: 'Head', x: 24, y: 2, w: 12, h: 14 },
  { kind: 'TORSO', label: 'Torso', x: 20, y: 18, w: 20, h: 32 },
  { kind: 'RIGHT_ARM', label: 'Right arm', x: 8, y: 18, w: 10, h: 30 },
  { kind: 'LEFT_ARM', label: 'Left arm', x: 42, y: 18, w: 10, h: 30 },
  { kind: 'RIGHT_LEG', label: 'Right leg', x: 20, y: 52, w: 9, h: 44 },
  { kind: 'LEFT_LEG', label: 'Left leg', x: 31, y: 52, w: 9, h: 44 },
];

/** A part's state in words: whole, wounded, lost, or absent (this body never had it). */
export function partState(part?: BodyPart): 'whole' | 'wounded' | 'lost' | 'absent' {
  if (!part) {
    return 'absent';
  }
  if (part.lost) {
    return 'lost';
  }
  return part.currentHealth < part.maxHealth - 1e-9 ? 'wounded' : 'whole';
}

const SLOTS: Omit<SlotView, 'item'>[] = [
  { name: 'HEAD', label: 'Head', column: '3 / span 2', row: 1 },
  { name: 'RIGHT_ARM', label: 'Right arm', column: '1 / span 2', row: 2 },
  { name: 'TORSO', label: 'Torso', column: '3 / span 2', row: 2 },
  { name: 'LEFT_ARM', label: 'Left arm', column: '5 / span 2', row: 2 },
  { name: 'RIGHT_LEG', label: 'Right leg', column: '2 / span 2', row: 3 },
  { name: 'LEFT_LEG', label: 'Left leg', column: '4 / span 2', row: 3 },
  { name: 'TRINKET_1', label: 'Trinket', column: '2 / span 2', row: 4 },
  { name: 'TRINKET_2', label: 'Trinket', column: '4 / span 2', row: 4 },
];

@Component({
  selector: 'app-character-card',
  standalone: true,
  templateUrl: './character-card.html',
  styleUrl: './character-card.scss',
  imports: [DecimalPipe, LowerCasePipe, Card],
})
export class CharacterCard {
  member = input.required<Member>();
  /** Dragon Spores in the Stockroom, for a Unit at home to be treated with. */
  sporesHeld = input(0);
  /** Whether this Unit is at home, where a Spore Dose can reach it. */
  atHome = input(false);
  remedy = output<number>();
  reattach = output<{ unitId: number; part: BodyPartKind }>();
  /** The unit was chosen (its name pressed): the hook the unit view will use. */
  choose = output<number>();

  /** Whether any part still attached is hurt. */
  wounded = computed(() =>
    (this.member().parts ?? []).some(
      (part) => !part.lost && part.currentHealth < part.maxHealth - 1e-9,
    ),
  );
  lostLimbs = computed(() =>
    REGIONS.filter((region) =>
      (this.member().parts ?? []).some(
        (part) => part.kind === region.kind && part.lost,
      ),
    ),
  );
  /**
   * The unit view's form: open for good, with every figure the frame carries spelled out in words
   * (parts and items by name, state and effect), instead of the army list's collapsed summary.
   */
  detailed = input(false);
  expanded = signal(false);
  open = computed(() => this.detailed() || this.expanded());
  /** The region or slot under the pointer or focus, spelled out for the readout. */
  detail = signal('');

  maxHealth = computed(
    () => this.member().maxHealth ?? this.member().stats?.health ?? 0,
  );
  healthPct = computed(() => {
    const max = this.maxHealth();
    return max ? (this.member().currentHealth / max) * 100 : 0;
  });
  damage = computed(() => this.member().damage ?? this.member().stats?.damage ?? 0);
  maxMana = computed(() => this.member().maxMana ?? 0);
  manaPct = computed(() => {
    const max = this.maxMana();
    return max ? ((this.member().currentMana ?? 0) / max) * 100 : 0;
  });
  maxSanity = computed(() => this.member().maxSanity ?? 0);
  sanityPct = computed(() => {
    const max = this.maxSanity();
    return max ? ((this.member().sanity ?? 0) / max) * 100 : 0;
  });
  phobias = computed(() => this.member().phobias ?? []);

  regions = computed(() => {
    const parts = this.member().parts ?? [];
    return REGIONS.map((region) => {
      const part = parts.find((p) => p.kind === region.kind);
      const pct =
        part && part.maxHealth > 0
          ? Math.max(
              0,
              Math.min(100, (part.currentHealth / part.maxHealth) * 100),
            )
          : 100;
      return {
        ...region,
        lost: part?.lost ?? false,
        pct,
        text: this.partText(region.label, part),
      };
    });
  });

  slots = computed<SlotView[]>(() => {
    const worn = this.member().gear?.slots ?? {};
    return SLOTS.map((slot) => ({ ...slot, item: worn[slot.name] }));
  });

  /** Every part with its health and a state in words, never by colour alone. */
  partRows = computed(() => {
    const parts = this.member().parts ?? [];
    return REGIONS.map((region) => {
      const part = parts.find((p) => p.kind === region.kind);
      return {
        kind: region.kind,
        label: region.label,
        state: partState(part),
        health: part && !part.lost
          ? `${this.round(part.currentHealth)} / ${this.round(part.maxHealth)}`
          : '',
      };
    }).filter((row) => row.state !== 'absent');
  });

  /** Whether a limb is gone for good: the unit lives on weaker (Maimed). */
  maimed = computed(() =>
    (this.member().parts ?? []).some(
      (part) => part.lost && part.kind !== 'HEAD' && part.kind !== 'TORSO',
    ),
  );

  /** Every slot with what it holds; an item on a lost part is gone, and the row says so. */
  itemRows = computed(() => {
    const parts = this.member().parts ?? [];
    return this.slots().map((slot) => {
      const partLost = parts.some((p) => p.kind === slot.name && p.lost);
      return {
        name: slot.name,
        label: slot.label,
        item: slot.item,
        partLost,
        stats: slot.item ? this.statsText(slot.item) : '',
        effect: slot.item ? effectText(slot.item.effectId) : '',
      };
    });
  });

  typeName = computed(() => {
    const type = this.member().stats?.type ?? '';
    return type.charAt(0) + type.slice(1).toLowerCase().replace(/_/g, ' ');
  });

  affinityText = computed(() => {
    const affinity = this.member().affinity;
    return affinity
      ? affinity.charAt(0) + affinity.slice(1).toLowerCase().replace(/_/g, ' ')
      : 'none';
  });

  toggle(): void {
    if (this.detailed()) {
      return;
    }
    this.expanded.update((open) => !open);
    this.choose.emit(this.member().id);
  }

  show(text: string): void {
    this.detail.set(text);
  }

  clear(): void {
    this.detail.set('');
  }

  tierName(item: Item): string {
    return item.tier.charAt(0) + item.tier.slice(1).toLowerCase();
  }

  /** The item's stats as one line, e.g. "Armour 1.5, Power 2". */
  statsText(item: Item): string {
    const stats = Object.entries(item.stats)
      .filter(([, value]) => !!value)
      .map(
        ([key, value]) =>
          `${key.charAt(0)}${key.slice(1).toLowerCase()} ${this.round(value!)}`,
      );
    return stats.length ? stats.join(', ') : 'No stats';
  }

  slotText(slot: SlotView): string {
    return slot.item
      ? `${slot.label}: ${this.tierName(slot.item)} ${slot.item.name} (${this.statsText(slot.item)})`
      : `${slot.label}: empty`;
  }

  phobiaText(enemyType: string | null): string {
    return enemyType
      ? enemyType.charAt(0) +
          enemyType.slice(1).toLowerCase().replace(/_/g, ' ')
      : '?';
  }

  private partText(label: string, part?: BodyPart): string {
    if (!part) {
      return label;
    }
    return part.lost
      ? `${label}: lost`
      : `${label}: ${this.round(part.currentHealth)} / ${this.round(part.maxHealth)}`;
  }

  private round(value: number): number {
    return Math.round(value * 10) / 10;
  }
}
