import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { GameComponent } from './game-component';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { GameAction } from '../../interfaces/game-action.enum';

describe('GameComponent', () => {
  let component: GameComponent;
  let fixture: ComponentFixture<GameComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GameComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(GameComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('offers Resume only while the Party is Held', () => {
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('.held-party')).toBeNull();

    TestBed.inject(GameStateService).update({
      session: {
        resumed: true,
        savedAt: null,
        schema: 1,
        notices: [],
        held: true,
      },
    });
    fixture.detectChanges();

    expect(host.querySelector('.held-party')?.textContent).toContain(
      'The Party has not moved.',
    );
  });

  it('sends RESUME', () => {
    const send = spyOn(TestBed.inject(GameWebSocketService), 'sendAction');

    component.resume();

    expect(send).toHaveBeenCalledWith(GameAction.RESUME, null);
  });
});
