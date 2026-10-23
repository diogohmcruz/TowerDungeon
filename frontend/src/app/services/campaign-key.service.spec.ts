import { TestBed } from '@angular/core/testing';
import {
  CAMPAIGN_KEY_PATTERN,
  CAMPAIGN_KEY_STORAGE,
  CampaignKeyService,
  mintCampaignKey,
} from './campaign-key.service';

describe('CampaignKeyService', () => {
  beforeEach(() => {
    localStorage.removeItem(CAMPAIGN_KEY_STORAGE);
    TestBed.resetTestingModule();
  });

  afterEach(() => localStorage.removeItem(CAMPAIGN_KEY_STORAGE));

  it('mints 22-character base64url keys', () => {
    for (let i = 0; i < 200; i++) {
      expect(mintCampaignKey()).toMatch(CAMPAIGN_KEY_PATTERN);
    }
  });

  it('mints a different key each time', () => {
    expect(mintCampaignKey()).not.toBe(mintCampaignKey());
  });

  it('mints a key on first use and stores it', () => {
    const service = TestBed.inject(CampaignKeyService);
    expect(service.key()).toMatch(CAMPAIGN_KEY_PATTERN);
    expect(service.existed).toBeFalse();
    expect(localStorage.getItem(CAMPAIGN_KEY_STORAGE)).toBe(service.key());
  });

  it('reuses the stored key across reloads', () => {
    const first = TestBed.inject(CampaignKeyService);
    const key = first.key();

    TestBed.resetTestingModule(); // a reload: new injector, same localStorage
    const second = TestBed.inject(CampaignKeyService);

    expect(second.key()).toBe(key);
    expect(second.existed).toBeTrue();
  });

  it('replaces a malformed stored key with a freshly minted one', () => {
    localStorage.setItem(CAMPAIGN_KEY_STORAGE, 'not a key');
    const service = TestBed.inject(CampaignKeyService);
    expect(service.key()).toMatch(CAMPAIGN_KEY_PATTERN);
    expect(service.existed).toBeFalse();
  });

  it('adopts a well-formed pasted key, trimmed, and persists it', () => {
    const service = TestBed.inject(CampaignKeyService);
    const pasted = mintCampaignKey();
    expect(service.adopt(`  ${pasted}\n`)).toBeTrue();
    expect(service.key()).toBe(pasted);
    expect(localStorage.getItem(CAMPAIGN_KEY_STORAGE)).toBe(pasted);
  });

  it('refuses a malformed pasted key and keeps the current one', () => {
    const service = TestBed.inject(CampaignKeyService);
    const before = service.key();
    expect(service.adopt('short')).toBeFalse();
    expect(service.adopt('!'.repeat(22))).toBeFalse();
    expect(service.key()).toBe(before);
  });
});
