import type { LoadoutPreview } from '../../interfaces/village';
import { loadoutForecast, type LoadoutChoice } from './loadout-forecast';

const preview = (over: Partial<LoadoutPreview> = {}): LoadoutPreview => ({
  carryingCapacity: 160,
  suppliesTaken: 100,
  pantryAfter: 40,
  partySize: 6,
  reservesStaying: 0,
  pantryHoldSeconds: null,
  drainPerUnit: 1.5,
  saltedDrainPerUnit: 1.25,
  suppliesPerSalt: 10,
  saltUseful: 10,
  saltHeld: 4,
  saltPansOpen: true,
  trustHeld: 12,
  packCapacity: 10,
  bearerTypes: ['TANK', 'WARRIOR'],
  sporesHeld: 5,
  panaceaSpores: 4,
  panaceaMaterials: 40,
  vaultMaterials: 100,
  panaceaCapacity: 10,
  panaceaBlocked: null,
  ...over,
});

const none: LoadoutChoice = { salt: 0, pack: 0, bearer: '', panacea: false };

describe('loadoutForecast', () => {
  it('takes the defaults: the capacity, the Supplies and the Pantry as the frame says', () => {
    const f = loadoutForecast(preview(), none);

    expect(f.capacity).toBe(160);
    expect(f.taken).toBe(100);
    expect(f.pantryAfter).toBe(40);
    expect(f.lastsPlain).toBe(11);
    expect(f.lastsFullySalted).toBe(13);
    expect(f.lastsChosen).toBe(11);
  });

  it('never chooses more Salt than is held or would salt anything', () => {
    expect(loadoutForecast(preview(), { ...none, salt: 9 }).saltChosen).toBe(4);
    expect(
      loadoutForecast(preview({ saltUseful: 2 }), { ...none, salt: 9 }).saltChosen,
    ).toBe(2);
    expect(loadoutForecast(preview(), { ...none, salt: -3 }).saltChosen).toBe(0);
  });

  it('lets the Supplies last longer as Salt is chosen', () => {
    const f = loadoutForecast(preview({ saltHeld: 10 }), { ...none, salt: 10 });

    expect(f.lastsChosen).toBe(13);
    expect(loadoutForecast(preview(), { ...none, salt: 4 }).lastsChosen).toBe(11);
  });

  it('leaves the Pack only the Salt the Supplies left, and a bearer to carry it', () => {
    const f = loadoutForecast(preview(), {
      salt: 2,
      pack: 9,
      bearer: 'WARRIOR',
      panacea: false,
    });

    expect(f.packMax).toBe(2);
    expect(f.packChosen).toBe(2);
    expect(f.bearerChosen).toBe('WARRIOR');
    expect(loadoutForecast(preview(), { ...none, pack: 1 }).bearerChosen).toBe('TANK');
  });

  it('takes no Pack with no Unit to carry it', () => {
    const f = loadoutForecast(preview({ bearerTypes: [] }), { ...none, pack: 2 });

    expect(f.bearerChosen).toBe('');
    expect(f.packChosen).toBe(0);
  });

  it('takes the Panacea off the Carrying Capacity, and gives the Supplies back to the Pantry when it bites', () => {
    const f = loadoutForecast(preview(), { ...none, panacea: true });
    expect(f.panaceaChosen).toBeTrue();
    expect(f.capacity).toBe(150);
    expect(f.taken).toBe(100);

    const tight = loadoutForecast(preview({ carryingCapacity: 105 }), {
      ...none,
      panacea: true,
    });
    expect(tight.capacity).toBe(95);
    expect(tight.taken).toBe(95);
    expect(tight.pantryAfter).toBe(45);
  });

  it('ignores a Panacea that cannot be made', () => {
    const f = loadoutForecast(preview({ panaceaBlocked: 'No Spores.' }), {
      ...none,
      panacea: true,
    });

    expect(f.panaceaAvailable).toBeFalse();
    expect(f.panaceaChosen).toBeFalse();
    expect(f.capacity).toBe(160);
  });

  it('flags a Pantry that would hold less than a minute, and says no one to feed', () => {
    expect(loadoutForecast(preview({ pantryHoldSeconds: 3 }), none).pantryHoldsShort).toBeTrue();
    expect(loadoutForecast(preview({ pantryHoldSeconds: 90 }), none).pantryHoldsShort).toBeFalse();
    expect(loadoutForecast(preview({ pantryHoldSeconds: null }), none).pantryHoldsShort).toBeFalse();
    expect(loadoutForecast(preview({ partySize: 0 }), none).lastsChosen).toBeNull();
  });
});
