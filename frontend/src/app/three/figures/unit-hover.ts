import { Member } from '../../interfaces/member';

/** "STONE_GOLEM" read as words: "Stone golem". */
function words(raw: string): string {
  const text = raw.toLowerCase().replace(/_/g, ' ').trim();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * The caption beside the pointer on a figure: its name and Type in words, and for an enemy
 * Guardian or Salted. No figures: the exact details stay in the unit view.
 */
export function hoverCaption(member: Member, kind: 'unit' | 'enemy'): string {
  const parts: string[] = [member.name];
  const type = member.stats?.type ? words(member.stats.type) : '';
  if (type && type !== words(member.name)) {
    parts.push(type);
  }
  if (kind === 'enemy') {
    if (member.boss) parts.push('Guardian');
    if (member.salted) parts.push('Salted');
  }
  return parts.join(', ');
}

/** What the pointer is over, for the caption and the outline. */
export interface UnitHover {
  /** The figure id the plan gives it ("unit-3", "enemy-7"). */
  id: string;
  caption: string;
  /** Pointer position in CSS px from the top left of the canvas. */
  x: number;
  y: number;
}
