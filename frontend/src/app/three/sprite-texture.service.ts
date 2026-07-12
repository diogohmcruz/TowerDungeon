import { Injectable } from '@angular/core';
import * as THREE from 'three';

/** Base colors used for placeholder billboards until real PNG art is supplied. */
const UNIT_COLORS: Record<string, string> = {
  WARRIOR: '#c0552f',
  ARCHER: '#3f9b52',
  MAGE: '#5b6bd6',
  HEALER: '#d8a12f',
  TANK: '#6b7280',
  ROGUE: '#7b3fa0',
  NECROMANCER: '#3b8a86',
  DRACO_METAMORPH: '#b5384f',
  PORTER: '#8a6d3b',
};

const ENEMY_DEFAULT = '#9a3b3b';

export type SpriteKind = 'unit' | 'enemy';

/**
 * Supplies billboard textures for units and enemies. Real art is loaded from
 * `/sprites/{kind}/{slug}.png` when present; until then a procedurally drawn
 * colored token is returned. The returned texture object is stable per key, so
 * a later successful PNG load swaps the image in place without re-binding
 * materials.
 */
@Injectable({ providedIn: 'root' })
export class SpriteTextureService {
  private readonly cache = new Map<string, THREE.Texture>();
  private readonly textCache = new Map<string, { texture: THREE.Texture; aspect: number }>();
  private readonly loader = new THREE.TextureLoader();

  get(kind: SpriteKind, typeKey: string): THREE.Texture {
    const key = `${kind}:${typeKey}`;
    const existing = this.cache.get(key);
    if (existing) {
      return existing;
    }

    const texture = this.makePlaceholder(kind, typeKey);
    this.cache.set(key, texture);
    this.tryLoadArt(kind, typeKey, texture);
    return texture;
  }

  colorFor(kind: SpriteKind, typeKey: string): string {
    if (kind === 'unit') {
      return UNIT_COLORS[typeKey.toUpperCase()] ?? this.hashColor(typeKey);
    }
    return ENEMY_DEFAULT;
  }

  /**
   * Returns a cached billboard texture with `content` drawn as outlined text,
   * plus the canvas aspect ratio so the caller can size the sprite without
   * distortion.
   */
  text(content: string, opts?: { color?: string }): { texture: THREE.Texture; aspect: number } {
    const color = opts?.color ?? '#f4ead2';
    const key = `text:${content}|${color}`;
    const cached = this.textCache.get(key);
    if (cached) {
      return cached;
    }

    const height = 72;
    const fontPx = 46;
    const font = `700 ${fontPx}px system-ui, sans-serif`;

    const measureCtx = document.createElement('canvas').getContext('2d')!;
    measureCtx.font = font;
    const textWidth = Math.ceil(measureCtx.measureText(content).width);
    const width = Math.max(height, textWidth + 56);

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d')!;
    ctx.font = font;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.lineJoin = 'round';
    ctx.lineWidth = 8;
    ctx.strokeStyle = 'rgba(8,10,18,0.85)';
    ctx.strokeText(content, width / 2, height / 2 + 2);
    ctx.fillStyle = color;
    ctx.fillText(content, width / 2, height / 2 + 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.magFilter = THREE.LinearFilter;
    texture.minFilter = THREE.LinearMipmapLinearFilter;
    texture.needsUpdate = true;

    const result = { texture, aspect: width / height };
    this.textCache.set(key, result);
    return result;
  }

  private slug(typeKey: string): string {
    return typeKey
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private tryLoadArt(kind: SpriteKind, typeKey: string, target: THREE.Texture): void {
    const folder = kind === 'unit' ? 'units' : 'enemies';
    const url = `/sprites/${folder}/${this.slug(typeKey)}.png`;
    this.loader.load(
      url,
      (loaded) => {
        target.image = loaded.image;
        target.colorSpace = THREE.SRGBColorSpace;
        target.needsUpdate = true;
        loaded.dispose();
      },
      undefined,
      () => {
        // No art yet — keep the placeholder token.
      },
    );
  }

  private makePlaceholder(kind: SpriteKind, typeKey: string): THREE.Texture {
    const size = 256;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    const color = this.colorFor(kind, typeKey);
    const label = this.labelFor(typeKey);

    ctx.clearRect(0, 0, size, size);

    // token body
    const pad = 24;
    const radius = 40;
    this.roundRect(ctx, pad, pad, size - pad * 2, size - pad * 2, radius);
    const grad = ctx.createLinearGradient(0, pad, 0, size - pad);
    grad.addColorStop(0, this.lighten(color, 0.25));
    grad.addColorStop(1, this.darken(color, 0.2));
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.lineWidth = 10;
    ctx.strokeStyle = kind === 'enemy' ? '#2a0d0d' : '#12203a';
    ctx.stroke();

    // label
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 92px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.shadowColor = 'rgba(0,0,0,0.55)';
    ctx.shadowBlur = 8;
    ctx.fillText(label, size / 2, size / 2 + 4);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.magFilter = THREE.LinearFilter;
    texture.minFilter = THREE.LinearMipmapLinearFilter;
    texture.needsUpdate = true;
    return texture;
  }

  private labelFor(typeKey: string): string {
    const parts = typeKey.replace(/_/g, ' ').trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return typeKey.slice(0, 2).toUpperCase();
  }

  private roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number,
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  private hashColor(key: string): string {
    let hash = 0;
    for (let i = 0; i < key.length; i++) {
      hash = (hash * 31 + key.charCodeAt(i)) % 360;
    }
    return `hsl(${hash}, 55%, 50%)`;
  }

  private lighten(color: string, amount: number): string {
    return this.mix(color, '#ffffff', amount);
  }

  private darken(color: string, amount: number): string {
    return this.mix(color, '#000000', amount);
  }

  private mix(color: string, target: string, amount: number): string {
    const c = new THREE.Color(color);
    c.lerp(new THREE.Color(target), amount);
    return `#${c.getHexString()}`;
  }
}
