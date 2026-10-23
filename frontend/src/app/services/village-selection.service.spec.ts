import { TestBed } from '@angular/core/testing';

import { VillageSelectionService } from './village-selection.service';

describe('VillageSelectionService', () => {
  let service: VillageSelectionService;

  beforeEach(() => {
    service = TestBed.inject(VillageSelectionService);
  });

  it('holds hovered and selected apart', () => {
    service.hover({ kind: 'annex', id: 'BREAKER' });
    service.select({ kind: 'upgrade', id: 'SCRIBE' });

    expect(service.hovered()?.id).toBe('BREAKER');
    expect(service.selected()?.id).toBe('SCRIBE');
    expect(service.isHovered('BREAKER')).toBeTrue();
    expect(service.isSelected('BREAKER')).toBeFalse();
  });

  it('lets a second pick of the same thing go, whichever kind picked it', () => {
    service.select({ kind: 'annex', id: 'BREAKER' });
    service.select({ kind: 'upgrade', id: 'BREAKER' });

    expect(service.selected()).toBeNull();
  });

  it('asks a row to take focus only when the scene is clicked', () => {
    service.select({ kind: 'annex', id: 'BREAKER' });
    expect(service.focusRequest()).toBeNull();

    service.selectFromScene({ kind: 'villagers', id: 'BREAKER' });
    const first = service.focusRequest();
    service.selectFromScene({ kind: 'villagers', id: 'BREAKER' });

    expect(service.selected()?.id).toBe('BREAKER');
    expect(service.focusRequest()!.seq).toBeGreaterThan(first!.seq);
  });

  it('clears both', () => {
    service.hover({ kind: 'annex', id: 'SALTER' });
    service.select({ kind: 'annex', id: 'SALTER' });

    service.clear();

    expect(service.hovered()).toBeNull();
    expect(service.selected()).toBeNull();
  });
});
