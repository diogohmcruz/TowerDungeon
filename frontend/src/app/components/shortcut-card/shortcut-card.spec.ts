import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ShortcutOffer } from '../../interfaces/game-state';
import { VillageSelectionService } from '../../services/village-selection.service';
import { ShortcutCard } from './shortcut-card';

const offer = (id: string, unlocked: boolean): ShortcutOffer => ({
  id,
  name: id,
  description: 'A way up.',
  trigger: '10 enemies',
  floor: 21,
  unlocked,
});

@Component({
  imports: [ShortcutCard],
  template: `<app-shortcut-card [shortcut]="shortcut()" />`,
})
class Host {
  shortcut = signal(offer('NARROW_STAIR', true));
}

describe('ShortcutCard linking', () => {
  let fixture: ComponentFixture<Host>;
  let selection: VillageSelectionService;
  let el: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(Host);
    selection = TestBed.inject(VillageSelectionService);
    fixture.detectChanges();
    el = fixture.nativeElement.querySelector('app-shortcut-card');
  });

  it('hovers its Shortcut on pointer or focus and lets go on leaving', () => {
    el.dispatchEvent(new Event('mouseenter'));
    expect(selection.hovered()).toEqual({ kind: 'shortcut', id: 'NARROW_STAIR' });
    el.dispatchEvent(new Event('mouseleave'));
    expect(selection.hovered()).toBeNull();
    el.dispatchEvent(new Event('focusin'));
    expect(selection.hovered()?.id).toBe('NARROW_STAIR');
  });

  it('shows a hairline class while its doorway is hovered', () => {
    selection.hover({ kind: 'shortcut', id: 'NARROW_STAIR' });
    fixture.detectChanges();
    expect(el.classList).toContain('linked');
    selection.hover(null);
    fixture.detectChanges();
    expect(el.classList).not.toContain('linked');
  });

  it('takes focus and marks itself chosen when its doorway is clicked', () => {
    const scroll = spyOn(el, 'scrollIntoView');
    selection.selectFromScene({ kind: 'shortcut', id: 'NARROW_STAIR' });
    fixture.detectChanges();
    expect(scroll).toHaveBeenCalled();
    expect(document.activeElement).toBe(el);
    expect(el.classList).toContain('chosen');
    expect(el.getAttribute('aria-current')).toBe('true');
  });

  it('ignores a click on another Shortcut and a request made before it existed', () => {
    selection.selectFromScene({ kind: 'shortcut', id: 'OTHER' });
    const late = TestBed.createComponent(Host);
    late.detectChanges();
    const lateEl: HTMLElement =
      late.nativeElement.querySelector('app-shortcut-card');
    const scroll = spyOn(lateEl, 'scrollIntoView');
    fixture.detectChanges();
    late.detectChanges();
    expect(scroll).not.toHaveBeenCalled();
    expect(document.activeElement).not.toBe(lateEl);
  });

  it('is not linked, focusable or hoverable while locked', () => {
    fixture.componentInstance.shortcut.set(offer('NARROW_STAIR', false));
    fixture.detectChanges();
    expect(el.getAttribute('tabindex')).toBeNull();
    el.dispatchEvent(new Event('mouseenter'));
    el.click();
    expect(selection.hovered()).toBeNull();
    expect(selection.selected()).toBeNull();
  });

  it('picks on click and Enter, and lets go on Escape; it never starts anything', () => {
    el.click();
    expect(selection.selected()?.id).toBe('NARROW_STAIR');
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(selection.selected()).toBeNull();
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(selection.selected()?.id).toBe('NARROW_STAIR');
  });
});
