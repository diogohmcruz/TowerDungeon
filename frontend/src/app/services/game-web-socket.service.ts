import { inject, Injectable } from '@angular/core';
import { GameStateService } from './game-state.service';
import { GameState } from '../interfaces/game-state';
import { GameAction } from '../interfaces/game-action.enum';

@Injectable({ providedIn: 'root' })
export class GameWebSocketService {
  private socket = new WebSocket('ws://localhost:8080/api/ws');
  private gameState = inject(GameStateService);

  constructor() {
    this.socket.onopen = () => console.log('[WebSocket] Connected');

    this.socket.onmessage = (event) => {
      try {
        const parsed: GameState = JSON.parse(event.data);
        this.gameState.setState(parsed);
      } catch (e) {
        console.error('Invalid game state received:', event.data);
      }
    };

    this.socket.onerror = (err) => console.error('[WebSocket] Error', err);
  }

  sendAction(gameAction: GameAction, payload: any) {
    const message = JSON.stringify({ gameAction, payload });
    console.log('Sending to WebSocket', message);
    this.socket.send(message);
  }
}
