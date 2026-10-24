import { TestBed } from '@angular/core/testing';

import { GameStateService } from '../../services/game-state.service';
import { endingOf } from './succession-building';
import { seaLine, villageFacts, waveSize, yearLine } from './village-facts';

describe('the Village facts (what the Stock card said)', () => {
  const service = () => TestBed.inject(GameStateService);
  const text = () =>
    villageFacts(service().state())
      .flatMap((g) => g.lines)
      .join('\n');

  it('states the Food flows, the Stockroom, the lost, the Tier and the Tally in words', () => {
    service().update({
      homeUnitCount: 3,
      theCount: { villagers: 2, reserves: 1 },
      enemiesDefeated: 38,
    });
    expect(text()).toContain('Upkeep (3 Reserves)');
    expect(text()).toContain('Lost to Starvation: 2 Villagers, 1 Reserves');
    expect(text()).toContain('38 enemies slain');
    expect(text()).toContain('Tally (this Campaign)');
  });

  it('says the Pantry holds for good, or for how long', () => {
    service().update({ pantryHoldSeconds: null });
    expect(text()).toContain('The Pantry holds. Fields cover Upkeep.');
    service().update({ pantryHoldSeconds: 45 });
    expect(text()).toContain('The Pantry holds 45 s at current rates.');
  });

  it('names the Year and the danger at sea only when there is one', () => {
    expect(yearLine(service().state())).toBe('');
    expect(seaLine(service().state())).toBe('');
    service().update({ year: 3, quarter: 'AUTUMN', nextQuarter: 'WINTER' });
    expect(yearLine(service().state())).toBe('Year 3, Autumn, Winter follows');
  });

  it('lists the Waves climbing only while an Expedition is out', () => {
    const wave = { units: { WARRIOR: [{}, {}] }, currentFloor: 4, supplies: 30 } as never;
    service().update({ reinforcements: [wave], expeditionActive: false });
    expect(text()).not.toContain('Wave of');
    service().update({ expeditionActive: true });
    expect(text()).toContain('Wave of 2, Floor 4');
    expect(waveSize(wave)).toBe(2);
  });

  it("writes each ending in the game's words and never congratulates", () => {
    for (const [outcome, heading] of [
      ['VICTORY', 'The Heirs'],
      ['DEFEAT', 'The Second Company'],
      ['EXODUS', 'The Flight'],
    ]) {
      service().update({ gameOutcome: outcome });
      const ending = endingOf(service().state())!;
      expect(ending.heading).toBe(heading);
      expect(ending.text).not.toMatch(/free|Conquered|Prestige/);
    }
    service().update({ gameOutcome: 'PLAYING' });
    expect(endingOf(service().state())).toBeNull();
  });
});
