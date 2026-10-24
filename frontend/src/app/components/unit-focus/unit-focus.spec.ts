import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Squad } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { Tower } from '../../interfaces/tower';
import { UnitStats } from '../../interfaces/unit.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { ZoomService } from '../../services/zoom.service';
import { StageService } from '../../services/stage.service';
import { UnitFocusCard, focusedMember } from './unit-focus';

describe('UnitFocusCard', () => {
  const member = (id: number, name: string): Member =>
    ({
      id,
      name,
      stats: {
        health: 10,
        damage: 2,
        attackType: 'MELEE',
      } as unknown as UnitStats,
      currentHealth: 7,
      maxHealth: 10,
    }) as Member;

  const party = { WARRIOR: [member(3, 'Maren')] } as unknown as Squad;
  const tower = {
    currentFloor: 4,
    maxFloor: 9,
    layoutVersion: 1,
    slainGuardians: [],
    floors: {
      4: { id: 4, difficulty: 1, enemies: [member(9, 'Ghoul')] },
    },
  } as unknown as Tower;

  let fixture: ComponentFixture<UnitFocusCard>;
  let zoom: ZoomService;
  let sendAction: jasmine.Spy;

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    TestBed.inject(GameStateService).update({ expeditionActive: true });
    zoom = TestBed.inject(ZoomService);
    fixture = TestBed.createComponent(UnitFocusCard);
    fixture.componentRef.setInput('party', party);
    fixture.componentRef.setInput('tower', tower);
    fixture.detectChanges();
  });
  afterEach(() => TestBed.inject(StageService).showMenus());

  const el = () => fixture.nativeElement as HTMLElement;

  it('shows nothing until a unit is chosen', () => {
    expect(el().querySelector('.unit-focus')).toBeNull();
  });

  it('shows the character card for a Party member', () => {
    zoom.focusUnit({ kind: 'unit', id: 3 });
    fixture.detectChanges();
    expect(el().querySelector('app-character-card')).not.toBeNull();
    expect(el().querySelector('app-enemy-card')).toBeNull();
    expect(el().textContent).toContain('Maren');
  });

  it('shows the enemy card for a garrison member', () => {
    zoom.focusUnit({ kind: 'enemy', id: 9 });
    fixture.detectChanges();
    expect(el().querySelector('app-enemy-card')).not.toBeNull();
    expect(el().querySelector('app-character-card')).toBeNull();
    expect(el().textContent).toContain('Ghoul');
  });

  it('is positioned from the figure and stays within the screen', () => {
    TestBed.inject(StageService).setViewport({ width: 600, height: 500 });
    zoom.focusUnit({ kind: 'unit', id: 3 });
    zoom.setAnchor({ left: 500, top: 100, right: 560, bottom: 220 });
    fixture.detectChanges();
    const card = el().querySelector('.unit-focus') as HTMLElement;
    const left = parseFloat(card.style.left);
    const width = parseFloat(card.style.width);
    expect(left).toBeGreaterThanOrEqual(0);
    expect(left + width).toBeLessThanOrEqual(600);
    zoom.setAnchor({ left: 100, top: 100, right: 160, bottom: 220 });
    fixture.detectChanges();
    expect(parseFloat(card.style.left)).toBe(160 + 16);
  });

  it('goes away when the view steps back', () => {
    zoom.focusUnit({ kind: 'unit', id: 3 });
    fixture.detectChanges();
    zoom.back();
    fixture.detectChanges();
    expect(el().querySelector('.unit-focus')).toBeNull();
  });

  it('finds members by kind and id, and not by id alone', () => {
    expect(focusedMember({ kind: 'unit', id: 3 }, party, tower)?.name).toBe(
      'Maren',
    );
    expect(focusedMember({ kind: 'enemy', id: 9 }, party, tower)?.name).toBe(
      'Ghoul',
    );
    expect(focusedMember({ kind: 'enemy', id: 3 }, party, tower)).toBeNull();
    expect(focusedMember(null, party, tower)).toBeNull();
  });
});
