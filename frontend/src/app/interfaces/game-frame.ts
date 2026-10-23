import { GameState } from './game-state';

export interface SessionNotice {
  code: string;
  message: string;
}

/** The session's own facts, sent beside the state in every frame. */
export interface SessionMeta {
  resumed: boolean;
  savedAt: string | null;
  schema: number;
  notices: SessionNotice[];
  held: boolean;
}

/** One frame from the server: the state, unwrapped, plus `session`. */
export type GameFrame = GameState & { session?: SessionMeta };
