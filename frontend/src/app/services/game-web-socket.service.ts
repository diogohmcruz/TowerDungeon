import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { GameStateService } from './game-state.service';
import { CampaignKeyService } from './campaign-key.service';
import { GameAction } from '../interfaces/game-action.enum';
import { GameFrame } from '../interfaces/game-frame';
import { refusalText } from '../interfaces/race-copy';

export const SUBPROTOCOL = 'td.v1';

/** Close codes the server uses to end a connection on purpose. */
export const CLOSE_REPLACED = 4001;
export const CLOSE_BAD_KEY = 4400;
export const CLOSE_SCHEMA_NEWER = 4426;
export const CLOSE_STORE_FAILURE = 4500;
/** A Trial was refused at the door; the close reason is the TrialDesk refusal name. */
export const CLOSE_TRIAL_REFUSED = 4403;

export const BACKOFF_BASE_MS = 500;
export const BACKOFF_MAX_MS = 15000;

/** Delay before reconnect attempt number `attempt` (0 first): 0.5 s doubling up to 15 s. */
export function backoffDelay(attempt: number): number {
  return Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS * 2 ** Math.max(0, attempt));
}

/** ws, or wss when the page is https, on the page's own host so the dev proxy applies. */
export function socketUrl(loc: Pick<Location, 'protocol' | 'host'> = location): string {
  const scheme = loc.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${loc.host}/api/ws`;
}

/** Which Trial a connection asks for; none means the real Campaign. */
export interface TrialSlot {
  raceId: string;
  /** Start the next attempt rather than return to the current one. */
  next: boolean;
  alias?: string;
}

/** The alias as base64url of its UTF-8 bytes, which is how the handshake carries it. */
export function encodeAlias(alias: string): string {
  const bytes = new TextEncoder().encode(alias);
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** The subprotocol offer: `td.v1, <key>[, trial.<raceId>[.new][, as.<alias>]]`. */
export function handshakeProtocols(key: string, slot: TrialSlot | null): string[] {
  const protocols = [SUBPROTOCOL, key];
  if (slot) {
    protocols.push(`trial.${slot.raceId}${slot.next ? '.new' : ''}`);
    const alias = slot.alias?.trim();
    if (alias) {
      protocols.push(`as.${encodeAlias(alias)}`);
    }
  }
  return protocols;
}

export type SocketFactory = (url: string, protocols: string[]) => WebSocket;

export const SOCKET_FACTORY = new InjectionToken<SocketFactory>('SOCKET_FACTORY', {
  providedIn: 'root',
  factory: () => (url, protocols) => new WebSocket(url, protocols),
});

export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'stopped';

export interface StopReason {
  code: number;
  message: string;
  /** The Trial refusal name when a Trial was refused at the door. */
  refusal?: string;
  /** True when trying again can help (another tab took over, or the store failed). */
  retryable: boolean;
}

export function stopReasonFor(code: number, reason = ''): StopReason | null {
  switch (code) {
    case CLOSE_TRIAL_REFUSED:
      return { code, message: refusalText(reason), refusal: reason, retryable: false };
    case CLOSE_REPLACED:
      return {
        code,
        message:
          'Another tab took over this Campaign. This tab has stopped. Close the other tab, or take the Campaign back here.',
        retryable: true,
      };
    case CLOSE_BAD_KEY:
      return {
        code,
        message: 'The server refused the Campaign Key. Check it and try again.',
        retryable: false,
      };
    case CLOSE_SCHEMA_NEWER:
      return {
        code,
        message:
          'This Campaign was saved by a newer version of the game. Load the newer version to continue it.',
        retryable: false,
      };
    case CLOSE_STORE_FAILURE:
      return {
        code,
        message: 'The server could not read or write the saved Campaign. Try again shortly.',
        retryable: true,
      };
    default:
      return null;
  }
}

@Injectable({ providedIn: 'root' })
export class GameWebSocketService {
  private readonly gameState = inject(GameStateService);
  private readonly campaignKey = inject(CampaignKeyService);
  private readonly factory = inject(SOCKET_FACTORY);

  private socket: WebSocket | null = null;
  private attempt = 0;
  private slot: TrialSlot | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  private readonly _status = signal<ConnectionStatus>('idle');
  readonly status = this._status.asReadonly();

  private readonly _trialRaceId = signal<string | null>(null);
  /** The race this connection asked to enter, or null when it is on the real Campaign. */
  readonly trialRaceId = this._trialRaceId.asReadonly();

  private readonly _stopReason = signal<StopReason | null>(null);
  readonly stopReason = this._stopReason.asReadonly();

  /** Opens the connection with the current Campaign Key. Safe to call again to retry. */
  connect(): void {
    this.cancelTimer();
    this.detach();
    this._stopReason.set(null);
    this._status.set(this.attempt === 0 ? 'connecting' : 'reconnecting');

    const socket = this.factory(
      socketUrl(),
      handshakeProtocols(this.campaignKey.key(), this.slot),
    );
    this.socket = socket;

    socket.onopen = () => {
      this.attempt = 0;
      this._status.set('open');
      // A reconnect returns to the attempt just started, never to a further one.
      if (this.slot?.next) {
        this.slot = { ...this.slot, next: false };
      }
    };

    socket.onmessage = (event) => this.handleFrame(event.data);

    socket.onerror = (err) => console.error('[WebSocket] Error', err);

    socket.onclose = (event) => {
      if (this.socket !== socket) {
        return;
      }
      this.socket = null;
      this.handleClose(event.code, event.reason);
    };
  }

  /** Enters a Trial: the real Campaign is set aside, frozen, until the Trial is left. */
  connectTrial(slot: TrialSlot): void {
    this.attempt = 0;
    this.slot = slot;
    this._trialRaceId.set(slot.raceId);
    this.connect();
  }

  /** Leaves a Trial, or a refused entry, and returns to the real Campaign. */
  leaveTrial(): void {
    this.attempt = 0;
    this.slot = null;
    this._trialRaceId.set(null);
    this.connect();
  }

  /** Closes the connection on purpose and does not reconnect. */
  disconnect(): void {
    this.cancelTimer();
    this.detach();
    this.attempt = 0;
    this._status.set('idle');
  }

  sendAction(gameAction: GameAction, payload?: any) {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      console.warn('[WebSocket] Not connected, dropping action', gameAction);
      return;
    }
    this.socket.send(JSON.stringify({ gameAction, payload }));
  }

  private handleFrame(data: unknown): void {
    try {
      const { session, ...state } = JSON.parse(data as string) as GameFrame;
      this.gameState.setState(state as GameFrame);
      this.gameState.setSession(session ?? null);
    } catch (e) {
      console.error('Invalid game state received:', data);
    }
  }

  private handleClose(code: number, closeReason?: string): void {
    const reason = stopReasonFor(code, closeReason);
    if (reason) {
      this.attempt = 0;
      this._stopReason.set(reason);
      this._status.set('stopped');
      return;
    }
    const delay = backoffDelay(this.attempt);
    this.attempt += 1;
    this._status.set('reconnecting');
    this.timer = setTimeout(() => this.connect(), delay);
  }

  private detach(): void {
    const socket = this.socket;
    this.socket = null;
    if (socket) {
      socket.onopen = socket.onmessage = socket.onerror = socket.onclose = null;
      socket.close();
    }
  }

  private cancelTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
