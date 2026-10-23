import { Injectable, signal } from '@angular/core';

/** The anonymous Campaign Key: 16 random bytes as base64url, 22 characters. */
export const CAMPAIGN_KEY_PATTERN = /^[A-Za-z0-9_-]{22}$/;
export const CAMPAIGN_KEY_STORAGE = 'td-campaign-key';

export function isCampaignKey(value: string): boolean {
  return CAMPAIGN_KEY_PATTERN.test(value);
}

export function mintCampaignKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function readStoredKey(): string | null {
  const stored = localStorage.getItem(CAMPAIGN_KEY_STORAGE);
  return stored !== null && isCampaignKey(stored) ? stored : null;
}

/**
 * Holds this browser's Campaign Key. The key is minted once, kept in localStorage and reused on
 * every reload. Whoever holds the key holds the Campaign, so it is shown in settings to be copied.
 */
@Injectable({ providedIn: 'root' })
export class CampaignKeyService {
  /** True when this browser already held a key before the service started. */
  readonly existed: boolean;
  private readonly _key = signal<string>('');
  readonly key = this._key.asReadonly();

  constructor() {
    const stored = readStoredKey();
    this.existed = stored !== null;
    if (stored !== null) {
      this._key.set(stored);
    } else {
      const minted = mintCampaignKey();
      localStorage.setItem(CAMPAIGN_KEY_STORAGE, minted);
      this._key.set(minted);
    }
  }

  /** Adopts a key the player pasted. Returns false, changing nothing, if it is malformed. */
  adopt(candidate: string): boolean {
    const trimmed = candidate.trim();
    if (!isCampaignKey(trimmed)) {
      return false;
    }
    localStorage.setItem(CAMPAIGN_KEY_STORAGE, trimmed);
    this._key.set(trimmed);
    return true;
  }
}
