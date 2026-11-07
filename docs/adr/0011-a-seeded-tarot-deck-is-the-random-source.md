---
status: proposed
---

# A seeded tarot deck is the random source for draws and halts

Floor draws, Halts, Orders and event tables draw from one seeded tarot Deck (the 21 major arcana for events and dooms, the 56 minor arcana and the Fool for tests), with a Test of Fate (one minor card plus an attribute, 14 or more to succeed, one push allowed) for a few named moments. Fights stay auto-resolved and never use cards. The save stores the shuffle seed and a draw index, so a reload replays the same card.

The book resolves everything with cards (pp. 7-13). Per-tick card play does not fit a one-second auto-fight, because its hidden hands and bluffing vanish with many units. Cards as the single seeded source do fit, and they give floors and events a visible, grim vocabulary: a major arcana card sets an event's severity and its art foreshadows the floor. We rejected plain dice tables because a card has meaning to show, and rejected per-tick cards as unplayable.

## Consequences

- The keyed seed of the roguelite doc (Generation, floor, `expeditionsCompleted`) becomes the Deck's shuffle and draw index; `contentVersion` must be saved with it.
- The Fool reshuffles; a reshuffle is a visible event, not a silent reset.
- Card art and meanings are new content and need the palette and the scene's readability rules (ADR 0003, 0004).
- Success odds come from the actual deck, not a formula: with a push they run from about 65% (attribute 1) to 80% (attribute 4).
