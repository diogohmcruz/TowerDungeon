---
status: proposed
---

# Creatures have health per body part

Units and enemies are made of body parts (head, torso, two arms, two legs; fewer for some enemies), each with its own health, and attacks are aimed at one part, a line of parts, or the whole body. A creature keeps its type's total health, divided between its parts by per-type weights; it dies when its head or torso reaches zero, and any other part at zero is lost for good. This replaces the single health pool that `BaseUnit`, `PartyCombat`, Heal and Recovery all use today.

We chose it so that gear, maiming, accuracy and aim all have something to act on, which a single pool cannot give. We rejected keeping the pool and severing a part on a large hit, because it makes losing a part arbitrary and leaves aim meaningless. The head carries little health but is hard to hit, so accuracy and evasion are what keep a head kill from becoming the only sensible tactic.

## Consequences

- A kill now needs only the head or torso at zero, so kill speed rises even though total health is conserved. The enemy budget formula and the gentle first reach must be re-tuned in the scaling pass (#8).
- The campaign file (ADR 0001) and the snapshot must carry each unit's parts, items, sanity and phobias, and the MCP state tools must expose them.
- The Weakness multiplier applies to a whole hit before it is spread over parts.
- **Wounded** in the glossary changes meaning once this is built; **Maimed** is the lasting state.
