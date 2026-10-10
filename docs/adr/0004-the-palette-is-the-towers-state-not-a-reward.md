---
status: accepted
---

# The scene palette is the Tower's state, not a reward

The tower scene's colour ramp is a function of Tower Memory, so it follows the Generation (Generation 5 and later share the last ramp). It changes only when a new Generation begins, and it cannot be earned, bought, chosen, previewed or reversed. Each ramp is grim and near-neutral, and the ground only gets darker. Full design: `docs/brainstorm/palette-per-generation.md`.

An earlier design rejected a palette per Generation because the village design forbids "a palette swap as a reward". The two are reconciled by making the ramp a consequence of the Tower hardening, not a prize. Tower Wizard swaps its palette on every ascension and the author liked that; here the same mechanism marks a heavier, more remembered Tower, so Generation 3 looks like the Tower's third memory of the player.

## Consequences

- Anything new that adds variety (mutations, a roguelite layer) must not recolour the ramp.
- The Guardian crimson, lamp amber and resource tints are fixed role colours, never ramp slots, and Guardians are never marked by colour alone.
- Distinguishing Generations does not rely on hue: a numeral and marks on the Tower's base carry the count too.
- To keep crimson visible under protanopia, the dark slots carry an indigo tint, so Generation 1's night ground moves from `#0e1424` to `#080923`. This is the largest tone cost and is judged on screen in the ink spike.
