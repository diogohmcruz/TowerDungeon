import { ComponentFixture, TestBed } from '@angular/core/testing';

import type { ActionView } from '../../three/village/building-registry';
import { ActionRow } from './action-row';

describe('ActionRow', () => {
  let fixture: ComponentFixture<ActionRow>;
  let performed: string[];

  const show = (action: Partial<ActionView>) => {
    fixture.componentRef.setInput('idPrefix', 'row');
    fixture.componentRef.setInput('action', {
      id: 'exodus',
      label: 'Exodus',
      usable: true,
      reason: null,
      confirm: null,
      ...action,
    });
    fixture.detectChanges();
  };
  const root = () => fixture.nativeElement as HTMLElement;
  const button = () => root().querySelector('button.btn') as HTMLButtonElement;
  const answers = () =>
    Array.from(root().querySelectorAll('button.answer')) as HTMLButtonElement[];
  const asking = {
    prompt: 'The village leaves in the night.',
    confirmLabel: 'Leave',
    cancelLabel: 'Stay',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ActionRow] });
    fixture = TestBed.createComponent(ActionRow);
    performed = [];
    fixture.componentInstance.perform.subscribe((id) => performed.push(id));
  });

  it('runs a plain action at once', () => {
    show({});
    button().click();
    expect(performed).toEqual(['exodus']);
  });

  it('refuses a closed action and says why in words', () => {
    show({ usable: false, reason: 'Exodus opens at 3 Legacy.' });
    expect(button().getAttribute('aria-disabled')).toBe('true');
    expect(root().querySelector('.reason')?.textContent).toContain(
      'Exodus opens at 3 Legacy.',
    );
    expect(button().getAttribute('aria-describedby')).toBe('row-exodus');
    button().click();
    expect(performed).toEqual([]);
  });

  it('asks first, and runs only on the answer Leave', () => {
    show({ confirm: asking });
    button().click();
    fixture.detectChanges();

    expect(performed).toEqual([]);
    expect(root().querySelector('[role=alertdialog]')?.textContent).toContain(
      'The village leaves in the night.',
    );
    expect(answers().map((b) => b.textContent?.trim())).toEqual(['Leave', 'Stay']);

    answers()[0].click();
    fixture.detectChanges();
    expect(performed).toEqual(['exodus']);
    expect(root().querySelector('[role=alertdialog]')).toBeNull();
  });

  it('does nothing on the answer Stay', () => {
    show({ confirm: asking });
    button().click();
    fixture.detectChanges();
    answers()[1].click();
    fixture.detectChanges();

    expect(performed).toEqual([]);
    expect(root().querySelector('[role=alertdialog]')).toBeNull();
    expect(button()).not.toBeNull();
  });

  it('drops the question when the action closes under it', () => {
    show({ confirm: asking });
    button().click();
    fixture.detectChanges();
    show({ usable: false, reason: 'Extract first.', confirm: null });

    expect(root().querySelector('[role=alertdialog]')).toBeNull();
    expect(root().querySelector('.reason')?.textContent).toContain('Extract first.');
    expect(performed).toEqual([]);
  });
});
