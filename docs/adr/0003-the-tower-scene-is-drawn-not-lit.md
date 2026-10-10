---
status: proposed
---

# The tower scene is drawn, not lit

The scene has no lights. Surfaces are shaded in the material through a four-step ordered dither, and a shared shader module resolves fog into the background colour. Each floor is a flat strip drawn like a section through a much wider structure, and 3D geometry is reserved for the pillars, the shaft and the village. There is no post-processing pass. Full design: `docs/brainstorm/architecture-and-visual-identity.md`, section 3.

We chose this to carry Nihei's architectural register (structure, scale, line over black) with Tower Wizard's limited-palette dither technique, and to make the unknown above the highest floor read as an absence. We rejected a post-processing composer because it would take over the render loop, quantise the sprites too, and would need a selection mask, and `angular-three` ships no post-processing package in this repo.

## Consequences

- The current `MeshStandardMaterial` boxes, the three lights and the blue rim light are replaced.
- Anything the scene shows must read at 2 CSS pixels per virtual pixel, and the performance budget is measured, not assumed.
- Guardian floors and danger must never rely on colour alone.
