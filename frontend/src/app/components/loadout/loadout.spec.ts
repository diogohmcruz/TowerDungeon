import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoadoutPreview } from '../../interfaces/village';
import { Loadout } from './loadout';

describe('Loadout', () => {
  let fixture: ComponentFixture<Loadout>;

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
    ...over,
  });

  const load = (over: Partial<LoadoutPreview> = {}) => {
    fixture.componentRef.setInput('preview', preview(over));
    fixture.detectChanges();
  };

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const toggle = () =>
    root().querySelector('.loadout-toggle') as HTMLButtonElement;
  const stepper = (label: string) =>
    root().querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
  const open = () => {
    toggle().click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Loadout],
    }).compileComponents();
    fixture = TestBed.createComponent(Loadout);
  });

  it('is collapsed by default and shows no lines', () => {
    load();

    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(text()).not.toContain('Carrying Capacity');
  });

  it('opens to the Carrying Capacity and what setting out takes from the Pantry', () => {
    load();
    open();

    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    expect(text()).toContain('Carrying Capacity 160');
    expect(text()).toContain('Taking 100 Supplies');
    expect(text()).toContain('Setting out takes 100 Food.');
    expect(text()).toContain('Fields cover Upkeep.');
  });

  it('states how long the Pantry would hold when Reserves stay home', () => {
    load({ pantryHoldSeconds: 3 });
    open();

    expect(text()).toContain('Pantry would hold 3 s.');
    expect(root().querySelector('.hold.short')).not.toBeNull();
  });

  it('states how long Supplies last plain and fully salted', () => {
    load();
    open();

    expect(text()).toContain('Supplies last about 11 s at Party size 6.');
    expect(text()).toContain('11 s plain, 13 s if fully salted.');
  });

  it('chooses Salt one at a time, never beyond what is held', () => {
    load({ saltHeld: 2 });
    open();

    stepper('Use one more Salt').click();
    stepper('Use one more Salt').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.salt()).toBe(2);
    expect(stepper('Use one more Salt').disabled).toBeTrue();
    expect(text()).toContain('2 of 2 Salt held');

    stepper('Use one less Salt').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.salt()).toBe(1);
  });

  it('shows the Supplies lasting longer as Salt is chosen', () => {
    load({ saltHeld: 10 });
    open();
    for (let i = 0; i < 10; i++) {
      stepper('Use one more Salt').click();
    }
    fixture.detectChanges();

    expect(text()).toContain('Supplies last about 13 s');
  });

  it('says the Salt Pans are not open when they are not', () => {
    load({ saltPansOpen: false });
    open();

    expect(text()).toContain('the Salt Pans are not open yet');
    expect(stepper('Use one more Salt')).toBeNull();
  });
});
