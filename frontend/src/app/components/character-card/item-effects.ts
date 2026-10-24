/** What each special effect does, in words; the numbers live in the game's configuration. */
const EFFECTS: Record<string, string> = {
  steadfast: 'Raises the wearer\'s maximum Sanity.',
  keen: 'Sharpens the wearer\'s Accuracy.',
  wary: 'Raises the wearer\'s Evasion.',
  ember: 'Adds damage to a Magic attack.',
  gloom: 'Cuts the damage taken from Dead Soldiers and Necromancers.',
  salted: 'Cuts the damage taken from Slimes.',
  thorn: 'Returns damage to a melee attacker that hurts the wearer.',
  lantern: 'Restores Sanity on each floor climbed.',
  'sargans-mask': 'Phobias have no hold on the wearer.',
  'lilicens-mana-vessel': 'Raises a Caster\'s maximum Mana.',
  'dragon-slayers-blade': 'Multiplies damage against Dragons and Dead Dragons.',
  'yuvas-salt-satchel': 'Halves the wear of attrition on the wearer.',
};

/** The effect in words, or the bare id for one this build does not know. */
export function effectText(effectId: string | null | undefined): string {
  return effectId ? (EFFECTS[effectId] ?? effectId) : '';
}
