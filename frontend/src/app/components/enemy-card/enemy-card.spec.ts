import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BodyPart, Member } from '../../interfaces/member';
import { UnitStats } from '../../interfaces/unit.enum';
import { EnemyCard } from './enemy-card';

describe('EnemyCard', () => {
  let fixture: ComponentFixture<EnemyCard>;

  const part = (
    kind: BodyPart['kind'],
    currentHealth: number,
    maxHealth: number,
    lost = false,
  ): BodyPart => ({ kind, currentHealth, maxHealth, lost });

  const render = (overrides: Partial<Member>): HTMLElement => {
    const enemy = {
      id: 1,
      name: 'Granite Golem',
      stats: { health: 300, damage: 30, attackType: 'MELEE' } as unknown as UnitStats,
      currentHealth: 100,
      maxHealth: 100,
      ...overrides,
    } as Member;
    fixture.componentRef.setInput('enemy', enemy);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EnemyCard] });
    fixture = TestBed.createComponent(EnemyCard);
  });

  it('shows no part bars for a single body', () => {
    const el = render({ parts: [part('TORSO', 8, 8)] });

    expect(el.querySelectorAll('.part-bar').length).toBe(0);
  });

  it('shows a bar per part with its health share', () => {
    const el = render({
      parts: [part('TORSO', 25, 50), part('HEAD', 10, 10), part('LEFT_ARM', 0, 0, true)],
    });

    const bars = el.querySelectorAll<HTMLElement>('.part-bar');
    expect(bars.length).toBe(3);
    expect(bars[0].querySelector<HTMLElement>('.part-fill')?.style.width).toBe('50%');
    expect(bars[1].querySelector<HTMLElement>('.part-fill')?.style.width).toBe('100%');
    expect(bars[2].classList).toContain('lost');
    expect(bars[2].title).toContain('lost');
  });
});
