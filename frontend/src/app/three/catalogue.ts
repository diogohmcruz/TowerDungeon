import * as THREE from 'three';
import { extend } from 'angular-three';

/**
 * Registers the whole three.js namespace with Angular Three's renderer so that
 * every class is usable as an `<ngt-*>` element in scene templates. Import this
 * module for its side effect once, before any `<ngt-canvas>` is rendered.
 */
extend(THREE);
