import { TestBed } from '@angular/core/testing';

import { GameWebSocketService } from './game-web-socket.service';

describe('GameWebSocket', () => {
  let service: GameWebSocketService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(GameWebSocketService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
