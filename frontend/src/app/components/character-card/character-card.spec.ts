import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BodyPart, Member } from '../../interfaces/member';
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

  const parts = (over: Record<string, Partial<BodyPart>> = {}): BodyPart[] =>
    (
      [
        'TORSO',
        'HEAD',
        'LEFT_ARM',
        'RIGHT_ARM',
        'LEFT_LEG',
        'RIGHT_LEG',
      ] as const
    ).map((kind) => ({
      kind,
      currentHealth: 10,
      maxHealth: 10,
      lost: false,
      ...over[kind],
    }));

  const expand = (el: HTMLElement): void => {
    (el.querySelector('.toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  const region = (el: HTMLElement, label: string): SVGElement =>
    el.querySelector(`.region[aria-label^="${label}"]`) as SVGElement;

  it('keeps the compact row and hides the detail until clicked', () => {
    const el = render({ parts: parts() });

    expect(el.querySelector('.detail')).toBeNull();
    expand(el);
    expect(el.querySelector('.detail')).not.toBeNull();
    expect(el.querySelectorAll('.region').length).toBe(6);
    expect(el.querySelectorAll('.slot').length).toBe(8);
  });

  it('fills a healthy region fully and a wounded one by its ratio', () => {
    const el = render({
      parts: parts({ HEAD: { currentHealth: 5 }, TORSO: {} }),
    });
    expand(el);

    expect(region(el, 'Torso').getAttribute('style')).toContain('100%');
    expect(region(el, 'Head').getAttribute('style')).toContain('50%');
    expect(region(el, 'Head').getAttribute('aria-label')).toBe('Head: 5 / 10');
  });

  it('outlines and greys a lost region and says so in text', () => {
    const el = render({
      parts: parts({ LEFT_ARM: { lost: true, currentHealth: 0 } }),
    });
    expand(el);

    const arm = region(el, 'Left arm');
    expect(arm.classList).toContain('region--lost');
    expect(arm.getAttribute('aria-label')).toBe('Left arm: lost');
    expect(region(el, 'Head').classList).not.toContain('region--lost');
  });

  it('shows the numbers of a focused region', () => {
    const el = render({ parts: parts({ HEAD: { currentHealth: 5 } }) });
    expand(el);

    region(el, 'Head').dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(el.querySelector('.readout')?.textContent).toContain('Head: 5 / 10');
  });

  it('makes every region and slot reachable by keyboard', () => {
    const el = render({ parts: parts() });
    expand(el);

    const stops = el.querySelectorAll(
      '.region[tabindex="0"], .slot[tabindex="0"]',
    );
    expect(stops.length).toBe(14);
  });

  it('shows a ? for an unknown phobia and the type once known', () => {
    const el = render({
      phobias: [
        { enemyType: null, known: false },
        { enemyType: 'SLIME', known: true },
      ],
    });
    expand(el);

    const phobias = Array.from(el.querySelectorAll('.phobia')).map((p) =>
      p.textContent?.trim(),
    );
    expect(phobias).toEqual(['?', 'Slime']);
  });

  it('shows the sanity bar and the rolled numbers', () => {
    const el = render({
      maxHealth: 130,
      damage: 13,
      sanity: 40,
      maxSanity: 80,
    });
    expand(el);

    expect((el.querySelector('.sanity-fill') as HTMLElement).style.width).toBe(
      '50%',
    );
    expect(el.querySelector('.rolled')?.textContent).toContain('130');
  });

  it('shows empty slots faint and worn items with tier and name', () => {
    const el = render({
      gear: {
        slots: {
          HEAD: {
            id: 'h1',
            name: 'Rusty Helm',
            tier: 'LEFTOVER',
            category: 'HELM',
            stats: { ARMOUR: 1.5 },
          },
        },
      },
    });
    expand(el);

    const head = el.querySelector('[data-slot="HEAD"]') as HTMLElement;
    expect(head.textContent).toContain('Leftover');
    expect(head.textContent).toContain('Rusty Helm');
    expect(head.title).toContain('Armour 1.5');
    expect(head.classList).not.toContain('slot--empty');
    const torso = el.querySelector('[data-slot="TORSO"]') as HTMLElement;
    expect(torso.classList).toContain('slot--empty');
    expect(torso.textContent?.trim()).toBe('');
  });

  it('falls back to the type damage when no rolled value is sent', () => {
    const el = render({});

    expect(el.querySelector('.member-damage')?.textContent).toContain('10');
  });

  it('offers a Remedy for a wounded Unit at home, and a reattachment for each lost limb', () => {
    const emitted: unknown[] = [];
    fixture.componentInstance.remedy.subscribe((id) => emitted.push(['r', id]));
    fixture.componentInstance.reattach.subscribe((e) => emitted.push(e));
    fixture.componentRef.setInput('atHome', true);
    fixture.componentRef.setInput('sporesHeld', 2);
    const el = render({
      parts: parts({
        TORSO: { currentHealth: 4 },
        LEFT_ARM: { lost: true, currentHealth: 0 },
      }),
    });
    expand(el);

    const buttons = Array.from(
      el.querySelectorAll('.spore-doses .dose'),
    ) as HTMLButtonElement[];
    expect(buttons.map((b) => b.textContent?.trim())).toEqual([
      'Remedy, 20 hp, 1 Spore',
      'Reattach left arm, 1 Spore',
    ]);
    buttons.forEach((b) => b.click());

    expect(emitted).toEqual([['r', 1], { unitId: 1, part: 'LEFT_ARM' }]);
  });

  it('disables the Spore Doses with no Spores and hides them away from home', () => {
    fixture.componentRef.setInput('atHome', true);
    const el = render({ parts: parts({ TORSO: { currentHealth: 4 } }) });
    expand(el);
    expect(
      (el.querySelector('.spore-doses .dose') as HTMLButtonElement).disabled,
    ).toBeTrue();

    fixture.componentRef.setInput('atHome', false);
    fixture.detectChanges();
    expect(el.querySelector('.spore-doses')).toBeNull();
  });

  it('renders a member without stats, falling back to zero, instead of throwing', () => {
    const el = render({ stats: undefined, maxHealth: undefined, damage: undefined });
    expect(el.querySelector('.member-damage')?.textContent).toContain('Damage 0');
  });

  describe('detailed (the unit view)', () => {
    const detailedRender = (overrides: Partial<Member>): HTMLElement => {
      fixture.componentRef.setInput('detailed', true);
      return render({
        stats: {
          type: 'ARCHER',
          health: 100,
          damage: 10,
        } as unknown as UnitStats,
        accuracy: 12.5,
        evasion: 5,
        affinity: 'BOW',
        ...overrides,
      });
    };
    const row = (el: HTMLElement, sel: string) =>
      Array.from(el.querySelector(sel)?.children ?? [])
        .map((c) => c.textContent?.replace(/\s+/g, ' ').trim())
        .filter(Boolean)
        .join(' ');

    it('opens expanded with type, accuracy, evasion and affinity', () => {
      const el = detailedRender({ parts: parts() });

      expect(el.querySelector('.detail')).not.toBeNull();
      expect(el.querySelector('.trait-type')?.textContent?.trim()).toBe('Archer');
      expect(el.querySelector('.trait-accuracy')?.textContent?.trim()).toBe('12.5');
      expect(el.querySelector('.trait-evasion')?.textContent?.trim()).toBe('5');
      expect(el.querySelector('.trait-affinity')?.textContent?.trim()).toBe('Bow');
    });

    it('does not collapse when the name is pressed', () => {
      const el = detailedRender({ parts: parts() });
      (el.querySelector('.toggle') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(el.querySelector('.detail')).not.toBeNull();
    });

    it('spells out every part as whole, wounded or lost, in words', () => {
      const el = detailedRender({
        parts: parts({
          HEAD: { currentHealth: 4 },
          LEFT_ARM: { lost: true, currentHealth: 0 },
        }),
      });

      expect(row(el, '[data-part="TORSO"]')).toBe('Torso whole 10 / 10');
      expect(row(el, '[data-part="HEAD"]')).toBe('Head wounded 4 / 10');
      expect(row(el, '[data-part="LEFT_ARM"]')).toBe('Left arm lost, maimed');
      expect(el.querySelector('.maimed')?.textContent).toContain('Maimed');
    });

    it('is not Maimed with every part attached', () => {
      const el = detailedRender({ parts: parts() });

      expect(el.querySelector('.maimed')).toBeNull();
    });

    it('lists every slot: the item with stats and effect, empty slots as empty', () => {
      const el = detailedRender({
        parts: parts(),
        gear: {
          slots: {
            TRINKET_1: {
              id: 't1',
              name: 'Pale Ring',
              tier: 'TALISMAN',
              category: 'TRINKET',
              stats: { ACCURACY: 2 },
              effectId: 'keen',
            },
          },
        },
      });

      expect(el.querySelectorAll('.item-row').length).toBe(8);
      const ring = row(el, '[data-slot="TRINKET_1"]');
      expect(ring).toContain('Talisman Pale Ring');
      expect(ring).toContain('Accuracy 2');
      expect(ring).toContain('Sharpens the wearer');
      expect(row(el, '[data-slot="HEAD"]')).toBe('Head Empty');
    });

    it('says an item on a lost part is gone', () => {
      const el = detailedRender({
        parts: parts({ RIGHT_ARM: { lost: true, currentHealth: 0 } }),
      });

      expect(row(el, '[data-slot="RIGHT_ARM"]')).toContain('Lost with the part');
      expect(row(el, '[data-slot="LEFT_ARM"]')).toBe('Left arm Empty');
    });
  });
});
