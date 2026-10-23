import { fakeAsync, TestBed, tick } from '@angular/core/testing';

import {
  BACKOFF_MAX_MS,
  backoffDelay,
  CLOSE_BAD_KEY,
  CLOSE_REPLACED,
  CLOSE_SCHEMA_NEWER,
  CLOSE_STORE_FAILURE,
  GameWebSocketService,
  SOCKET_FACTORY,
  socketUrl,
} from './game-web-socket.service';
import { CAMPAIGN_KEY_STORAGE, CampaignKeyService } from './campaign-key.service';
import { GameStateService } from './game-state.service';
import { GameAction } from '../interfaces/game-action.enum';

class FakeSocket {
  readyState = 0;
  sent: string[] = [];
  closed = false;
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  onclose: ((e: { code: number }) => void) | null = null;

  constructor(
    public url: string,
    public protocols: string[],
  ) {}

  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.closed = true;
  }
  open() {
    this.readyState = 1;
    this.onopen?.();
  }
  receive(frame: object) {
    this.onmessage?.({ data: JSON.stringify(frame) });
  }
  serverClose(code: number) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
}

describe('socket helpers', () => {
  it('doubles the backoff from 500 ms and caps it', () => {
    expect([0, 1, 2, 3].map(backoffDelay)).toEqual([500, 1000, 2000, 4000]);
    expect(backoffDelay(20)).toBe(BACKOFF_MAX_MS);
  });

  it('derives ws or wss from the page protocol and host', () => {
    expect(socketUrl({ protocol: 'http:', host: 'localhost:4200' })).toBe(
      'ws://localhost:4200/api/ws',
    );
    expect(socketUrl({ protocol: 'https:', host: 'play.example.com' })).toBe(
      'wss://play.example.com/api/ws',
    );
  });
});

describe('GameWebSocketService', () => {
  let sockets: FakeSocket[];
  let service: GameWebSocketService;
  let gameState: GameStateService;
  let key: string;

  beforeEach(() => {
    localStorage.removeItem(CAMPAIGN_KEY_STORAGE);
    sockets = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SOCKET_FACTORY,
          useValue: (url: string, protocols: string[]) => {
            const s = new FakeSocket(url, protocols);
            sockets.push(s);
            return s as unknown as WebSocket;
          },
        },
      ],
    });
    service = TestBed.inject(GameWebSocketService);
    gameState = TestBed.inject(GameStateService);
    key = TestBed.inject(CampaignKeyService).key();
  });

  afterEach(() => {
    service.disconnect();
    localStorage.removeItem(CAMPAIGN_KEY_STORAGE);
  });

  it('does not open a socket until asked', () => {
    expect(sockets.length).toBe(0);
    expect(service.status()).toBe('idle');
  });

  it('connects with the td.v1 subprotocol and the Campaign Key', () => {
    service.connect();
    expect(sockets.length).toBe(1);
    expect(sockets[0].protocols).toEqual(['td.v1', key]);
    expect(sockets[0].url).toBe(socketUrl());
    expect(service.status()).toBe('connecting');
    sockets[0].open();
    expect(service.status()).toBe('open');
  });

  it('reads the state from a frame and the session beside it', () => {
    service.connect();
    sockets[0].open();
    const session = {
      resumed: true,
      savedAt: '2026-01-01T00:00:00Z',
      schema: 1,
      notices: [{ code: 'X', message: 'hello' }],
      held: false,
    };
    sockets[0].receive({ credit: 42, session });
    expect(gameState.state().credit).toBe(42);
    expect((gameState.state() as any).session).toBeUndefined();
    expect(gameState.session()).toEqual(session);
  });

  it('still accepts a bare state with no session', () => {
    service.connect();
    sockets[0].receive({ credit: 7 });
    expect(gameState.state().credit).toBe(7);
    expect(gameState.session()).toBeNull();
  });

  it('sends actions as JSON only while open', () => {
    service.connect();
    service.sendAction(GameAction.EXTRACT, null);
    expect(sockets[0].sent).toEqual([]);
    sockets[0].open();
    service.sendAction(GameAction.EXTRACT, null);
    expect(JSON.parse(sockets[0].sent[0])).toEqual({
      gameAction: GameAction.EXTRACT,
      payload: null,
    });
  });

  it('reconnects with growing backoff after an unexpected close', fakeAsync(() => {
    service.connect();
    sockets[0].serverClose(1006);
    expect(service.status()).toBe('reconnecting');

    tick(499);
    expect(sockets.length).toBe(1);
    tick(1);
    expect(sockets.length).toBe(2);

    sockets[1].serverClose(1006);
    tick(999);
    expect(sockets.length).toBe(2);
    tick(1);
    expect(sockets.length).toBe(3);

    sockets[2].serverClose(1006);
    tick(1999);
    expect(sockets.length).toBe(3);
    tick(1);
    expect(sockets.length).toBe(4);
    service.disconnect();
  }));

  it('resets the backoff once a connection opens', fakeAsync(() => {
    service.connect();
    sockets[0].serverClose(1006);
    tick(500);
    sockets[1].serverClose(1006);
    tick(1000);
    sockets[2].open();
    sockets[2].serverClose(1006);
    tick(499);
    expect(sockets.length).toBe(3);
    tick(1);
    expect(sockets.length).toBe(4);
    service.disconnect();
  }));

  it('stops on 4001 and says another tab took over', fakeAsync(() => {
    service.connect();
    sockets[0].open();
    sockets[0].serverClose(CLOSE_REPLACED);
    expect(service.status()).toBe('stopped');
    expect(service.stopReason()?.code).toBe(CLOSE_REPLACED);
    expect(service.stopReason()?.message).toContain('Another tab');
    expect(service.stopReason()?.retryable).toBeTrue();
    tick(60000);
    expect(sockets.length).toBe(1);
  }));

  for (const [name, code, retryable] of [
    ['4400 BAD_KEY', CLOSE_BAD_KEY, false],
    ['4426 SCHEMA_NEWER', CLOSE_SCHEMA_NEWER, false],
    ['4500 STORE_FAILURE', CLOSE_STORE_FAILURE, true],
  ] as const) {
    it(`stops without reconnecting on ${name}`, fakeAsync(() => {
      service.connect();
      sockets[0].serverClose(code);
      expect(service.status()).toBe('stopped');
      expect(service.stopReason()?.code).toBe(code);
      expect(service.stopReason()?.retryable).toBe(retryable);
      tick(60000);
      expect(sockets.length).toBe(1);
    }));
  }

  it('connects again on demand after a stop and clears the reason', () => {
    service.connect();
    sockets[0].serverClose(CLOSE_REPLACED);
    service.connect();
    expect(sockets.length).toBe(2);
    expect(service.stopReason()).toBeNull();
    expect(service.status()).toBe('connecting');
  });

  it('does not reconnect after disconnect', fakeAsync(() => {
    service.connect();
    sockets[0].serverClose(1006);
    service.disconnect();
    tick(60000);
    expect(sockets.length).toBe(1);
    expect(service.status()).toBe('idle');
  }));
});
