# Tower sprites (2.5D billboards)

PNG art in this folder replaces the procedurally-drawn placeholder tokens shown
in the 3D tower scene. Files are loaded lazily by `SpriteTextureService`. Only
slugs listed in `catalogue.ts` are requested, so a type without art keeps its
colored placeholder token and causes no 404.

## Naming

The lookup slug is the unit/enemy **type** lowercased, with non-alphanumeric
characters collapsed to `-`.

- Units: `units/{slug}.png` — `warrior`, `archer`, `mage`, `healer`, `tank`,
  `captain`, `rogue`, `necromancer`, `draco-metamorph`, `porter`
- Enemies: `enemies/{slug}.png`, keyed by the enemy's `stats.type` — `slime`,
  `dead-soldiers`, `tower-beast`, `chimera`, `stone-golem`

Adding art for a new type means dropping the PNG here **and** adding its slug to
the list in `catalogue.ts`.

## Format

- **12 × 16 px index PNG**, transparent background, matching one cell of the
  silhouette atlas in `docs/brainstorm/architecture-and-visual-identity.md`
  (section 8, "Figures").
- Four greys encode **ramp slots**, not colours: `0` (transparent / clear),
  `85` = slot 1 (line), `170` = slot 2 (detail), `255` = slot 3 (fill). The
  renderer maps them through the current palette ramp, so the art follows the
  Tower's Generation (ADR 0004) without a PNG per Generation.
- Style: small, silhouette-driven, Nihei-like (line over black), grim. Each type
  has one distinguishing mark readable at 12 px (Archer bow arc, Mage hat,
  Healer hood, Tank shoulder block, Slime dome, ...).
- **Light on dark.** Enemies are drawn at line luminance (bone) with a one-pixel
  rim; units are stone tone with one amber pixel at the head. Dark silhouettes
  would vanish on the dithered battle strip.
- Guardians and danger must never rely on colour alone (ADR 0003).
- Character faces the viewer and is billboarded to the camera; feet sit at the
  bottom edge of the cell.

## Regenerating

The committed set is produced by `frontend/tools/generate-sprites.mjs`
(`npm run sprites` in `frontend/`). The generator lives outside `public/` and
`src/`, so it is not part of the Angular build and is never served to players;
only the PNGs ship. Edit a recipe and rerun to regenerate, or replace a PNG by
hand.
