import { TestBed } from '@angular/core/testing';

import {
  CLOSE_TRIAL_REFUSED,
  encodeAlias,
  GameWebSocketService,
  handshakeProtocols,
  SOCKET_FACTORY,
} from './game-web-socket.service';
import { CAMPAIGN_KEY_STORAGE, CampaignKeyService } from './campaign-key.service';
import { GameStateService } from './game-state.service';

class FakeSocket {
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  onclose: ((e: { code: number; reason?: string }) => void) | null = null;
  constructor(public protocols: string[]) {}
  send() {}
  close() {}
  open() {
    this.readyState = 1;
    this.onopen?.();
  }
  receive(frame: object) {
    this.onmessage?.({ data: JSON.stringify(frame) });
  }
  serverClose(code: number, reason = '') {
    this.readyState = 3;
    this.onclose?.({ code, reason });
  }
}

describe('Trial handshake', () => {
  it('encodes an alias as unpadded base64url of its UTF-8 bytes', () => {
    expect(encodeAlias('Ada')).toBe('QWRh');
    expect(encodeAlias('Zoë?>')).not.toMatch(/[+/=]/);
  });

  it('offers the key alone for the real Campaign', () => {
    expect(handshakeProtocols('K', null)).toEqual(['td.v1', 'K']);
  });

  it('adds the slot, the next-attempt marker and the alias for a Trial', () => {
    expect(handshakeProtocols('K', { raceId: 'season-1', next: false })).toEqual([
      'td.v1',
      'K',
      'trial.season-1',
    ]);
    expect(handshakeProtocols('K', { raceId: 'season-1', next: true, alias: ' Ada ' })).toEqual([
      'td.v1',
      'K',
      'trial.season-1.new',
      'as.QWRh',
    ]);
  });
});

describe('GameWebSocketService in a Trial', () => {
  let sockets: FakeSocket[];
  let service: GameWebSocketService;
  let key: string;

  beforeEach(() => {
    localStorage.removeItem(CAMPAIGN_KEY_STORAGE);
    sockets = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SOCKET_FACTORY,
          useValue: (_url: string, protocols: string[]) => {
            const s = new FakeSocket(protocols);
            sockets.push(s);
            return s as unknown as WebSocket;
          },
        },
      ],
    });
    service = TestBed.inject(GameWebSocketService);
    key = TestBed.inject(CampaignKeyService).key();
  });

  afterEach(() => {
    service.disconnect();
    localStorage.removeItem(CAMPAIGN_KEY_STORAGE);
  });

  it('enters a Trial with the selector and names the race it asked for', () => {
    service.connectTrial({ raceId: 'season-1', next: true, alias: 'Ada' });
    expect(sockets[0].protocols).toEqual(['td.v1', key, 'trial.season-1.new', 'as.QWRh']);
    expect(service.trialRaceId()).toBe('season-1');
  });

  it('reconnects to the attempt it started, never to a further one', () => {
    service.connectTrial({ raceId: 'season-1', next: true });
    sockets[0].open();
    service.connect();
    expect(sockets[1].protocols).toEqual(['td.v1', key, 'trial.season-1']);
  });

  it('stops with the stated reason when a Trial is refused, and does not retry', () => {
    service.connectTrial({ raceId: 'season-1', next: true });
    sockets[0].serverClose(CLOSE_TRIAL_REFUSED, 'NO_ATTEMPTS');
    expect(service.status()).toBe('stopped');
    expect(service.stopReason()?.message).toBe('Every attempt has been used.');
    expect(service.stopReason()?.refusal).toBe('NO_ATTEMPTS');
    expect(service.stopReason()?.retryable).toBeFalse();
    expect(sockets.length).toBe(1);
  });

  it('returns to the real Campaign when the Trial is left', () => {
    service.connectTrial({ raceId: 'season-1', next: false });
    sockets[0].open();
    service.leaveTrial();
    expect(sockets[1].protocols).toEqual(['td.v1', key]);
    expect(service.trialRaceId()).toBeNull();
  });

  it('keeps the race frame fields beside the state', () => {
    const state = TestBed.inject(GameStateService);
    service.connectTrial({ raceId: 'season-1', next: false });
    sockets[0].receive({
      credit: 1,
      trial: { raceId: 'season-1', status: 'RACING' },
      trialCost: { expeditions: 1, wipes: 0, unitsLost: 2, lootBanked: 3 },
      raceRecords: [],
      pillarMarks: [10],
    });
    expect(state.state().trial?.raceId).toBe('season-1');
    expect(state.state().trialCost?.unitsLost).toBe(2);
    expect(state.state().pillarMarks).toEqual([10]);
  });
});
