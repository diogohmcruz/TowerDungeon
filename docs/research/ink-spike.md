# Ink shader spike (issue #15)

Spike report. It proves the dithered "ink" look on the existing tower scene and records the numbers that #17, #18 and #25 depend on. The agent recommends. **The author decides** the two judgements marked below.

Terms follow `CONTEXT.md`. Design basis: `docs/brainstorm/architecture-and-visual-identity.md` sections 3.3, 3.6 and 3.7, ADR 0003 (drawn, not lit), ADR 0004 and `docs/brainstorm/palette-per-generation.md` (Generation 1 ramps).

## What was built

| Piece | Where |
|---|---|
| Ramps (`RAMPS.night`/`day`, Generation 1), `LEGACY_INK`, `GUARDIAN_CRIMSON`, colour helpers | `frontend/src/app/three/ink/palette.ts` |
| GLSL: 4x4 Bayer, ramp pick, dithered fog, face shade, dithered translucency (`discard`), crimson role | `three/ink/ink.glsl.ts` |
| Shared uniforms, theme swap in place, `createInkMaterial`, tone presets, `prefers-contrast` flattening | `three/ink/ink-material.ts` |
| `PIXEL_SCALE`, `INK_DPR = 1 / PIXEL_SCALE` (dev override `?pixelScale=1\|2\|3`) | `three/ink/pixel-scale.ts` |
| Scene: no lights, no scene fog, clear colour = slot 0, `[flat]="true"`, `[gl]="{ antialias: false }"`, `image-rendering: pixelated`, smoothed look-at | `components/tower-scene/*` |
| Synthetic tower for capture without the backend (`?inkFixture=100&floor=50&garrison=12`, dev mode only) | `three/ink/ink-fixture.ts` |
| Capture and `renderer.info` tool (headless Chrome over CDP) | `frontend/tools/capture-scene.mjs` |
| Unit tests (palette round trip, ramps, uniforms, pixel scale) | `three/ink/palette.spec.ts` |

Slab bodies, ghost floors and the backdrop wall are one `InstancedMesh`; the walkable decks are a second. Per-instance attributes `aTone`, `aAlpha`, `aRole` carry ink density, dithered translucency and the Guardian crimson flag. Sprites and the floor plaques are unchanged (full-colour textures, `fog` off); they are #18 and #25.

## Numbers

Scenario: 100 discovered Floors, current Floor 50, a garrison of 12, a party of 8, camera as shipped. Counts are `renderer.info` of one frame plus a scene traversal, in headless Chrome (software GL, so frame time was **not** measured).

| | Before (lit, one mesh per slab) | After (ink, instanced) |
|---|---|---|
| Renderable objects in the scene (no culling) | 333 | 132 |
| Draw calls per frame (frustum-culled) | 57 | 33 (2 instanced + 31 sprites) |
| Triangles per frame | 374 | 2,498 (culling is off on the two instanced meshes, so every slab is drawn) |
| Scene triangles total | 2,696 | 2,696 |
| Shader programs | 3 | 2 |
| Geometries | 176 | 3 |
| Drawing buffer, CSS 420 x 900 | 840 x 1800 (dpr 2) | 420x900 at scale 1, 210x450 at 2, 140x300 at 3 |

Findings:

- The design's "about 400 draw calls" is the whole scene, not what is drawn: three.js frustum-culls, so a frame at 100 Floors costs 57 calls before. The 333 objects (101 bodies and 101 decks including the base, 100 plaques, 10 Guardian tags, 20 figures, 1 backdrop) match the estimate.
- Slabs now cost 2 calls however many Floors there are. What remains is one call per sprite (31 here). The 60-call budget in 3.7 is met with room; sprites are the only term that still scales (#25 should instance them).
- Triangles rise from 374 to 2,498 because culling is off. Still far under the 30k budget. If it matters later, cull per instance on the CPU or set a bounding sphere.
- The brief asked to "count real garrison sizes". From the backend rules (`GameProperties` defaults, `TowerFloor.populateEnemies`, enemy weights 1, 1, 2, 3, 10, 8, 20, 50, 90, no Guardian slain, 2,000 simulated Campaigns per Floor): Floor 1 about 4 (max 5), Floor 5 about 12 (max 15), Floor 15 about 37 (p95 45), Floor 25 about 54 (p95 67, max 74), Floor 99 about 25 (max 57). Guardian Floors (every 10th, and 100) hold one. A regular Floor can reach about 70 figures, so a garrison of 12 is not the worst case. `docs/research/ink-spike/` uses 12. The script is not committed; it re-implements `enemyBudget` in about 20 lines.

## Library claims (3.6), checked in the running app on `angular-three` 4.2.3, `three` 0.182

| Claim | Result |
|---|---|
| `ngt-instanced-mesh` with `*args` | **Verified.** `*args="[undefined, undefined, 256]"` creates the mesh; `mesh.count` is then set at runtime. A template ref's `nativeElement` is the `InstancedMesh` itself. Capacity is fixed by `*args`: changing it would recreate the mesh, so use a high cap and `count`. |
| `ngt-instanced-buffer-attribute` | **Verified.** `attach="attributes.aTone"` inside `ngt-box-geometry` with `*args="[Float32Array, 1]"`; written later with `setX` and `needsUpdate`. |
| `ngt-shader-material` | **Verified.** `[vertexShader]`, `[fragmentShader]`, `[uniforms]` work, and two materials share one uniforms object, so a theme change is one in-place update. `instanceMatrix` is available without declaring it. |
| Pointer events on `ngt-primitive` | **Verified at runtime, fails at compile time.** Template outputs are rejected (`NG5`: `ngt-primitive` accepts only `attached`, `updated`, `created`, `disposed`). `injectObjectEvents(() => ref.nativeElement, { click, pointerover })` on a `#probe` ref does work: real mouse events (CDP `Input.dispatchMouseEvent`) gave 1 hover and 1 click on a primitive mesh. The manual `Raycaster` fallback is not needed. |
| Renderer defaults | **Verified.** With `[flat]` and `antialias: false`: `getContextAttributes().antialias` is false, `toneMapping` is `NoToneMapping`, output sRGB, drawing buffer `floor(css * dpr)` (140 x 300 at scale 3), computed `image-rendering: pixelated`. |
| Colour management | **Verified.** `#0e1424` and `#7a2230` round-trip through `Color` (unit test). The clear colour read back from a screenshot is exactly `#080923` at night and `#b9bcc0` by day; the legacy build read `#0e1424`. Shader output goes through `colorspace_fragment`. |
| Fractional pixel ratio | **Confirmed, not fixed.** The column was 420.39 CSS px, so the buffer is 140 wide and a virtual pixel is 3.003 CSS px. Snapping the column to a multiple of `PIXEL_SCALE` is left for the layout slice. |

Notes for the next slices: `NgtThreeElement<any>` is why primitive events are untyped; `ngt-primitive` needs its instance prepared, so attach events through a signal ref, not a constant. `ng test` and the production build pass with the dev hooks present (they are behind `isDevMode()`).

## Screenshots

`docs/research/ink-spike/`: `after-{night,day}-{1,2,3}x.png` (the scene at pixel scale 1, 2 and 3 in both themes) and `before-night.png`, `before-day.png` (the lit scene, scale-independent). Same scenario as the table. Captured by `frontend/tools/capture-scene.mjs` at a 1440 x 900 viewport with a device scale factor of 2, so a 1x capture is 2 device pixels per virtual pixel. The captures are of a **software-rendered headless Chrome** with a synthetic tower and placeholder figures, not a real GPU or a real game. They are a basis for the judgement, not a replacement for looking at it live (`ng serve`, then `/?inkFixture=100&floor=50&pixelScale=2`).

## Judgement 1: does the dithered indigo read as grim stone? (author's call)

What the captures show, for the author to weigh:

- Night: shadow fill (slot 1, `#232449`) is a deep indigo and most body faces are nearly flat slot 1 with only light dither, so large areas read as dark blue cloth more than as stone. Tops of decks (slot 1 and 2 mixed) are the textured part and read as stone slabs. Distance fog dissolves cleanly into `#080923`; the dotted ghost floors above the explored top are the strongest "dread" read. The Guardian backdrop is barely visible at night because crimson shows only at ramp index 2 and above.
- Day: overcast grey reads as stone, and the current deck is the darkest area (ink), which makes the party plane pop. The crimson backdrop shows as a sparse dotted wall.
- Slot 0 is darker than `#0e1424` and the contrast between the void and the slab fronts is low; that is consistent with the decision but is the thing to look at.
- Tuning knobs if it feels too blue (before switching ramps): raise `TONE.slab` or the front face shade so the fronts dither between slots 1 and 2; both are constants in `ink-material.ts` and `ink.glsl.ts`. The fallback in `palette-per-generation.md` open decision 1 (lighter, CIE76-only) is the structural option. **I did not switch to it.**

## Judgement 2: default `PIXEL_SCALE` (recommendation: 2, author confirms)

For a 420 x 900 CSS column:

| Scale | Virtual pixels | Reading |
|---|---|---|
| 1 | 378,000 | Fine grain; plaques and the 12 x 16 figures are crisp. Over the 250k budget in 3.7 at this column size, so keep it as the High quality level only. |
| 2 | 94,500 (238k for the 880 x 1080 column in 3.7) | Dither visible but not coarse; figure art pixels land on about 2.5 CSS px; plaques readable. Inside the budget. |
| 3 | 42,000 | Chunky; plaque text turns to mush (plaque textures are still smooth canvas text, #25), and floor stripes lose their edge. Fine as the Low level on weak GPUs. |

Recommendation: default **2**, Low = 3, High = 1, which matches the Low, Medium, High plan in 3.7. This is a visual recommendation; no GPU frame time was measured.

## Deviations and gaps

- **Frame time and GPU cost were not measured** (headless software GL). The Intel UHD 620 and M-series budgets in 3.7 are still unverified.
- The backend was not needed: the capture tool stubs its REST calls and the scene uses a synthetic fixture. Screenshots therefore do not show real run data. Without the stub, the page fails to start the scene when the backend is down or CORS-blocks the dev origin (the backend allows only `localhost:4200`); this predates the spike.
- The brief says "InkMaterial on the existing slab boxes". The slab boxes moved to two instanced meshes sharing a `ShaderMaterial` (so the library claims could be verified on real content); the non-instanced `createInkMaterial` exists for later use, but there is no `InkMaterial` class and no `<ngt-ink-material>`.
- Fog moved from `ngt-fog-exp2` into the shader (dithered); sprites and plaques set `fog` off and keep their colours. Per-Generation ramps, the line-art pass, and sprite or plaque ramp mapping are out of scope (#17, #18, #25).
- The current deck and Guardian backdrop no longer glow amber (no emissive without light); the current deck is the highest-tone slab. Amber as a role colour is not implemented.
- `?inkFixture`, `?inkPick` and `?pixelScale` are dev tooling. `pixelScale` is read in production too (it only selects 1, 2 or 3); the fixture, pick probe and `window.__tower` hook are dev-mode only.
