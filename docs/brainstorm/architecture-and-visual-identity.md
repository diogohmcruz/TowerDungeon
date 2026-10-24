# Architecture and visual identity: Nihei's Tower

Design only. No repository files were written. Terms follow `CONTEXT.md`. Where a word the glossary forbids is unavoidable (the existing Shortcut names "Supply Lift" and "Vanguard Camp", the manga's own room names), it is quoted as a name and not used as a term. "Village Tier" is always written in full (see section 9).

**Tags.**
- [Manga §n] is `docs/research/tower-dungeon-since-vol6.md`, section n, with the research's own label where it matters (official, review, wiki, fan).
- [TW §5b] is `docs/research/tower-wizard.md`, section 5b.
- [Package B §n] is `docs/brainstorm/village-resources.md`. [P&P §n] is `docs/brainstorm/prestige-and-persistence.md` (it now exists; section 7 is aligned to it).
- [Code] is something read in the repository for this review.
- **[U: ...]** marks a claim that rests only on the "Nihei style" or "Dragon Tower architecture" reports (cited as `[U: Nihei §n]` or `[U: DT-arch Sn]`). Those two reports are **not in the repository** (`docs/research/` holds only `tower-dungeon-since-vol6.md` and `tower-wizard.md`; a search of `docs/` finds Nihei only in those two files, Package B and `towe_dungeon_manga.md`). A [U] claim is unverified. It may inspire the drawing, but it is treated as [Invented] for every decision, and it must never appear in game copy as manga fact.
- [Invented] is original to this game. The manga is inspiration, not canon.

**Source hygiene.** Save the two reports under `docs/research/` (suggested `nihei-architecture-style.md` and `dragon-tower-architecture.md`) and re-check every [U] tag against them before any tag cites a file. No quotation or biographical claim about Nihei is made, because nothing in the repository verifies one. **Technical basis:** installed `three` 0.182.0 and `angular-three` 4.2.3 were checked in `node_modules` (see 3.6). Nothing was rendered or profiled. Every number marked "start value" is for tuning on screen, and every performance figure is a budget, not a measurement.

---

## 1. What is attested, and what is not

### 1.1 Nihei's style (what the repository supports)

| Claim | Status |
|---|---|
| Reviewers call the art "white Nihei" (clean lines, high contrast, large negative space, huge architecture, dialogue-light storytelling) against the "black Nihei" of *BLAME!* and *Biomega*. | [Manga §11, review]. The two labels are reviewers' shorthand. Use them only in design notes, never in game copy. |
| The research found no Nihei interview about the design intent of this Tower. | [Manga §11]. Every statement about *why* he draws the Tower as he does is outside the repository's evidence. |
| Reviewers describe a Wizardry-style hack-and-slash with Soulslike difficulty, where falling is fatal and rival delvers ambush the party. | [Manga §11, review]. This supports a grim register. |
| The Gatekeepers' mask resembles the *BLAME!* Safeguard. | [Manga §5, §11]. A reviewer's inference, not stated lore. |
| Structure first, characters after; the figure dwarfed by the structure; function before surface; exposed structure; steep stairs and cross-section drawings; a move from heavy black to line and white. | **[U: Nihei §1-§3]**. Used here only as a design stance ([Invented] in effect). No quotation, biography or "influence" claim from the report is reproduced. |

**Tone note.** The game's standing preference is serious, bleak and attritional. The manga's own register is not established beyond the reviewers' "Soulslike" remark above. Pushing the mood toward dread and the unknown is an [Invented] tonal choice. The only claim to the manga is the architecture it names.

### 1.2 The Tower: attested facts and unverified ones

**Supported by `docs/research`.**

| Element | Source |
|---|---|
| 3 km wide, 1000-plus floors (height is disputed: 15 km to 18 km, and 18 km is a derived figure). | [Manga §4, wiki] |
| 12 main pillars run the full height; about 50,000 steps; each floor has a reference pillar showing its floor number. | [Manga §4, wiki] |
| Dragons circle the Tower and maul anything flying up, so the only route is the interior stairs. | [Manga §4, wiki] |
| A hidden spiral staircase from floor 1 to floor 80; the Excavators' Union repaired its damaged top section. Its look is not described. | [Manga §4, review and wiki] |
| Wooden stairs on the outer wall, water sources, huge halls and ruins. "Forests, rivers and organic growth" are explicitly unsupported. | [Manga §3 item 5] |
| Floor 1 columned ruins and undead; 11 necromites; 14-15 two Gatekeepers at the stairs, looping identical rooms and the Waterway Monster (the monster is placed on 14 in §5); 20 potable water and Dragon Spores; 50 a great hall with a Chymus Insect, a Gatekeeper and undead crossbowmen; 59-60 basilisks; 80 a gigantic hall with the Upper Body Dragon, the top of the hidden staircase and the undead army (ch.30 is set here, official); 100 a well. | [Manga §4], mostly wiki-only |
| Gatekeepers: black armour, white mask, shields, maces and swords; they hoard corpses; they can lock doors even against royal blood (single source). | [Manga §5, wiki], [Manga §4, wiki] |
| People who die in the Tower become undead; basilisk victims are smashed and infected by maggots. | [Manga §4, §5] |
| Ch.28 is titled "The Great Pillar Maze and the Triangular March"; its content is unknown. Ch.31: "Miligore's strike begins to break down the tower's order". | [Manga §2, official; ch.31 partly seen] |

**Not supported by the repository (do not present as manga fact).**
- A floating monolith that dwarfs mountains and ends in cloud, "hanging from the sky" [U: DT-arch S1].
- Brick, stone and wood only; "cathedral" vaults; narrow crumbling spiral stairs and sheer drops [U: DT-arch S19, S5].
- A bridge on Floor 50; water on Floor 1; a Floor 80 "horizon line" or "cross-section" look; a coordinate on each reference pillar [U: DT-arch S4, S15, S17].
- A ruined fortress joined to the Tower by a giant bridge [U: DT-arch S2] (the repository only knows that a fortress side exists, [Manga §6]).
- "Organic growth" and "inside larger than outside" ([Manga §3 item 5] says unsupported).

Wherever the catalogue below draws one of these, the drawing is [Invented].

---

## 2. Design thesis

The Tower is a drawn object, not a lit one. The scene stops simulating light. It becomes a luminance drawing: a near-black ground, structure expressed as hairline and flat tone, quantised and dithered through a four-step ramp. Tower Wizard supplies the technique [TW §5b: few colours per state, dithered depth, flat bordered panels, tiny silhouettes, a thick outline on the selected thing, visible workplaces]. Nihei supplies what is drawn and how it is composed (as a stance, [U: Nihei §1-§3]): structure first, one module repeated with deliberate breaks, vertical shafts, the figure dwarfed by the structure, silence, line carrying information over black.

The player does not send a hero up a dungeon. They feed a thin chain of small bodies into a structure that was there before them and does not notice them. Floors above the Highest Floor are not shown as unlit rooms. They are pencil only: a faint line that dissolves by dither into void, so the unknown is drawn as an absence. The height of that void is not tied to `maxFloor`, so the picture never reveals the Summit.

**Colour is loss, with roles and a budget.** The only chromatic notes are three, each tied to a meaning and each held to a few percent of pixels, using the values [Package B §8.4] already settled and contrast-checked:
- **Crimson `#7a2230`** (`--ember`) for a Guardian and for danger. Fill and line only, never text on a night surface (use `--ember-text` `#d0556a`, 4.8:1 on the panel; in the light variant `--ember-text` becomes `#7a2230`).
- **Lamp amber** for human labour only: resting lamps `#8a5a1e`, a lit lamp core `#c98a3a`, and the dull Quarry mass `#6a5326`. It is art, never a UI text colour. Lamps appear in Village windows, the Works lamps on cleared Floors and the Party's lamp row. They never appear above the Highest Floor.
- **Resource tints, art only**: Salt is bone-white, Spores are a desaturated lichen grey-green (about `#4a5a52`), per [Package B §8.4].

The lower Tower, where the Party has Eased things, slowly becomes line-and-pale (Works). The upper Tower stays black. This reads as the reviewers' "black Nihei to white Nihei" move [Manga §11] run in reverse by attrition: an [Invented] interpretation, and the labels stay out of copy. Nothing cheers. There are no sparkles, no clouds, no bounce, no count-ups, no fanfare. The Village is a small fort at the foot of an enormous stack, with exposed structure and one steep stair ([U: DT-arch S2] inspiration only).

---

## 3. Rendering plan

### 3.1 Rulings between the three upstream designs

The Rendering, Floor and Village designs that fed this synthesis are not files in the repository. This table records the ruling on each disagreement.

| Question | Ruling |
|---|---|
| Quantise and dither | **In-material ordered dither, no post-processing.** One shared GLSL module (`ink`) for every scene material. `EffectComposer` would take over the render loop, would also quantise sprites, would need a selection-mask pass, and `angular-three` ships no post-processing package in this repo. Optional later step only. |
| Material type | **Plain `ShaderMaterial` family** with its own uniforms (`uRamp[4]`, `uCell`, `uFogLum`, `uFocusY`). No dependence on three chunk names (`opaque_fragment`, `fog_fragment`). Finish with `colorspace_fragment` [verify output]. |
| Lights | **None.** Face shade is authored in the shader from the normal. The blue rim light (`#8fb7ff`) and all three lights are deleted. The 1 px cold rim that [Package B §8.4] wants on silhouettes is baked into the sprite atlas as a ramp index, not lit. [Package B]'s "at most 4 point lights" is superseded by shader lamps. |
| How a Floor is drawn | **One instanced flat strip per Floor, three layers, painted atlas** (cheap, hand-drawable later, state in the shader). **3D is reserved** for the twelve pillars, the central shaft and hidden stair, the Village and set pieces. |
| Palette driver | **Per-Floor state** (Easing steps, Works) plus a height-based ink density, so the art equals the rule. **No palette swap per Generation** (see 7.3 and open decision 2): [Package B §R26] says "no whole-scene palette swap" and §8 rejects "palette swap as a reward". |
| Camera | **One `PerspectiveCamera` with a `Shot` table** (3.5). Slice 1 keeps the existing camera (FOV 64). |
| Pixel grid | **One constant `PIXEL_SCALE`** (CSS px per virtual pixel), applied as the canvas `dpr` of `1 / PIXEL_SCALE` with `image-rendering: pixelated`. Default 2. Review at 1, 2 and 3 in the ink spike. |
| Selected-thing outline | In-shader edge on the strip quad for Floors (bone; crimson-warm on a Guardian Floor). `LineSegments2` overlay for Annexes. No mask pass. |
| Warm light | **Role-based**, as in section 2. |
| Fog | **Dither-resolved fog**: slot 0 of every ramp equals the fog and clear colour, so far geometry dissolves into exactly the void. |
| Floor numbers | **Numerals on every discovered Floor's reference pillar** via one instanced digit mesh, dim by default, bright every 5th Floor, on Guardian Floors and on the current Floor [Manga §4, wiki]. A Guardian Floor is never marked by colour alone: it also carries the seal ring shape and the DOM text "GUARDIAN". |

### 3.2 What the code does today [Code]

`tower-scene-graph.component.ts` and `tower-scene.component.ts`: `FLOOR_HEIGHT = 2.2`; per-Floor `@for` meshes (a body 4.4 x 1.7 x 2.8 and a deck 4.7 x 0.16 x 3.1) with `MeshStandardMaterial`; Guardian slab `#7a2230`, ordinary `#5b6472`, deck `#2b3346` (current Floor deck `#6a5326` with emissive `#8a5a1e`); a base slab `#4c5464`; a glowing backdrop wall; background `#0e1424` (dark) and `#9fc0e8` (light); `ngt-fog-exp2` 0.05 / 0.028; an ambient light plus two directional lights; `GHOST_FLOORS_AHEAD = 8` faint boxes (bounded by `maxFloor`); `SPRITE_SCALE = 0.72` with one `<ngt-sprite>` per Unit and Enemy and `layoutGroup` (no cap); reinforcement Waves drawn as Porter sprites tinted by `waveColor` (golden-angle hue); one canvas-texture label sprite per Floor ("FLOOR n", plus "GUARDIAN"); `NgtCanvas` with camera `fov: 64`, `dpr = [1, 2]`; `panY` wheel and drag with decay; the camera position is lerped but the look-at target snaps. The scene sits in `.tower { flex: 0 0 46vw; min-width: 440px; height: 100vh; position: sticky }`, so the canvas is a tall portrait column (440 px wide at minimum, about 880 px on a 1920 px screen). `tower-display.component.html` also prints `Floor n / {{ maxFloor() }}` and a `depth-bar`, and already has an `.enemy-groups` DOM panel.

**Kept as is:** `FLOOR_HEIGHT`, `floorToY`, `anchorFloor`, the "stand on Floor N-1" convention, `layoutGroup` maths, `readTheme()` and its `MutationObserver`, `bindPanControls`, the `panY` clamp and decay, the `#0e1424` and `#7a2230` constants, the `SpriteTextureService` placeholder-then-PNG pattern and the 46vw column.

### 3.3 Techniques and data shape

**Shared module `three/ink/`.**
- `palette.ts`: `RAMPS[theme]`, four colours each, slot 0 equal to fog and background. A unit test pins that `#0e1424` and `#7a2230` round-trip through the colour-space conversion. Night and day ramps start from [Package B §8.4] (ink `#0e1424`, slab `#2b3346`, bone `#e7dcc4`; the light variant is an overcast ash `#b9bcc0` to `#c9c9c4`).
- `ink.glsl.ts`: `bayer4(gl_FragCoord.xy / uCell)`, `ramp(idx)`, `inkFog(depth)` (dither resolve), a face-shade helper. One `uAmount` uniform (0 flat bands to 1 full dither), lowered by `prefers-contrast`.
- `line-art.ts`: `EdgesGeometry(geo, 15)`, subdivision to about 0.5 units, interior points displaced by a seeded `mulberry32` (written in the repo, not part of three), amplitude about one virtual pixel, merged per colour with `BufferGeometryUtils.mergeGeometries` (`three/examples/jsm/utils/BufferGeometryUtils.js` exists in 0.182.0). Deterministic, so unit-testable.

**Floor strips (the Tower body).**
- One `InstancedMesh` of a `PlaneGeometry`, 3 layers per Floor (back z -0.9, structure -0.3, lip +0.5), about 324 instances for 108 Floors, one draw call. Layers use `discard` (alpha test), no sorting. At camera distance about 13.7 a 1.4-unit z spread gives roughly 10% scale difference, so the parallax is subtle but real.
- **Cell resolution.** Arithmetic from the code constants (camera at `y+7, z 12.5` looking at `y+1.3`, FOV 64, a 440 to 880 px wide canvas): the 4.4-unit strip is about 115 to 140 virtual pixels wide at `PIXEL_SCALE` 2. A 256 x 99 cell would be oversampled by about 2x, and `NearestFilter` would then shimmer and drop one-pixel lines when the camera pans. Author cells at **128 x 50** (one texel per virtual pixel in the default shot; verify on screen). At `PIXEL_SCALE` 1 the texels are 2x a screen pixel, which is acceptable for this look.
- `ArchitectureAtlasService`, modelled on `SpriteTextureService`: procedural placeholder cells now, PNG swap later from `/sprites/architecture/{architecture}-{variant}-{layer}.png`. 12 Architectures x 4 variants x 3 layers = 144 cells, 16 columns x 9 rows = 2048 x 512, **R8 index texture (about 1 MB)**, `NearestFilter`, no mipmaps, `NoColorSpace`, `unpackAlignment = 1`. A texel stores a ramp index (0 clear, 1 line, 2 detail, 3 fill or hatch). Placeholder cells are written **pixel by pixel into a `Uint8Array`** (or quantised after drawing), never with anti-aliased 2D-canvas strokes, which would produce grey texels with no ramp meaning. Cells are painted lazily per Architecture on first discovery, with seeded plus-or-minus 1 px endpoint jitter.
- Per-instance `InstancedBufferAttribute`s (not `instanceColor`): `aCell` (col, row, layer, unknown) and `aState` (works 0..3, selected, seal 0..2, mutation sign 0..n). The seal states are: 0 no Guardian here, 1 Guardian floor with no Guardian slain yet, 2 Guardian slain this Campaign (see 4.5).
- State effects in the shader: Held Works drops fill (the white read); ink density rises with Floor number through a uniform ramp (art only, no rule and no backend field); selected draws a bone edge.

**Skeleton (3D, instanced).**
- **The twelve:** 12 tall dark columns, one `InstancedMesh`, in two fogged rings behind the stack at x spacing 3.6 [Manga §4, wiki: 12 pillars]. The twelve span about 39.6 units while a Floor strip is 4.4 wide, so in the battle shot only about three pillars are in frame and the strips read as a section cut through the middle of a much wider structure. The overview shot shows roughly 30 Floors of height (not the whole Tower) at that width. The 1:5.6 width to height ratio against the manga's "about 1:5 to 1:6" is derived from disputed heights, so say nothing about it in copy.
- **Central shaft and hidden stair:** one dark channel behind the strips with a single `LineSegments` helix, solid up to the highest open Shortcut or mended section and dashed hairline above, ending at Floor 80 [Manga §4]. Its look is [Invented]. It doubles as the Shortcut map (4.5).
- **Floor numerals:** one instanced digit quad mesh from a small bitmap-font atlas (5 x 7 pixel digits).
- **Ghosts above the Highest Floor:** one banded `ShaftMesh` (a tall box with `step(fract(y / FLOOR_HEIGHT), 0.04)` for the floor line and dither dissolve by the fog term), replacing the 8 faint boxes. Generic pencil lines, not the true Architecture (the plan is backend-authoritative, and undiscovered Floors are never sent). Its height is not derived from `maxFloor`.
- **Impostor** (Far detail band, needed only once the overview shot exists): one quad with a 1 x 128 RGBA8 `DataTexture`. Updated only when the Floor set changes.

**Figures.**
- One silhouette atlas, 16 x 16 cells, 12 x 16 pixel art, procedural recipes first, PNGs from `public/sprites/` still override (when a PNG loads, copy it into its atlas cell and mark the texture `needsUpdate`). The PNGs are 12 x 16 **index PNGs**: transparent plus the greys 85 / 170 / 255 for ramp slots 1 to 3, mapped through the current ramp so sprites follow the Generation (ADR 0004). A committed generator, `frontend/tools/generate-sprites.mjs`, produces the first set and is not part of the Angular build; `SpriteTextureService` requests only the slugs listed in `catalogue.ts`, so a type without art causes no 404. Format and workflow: `frontend/public/sprites/README.md`. One `InstancedMesh` of screen-facing quads with per-instance `aCell` and `aTint`, `discard`, no sorting, pixel snapping of the quad centre in the vertex shader (the camera never rolls except in the optional `guardian` shot, where snapping is skipped). One distinguishing mark per Unit Type and Enemy Type, readable at 12 px (Archer bow arc, Porter wide pack, Mage hat, Healer hood, Tank shoulder block; Slime dome, Tower Beast quadruped, Chymus Insect leg cluster, Stone Golem block).
- **Cap 24 sprites per side**, heaviest first. The rest of a Garrison is `Points` specks behind the formation, and the DOM `.enemy-groups` panel stays authoritative for counts. `layoutGroup` has no cap today. (Garrison head-counts follow from budget over mean weight and are dozens at mid Floors, then drop as heavy Enemy Types enter. Count them in the spike before fixing the cap.)
- **Reinforcement Waves** are drawn as a short column of small silhouettes on the stair of their current Floor with a numeral beside it. The per-Wave `waveColor` hue rotation is removed: identity is the number and the order, never a hue.
- **Light on dark, with a reconciliation to review:** Enemies drawn at line luminance (bone) with their one-pixel rim, Units stone-tone with one amber pixel at the head. [Package B §8.4] specifies dark `#0a0d15` silhouettes with a cold rim for the Village. Dark figures on a dark dithered strip would not read in battle, so the battle figures are light on dark. **Decided (open decision 10): light on dark.**
- Guardians are up to the Floor's interior height (about 1.9 units, about 3.5 times a Unit). `SPRITE_SCALE` drops from 0.72 to about 0.5 (about 11 to 12 virtual pixels at scale 2). The `fit` clamp is recomputed from the new view. `PIXEL_SCALE` 3 would make a Unit about 8 px, too small, so scale 3 needs larger figures.
- Animation is stepped at 2 fps (a one-pixel bob), a hit is one inverted frame, a death is a dither dissolve.

**Village diorama (3D, module kit).** Section 5.

### 3.4 Render loop and state

- Keep angular-three's loop. Uniform updates (`uFocusY`, lamp flicker) run in `injectBeforeRender`, throttled by hand to 12 Hz. Lamp flicker follows [Package B §8.4]: plus or minus 8% on a slow noise of about 0.3 Hz per lamp. (A 12 Hz update rate is not a 12 Hz flicker.)
- `frameloop` is a real `ngt-canvas` input (`'always' | 'demand' | 'never'`) and `store.invalidate(frames?)` exists in 4.2.3 [Code: `angular-three.d.ts`]. `demand` mode renders only after `invalidate()`. Prop changes made through the template invalidate automatically. Anything done manually (uniforms, instance buffers, camera damping) must call `invalidate()` until it settles. `injectBeforeRender` callbacks do not run on idle frames, which is the point. An idle Village then costs about nothing between 4 Hz villager steps.
- Instance buffers are rewritten only when `layoutVersion` changes: `computed(..., { equal })` on the version, preallocated `Float32Array`s, `mesh.count = filled`, `instanceMatrix.needsUpdate = true`. This also fixes today's per-push recomputation of `floors()`, `slabs()` and `labels()` (the state is pushed every 100 ms). The version must bump on Floor added and on architecture or Easing change, but **not** when a Floor is rebuilt with new random Enemies on arrival.
- Dispose geometries, materials and textures in a `DestroyRef` block. `SpriteTextureService` never disposes today.
- Before the first Departure `tower` is undefined (the Tower is created lazily): the Village shot draws the Base slab, the twelve and the void.

### 3.5 Camera Shots

One `PerspectiveCamera`, a table of `Shot { fov, viewH, pitch, roll, lookAhead }`, scalars damped toward the active shot, texel-snapped on y, with the existing `damp` and `panY` logic kept and the look-at target smoothed as well (a real fix: today the position lerps and the target snaps).

| Shot | Use | Source |
|---|---|---|
| `battle` (slice 1 keeps the existing pose) | Expedition under way | existing |
| `baseUp` (about 15 degrees up in slice 2) | Village mode, no Expedition; about 70% of the frame is Tower rising into dither | [Invented]; [U: DT-arch S1] |
| `overview` | Player toggle only; about 30 Floors of height, the twelve, Guardian seals, the unknown above as pencil | [Invented] |
| `guardian` (about 1 degree dutch roll) | Guardian Floor, **off under `prefers-reduced-motion`** | [Invented] |
| `expanse` | Floor 80 only | [Manga §4] for the Floor; the horizon is [Invented] |
| `shaftDown` | Extraction or a Shortcut opening; the Wipe descent | [Invented] |
| `closeup` | The player's click on empty space in the Tower scene: the current Floor's fight fills the screen, the Floor above and below just visible; it follows the Party and stays until clicked again. The Party and the Village clock are Held while it lasts (5.5) | [Invented] |
| `unit` | The player's click on one figure: the camera frames that unit and a floating card shows its details; the Party and the Village clock are Held while it lasts (5.5) | [Invented] |

There is no automatic overview after a Guardian is slain or a Shortcut opens: it would take control from the player in the middle of reading, and [Package B §8.5] wants such events as a Ledger line plus a lamp, not a camera event. The Easing sweep (4.5) already shows the change. Camera moves have no overshoot. `closeup` and `unit` are the player's own toggles, so they do not break this rule, and under `prefers-reduced-motion` every camera change is an instant cut.

### 3.6 Feasibility check against the installed libraries

Checked in `frontend/node_modules`: `three` 0.182.0, `angular-three` 4.2.3.

| Proposal | Result |
|---|---|
| `ngt-instanced-mesh`, `ngt-shader-material`, `ngt-instanced-buffer-attribute`, `ngt-data-texture`, `ngt-primitive` | Available because `catalogue.ts` calls `extend(THREE)`. Instance count goes through `*args`. [verify in the spike] |
| `LineSegments2`, `LineMaterial`, `LineSegmentsGeometry` | Exist in `three/examples/jsm/lines/` but are **not** in the `THREE` namespace, so `extend({ LineSegments2, LineMaterial, LineSegmentsGeometry })` is needed in `catalogue.ts`. `LineMaterial.resolution` must be set to the drawing-buffer size (angular-three does not set it) and updated on resize, otherwise the 2-pixel line is wrong at `dpr` 0.5. |
| `NgtCanvas` `dpr` | `NgtDpr = number | [min, max]`. A plain number is passed straight to `gl.setPixelRatio`, so `1 / PIXEL_SCALE` works. `image-rendering: pixelated` must be applied to the canvas element (global style or `::ng-deep`). |
| Renderer defaults | angular-three creates the renderer with `antialias: true` and `alpha: true`, and uses `ACESFilmicToneMapping` unless the canvas has `flat`. Set `[flat]="true"` and `[gl]="{ antialias: false }"` on `ngt-canvas`, or MSAA smears the pixel grid and any built-in material is tone-mapped. |
| Post-processing | Not installed. The plan uses none. |
| `frameloop="demand"`, `invalidate()` | Present (above). |
| Pointer events on `ngt-primitive` | [verify]. Fallback: a throttled manual `Raycaster` on pick meshes. |
| Colour management | `ColorManagement` converts hex to linear. The ramp must be converted back or output through `colorspace_fragment`, and the clear colour must equal ramp slot 0. The unit test pins `#0e1424` and `#7a2230`. |
| Fractional pixel ratio | The drawing buffer is `floor(size * dpr)`, so an odd CSS width gives slightly non-square virtual pixels, and a resize can flash for a frame. Snap the column to a multiple of `PIXEL_SCALE` or accept the stretch. |

### 3.7 Budgets (to measure)

| Item | Budget |
|---|---|
| Reference hardware | Intel UHD 620 laptop and Apple M-series at 60 fps; scene at most 8 ms GPU, Village at most 6 ms |
| Internal resolution | About 250k virtual pixels or fewer (an 880 x 1080 column at `PIXEL_SCALE` 2 is 440 x 540 = 238k). |
| Draw calls | Whole scene at most 60 at 100 discovered Floors (strips 1, bodies and decks 2, twelve 1, shaft and thread 2, numerals 1, sprites 2, specks 2, Village at most 25), against about 400 today (about 200 floor meshes, 110 label sprites and one sprite per figure) |
| Triangles | At most 30k whole scene, at most 6k in the Village, 8k line segments |
| Textures | Floor atlas about 1 MB, props atlas about 1 MB, sprite atlas 256 x 256, digit atlas, palette; under 8 MB total |
| Shader programs | 4 to 5, replacing about 200 standard materials |
| JS per frame | At most 0.3 ms, no allocation in the render callback; an Annex rebuild at most 4 ms and only on state change |
| Idle | 4 renders per second in the Village; none when dots are frozen and the camera is settled |
| Dependencies | None new. |

Quality levels (a word that does not collide with the glossary): Low, Medium (default), High. A frame-time EMA over 20 ms for 2 s steps down by raising `PIXEL_SCALE` and turning off wobble and flicker.

---

## 4. Floor Architecture catalogue and gameplay effects

### 4.1 Rules of the system

1. Every Floor has an **Architecture**: one of 12 structural forms. It is a pure, versioned function of (the Tower seed, Floor number) plus 14 pinned Floors. In Generation 1 the seed is a shipped constant, so the Tower is the one the player learns ([P&P §3.8]: "Generation 1 is the Tower the player knows"). From Generation 2 it derives from `towerSecret` and the Generation, which stay on the server. It never changes mid-Campaign. The pins hold in every Generation so the manga-attested Floors stay recognisable.
2. Ten Floors form a **Reach**, closed by a Guardian Floor. Reaches carry the weights that pick Architectures.
3. The plan is **backend-authoritative**. The frontend never computes Architectures and never receives an undiscovered Floor, so ghost Floors show only a generic dithered outline.
4. Gameplay effects are **off by default** behind `game.architecture.enabled`, shipped one lever family at a time (section 8). Visuals ship first. The levers are Phase 2 and must pass the balance review in 4.4.
5. A non-neutral ordinary Floor shows one **rule glyph per lever**, shaped not coloured. The glyph carries a Party marker: helps the Party, hurts the Party, or both sides. Sightline and Width apply to both sides, so they show "both sides". Attrition, Supplies drain and Traverse hurt only the Party. Guardian-Floor Architectures show their Costs; the ×3 Loot and Easing are the existing reward.
6. Levers are capped (4.4). **Loot is not a lever** (see 4.4).

### 4.2 Catalogue

Multipliers are default values for `game.architecture.*` in `application.yml`. Every number is a starting value needing a spreadsheet and a playtest pass. Attrition is 1 HP per second of true damage (`GameState.passingTime` with the 1000 ms lifecycle tick [Code]) against Unit HP in the tens, so even ×1.25 is strong. Combined Attrition on a Floor is capped at ×1.5.

| # | Architecture | Strip (how it is drawn) | Rule (Gain / Cost) | Where | Source |
|---|---|---|---|---|---|
| 1 | **Colonnade** | Even rhythm of round columns with one broken stump as the break; flat beam line; mostly line and negative space | None. The neutral type, so "no glyph" teaches "nothing special". | About 30% of unpinned ordinary Floors; pinned on 1 and 5 | Columned ruins on Floor 1 [Manga §4, wiki]; the drawing is [Invented] |
| 2 | **Nave** | Tall vault ribs, two aisles, one dithered shaft of light, three tiny figures at the far end | Sightline ×1.15 both sides / Garrison ×1.25 (a Surge Floor uses the existing ×1.5 instead) | Surge-eligible; later Reaches | "Cathedral" is inferred [U: DT-arch S13, S19]; [Invented] |
| 3 | **Pillar Maze** | Three rows of columns at different spacings, floor numerals stencilled on the faces | Sightline ×0.8 both sides (melee Parties do better) / Traverse 1 s for the Party; Tower Beast mix weight ×1.5 | Surge-eligible; Reaches 3-10; pinned on 25 | Title only [Manga §2 ch.28]; layout [Invented] |
| 4 | **Stair Shaft** | Switchback stair in a narrow shaft with a missing tread; shaft lines run past the strip edge so adjacent Stair Shafts join | Width ×0.5 both sides (a swarm cannot surround you) / Supplies drain ×1.2. Never Surge. | Reaches 1-4 and 7; pinned on 3 | Stairs [Manga §4]; narrow crumbling stairs [U: DT-arch S5]; [Invented] |
| 5 | **Span** | Lower 60% is a dithered void; one thin span with a snapped support and a far stub | Garrison ×0.8 / Attrition ×1.25 and Traverse 1 s. Never Surge. | Reaches 1-10 | [U: DT-arch S2, S11]; [Invented] |
| 6 | **Waterway** | Stone quay, a water band with two stepped ripple dashes (2 fps; static under `prefers-reduced-motion`), a low arch | Supplies drain ×0.85 / Attrition ×1.25 and Tower Beast mix weight ×2.0. "Wet": the Salt Pack bonus does nothing here (matters only from Floor 45). | Surge-eligible; pinned on 15 | Waterway Monster [Manga §5, wiki]; water sources [Manga §3] |
| 7 | **Loop Rooms** | Five identical door-framed cells, same stencilled number, identical line weights | Garrison ×0.9 / Traverse 2 s on first entry in the Campaign, 1 s after, 0 once Shored. Where a Prestige Mutation also loops a Floor, the Mutation's rule replaces this one (never both). | Pinned on 14; at most one more seeded in Reaches 6-7 | Looping identical rooms on 14-15 [Manga §4, wiki only] |
| 8 | **Statuary** | Columns and 6 to 9 stone figures caught mid-step, smashed fragments on the deck | Garrison ×0.9 / Chimera mix weight ×2.0 | Surge-eligible; Floors 41 and up; pinned on 59 | Basilisks on 59-60 smash petrified victims [Manga §5, review]; the figures are [Invented] |
| 9 | **Warren** | Compressed ceiling at 40% height, a hole in the wall with a crawl line, one hearth point | Garrison ×0.6 and Attrition ×0.75 / Sightline ×0.8 both sides. Never Surge; at most one per Reach; Reaches 1-6 only. | Natural home for the Ruins-Dwellers' Stall (R14) | Ruins-Dwellers [Manga §5, wiki]; [Invented] |
| 10 | **Gate** (Guardian Floor) | Monumental door arch, a crimson seal ring, two empty armour-shaped niches, a stair behind | Width ×0.5 for the Party's melee (minimum 2) | Pinned on 10, 40, 50, 60, 100; seeded on 30, 70 and 90 between Gate and Well | Gatekeepers at stairs [Manga §4, §5]; [Invented] |
| 11 | **Well** (Guardian Floor) | Circular shaft coping in the deck, a black slot, a rope, a one-pixel drip | Attrition ×1.25 (wet); Salt does nothing. Clearing it adds 15 Supplies (Spores when R07 ships). | Pinned on 20; at most three in total; never on 40 | Floor 20 water and Spores [Manga §4]; Floor 100's well [Manga §4, wiki] |
| 12 | **Expanse** (Guardian Floor) | No walls; a hairline horizon at 45% of strip height, tiny columns receding, far lamp points, a speck of altar; the Guardian is drawn small and far, then grows as the Party advances | Traverse 3 s (3 HP and 4.5 Supplies per Unit at 1 HP/s and 1.5 Supplies/s), Sightline ×1.15 both sides | Pinned on 80 only | Floor 80's gigantic space, Upper Body Dragon and army [Manga §4, official for ch.30]; the horizon and the look are [Invented] |

**Traverse** is measured in seconds (lifecycle ticks of 1000 ms), not in the 10 ms invasion ticks. It delays combat while Attrition and Supplies drain keep running, so its cost is the arithmetic in the Expanse row.

**Not included:** Brood (it depended on Taint and Remains and leaned on "organic growth", which [Manga §3 item 5] marks unsupported); Cistern; Girder Lattice (no metal machinery in the Tower is a house rule, [Invented]); Sanctuary as its own type; Summit and Great Colonnade types (Floor 100 is a provisional Gate, and the glossary forbids naming an Architecture "Summit"). **Candidate only:** Fault (ledges and walls give way, the running gag of falling).

**Pins (14).** 1 Colonnade, 3 Stair Shaft, 5 Colonnade (long, Surge), 10 Gate, 14 Loop Rooms, 15 Waterway (Surge), 20 Well, 25 Pillar Maze (Surge), 40 Gate (dry), 50 Gate, 59 Statuary, 60 Gate, 80 Expanse, 100 Gate (provisional). The manga attests content on Floors 1, 14-15, 20, 50, 59-60, 80 and 100 (mostly wiki-only). The Waterway Monster is placed on 14 in [Manga §5], so assigning the loop to 14 and the Waterway to 15 is an [Invented] split of 14-15. Pins 3, 5, 10, 25 and 40 are [Invented] and exist to keep the Shortcut fixtures coherent. Floor 100 draws a quiet well-ring and adds no Well rules until the manga resolves the Summit [Manga §8]. If the Door of [P&P §3.7] ships, Floor 99 gets a sealed-door fixture on its own Architecture (the Door is [Invented], and the UI line is "The door does not open.").

**Reach weights and constraints.** Lower Reaches are plain and passable; upper Reaches fill with Nave, Pillar Maze, Span and Statuary. The plan is a sequential memoised pass: no Architecture twice in a row except Colonnade, at most one Warren per Reach, no more than three consecutive Floors carrying a Cost on Attrition, Supplies drain or Traverse, Statuary needs Floor 41 or higher.

**Surge Floors read as architecture.** The Surge Floors in practice are 5, 15, 25 ... 95: the interval is 5, but Floors 10, 20 ... 100 are Guardian Floors with no Garrison, so they never use the Surge budget [Code: `Tower.buildFloor`]. Those ten Floors are restricted to wide Architectures (a long Colonnade, Nave, Pillar Maze, Waterway, Statuary) so the bigger Garrison has a visible reason. The existing `surge-multiplier` replaces the Architecture's own Garrison multiplier and never stacks.

### 4.3 Data shape

```ts
interface Tower {
  currentFloor: number; maxFloor: number;          // maxFloor is not displayed (5.4)
  floors: Record<number, TowerFloor>;
  layoutVersion: number;                           // bumps on Floor added or architecture/easing change, not on re-populated Enemies
  slainGuardians: number[];                        // exposes clearedBosses (@JsonIgnore today)
  guardianFloors: number[];                        // so nothing hardcodes n % 10 (the Summit floor also counts)
}
interface TowerFloor { id; difficulty; enemies; boss?;   // `boss` stays: code name; new fields say guardian
  architecture: ArchitectureId; variant: 0|1|2|3;
  easeSteps: number;                               // derived from easingStepsFor(floor)
  traverseSeconds: number;
  mutationSign?: string; }                         // only for Mutations already met (7.3); never a list of locations
```

The seed, `towerSecret`, Generation and any imposed Mutations stay on the backend (a seed `long` can also exceed JavaScript's safe integer range). Cosmetic jitter is seeded on the client from (Floor number, variant). A rule table comes from an `ArchitectureController` mirroring `UnitStatsController`, so numbers live only in `application.yml`. Each Floor is rebuilt with new random Enemies on every arrival [Code], so any per-Floor persistent state (the Risen of Package B's D1) lives on `Tower`.

### 4.4 Levers, fairness and balance review

| Lever | Applied in | Cap |
|---|---|---|
| Garrison × | `Tower.enemyBudget` before variance, ordinary non-Surge Floors | 0.6 to 1.25 |
| Mix weights | `TowerFloor.populateEnemies`, replacing the uniform pick, never above the Floor Difficulty cap | weight at most ×2.0 |
| Sightline | Party Ranged and Magic damage and all enemy Magic damage; mana cost unchanged | 0.8 to 1.15 |
| Width | Fraction of Party size allowed to melee per side per tick (minimum 2) | 0.5 to 1.0 |
| Attrition × | `GameState.passingTime` via the current Floor's rules | combined at most ×1.5 |
| Supplies drain × | `updateInvasion` | 0.85 to 1.2 |
| Traverse | Seconds of approach before combat; Attrition and drain only | at most 3 s |
| Wet | The Salt Pack's bonus is void | flag |

**Excluded: a Loot lever.** No Architecture multiplies Loot: that would shift the loot formulas that [P&P §3.4] fits its Toll par values to (Floor f pays 5f Materials and 2f Credits; Relics come only from surplus items) and would add a second Materials source beside Package B's Quarry. Reopen it only with a rule that Loot multipliers average 1.0 over every Reach.

**Fairness contract.** Architecture is never random; only the existing plus-or-minus 25% variance remains. The type shows the moment a Floor is discovered and stays on the Tower ladder. Floors above the Highest Floor show only a dithered silhouette (the unknown is which of the 12, never how bad it can be). The Expedition Summary gains a ribbon of the Floors traversed, with a tick where a Unit fell. The first Chymus Insect the player meets is the **Floor 40 Guardian** (Floor Difficulty 4 puts a Chymus Insect at index 4 [Code]); Garrisons only add Chymus Insects from Floor 45 (Difficulty 5). The Floor 40 Gate is dry (no Wet rule), and Waterway and Well Floors from 45 up carry the Wet flag and void the Salt Pack.

**Stacking rules.**
- On any one Attack Type the combined Sightline and mix-weight effect must stay at or above 0.64 of the Weakness table's value. Pillar Maze is the example. A Tower Beast is weak to Ranged (×1.5 in `EnemyStats`), so Pillar Maze's Sightline ×0.8 and Tower Beast ×1.5 both bite a Ranged Party, and a melee Party thrives. That is legible and intended.
- Architecture multipliers apply on top of Easing, which already thins a Floor by 15% per slain Guardian above and lowers Floor Difficulty by one. Garrison × and Easing multiply (a Warren ×0.6 under three Easing steps is about 0.37 of the base, floored by `min-budget`). Works never change Easing's mechanics.
- **Phase 2 balance review before any lever ships.** [P&P §3.11] already shows the upper half of the Tower (Floors 70 to 100) is not beatable on the current enemy and Supplies numbers, pending a scaling pass. Adding Attrition or Supplies costs there makes that worse. Run the lever numbers against the scaling pass, against Package B's guard ("no consequence may reduce Food income or stack on Starvation"), and against [P&P §3.8]'s Mutations (Long Stair is a Supplies drain ×1.5 band, so it must not stack with Stair Shaft ×1.2 on the same Floors).
- Gate's Width on a Guardian Floor, where the opponent is one Enemy, is a pure Party Cost on the most dangerous Floors. It starts at ×0.5, and it needs a simulation before it ships.
- **Test infrastructure.** `backend/src/test` does not exist [Code]. Step 3 of the build order must create it, and the engine uses `ThreadLocalRandom` everywhere, so add an injectable random source before the lever tests.

### 4.5 Shortcuts, Easing, Guardians, the dead

**Easing, as Works** (driven by `easingStepsFor(floor)`, so the art follows the rule, and forgotten when a new Generation starts [P&P §3.3]): Raw (0 steps, bare ruin), Chalked (1-2, numerals repainted on the reference pillar and an amber lamp at the stair head), Shored (3-5, timber props, rope lines, a wooden stair on the outer wall [Manga §3 item 5]), Held (6 or more, clean, sparse and lit: the white read). This matches [Package B §R26]: "a thin pale edge line and a few lit windows" below a slain Guardian, slabs staying pure shadow above it. Optional: Works soften Traverse (Shored halves it, Held removes it). Easing's mechanics are unchanged.

**The Guardian's seal.** The backend rebuilds a Guardian Floor with a Guardian on every arrival, and "slain" means only that Easing was recorded (`markBossCleared`). So a seal cannot "permanently lose its crimson" while a Guardian still meets the next Expedition. Seal states: crimson ring (no Guardian slain yet), dim crimson ring with a struck numeral (slain this Campaign, so Easing applies below, yet a Guardian will hold the Floor again), none on ordinary Floors. The seal returns to full crimson in a new Generation, when Easing is forgotten.

A new slay triggers a **sweep**: a world-Y uniform descends from the Guardian Floor to the Base at about 8 Floors per second, and pixels below it switch to the eased ramp. A mechanical event, not a glow, with one Ledger line [Package B §8.5].

**Shortcuts as fixtures that grow with progress.** The four existing Shortcuts are on Floors 3, 5, 10 and 15 [Code: `ShortcutType`]. Each shows a fixture on its pinned Architecture, locked and open: Supply Lift (Floor 3, Stair Shaft) is a rope stub and cage frame, then a counterweight cage and taut rope; Vanguard Camp (5) is posts, then a palisade and one lamp; Knights' Waystation (10, Gate) is a barred door, then ajar with an anchor; Deep Anchor (15, Waterway) is iron stubs, then guide-lines across the water. `ShortcutOffer` needs a `progress` (0 to 1) field to draw the half-built state. The hidden spiral thread (3.3) is lit up to the highest open Shortcut and dashed above. Opening a Shortcut visibly repairs the stair [Manga §4: the damaged top section was repaired]. Narrow Stair (the Roll's Shortcut, Floor 21 as a placeholder [P&P §3.5]) and the Severed Stair Mutation (a broken fixture) reuse the same fixture kit. The Shortcut descriptions in `ShortcutType` contain glossary-avoided words ("lifts", "camp", "landing", "waypoint"). Clean the copy while adding fixtures. The names stay.

**The dead are Package B's Risen, not a new "Remains" rule.** A Remains rule (one per Unit lost, cap 6, up to four Garrison slots) and Taint would conflict with [Package B §D1]: Risen exist only where a Wipe ended, are capped at `min(3, 30% of the Floor's eased base budget)`, raise nothing on Guardian Floors in Package B, and Package C gives Guardian Floors a hoard (+3% health per body, capped at +15%) and makes Floors 59-60 raise more. Architecture therefore adds **no** Risen rule (a "Statuary: no Dead Soldiers rise" rule would also contradict D1's larger cap on 59-60). Art only: Risen are crimson notches, one per body, on the slab [Package B §8.4]; a Gate shows heaped bodies at its foot only if Package C's hoard ships; a Span shows no notch because those Units fell into the drop.

**Depth of the dark is art, not a rule.** Ink density rising with Floor number is a shader ramp. There is no Taint term, no standing Attrition tax and no backend field. Package B rejects a creeping Taint ("a clock that runs whether or not the player acts", [Package B §D9]) and dropped R18's Taint meter in favour of D1.

---

## 5. Village and UI architecture

### 5.1 Reconciliation (translation, not decoration)

| Tower Wizard technique [TW §5b] | Nihei reading ([U] stance) | What we build |
|---|---|---|
| Few colours per state | Black to line | Four-step ramp per state; the state is Easing and Works (per Floor) and Day or Night |
| Dithered parallax and fog | Screentone and negative space | Bayer dither on shading and fog; three strip layers plus the twelve |
| Flat bordered panels | Few surfaces | Zero radius, no shadow, no gradient, 1 px borders |
| Tiny silhouette sprites | The dwarfed figure | Villagers about 7 px tall in the Village shot, the Fort smaller than the Base slab |
| Thick outline on the selected thing | The only heavy line in a world of hairlines | 2 to 3 px on the selected Annex or Floor and nothing heavier elsewhere |
| Visible workplaces | Function-driven form | Each Annex's shape comes only from its Trade |
| Hanging annexes joined by bridges | Stairs and bridges | Annexes on rectangular volumes joined by bridges [Package B §R26]; one steep stair-bridge in |

Cut from the Tower Wizard feel: bounce, pulse, sparkle drift, coloured swarms, rounded corners, count-up tweens, Victory fanfare, cloud sprites.

### 5.2 The Village screen

**Composition.** The camera sits low and looks up about 15 degrees, so about 70% of the frame is the Tower going into dither and the Village is a thin strip at the bottom (it keeps [Package B §8.4]'s "at least 60% ink or fog"). A 0.35-unit black gap separates the ground from the underside of the Base slab, so it floats (inspiration [U: DT-arch S1]). The left and right 20% of the frame stay empty, except that the Port's sea may lie in the right-hand 20% once the Port has a footing. The Village never spans more than 60% of the width, and its bounding box stays under about 2.0 units tall. Unit tests assert this from a pure `village-layout.ts`.

**Fort and stair.** The Fort is a timber-framed keep on a stone plinth with a stepped parapet, holding the Pantry silo and the Reserves yard (the glossary forbids calling it a base or camp). One steep flight (rise about 2.2, run about 2.6) is drawn as one extruded stepped profile with tread lines. Reserves stand in ranks (cap 30, wounded ones lean 12 degrees, from `currentHealth` and `maxHealth` on `Member`). The Party crosses the stair at Set out. **Village Tier** is shown as lamps along the stair-bridge, one more lit per level [Package B §8.4].

**Annexes.** Annex count grows with Village Tier. Annex size does not: the Village grows denser while the Tower stays enormous. Annexes are rectangular volumes with bridges, with **no curved roofs** [Package B §8.4].

| Annex | Form follows function | Data shown | Gate |
|---|---|---|---|
| Fields | No building; three stepped terraces in edge lines and furrow hatch | Dots working them | Always |
| Quarry | Stepped cut into rock and a gantry (two piers, a beam, a rope as one line); the Heap is a pile of small blocks at its foot, in the dull `#6a5326` | Heap blocks equal Heap fill | R01 |
| Scribe's Study | Sealed cell on four stilts with one vertical slit (the only clad timber building) | 0 to 3 lit slits | R01 |
| Salt Pans | Three raised bunded basins, 0.12 high; a pale crust fills with stock | Crust height | Village Tier II (interim: Highest Floor 5) |
| Spore Cellar | Sunk below ground, a stair into a low flat-roofed clad box with a stepped (corbelled) ceiling, never a curved vault; one lichen-green slit | Slit lit if Spores are above 0 | Village Tier III (interim: Highest Floor 10) |
| Pantry silo | Tall thin box, the Village's only vertical accent | Slit shows fill, `min(1, log10(1 + food) / 3)` | Always |
| Vault Annex | Squat monolithic block corbelled off the Base, one door slab, no windows; crates on its roof | Roof stack `min(12, floor(log2(1 + materials + 5 * relics)))` | Always |
| Port | A short pier of two piers and a deck at the right edge of the Base slab, with a strip of dithered sea beyond it in horizontal bands and no waves. Once the Boat has arrived, a dark hull silhouette docks at the pier | Pier length follows the build; the hull is present when the Boat is docked and absent while it is on a Voyage | Footing at 12 completed Expeditions; finished with Materials and Credits |

**Rules.** Expose structure by default and clad only where function demands (Vault, Cellar, Study) [U: Nihei §3]. Materials are stone, brick and wood (a house rule). Locked Annexes appear as **footings**: a hairline rectangle on the ground with no label, progress and unknown at once. Before R01 and R02 exist, show no footings, so the scene never promises what the backend cannot do. A new Annex **draws itself** with `geometry.setDrawRange` (about 2.4 s, six segments per step at 6 Hz), and the fill appears only when the lines are complete.

**Growth with Upgrades.** Each purchased Upgrade (and each level of a Repeatable one, up to a cap per Upgrade) adds one visible module from the same kit: roofs, fences, extra Field rows, a watchtower, a second silo, more crates. The picture is a pure function of the state, so it never shows what the player does not own. Village Tier still owns the lamps and the Annex footings, and the bounding rules above still hold, so growth makes the Village denser and never taller.

**Shortcut marks.** An open Shortcut is a small lit mark on the Tower's flank behind the Base slab, at the height of its Floor, with no numeral. A locked Shortcut shows nothing, not even a hairline, so the Village never reveals how many exist. Hovering a mark highlights its Shortcut card.

**The Year.** Each Quarter has its own ground: Spring thaw and green rows, Summer long light and dry ground, Autumn gold-grey rows and stubble, Winter snow marks and bare rows. The sea's tone follows the same wave as the sea risk. The Tower's drawing does not change with the Quarter.

**Construction kit.** A pure `buildAnnex(id, tier, seed)` composes a small module kit (pier, beam, slab, block, stair, rail, corbel), merges it and returns `{ fill, edges, slits }`. It repeats one module with deliberate breaks. It mounts through `<ngt-primitive>` and rebuilds only the changed Annex.

**Villagers [Package B §R26].** One grey ash silhouette for every Villager. The Trade is shown by what is carried (a basket, a stone, a scroll) and by the lane walked, never by hue. One `Points` or instanced sprite per two Villagers, capped at 40, moving one pixel every four frames in sparse single-file lanes, no bobbing. The count is a roll-call, not a growth display. Load colours follow section 2: bone for Salt, lichen for Spores, the dull Quarry brown for stone. `prefers-reduced-motion` freezes dots and snaps the camera.

**Selection.** A `VillageSelectionService` holds `hovered` and `selected` as signals. DOM Trade and Upgrade rows and 3D Annexes, Villagers and Shortcut marks read and write the same state, so hovering a row thickens its Annex and the reverse. Hovering shows a short caption (its name, whether it works, what it makes); clicking focuses and scrolls to its row. Every action is a click on the object it concerns (section 5.6), and every hover and click has a keyboard and list equivalent. Hovering a locked footing (an Annex or the Port) states its real unlock condition in words; Shortcuts stay hidden until open. No scale-up, glow or pulse. Only line weight changes.

**Events (aligned to [Package B §8.5]; no fanfare).** Sound is Package B's policy, not new: off for the first session, loss only, every sound has a Ledger line.

| Event | Scene |
|---|---|
| Set out | The Party crosses the stair; each Unit set out lights one lamp in the gate row; the camera rises over about 1.6 s with no overshoot |
| Extraction | The camera drops; survivors walk in and set down crates one at a time with no particles (their count shows before the Loot); the Vault Annex window fades from dim lamp to lit lamp over 1.2 s |
| Wipe | The camera drops; the stair stays empty; one gate lamp goes out per Unit lost (at most 6) with a bell toll each, 1.1 s apart; then a lantern in the Annex goes out and 3 s of silence hold the empty lane. **The Loot does not dissolve or animate; the Wipe ledger lists it as text** ("They stay on the Floor."). |
| Starvation | A starved sprite stops where it stands, stays still for about 10 ticks, then is removed with no effect; its Annex window goes dark; one line in the Ledger (optional low wooden knock, at most one per second) |
| Guardian slain | The Easing sweep (4.5) and one Ledger line |
| Village Tier up | A lamp lit on the bridge, a sealed Annex opens its window, one Ledger line |
| Boat arrives | The hull silhouette appears at the pier once, the Arrivals step off one at a time, and each has a Ledger line naming them. Under `prefers-reduced-motion` the hull simply appears docked |
| Voyage | The hull leaves when loaded and returns a Quarter later; a lost cargo is one plain Ledger line naming the Quarter, with no effect |
| Defeat | The Stock panel in full crimson ("No Units. No Villagers. Nothing left to sell."); no extra animation |

### 5.3 UI as architecture

1. Panels are slabs: solid fill, 1 px border, radius 0, no shadow, no gradient, no hover lift [Package B §8.4].
2. Two line weights: hairline for everything, a heavier line only for the selected thing. A scene hairline is one virtual pixel, which is 2 CSS px at `PIXEL_SCALE` 2, so it is heavier than a DOM 1 px border. Accept this and keep the DOM at 1 px.
3. Tokens are the settled ones from [Package B §8.4]: `--void #07090f`, `--ink #0e1424`, `--panel #0b0e17`, `--rule #2e3546` (decoration only, 1.6:1), `--text #aab3c7` (9.2:1), `--bone #e7dcc4` (14.2:1, numbers and the selection outline), `--muted #7b869c` (5.3:1), `--ember #7a2230` (fill only), `--ember-dim #4a1620`, `--ember-text #d0556a` (4.8:1), `--cold #8fb7ff` (rim only). No other UI tokens (a `--line`, an oxblood text colour or a new amber) are used. Re-verify the final set with a contrast checker before shipping, and check the light variant separately. Interactive borders are `--bone`-grey at 3:1 or better.
4. A 4 px module, 16 px gaps, 12 px padding. The 12-column grid is a wink at the twelve pillars ([Invented]).
5. Dense data sits inside frames that are at least 40% empty.
6. Numbered lintels (`01 STOCK`) replace the `<hr>` separators.
7. Silence in copy: one line of help at most; delete `.food-note` (the italic note) and `.heal-hint`.
8. Colour never carries meaning alone: losses carry a leading "-" and gains a "+" or ".", per [Package B §8.4]; a selection is a heavier line; a Guardian is a shape plus text.

**Layout.** Keep the existing two columns (scrolling ledger on the left, sticky scene on the right). The scene is already `46vw` with a 440 px minimum, so no widening is needed. Cap slab width at about 56 characters. A full-bleed canvas with overlaid slabs is a later decision.

**Panels.**
- **01 STOCK** is [Package B §R04]'s Stock panel (sections Pantry, Vault, Stockroom, Tally; the per-source rate and "Pantry holds N s at current rates"). `getNetFoodPerSecond()` excludes Recovery spend, so Recovery needs the small instrumentation change R04 specifies. No Autobuy in this panel.
- **02 TRADES** (R01): one row per Annex with a name, a rail of ticks, a count and minus/plus buttons. A Trade whose Annex is still a footing is grey with no hint text.
- **04 LOADOUT** replaces `expedition-start`. The Start Floor selector is a vertical cross-section hairline from the Base to Highest Floor plus 10, with notches at open Shortcuts, a dotted top, and a thick outline on the selected start. It never prints `maxFloor`.
- **05 EXPEDITION SUMMARY** (the glossary's term; Package B styles it as the Extraction, Wipe or Summit Conquered ledger): one caps word for the Expedition Outcome, a section bar from Start Floor to Highest Floor, gained or lost values with a "-" on loss.
- **Tower ladder** replaces `depth-bar`: a 100-cell column in the left column, cell shade by Architecture family, a notch every 5th Floor, a tick on Guardian Floors from `guardianFloors` (crimson if no Guardian slain yet, dim and struck if slain), marks for current Floor and Highest Floor, dotted above Highest Floor plus 10. It never shows an undiscovered Floor's Architecture.
- **Rule panel**: the rule glyphs of 4.1 for the current or hovered Floor, each with its Party marker and a one-line text (a 1 px accent border, crimson on Guardian Floors).

**Copy.** Use the glossary and Package B's copy: SET OUT (not "Invade the tower"), EXTRACT, SEND WAVE (Reinforcement Wave), PANTRY, RECOVERY as a row in STOCK, "Take in Villager (21 Credits)" (Package B), FLOOR 004 and HIGHEST 014. Do not print the Tower's total Floors; remove `/ {{ maxFloor() }}` from `tower-display.component.html`.

**Type.** A pixel face (Fusion Pixel, as Tower Wizard uses [TW §5b], or Departure Mono) is for headings and numerals at integer sizes. Body, tooltips and Ledger lines stay in system-ui or a monospace at 12 to 13 px with a 1.5 line height, per [Package B §8.4]. Pixel body text would hurt legibility, so the pixel face is never used for body text. No italics, no synthetic bold. Check the licence (OFL), WOFF2 size and glyph coverage for the minus, multiplication and arrow glyphs before adopting.

**Motion.** UI transitions are 0 to 200 ms at most. Scene state changes ease over 2 s and never flash [Package B §8.4]. Numbers change in place. The only Village scene motion is the villager step, lamp flicker (plus or minus 8%, about 0.3 Hz) and at most 12 slow dust motes in the Salt Pans. Parallax layers do not animate. With `prefers-reduced-motion`: dots freeze, flicker and ripples stop, the camera snaps, the `guardian` roll is off and every state changes by cut. Nothing in the scene flashes more than three times per second.

### 5.4 Hiding the Tower's height (dread)

The backend still sends `maxFloor` (it is visible in the network payload, so this is a presentation choice, not a secret). The UI does not print it, and the ladder goes dotted above Highest Floor plus 10. The Summit is revealed by arriving. The game's 100 Floors against the manga's "1000-plus" [Manga §4] is a deliberate compression, never stated in copy.

---

### 5.5 The full-screen scene, launching from the picture, and zoom

**Layout.** The canvas is one fixed, full-screen backdrop. The Tower stays at the position it has today, on the right. The management layer is a full-width, translucent card layer in front of the scene (section 5.6): its cards are about 88% opaque with an ordered-dither scrim (no blur) behind them, and the space between cards is fully transparent and click-through, so the scene stays visible and clickable. A test samples the worst-case backdrop (Winter) and fails if text contrast falls below 7:1. A key and a button hide the layer entirely. Below about 1100 px the cards take the full width over a dimmed backdrop and the launch controls move to a bottom bar. The pixel scale is chosen automatically to keep the virtual pixels near the 250k budget measured in the ink spike (3 on a large screen), with figures drawn in virtual pixels, so they grow bigger rather than sharper; the quality setting can still override it.

**The ambient left.** Behind the menus lies an open plain, far ridge silhouettes, a few distant ruined towers (invented; the manga is inspiration, not canon) and the Quarter's weather. It has minor moving parts (drifting haze, weather marks) that freeze under `prefers-reduced-motion`. It carries no gameplay information, not even marks for the fallen. Cairns for the dead would be a later idea.

**Launching from the picture.** Launching is the first interaction to move into the scene, and every other action follows it (section 5.6). The controls are objects built from the box kit and picked with the raycaster, in the Tower's own style.

| Action | Object | Notes |
|---|---|---|
| Set out | A bell at the gate at the stair's foot | One click rings it; no confirmation |
| Send Reinforcement Wave | The same bell, while an Expedition is out | A different caption |
| Extract | A recall lamp on a post beside the Party's current Floor | Lighting it calls the Party home |
| Start Floor | The Base door and each open Shortcut's doorway (the marks from section 5.2) | The chosen one gets the bone outline; the Base door is the default |

A control that cannot be used is dark and unlit, with a dashed outline and a caption saying why, so state is never carried by colour alone. Hovering the bell shows a plain caption (Party size, Start Floor, Supplies carried and what the Pantry holds after leaving). Refusals (an Expedition already out, a Held Party, the Pantry guard) appear as one line beside the control and in the Ledger. Every action keeps its button and keyboard route in the panel (the radios, Set out, Extract and Send Wave), so nothing is only reachable by pointer. Buying, assigning and the Loadout follow the same pattern (section 5.6).

**The Set out transition.** The Village scene and the Tower scene stay two scenes, joined by a short dither dissolve with the 1.6 s camera rise from the Events table. Under reduced motion it is a cut. Merging them into one continuous scene is a larger scene-graph change and can come later.

**The Expedition HUD.** The Floor header, loot, Supplies, Party and enemy cards become objects in the scene (section 5.6: a Supplies gauge post, crates on the back wall behind the Party, the numeral on the Floor strip, hover on the groups), so the scene is unobstructed apart from the launch controls.

**Zoom and time.** A click on empty space in the Tower scene toggles `closeup`. A click on a unit (a figure drawn as a sprite; specks beyond the 24-sprite cap do nothing) zooms to that unit, shows its details in a floating card beside it (the character card for the Party and Reserves, the enemy card for enemies), and outlines its figure in bone. A second click on the same unit, or on empty space, steps back: unit to closeup to the normal `battle` view. The `overview` shot stays its own toggle. Both zoom states fill the whole screen, hiding the menus as the hide key does, and **stop time**: the client sends a `HOLD` action and the Party and the Village clock (including the Year, a Voyage and the dock delay) stand still exactly as in a Held Party (prestige doc 2.6), until `RESUME` when the player steps back to the normal view. While Held the allowed actions are the same as there: `RESUME`, `EXTRACT` and Village purchases, and `REINFORCE` is refused. Nothing can die or end while time is stopped, so a unit view never has to close on a death. The army list in the panel is the keyboard equivalent: choosing a unit there opens the same view, and a button in the HUD toggles the closeup.

### 5.6 The in-world interface

**Principle.** Everything the player can do is a click on the object it concerns, and every number is a gauge on a building with the exact figure on hover. No number lives in a separate strip or menu. Only these stay ordinary menus: a small top bar (Settings, the Campaign Key, Races and the hide toggle) and the Campaign Key gate screen.

**Popovers and hover.** Clicking a building opens one translucent card beside it that offers only that building's actions; clicking elsewhere or Escape closes it, only one is open at a time, and it stays on screen at any size. Hovering shows a caption with the exact figure, its rate and what spending would leave. The Quarter's weather, the Year and the sea risk follow the same rule. Objects are picked with the raycaster through `injectObjectEvents`, and the scene keeps the 5 px and 500 ms click limits and the near-miss rules the closeup uses.

**Gauges follow the Village Tier.** The lower the tier, the more values one building shows; the higher the tier, the more each building specialises and shows one. A building's popover offers only the actions for what it shows, so a sale always happens where the number is.

| Tier | What each building shows |
|---|---|
| I | The Silo: Food. The Vault Annex: Credits, Materials and Relics (three gauges, all three sell actions). The Quarry: its Heap. The Fort: the Reserves and hiring |
| II | The Salt Pans open and Salt moves there; the Vault no longer shows or sells Salt |
| III | The Spore Cellar opens and Spores move there |
| IV | The Vault splits. A Strongbox shows Credits only; a Reliquary shows Relics only; Materials move to the Quarry's Heap building |

**Where the other things live.**

| Thing | Object | At Tier IV |
|---|---|---|
| Hiring Units | The Fort | unchanged |
| Assigning Villagers, buying Villagers | The Annexes (Fields, Quarry, Study, Pans, Cellar) and the Silo | unchanged |
| Upgrades | The module each Upgrade adds to the Village (the roof, the fence, the watchtower, the spears) | unchanged |
| Tomes | The Scribe's Study | unchanged |
| Posts | The Annex whose Trade they serve | unchanged |
| The Thought-Binding Ring | The Fort's stair | unchanged |
| The Loadout | The Gate, beside the bell | unchanged |
| The Ledger | The Scribe's Study | The Study keeps the Ledger and a Wall of Names takes the fallen Units |
| The Run summary | The Gate, opened automatically after an Extraction or a Wipe, dismissible | unchanged |
| Highest Floor, Milestones, the Toll forecast and Exodus | The reference pillar | The pillar keeps the Highest Floor and the Milestones and a Stone of Count takes the Toll and Exodus ("The Tower keeps count.") |
| Succession | The Tower's foot, opened automatically when the Campaign ends | unchanged |
| A Voyage and the Boat | The Port | unchanged |

**During an Expedition.** Supplies is a gauge post beside the recall lamp, loot carried is crates on the back wall behind the Party, the Floor is the numeral on the Floor strip, and hovering the Party formation or an enemy group shows the exact numbers. Clicking a unit still opens its card (5.5).

**The list view.** Every in-world action also exists as a DOM list, hidden by default, opened with the `L` key and shown automatically for a screen reader. It is the end state of the interim card layer: while a panel's action has not moved into the scene, its card in the full-width layer is the only route, and when it moves the card is deleted from the layer and kept only in the list view.

**Migration order.** Step by step, each step one issue, shipping something playable: the full-width card layer; the popover and hover framework; hiring at the Fort; assigning Villagers at the Annexes; the gauges and selling for Tiers I to III; the Tier IV Strongbox and Reliquary; Upgrades, Tomes, Posts and the Ring; the Loadout at the Gate; the Ledger and the Run summary; the reference pillar; Succession; the Tier IV Wall of Names and Stone of Count; the Expedition objects; and finally removing the card layer and keeping the list view.

## 6. How Tower Wizard's technique and Nihei's architecture reconcile

The reconciliation is tabulated in 5.1. As rules for any new art:

- **Dither is screentone.** It carries shading and depth. It never carries colour meaning.
- **Palette per state, not per brightness.** A Floor's state (Easing, Works) selects its ramp. We hold the colour count constant and move toward black with height and toward line-and-pale where the Party has worked. No palette swap as a reward ([Package B §R26], §8).
- **The structure is drawn first.** In every strip cell the load path (pillars, spans, stairs) is drawn before ornament. If a cell reads without its detail layer, it is right.
- **Selection is the only heavy line.**
- **Visible workplaces are structural.** Works traces and Annex forms are shapes you could hold to a drawing and say what they do.
- **Cheer is removed at the source.** Lamps are small, amber, sparse and fade with Supplies. Clouds, sparkle drift, bobbing and warm gradients do not exist.
- **No metal.** BLAME!-style pipes and cables are not imported (a house rule, [Invented]). The pipe-framed foreground shot of the Nihei report [U] becomes timber scaffolds, lattices and chains.
- **Black stays in the interiors.** Even the white read keeps heavy blacks in cavities.

---

## 7. Interactions with the other in-flight designs

### 7.1 Trades and the Stockroom (Package B: R01, R04, R05, R07, R26)

- **Annexes are the Trades made visible.** Each Annex's data readout is its Stockroom: Quarry Heap blocks, Salt crust, Spore slit, Study slits. Stockroom caps set how full a readout can be.
- **Vault versus Stockroom.** The Vault Annex shows the Vault (banked Credits, Materials and Relics). Produced goods stay in the Stockroom, so the Quarry's Heap sits at the Quarry.
- **Quarry and Architecture.** With the Loot lever excluded (4.4), the Quarry Heap stays the only extra Materials source and the combined cap of Package B is untouched.
- **Salt and Wet.** The first Chymus Insect is the Floor 40 Guardian; Garrison Chymus appear from Floor 45. Waterway and Well are Wet and void the Salt Pack. The Floor 40 Gate is dry on purpose.
- **Spores.** Floor 20 (Well) has potable water and Spores [Manga §4]. The Cellar's lichen slit lights when Spores are above 0.
- **Warren.** The Ruins-Dwellers' Stall (R14) lives there. The design does not present the Ruins-Dwellers as traders as manga fact [Manga §5: intelligent, harmless; one returned stolen luggage].
- **Loadout.** Salt, Spores and Supplies compete for Carrying Capacity, so the Loadout panel precedes the Pack and Panacea.
- **Colour budget.** Section 2's three roles. Villagers are monochrome with a carried-object silhouette, which is [Package B §R26]'s answer and the Nihei-restrained reading of R26's "Trade-coloured sprites".
- **R26 and R01.** Package B ships the Village Elevation with R01. The diorama's v1 (Fort, stair, Fields, silo, Vault Annex) needs no R01, but it should not go live alone.
- **D1 Risen.** See 4.5: notches only, no Architecture rule.

### 7.2 Village Tier and persistence

- **Use "Village Tier", always in full.** The author prefers Tier, and [Package B] and [P&P §5.1] retire "Standing", which is added to Village Tier's _Avoid_ list. The frontend derives the Village Tier from the interim thresholds (II at Highest Floor 5, III at 10, IV at 25 plus 60 Relics banked) until R02 ships. Village Tier governs the Village's drawing only (Fort cladding, bridge lamps, Annex count). The Tower's Architecture is independent of it.
- **Persistence is now designed.** [P&P §2] covers it (anonymous Campaign Key, a stored file, `towerSecret` sealed, migration, offline frozen). It is a recommendation awaiting the author. This design needs only: the plan inputs (`towerSecret`, Generation), a `planVersion`, the existing `clearedBosses` and D1's Risen. **Version the plan.** If Reach weights or pins change between releases, an old Campaign's Tower must not silently change: store `planVersion` and keep the old tables, or migrate. The restored `tower.floors` are rebuilt as stubs [P&P §2.3], so Architecture is derived, not stored. No visual state is persisted, because `layoutVersion` is recomputed.

### 7.3 Prestige (Generations and Mutations, aligned to [P&P])

[P&P] settles the vocabulary and the shape, so this document has no mutation list of its own and no `prestigeCount`. Prestige is the Succession from one Generation to the next. Its v1 has **no Tower Mutations** and a "samey" Tower [P&P §1]. The variation layer is v2 (eight Mutations, Omens, a Pact, an Atlas). The diegetic cover is the official ch.31 teaser "Miligore's strike begins to break down the tower's order" [Manga §2, partly seen], which [P&P §3.2] already uses.

What this design adds:
- **Architecture variation is a plan seed, not a Mutation.** From Generation 2 the unpinned Floors' Architectures reseed from `towerSecret` and the Generation. The pins hold. It is a second, cheap source of variety against sameness, on top of the Mutations, and it stays inside the 4.4 caps (open decision 11).
- **Visual signature per Mutation**, so the player can see what was met. Locations stay hidden until met ([P&P §3.8]: names unknown until met, never the location without Survey), so the client receives `mutationSign` only on a Floor the Party has reached.

| Mutation [P&P §3.8] | Visual signature |
|---|---|
| Long Stair | A longer stair profile across the 10-Floor band |
| Veil | Floors in the band draw no figure count and a heavier dither until half the Garrison is dead |
| Hollowing | The Works on cleared Floors stay thin (the lamps and props never appear) |
| Severed Stair | Two Shortcut fixtures drawn broken |
| Looping Rooms | The Loop Rooms Architecture on the three chosen Floors |
| Spore Blight | Pale spore marks on the band's walls, no lichen slit in the Cellar for that Generation |
| Unburied | A second, smaller seal under the Guardian's seal |
| Chymus Spread | Salt-pale streaks on Garrison Floors from 23 |

- **The Door** (Floor 99 under [P&P §3.7]) draws as a sealed Gate. **Tower Memory** (a harder Tower each Generation) has no art.
- **No ramp family per Generation.** Tower Wizard swaps palette per ascension [TW §5b], but [Package B] forbids a whole-scene palette swap and a palette swap as a reward, and [P&P §3.12] warns against its "samey" prestige. If the author wants a per-Generation ramp, it is one row of `RAMPS` and one uniform (open decision 2).
- A Prestige transition may reuse the draw-in (the Tower's lines redrawn from the Base upward, no sound), under the reduced-motion rules. Mutations never change combat directly through art; the rules are theirs.

Dependencies: mutation work is gated on [P&P] v2 and on persistence. The rendering path needs only one extra attribute per instance (`mutationSign` in `aState`).

---

## 8. Build order, first slice, risks, decisions

### 8.1 Build order with sizes and dependencies

Sizes are S, M, L, for sequencing only.

| # | Work | Size | Depends on |
|---|---|---|---|
| 0 | Save the two reports into `docs/research/`; verify every [U] tag; glossary entries and two ADRs after sign-off | S | None |
| 1 | Tokens from Package B, pixel face for headings, slab restyle of every panel (pure CSS, `card.scss` defaults to radius 0 and no shadow, copy renames, remove `/ maxFloor`) | S | Font licence check |
| 2 | **Ink spike**: shared ink module, `InkMaterial` on the existing slab boxes, no lights, dithered fog replacing exp2, `PIXEL_SCALE`, `[flat]`, `antialias: false`, smoothed look-at target, Day and Night screenshots at scale 1, 2 and 3, a `renderer.info` baseline, a count of real Garrison sizes | M | 1 optional |
| 3 | Backend: create `backend/src/test` and an injectable random source; `Architecture` enum, `ArchitecturePlan` (seeded, pinned, weighted, constrained, versioned); expose `architecture`, `variant`, `layoutVersion`, `slainGuardians`, `guardianFloors`; frontend interfaces; determinism and distribution tests. Levers off. | M | None (parallel with 2) |
| 4 | Strip renderer v1: atlas service (procedural, 128 x 50 cells), instanced strips, bodies and decks, numerals, the twelve, `ShaftMesh` ghosts, selected outline, `layoutVersion` guard, dispose | M | 2, 3 |
| 5 | Figures: silhouette atlas, instanced sprites, cap 24 plus specks, scale refit, Wave columns | M | 2 |
| 6 | Easing: Works ramps, the slain-state seal, the sweep, Victory ease-all | M | 3, 4 |
| 7 | Panels: Stock, Loadout with cross-section, Expedition Summary, Tower ladder, rule panel | M | 1, 3 (Stock needs no backend) |
| 8 | Village diorama v1 (module kit, Fort, stair, Fields, silo, Vault Annex, footings, layout tests, villager lanes), shipped with R01 | L | 2 |
| 9 | Levers v1 behind `game.architecture.enabled`: Garrison, mix, Surge restriction | M | 3, injectable random, balance review |
| 10 | Levers v2: Sightline, Width, Attrition, Supplies drain, Traverse and its approach animation | M | 9 |
| 11 | Shortcut fixtures, `ShortcutOffer.progress`, hidden-stair thread | M | 4 |
| 12 | Camera Shots (`baseUp`, `overview`, `guardian`, `expanse`, `shaftDown`), overview impostor | M | 4 |
| 13 | Trades-dependent Annexes (Quarry, Study, Salt Pans, Cellar), Village Tier lamps | M | 8, R01, R02 |
| 14 | D1 Risen notches on the slab and the loss ribbon in the Summary | S | 3, 4, D1 |
| 15 | Mutation signatures, the Door fixture, Generation reseed | M | [P&P] v2, persistence |
| 16 | Hand-drawn art pass for the 144 cells | L | 4 |
| 17 | Quality levels; demand frameloop polish | S | 2 |

### 8.2 The first slice that already looks like the manga

Steps 1, 2, 3 and 4, with a minimal 5, make "Slice 1: the Section". Nothing in it depends on R01, R02, persistence or Prestige.
- Flat bordered panels and the pixel face for headings.
- `InkMaterial`, no lights, dithered fog, `PIXEL_SCALE`.
- Strips for four Architectures (Colonnade, Nave, Stair Shaft, Gate). The other eight fall back to the Colonnade drawing while the backend plan already names them.
- The twelve behind the stack, numerals on every discovered Floor's reference pillar, the thick outline on the current Floor, a Guardian Floor's crimson seal, and a dithered void with only faint line above the Highest Floor.
- Smaller figures with the 24-sprite cap.

Acceptance: a capture of the scene column at its 440 CSS px minimum width (about 220 virtual pixels at scale 2), at device pixel ratios 1 and 2, reads as pillars, hairline and void, with the Guardian Floor in crimson and no gradient or rounded corner anywhere. It holds at Floors 1, 14, 50 and 80 by pinned plan. The same capture at 100 discovered Floors stays within the draw-call budget.

### 8.3 Risks

| Risk | Notes and mitigation |
|---|---|
| **Evidence thinness** | Nearly all Floor-level architecture (the twelve, reference pillars, Floor 14's loop, Floor 80, Floor 50) is wiki-only. No Nihei interview exists on this Tower's design [Manga §11]. The two reports are not in the repo, so every [U] tag is unverified. Keep Invented items labelled, show a "[wiki]" or "invented" note on any credits page, be ready to retire a pin if a volume contradicts it, and keep "white Nihei" out of copy. |
| **Performance** | Budgets are estimates. The low-resolution framebuffer is the main lever. A driver quirk with `R8` textures or many line segments could surprise on old integrated GPUs. Profile at step 2 before step 4. |
| **Aliasing and shimmer** | Nearest-filtered 1-pixel lines under a perspective camera alias when the scale differs from the authored one. Mitigation: author cells at the default shot's virtual-pixel size, texel-snap the camera on y, offset the dither pattern by the camera's y translation in virtual pixels (exact for the middle plane, approximate for the parallax layers), and use the impostor for far views. |
| **Readability** | Reviewers say Nihei's dense panels are hard to parse ([U: DT-arch §3]). 11 to 12 px figures on dithered ground may be too small. Mitigation: the DOM `.enemy-groups` panel as the source of truth for counts, a `uAmount` that can be lowered, distinct silhouettes, a "larger figures" setting, and a legibility capture per Architecture. |
| **Accessibility and contrast** | Colour carries nothing alone: rule glyphs are shapes with Party markers, losses carry a minus sign, a Guardian is a shape plus text. Interactive borders at 3:1 or better. Re-verify text contrast on the final hexes, including the light variant. Honour `prefers-reduced-motion` and `prefers-contrast`. Flicker is plus or minus 8% at about 0.3 Hz. No auto-played camera moves. |
| **Package B and P&P agreement** | The vocabulary and rules (Village Tier, Risen, palette, curved vault, Wipe dissolve, sound, villager colours, tokens, Mutation) follow Package B and P&P. Re-check when either document changes. |
| **Balance** | Every lever number is a starting point. Upper Floors are already unbeatable on current numbers [P&P §3.11]. Levers stay Phase 2, with the 4.4 review. A Floor that keeps beating the Party must not get harder. |
| **Scope creep** | Cap the catalogue at 12 Architectures and 4 variants. Procedural cells first, hand art last. Camera set pieces and ambience are optional polish. Levers ship one family at a time. |
| **Library surface** | `ngt-instanced-mesh` with per-instance attributes, pointer events on `ngt-primitive` and `LineMaterial` on angular-three 4.2.3 and three 0.182 are unverified beyond the checks in 3.6. Fallbacks: a manual `Raycaster`, `LineBasicMaterial`, no post-processing (already the plan). Pin three at 0.182.x. |
| **Tone drift** | Tower Wizard's techniques invite its cheer. Review real captures at the end of step 4 against the tone guard: no warm colour except crimson and amber in their roles, no sparkles, nothing lights the whole scene, silence in UI, bleakness as loss and not decoration. |
| **Backend coupling** | `GameState` is pushed every 100 ms, so new per-Floor fields add payload (about 100 small objects) and the `layoutVersion` guard must exist before they are consumed. `clearedBosses` is `@JsonIgnore` today. |
| **Existing copy and names** | Shortcut copy uses glossary-avoided words. The names are proper nouns and can stay. |

### 8.4 Decisions only the author can make (with recommendations)

1. **Light theme.** Replace the sky-blue `#9fc0e8` with the overcast ash of [Package B §8.4] (`#b9bcc0` to `#c9c9c4`)? Recommendation: yes. It is the one value that fights the tone. `#0e1424` and `#7a2230` stay, and the old colour stays switchable by token.
2. **Per-Generation ramp family.** Tower Wizard swaps palette per ascension. Package B forbids a whole-scene palette swap. Recommendation: none in v1.
3. **Pixel grid.** `PIXEL_SCALE` 1, 2 (default) or 3 (chunky). Recommendation: review all three in the ink spike and ship 2. Scale 3 needs larger figures.
4. **Strip as the primary Floor drawing**, with 3D only for the skeleton, shaft, Village and set pieces. Recommendation: yes.
5. **Colour roles.** Crimson for Guardians, amber for human labour (art only), cold neutrals for the Tower, Package B's tints for resources. Recommendation: yes.
6. **Visual-only first, then levers one family at a time** behind a flag, after the 4.4 balance review. Recommendation: yes.
7. **Pinned Floors.** Pin 14 Floors (seven manga-attested, mostly wiki-only) or fully seed? Recommendation: pin, and keep Floor 100 a quiet Gate until the manga resolves the Summit.
8. **Hide `maxFloor`** in the UI. Recommendation: yes.
9. **Works soften Traverse** (Shored halves, Held removes). Recommendation: yes, Traverse only.
10. **Battle figures:** light on dark. **Decided**, over Package B's dark silhouettes with a cold rim.
11. **Generation reseed of unpinned Architectures** from Generation 2 (a second variety source beside the Mutations). Recommendation: yes, within the 4.4 caps.
12. **Slain Guardian's seal:** a dim crimson ring and a struck numeral, because the Guardian returns each Expedition (recommended). "Loses crimson permanently" is not accurate to the engine.
13. **Loot lever.** Excluded from v1. Reopen only with a per-Reach average of 1.0.
14. **Gate's Width cost.** Keep ×0.5 and simulate, or drop Width from Guardian Floors.
15. **Floor 80 Guardian:** leave the formula (a Dragon) and treat the Expanse silhouette as cosmetic unless the author pins it.
16. **Expanse undead army:** non-combat specks (recommended) or real escorts, which would break "a guardian instead of a garrison".
17. **Layout:** Option A (the existing two columns) now, a full-bleed canvas with slabs later.
18. **Typeface:** a pixel face for headings and numerals only, subject to licence and glyph coverage.
19. **Sound:** Package B's policy only (the Wipe bell, optional low knock, off by default), no new ambience.
20. **Which wiki-only claims may appear as manga fact in UI copy?** Recommendation: label them "[wiki]" or make no claim in copy.
21. **Save the Nihei style and Dragon Tower reports under `docs/research/`**, then verify every [U] tag. Recommendation: before step 4.

---

## 9. Glossary terms to add to `CONTEXT.md`, and conflicts

Add after sign-off, via the domain-modeling skill.

| Term | Definition | _Avoid_ |
|---|---|---|
| **Architecture** | The structural form of a Floor, fixed for the Campaign, which sets how it looks and the small rules it adds. | Biome, theme, floor type, tileset |
| **Reach** | Ten consecutive Floors ending in a Guardian Floor (Reach 1 is Floors 1-10). | Zone, region, tier |
| **Works** | The human traces left on Floors below a slain Guardian, in four states: Raw, Chalked, Shored, Held. | Stage, upgrade level |
| **Sightline**, **Width**, **Traverse** | The rule words for Architecture effects: how well ranged attacks carry, how many can fight in melee, and seconds of approach before combat. | |
| **Annex** | The Village building for one Trade. | Building, hall, workshop |
| **Village Tier**, **Trade**, **Stockroom**, **Knowledge**, **Loadout**, **Risen**, **Quarry Heap**, **Salt**, **Dragon Spores**, **Trust**, **Mutation**, **Generation** | Already proposed by Package B and [P&P §5]. Do not redefine them here. Write "Village Tier" in full. | Standing (Village Tier), Tier on its own (Floor Difficulty) |

Not added: **Remains** (it duplicates Package B's Risen), **Taint** (R18's term, superseded by D1) and a redefinition of **Mutation** (already defined in [P&P §5.2], with Load, Omens and a Pact).

**Art-only words (not domain terms).** Fort, the twelve, Section (the cross-section ladder), Gatekeeper figures. Gatekeepers on a Gate Floor are scenery. They are not Enemies, because the glossary defines an Enemy as part of a Garrison or a Guardian. The Architecture named "Gate" is not the Village's gate row of Party lamps nor the Door of [P&P §3.7]. Keep the three distinct.

**Conflicts with existing _Avoid_ lists, and how they were resolved.**
- Floor avoids hall, level, stage, landing, depth. Use "great room" or "space" for the manga's halls, "Expanse" for Floor 80, "detail bands" (Near, Mid, Far), "quality levels" and "battle pocket". Do not write "depth" for a Floor's height (use "height" or "Floor number").
- Tower avoids Dungeon and Dragon Tower (manga name only). Expedition avoids run, delve, climb: the Wipe descent is a camera shot, not a "run" event, and the Party is sent up, not made to "climb a dungeon".
- Base avoids "floor 0", entrance, foundation. Say "Base slab".
- Village avoids base, camp, home. The Fort is the Village's keep, not the Base.
- Shortcut avoids lift, camp, foothold, waypoint, checkpoint. The existing names "Supply Lift", "Vanguard Camp" and "Deep Anchor" are proper names and stay. Do not add new uses of those words as terms.
- Summit avoids top, max floor, final floor. No Architecture named "Summit". Floor 100 is a provisional Gate.
- Guardian avoids Boss. The code and `tower.ts` still use `boss`; new fields use `guardian` (`slainGuardians`, `guardianFloors`).
- Garrison avoids horde, spawn, wave. "Garrison specks" is fine. Never call them waves.
- Enemy and Party avoid army, squad (the manga's "undead army" and "squad" are quoted facts, not terms).
- Floor Difficulty avoids Tier. The two code comments and the Floor Difficulty entry are edited per [P&P §5.1] when "Village Tier" is adopted. Until then, write "Village Tier" in full everywhere and never use a bare "tier".
- Prestige avoids "reset" and "new game". Say "starts the next Generation" and "reseeds".
- Vault avoids stockpile, bank, treasury. The Stockroom is a separate store for Trade output, and the Vault stays for banked Loot.

**ADR candidates.**
1. Architecture is a pure, versioned function of (Tower seed, Floor number) plus pins, backend-authoritative, with per-Floor persistent state on `Tower` (Risen via D1).
2. The scene is drawn through one shared ink module (in-material ordered dither, a four-step ramp whose slot 0 equals the fog colour, no post-processing), with the Floor strip as the primary drawing and 3D reserved for skeleton, Village and set pieces.

**Files read for this review.** `/Users/diocru1/IdeaProjects/TowerDungeon/CONTEXT.md`; `docs/research/tower-dungeon-since-vol6.md`; `docs/research/tower-wizard.md`; `docs/brainstorm/village-resources.md`; `docs/brainstorm/prestige-and-persistence.md`; `frontend/package.json`; `frontend/src/app/components/tower-scene/tower-scene-graph.component.ts` and `tower-scene.component.ts`; `frontend/src/app/three/catalogue.ts` and `sprite-texture.service.ts`; `frontend/src/app/components/tower/tower-display.component.html`; `frontend/src/app/components/game-component/game-component.scss`; `frontend/src/app/interfaces/tower.ts`; `frontend/node_modules/angular-three` (types and `fesm2022`) and `three/examples/jsm/lines`; backend `Tower.java`, `TowerFloor.java`, `EnemyStats.java`, `GameState.passingTime`, `ShortcutType.java` and `application.yml`.
