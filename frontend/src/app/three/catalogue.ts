import * as THREE from 'three';
import { extend } from 'angular-three';

/**
 * Registers the whole three.js namespace with Angular Three's renderer so that
 * every class is usable as an `<ngt-*>` element in scene templates. Import this
 * module for its side effect once, before any `<ngt-canvas>` is rendered.
 */
extend(THREE);

/**
 * Slugs that have PNG art under `public/sprites/`. `SpriteTextureService`
 * requests only these, so a type without art keeps its placeholder token and
 * causes no 404.
 */
export const SPRITE_ART: Record<'unit' | 'enemy', readonly string[]> = {
  unit: [
    'warrior',
    'archer',
    'mage',
    'healer',
    'tank',
    'captain',
    'rogue',
    'necromancer',
    'draco-metamorph',
    'porter',
  ],
  enemy: ['slime', 'dead-soldiers', 'tower-beast', 'chimera', 'stone-golem'],
};
