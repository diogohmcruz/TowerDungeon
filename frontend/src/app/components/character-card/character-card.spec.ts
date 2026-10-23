import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Member } from '../../interfaces/member';
import { UnitStats } from '../../interfaces/unit.enum';
import { CharacterCard } from './character-card';

describe('CharacterCard', () => {
  let fixture: ComponentFixture<CharacterCard>;

  const render = (overrides: Partial<Member>): HTMLElement => {
    const member = {
      id: 1,
      name: 'Zeno',
      stats: { health: 100, damage: 10 } as unknown as UnitStats,
      currentHealth: 120,
      ...overrides,
    } as Member;
    fixture.componentRef.setInput('member', member);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CharacterCard] });
    fixture = TestBed.createComponent(CharacterCard);
  });

  it('shows the unit own rolled health and damage', () => {
    const el = render({ maxHealth: 130, damage: 13 });

    expect(el.querySelector('.member-hp')?.textContent).toContain('120 / 130');
    expect(el.querySelector('.member-damage')?.textContent).toContain('13');
  });

  it('falls back to the type damage when no rolled value is sent', () => {
    const el = render({});

    expect(el.querySelector('.member-damage')?.textContent).toContain('10');
  });
});
