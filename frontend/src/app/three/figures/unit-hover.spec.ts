import { Member } from '../../interfaces/member';
import { hoverCaption } from './unit-hover';

function member(over: Partial<Member>): Member {
  return {
    id: 1,
    name: 'Mira',
    stats: { type: 'ARCHER' } as Member['stats'],
    currentHealth: 40,
    ...over,
  };
}

const golem = { type: 'STONE_GOLEM' } as Member['stats'];

describe('the hover caption', () => {
  it('names a unit and its Type in words', () => {
    expect(hoverCaption(member({}), 'unit')).toBe('Mira, Archer');
  });

  it('reads an enemy Type with spaces and drops a repeated name', () => {
    expect(
      hoverCaption(member({ name: 'Stone Golem', stats: golem }), 'enemy'),
    ).toBe('Stone Golem');
    expect(hoverCaption(member({ name: 'Gor', stats: golem }), 'enemy')).toBe(
      'Gor, Stone golem',
    );
  });

  it('says Guardian and Salted for an enemy, and never for a unit', () => {
    expect(hoverCaption(member({ boss: true, salted: true }), 'enemy')).toBe(
      'Mira, Archer, Guardian, Salted',
    );
    expect(hoverCaption(member({ boss: true, salted: true }), 'unit')).toBe(
      'Mira, Archer',
    );
  });

  it('carries no figures', () => {
    expect(hoverCaption(member({ boss: true }), 'enemy')).not.toMatch(/\d/);
  });
});
