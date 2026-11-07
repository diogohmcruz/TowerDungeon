---
status: proposed
---

# Enemy types are Theme-and-Threat cells with debut floors

Each enemy type stays a named manga creature but sits in one cell of a Theme (Beast, Undead, Elemental, Sorcerous, Man) by Threat (Minion, Brute, Strategist, Elite, Lord) matrix, and has a Debut Floor, the lowest floor it can appear on. Floor Difficulty stops meaning "the first N types of a weakest-to-strongest ladder" and means "which types have debuted, thinned by Easing". Theme sets immunities and Hates; Threat sets toughness, Morale and behaviour.

We took the shape from *His Majesty the Worm*'s creature recipe (theme times threat, pp. 242-251) because one ladder conflates what a creature is with how it fights, and cannot hold a weak strategist beside a tough brute on the same floor. We rejected keeping the ladder and adding Threat as a tag, because the ladder would still decide which types may appear and new creatures could only be added at a rank.

## Consequences

- The enemy budget formula, Floor Difficulty and Easing all change meaning and need re-tuning (see #8 and the floor curve research).
- Guardians become Lords with per-body-part pools that carry their own abilities, which builds on ADR 0006.
- The ladder order in `CONTEXT.md` is kept only until the matrix is built.
