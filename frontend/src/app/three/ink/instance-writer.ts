import * as THREE from 'three';

const IDENTITY_QUATERNION = new THREE.Quaternion();

/** Fills an instanced ink mesh (matrices plus the three ink attributes) in order. */
export class InstanceWriter {
  private n = 0;
  private readonly matrix = new THREE.Matrix4();
  private readonly position = new THREE.Vector3();
  private readonly scale = new THREE.Vector3();
  private readonly tone: THREE.BufferAttribute;
  private readonly alpha: THREE.BufferAttribute;
  private readonly role: THREE.BufferAttribute;

  private readonly capacity: number;

  constructor(private readonly mesh: THREE.InstancedMesh) {
    this.capacity = mesh.instanceMatrix.count;
    const a = mesh.geometry.attributes;
    this.tone = a['aTone'] as THREE.BufferAttribute;
    this.alpha = a['aAlpha'] as THREE.BufferAttribute;
    this.role = a['aRole'] as THREE.BufferAttribute;
  }

  add(
    position: [number, number, number],
    scale: [number, number, number],
    tone: number,
    alpha: number,
    crimson: boolean,
  ): void {
    if (this.n >= this.capacity) {
      return;
    }
    this.matrix.compose(
      this.position.set(...position),
      IDENTITY_QUATERNION,
      this.scale.set(...scale),
    );
    this.mesh.setMatrixAt(this.n, this.matrix);
    this.tone.setX(this.n, tone);
    this.alpha.setX(this.n, alpha);
    this.role.setX(this.n, crimson ? 1 : 0);
    this.n++;
  }

  finish(): void {
    this.mesh.count = this.n;
    this.mesh.instanceMatrix.needsUpdate = true;
    this.tone.needsUpdate = true;
    this.alpha.needsUpdate = true;
    this.role.needsUpdate = true;
    // three computes an InstancedMesh's bounding sphere on the first raycast (or cull) and
    // never again. The launch objects move up a Floor with the Party, so a stale sphere
    // stops the ray from reaching the lamp: the click then falls through to the canvas.
    this.mesh.boundingSphere = null;
    this.mesh.boundingBox = null;
  }
}
