# Tower sprites (2.5D billboards)

Drop PNG art here to replace the procedurally-drawn placeholder tokens shown in
the 3D tower scene. Files are loaded lazily by `SpriteTextureService`; if a file
is missing, the colored placeholder token is used instead (no error).

## Naming

The lookup slug is the unit/enemy **type** lowercased, with non-alphanumeric
characters collapsed to `-`.

- Units: `units/{slug}.png` — e.g. `units/warrior.png`, `units/archer.png`,
  `units/mage.png`, `units/healer.png`, `units/tank.png`, `units/rogue.png`,
  `units/necromancer.png`, `units/draco-metamorph.png`, `units/porter.png`
- Enemies: `enemies/{slug}.png` — keyed by the enemy's `stats.type`, e.g.
  `enemies/slime.png`, `enemies/dead-soldiers.png`, `enemies/tower-beast.png`,
  `enemies/chimera.png`, `enemies/stone-golem.png`

## Art guidance

- Square (e.g. 256×256 or 512×512), transparent background (PNG).
- Character faces the viewer; it is billboarded to always face the camera.
- Feet near the bottom edge — sprites are anchored just above the floor slab.
