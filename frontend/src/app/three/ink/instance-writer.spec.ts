import * as THREE from 'three';
import { floorToY } from '../floor-strip/floor-strip-plan';
import { TOWER_RECALL_X, towerLaunchVolumes } from '../launch/launch-objects';
import { LAUNCH_CAPACITY, rimVolumes } from '../launch/object-rim';
import { subjectOfVolume } from '../village/village-subjects';
import { InstanceWriter } from './instance-writer';

/** An instanced box mesh as the Tower scene builds it: the three ink attributes on the geometry. */
function inkMesh(capacity: number): THREE.InstancedMesh {
  const geometry = new THREE.BoxGeometry(1, 1, 1);
  for (const name of ['aTone', 'aAlpha', 'aRole']) {
    geometry.setAttribute(
      name,
      new THREE.InstancedBufferAttribute(new Float32Array(capacity), 1),
    );
  }
  const mesh = new THREE.InstancedMesh(
    geometry,
    new THREE.MeshBasicMaterial(),
    capacity,
  );
  mesh.updateMatrixWorld(true);
  return mesh;
}

/** The instance a ray from the camera's side, straight at (x, y), meets first. */
function pick(mesh: THREE.InstancedMesh, x: number, y: number): number | null {
  const ray = new THREE.Raycaster(
    new THREE.Vector3(x, y, 12.5),
    new THREE.Vector3(0, 0, -1),
  );
  const [hit] = ray.intersectObject(mesh);
  return hit?.instanceId ?? null;
}

describe('InstanceWriter', () => {
  it('lets a ray reach an instance that was rewritten to another place after a first pick', () => {
    const mesh = inkMesh(4);
    const first = new InstanceWriter(mesh);
    first.add([0, 0, 0], [1, 1, 1], 1, 1, false);
    first.finish();
    // The first raycast makes three keep a bounding sphere of what is written now.
    expect(pick(mesh, 0, 0)).toBe(0);

    const second = new InstanceWriter(mesh);
    second.add([0, 30, 0], [1, 1, 1], 1, 1, false);
    second.finish();

    expect(pick(mesh, 0, 30)).toBe(0);
    expect(pick(mesh, 0, 0)).toBeNull();
  });

  it('lets a click find the recall lamp on a Floor the Party climbed to after the first pick', () => {
    const mesh = inkMesh(LAUNCH_CAPACITY);
    const write = (floor: number) => {
      const volumes = towerLaunchVolumes({
        surfaceY: floorToY(floor),
        bellUsable: true,
        recallUsable: true,
      });
      const all = [...volumes, ...rimVolumes(volumes)];
      const writer = new InstanceWriter(mesh);
      for (const v of all) {
        writer.add([v.x, v.y, v.z], [v.w, v.h, v.d], v.tone, v.alpha, false);
      }
      writer.finish();
      return { all, bottom: floorToY(floor) };
    };

    // On Floor 1 the pointer is over the scene: the first pick.
    const low = write(1);
    const lampAt = (bottom: number) => bottom + 0.7;
    const subjectAt = (all: { id: string }[], y: number) => {
      const instance = pick(mesh, TOWER_RECALL_X, y);
      return instance === null ? null : subjectOfVolume(all[instance].id);
    };
    expect(subjectAt(low.all, lampAt(low.bottom))?.kind).toBe('recall');

    // The Party climbs; the lamp stands beside the new Floor.
    for (const floor of [3, 6, 20]) {
      const high = write(floor);
      expect(subjectAt(high.all, lampAt(high.bottom))?.kind).toBe('recall');
    }
  });
});
