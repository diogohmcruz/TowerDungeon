# Learning from *Runecairn*: items, afflictions and Motifs

Design only. Nothing here is built. Written 2026-11-07 after a grilling session on `docs/runecairn.pdf` (Runecairn core rules v1.7, 36 pages, a solo-friendly OSR game based on Cairn). Page numbers are the book's printed pages. Terms follow `CONTEXT.md`. Decisions are the author's. Tags: **[Book]** taken from the text, **[Adapted]** the book's idea changed to fit, **[Proposal]** invented here, a first guess. The ideas only are used; no text is copied. Related design: `docs/brainstorm/worm-inspiration.md` (the loop, the enemies, the units), whose Items, Afflictions and Motifs this extends.

## 1. What the book is

A short game whose loop is death. You die often, wake at a bonfire, lose some of your will (Vigour), and have one chance to retrieve the souls you dropped. Health is Resilience (Vitality + Vigour). Skills come from the items you carry, not from a class, and magic and skills cost Fatigue, which fills inventory slots until you rest. Its principles: telegraph danger, treasure is valuable but bulky, fiction decides before dice.

## 2. What was taken

| Item | Book | Decision |
|---|---|---|
| Equipment gives skills (pp. 7-9, 17-18: a class is just a key item) | [Adapted] | **Items grant abilities.** An item (leftover, talisman, artifact) carries one ability that any unit can use while it holds the item. Old Hands keep their own pool. Customisation for ordinary units without making them individuals (ADR 0010 holds). |
| Rings with trade-offs (p. 25: +1 defence, -10 speed; reroll 1s but lose armour; +2 slots, -1 Vigour) | [Adapted] | **Any item can carry a trade-off**, not only rings, but rarely: a cursed item is the exception. The manga's Thought-Binding Ring is the first Artifact. |
| Status effects (p. 12: Cursed, Deprived, Poisoned) | [Adapted] | First Afflictions: **Cursed** (health halved until lifted), **Poisoned** (damage each tick that bypasses defence, cleansed by Camp), and a contagion from a hazard. Deprived is already Hungry. |
| Virtue and Vice tables (p. 33) | [Adapted] | **Motif = Virtue + Vice + Profession.** The Virtue is an edge on matching floors, the Vice a risk it triggers. |

## 3. What was declined, and why

| Book mechanic | Why not |
|---|---|
| Vigour (a death costs 1; at 0 a shade; pp. 12-13) for Old Hands | Author declined. Old Hands stay lost on death, with funerals (ADR 0010). |
| Bonfires that link the realms, reset the floor and wake the dead (p. 13) | Author declined. Camp and the Cache stay as designed. |
| Souls dropped where a unit falls, one chance to retrieve (pp. 13-14) | Author declined. Experience is earned at Extraction as designed. |
| An Omen at exactly zero health (p. 21) | Author declined; "Omen" already means a foretold mutation. |
| Fatigue as inventory slots (p. 13), Dire strike, damage advantage | Not proposed; mana, body parts and Mob Rule already do these jobs. |

## 4. Open: how the taken ideas work

### 4.1 Items grant abilities [Decided]

An item's ability uses the format of the ability pools in `worm-inspiration.md` 4.3: a base rule, plus a mastered clause that applies only to an Old Hand. Tier sets strength: a leftover a weak, plain ability; a talisman a defined one; an artifact a named one. A unit holding two items with abilities uses both. Abilities follow the book's equipment skills: an axe grants Hack and Disarm, a bow Volley, a shield Block, a staff Greystone and Barrier.

### 4.2 Item trade-offs [Decided: 3 in 100]

About 3 in 100 talismans and artifacts carry a trade-off, never a leftover. It shows as a Tell (a mark on the item) before the item is equipped, so taking it is a choice. A cursed item is the extreme end. Book examples to adapt: Iron Ring (+1 defence, slower), Red Berserkr Ring (reroll low damage, no armour bonus), Sylva's Ring (more carrying capacity, less health).

### 4.3 More afflictions [Decided]

Each is staged and cured at Camp. The first set is Cursed, Poisoned, a contagion, **larval infection** and **spore trance**; petrification waits for a Reach with basilisks.

| Affliction | Source | Stages |
|---|---|---|
| Larval infection (first set) | Manga: the Necromancer's maggots, Aridellia's larvae | Feverish, weak, then the unit rises as a Risen if it dies while infected |
| Spore trance (first set) | Overuse of Dragon Spores; the Worm's black honey | Good early stage (more damage, Sanity up), then jitters, then hallucination |
| Petrification (parked) | Manga basilisks; Sargan's mask negates it | Slowed, cannot act, stone (lost). Cured by a Purify effect or an oil |


## 5. How it meets what exists

| Existing | Meets |
|---|---|
| Items, tiers, slots | An item gains an ability and rarely a trade-off |
| Old Hands and abilities (ADR 0010) | Unchanged; item abilities add a second route for ordinary units |
| Afflictions (worm-inspiration 2.x, 7a) | Cursed and Poisoned join the first set |
| Motifs | Gain a Virtue and a Vice |
| Phobia, Affinity | Unchanged; Vice is a different thing from Phobia |

## 6. Details not yet used [candidates, not decided]

A second pass over the whole book (every page; pages 7, 13, 17 and 31 are blank filler, the cover is the only art).

### 6.1 Spells as ability seeds [Decided: listed only, no stats yet] (pp. 24-29)

Runestones and sagas hold one spell each, cannot be made and are found in tombs and ruins. Casting adds a Fatigue to inventory. The lists are kept as seeds for the casters; their damage, cost and stats are not set now:

| Type | Seeds from the book |
|---|---|
| Mage (Seer spells, 29) | Fireball (d12 blast), Slow, Haste (double speed, extra action), Poison Cloud, Web, Sleep, Cloak of Knives, Seiðr Volley (d6 blast), Armoured (+2 defence), Fade, Ward (a species cannot cross) |
| Healer (Skald spells, 26) | Heal (restore 10), Replenishment (1 a turn for 6 turns), Purify (purge poison, toxin or petrify), Remove Curse, Respite (restore a lost ability point), Sanctuary (neither side can attack one turn), Protection from Magic, Rebirth (the next lethal save restores full health) |
| Necromancer | Raise Spirit (a corpse answers questions), Command (a three-word order), Charm, Silence |
| Captain | Fortune (next save with advantage), Guidance, Justice (the next attacker takes magic damage) |
| Tank | Shield Wall (a dome of shields), Temper Weapon (weapon to d12) |

**Cures.** Purify, Remove Curse and Respite give the Healer a real job against Afflictions: Purify cures Poisoned (and petrification), Remove Curse cures Cursed, Rebirth is a Death's Door save.

### 6.2 Relics with charges and a recharge condition [Decided] (p. 24)

A relic has a number of uses and a stated recharge: the Eye of Oðinn (1 charge, bathe it in a god's tears), the Skull Beacon (burn it on a bonfire), the Mistletoe Dart (witness a kiss of true love), the Staff of Walking Weald (bury it under the oldest tree for a week). Not Fatigue. **Fit:** an Artifact's ability has charges that recharge on a named condition met in play (at a Camp, at the Shrine, after a Guardian). It keeps Artifacts rare and gives each a small story. (Our "Relic" is the loot currency; call these item charges, not relics.)

### 6.3 Monsters [Decided: a pool of ideas, not built now] (p. 30-31)

| Creature | Book | Theme / Threat | Trait worth taking |
|---|---|---|---|
| Draugr | 8 health, 1 defence; reeking stench | Undead / Minion | Stench: a nearby unit may lose a tick (Hungry-style) |
| Shade | 3 health, hordes, drawn to the living | Undead / Minion | The Risen we already designed |
| Skeleton | Reforms at half health on a d12 roll of 1; giant skeleton on 1-2 | Undead / Minion, Elite | A **reform** chance for Dead Soldiers |
| Skeleton, bonewheel | Rolls 3d6, takes the highest | Undead / Brute | Roll-3-keep-highest attack |
| Troll | Recovers health standing on stone | Beast / Brute | Ties to floor Architecture (stone floors) |
| Rock troll | Red lichen halves speed | Elemental / Minion | A slowing affliction on hit |
| Treant | Weak to fire, but fire draws others | Beast / Brute | A Hate with a cost |
| Warg | A dire strike takes an arm | Beast / Elite | **Arm loss** through the body-part rules (ADR 0006) |
| Wolf | A howl may call another (d6 of 6) | Beast / Minion | Enemies summoning a reinforcement wave |
| Lindworm | A dire strike swallows the target | Beast / Elite | Swallow: a unit is out of the fight until it escapes |
| Dwarf, Elf | Resistant to magic (and poison) | Man / Brute, Strategist | A magic-resistant Man type |
| Jotunn (fire, forest) | A dire strike ignites; a grab blocks movement and reactions | Elemental / Elite | Ignite affliction; a grab that holds a unit |

### 6.4 Principles for placing danger (pp. 2-5)

- Telegraph serious danger, and the more dangerous the more obvious (this is the Tell on the floor before a Debut).
- Put traps in plain sight and let the player work out a solution (floor hazards that can be read, not rolled).
- A Treasure is specific to where it came from and tells a story; it is valuable, almost always bulky and rarely useful (the Relic and Reliquary economy).
- Defeat teaches: "with every defeat comes new knowledge" (Tower Memory and the Atlas).
- Binary "A or B?" choices when intent is vague (the Halt's options).

### 6.5 Smaller mechanics [Decided: skipped]

- **Dire strike:** a maximum damage roll triggers an extra effect (a stagger, a swallow, an arm). Needs a damage roll; today fights use fixed damage, so only if damage gains a range.
- **Multiple attackers take the highest die** and **dual or two-handed weapons gain advantage**: both are mob rules in reverse; our Mob Rule covers the garrison.
- **Wealth:** gold has little value and trade is barter; our Credits could be spent partly in goods.
- **Mead:** a flask with two sips that heal 10 and clear 2 Fatigue, refilled at a bonfire. A candidate for a Camp-refilled personal flask; not proposed.

### 6.6 Decisions (round 3)

1. The Seer and Skald spell lists are ability seeds for Mage, Healer, Necromancer, Captain and Tank. **Listed only**: no stats or numbers are set now. Purify, Remove Curse and Respite are the intended cures for Afflictions.
2. Artifact abilities have charges that recharge on a named condition met in play.
3. The monster traits (reform, arm loss, stench, howl, swallow, stone-heal) are a pool of enemy ideas, not built now.

### 6.7 Decisions (round 4)

- **Monster traits:** posted as a pool on issue #156, one trait per type when it builds its types (suggested: reform on Dead Soldiers, arm loss on the Tower Beast).
- **Placement principles:** posted as design rules on #153 and #156.
- **Skipped:** dire strike (needs damage ranges), the Mead flask (overlaps Spore Doses, the Panacea and Camp) and barter-style wealth (Credits, Materials and Relics already compete).
