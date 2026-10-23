# A roguelite layer for Tower Dungeon (future)

Status: design only, and a future improvement. No repository file was written or edited. Every number is a starting knob, not a measurement: `backend/src/test` does not exist, and nobody has timed a Floor.

**Decisions recorded (2026-10-10):** a Halt stops both clocks, the Party's and the Village's, matching the Held Party rule; the Cache gives back 25% of a Wipe's lost Loot as Loot in hand, provisionally (the roadmap's Wipe-losses ruling, row 8, settled that a Wipe costs Risen only); nothing is scheduled before Prestige v2 has been played.

**Summary.** Prestige v1 and v2 already give Tower Dungeon the long loop of a roguelite: Generations, a finite Roll, Mutations, Omens and a Pact. What it lacks is texture inside a Campaign. Every Expedition is the same question ("how high before the Party dies?"), asked 15 to 17 times. This layer answers that with small, seeded, priced choices the player makes when the Party stops, plus a few records that make a Campaign's history matter. It is built on one primitive, the **Halt**: the Party's clock stops, one plain slab lists options with their Prices written beforehand, and the player answers. Nothing in it is timed and nothing is a gift. It has no counter of its own in the Toll; the few Loot-paying and Loot-taking cases still move the Toll's counters through the normal Extraction path, and section 2.4 bounds how far.

The recommended core is eight items: the Halt with its first case (Low Supplies), Orders after a Guardian, Finds with Tells, Lessons for survivors, What the Risen Carry, a widened Atlas, the Guardian Record and Heir Candidates. 40 raw ideas were grouped into these. Section 8.3 lists what was adopted, parked and rejected.

**Where this stands against the author's latest answers.** The author accepted the recommendations in the Package B, Prestige and architecture designs, wants a palette per Generation (it stays grim; roadmap row 3, `palette-per-generation.md` and ADR 0004), liked Variation (v2), and called a roguelite layer a nice future improvement. So this layer is **not scheduled**. It waits until Prestige v1 and v2 have been played and E0 has fitted the numbers (section 7). Only three seams are built earlier, and only because Persistence, Prestige and the Mutations need them anyway: one stop predicate on the game clocks, a keyed seeded random source, and a clamped rules object. The layer does not depend on the palette decision. Everything new here uses panel tokens and carries a word with every state, so any grim Generation palette applies unchanged.

**Source tags.** [Manga §n] is a section of `docs/research/tower-dungeon-since-vol6.md`, with its own confidence label where it matters (official, review, wiki, fan). [TW §n] is `docs/research/tower-wizard.md`. [Code] is something read in `backend/src/main/java/io/github/diogohmcruz/towerdungeon` for this document. [Invented] is original to Tower Dungeon. The manga is inspiration, not canon. Terms follow `CONTEXT.md`. Terms already decided but not in it yet (Held Party, Risen, Atlas, Survey, Village Tier, Tally, Ledger, Trust, Stockroom, Knowledge, Loadout, Mutation, Omen, Pact) are listed in `docs/brainstorm/roadmap.md`, `docs/brainstorm/prestige-and-persistence.md` (P&P) and `docs/brainstorm/village-resources.md` (Package B). `docs/brainstorm/architecture-and-visual-identity.md` is cited as Architecture.

---

## 1. What "roguelite" means here, and what it must not become

### 1.1 Meaning

Tower Dungeon is already a roguelite at the Prestige level: a lineage of Generations, a finite Roll, Tower Memory. That is the meta-progression. What the genre also expects, and this game does not yet have, is **variety inside a single attempt**. The layer takes four devices from the genre and nothing else:

| Device (genre source) | Here |
|---|---|
| Drafts and event nodes (Slay the Spire, FTL) | Orders after a Guardian; Finds on cleared Floors |
| Mortal Units with history (Darkest Dungeon quirks, XCOM promotions) | Lessons, taken by survivors, lost with the Unit |
| A death marker you can go back to (the bloodstain) | What the Risen Carry |
| A codex that fills with what you have met (Hades, Outer Wilds) | The Atlas, widened |

Prestige v2 answers sameness across Generations. This layer answers sameness across Expeditions. That is the whole division of labour (section 2).

### 1.2 Guardrails

These are rules, and each has an enforcement that is a test or a design constraint, not a promise.

| # | Guardrail | Enforced by |
|---|---|---|
| G1 | **Every Gain has a written Price, shown before the click.** The status quo ("Leave", "Take none", "Press on") is always listed and costs nothing further. A Find never takes anything from the Party unless the Party chooses to stop for it. | Content lint at boot and in a unit test: an option with no Price fails. A test fails any Find whose "Leave" option has a non-zero effect. |
| G2 | **Nothing is timed and nothing is a reflex.** No countdown, no live window, no quick-time moment. Time is a Price inside a stopped clock. | Section 4. No `ticksLeft` on a Decision. |
| G3 | **Nothing congratulates.** Ledger and slab lines are at most 12 words, no "!", no icon, no sound, no animation. A loss is held longer on screen than a gain. | A copy-lint test over a **curated** word list kept beside `CONTEXT.md` (not a naive parse of its `_Avoid_` lines: Village's `_Avoid_` list contains "home", and the canonical Extraction line is "Came home: 6 of 6"). The list carries phrase-level entries and an allow-list. It also fails on "!" and on any line over 12 words. |
| G4 | **Finite.** Fixed pools, hard caps (one Find per Reach, 3 Lessons per Unit, 12 Old Hands, one Cache per Floor). No streak, no repeatable sink, no second tree beside the Roll. | Config caps plus a test per cap. |
| G5 | **No new punisher on the Village.** The Package B guard stands: no consequence may reduce Food income, add a Food rate, or stack on Starvation. No Find removes a Villager. No Lesson Price touches Upkeep or Recovery cost. | The effect set is closed (4.5) and has no Villager-removing and no Food-rate effect. |
| G6 | **Chance is seeded, derived from stored keys and never in combat.** An offer is a pure function of the sealed seed and a key, so reload and import cannot reroll it. Combat stays on `ThreadLocalRandom`. First contact may hide an outcome; it never hides a Price. | Determinism and stream-independence tests (4.6). |
| G7 | **It is never counted toward the Toll, and it is neutral in two currencies.** (a) The expected net Loot of all Finds in a Reach, in Credits at sale price, stays within +/-3% of that Reach's Floor Loot. (b) The expected net Gain minus Price of any single option, in Party seconds (1 s = Party size x 1.5 Supplies and 1 hp per Unit), stays within +/-5 s per Reach for any Party size. | Two unit tests over the content data. (b) closes the hole in (a): Supplies sell for 1 Credit but are worth a Party's life on the Tower. |
| G8 | **The Tower stays indifferent.** It never "learns", "notices" or "wants". It keeps a record. | Copy lint: "the Tower learned" fails; the Guardian Record is a Ledger line. |
| G9 | **Not canon.** Anything claimed as manga fact is tagged and comes from `docs/research`. Ruins-Dwellers are not presented as traders. Nothing about ch.24-31 is asserted beyond [official] items. | Tag column in section 5; review before copy ships. |
| G10 | **Foresight is rationed.** Survey (P&P), Survey Maps (Package B R17), "Read ahead" (3.8), a Learn Order and Dorias all buy knowledge of what is coming. At most one of them is available per Reach. Otherwise the unknown stops being unknown. | A per-Reach flag in the Decision engine and a test. |

### 1.3 What it must not become

- **A card game or a loot cascade.** No card art, no rarity, no "pick a reward" fanfare. [TW §5b, Package B §8]: no sparkle, no count-up, no jolly names.
- **A task list.** A list of objectives with icons and streaks reads as achievements, which is the cheer the tone rules out (Charges, section 8.3).
- **Random for its own sake.** [Package B D10] rejected random falls and weapon wear. Every fatal outcome here is readable beforehand (The Crossing's fall is a sum of weights, not a die).
- **Real-time twitch.** Six Units drain 9 Supplies and 6 hp a second (4.1). A reflex window is a Wipe.
- **A pay-to-skip.** Nothing skips a Guardian, Easing, a Shortcut rule or Floors for Credits.
- **A heat ladder, a daily seed or a leaderboard.** They need accounts; the Campaign Key is anonymous (ADR 0001).
- **Endless.** The Roll is finite on purpose [TW §4: "The Dignity of a Finite Tower"]; nothing here adds content above the Summit.

---

## 2. How it relates to Prestige v2 (Mutations, Omens, Pact) and Package B

### 2.1 Division of labour

| | **Campaign-level (Prestige)** | **Expedition-level (this layer)** |
|---|---|---|
| Question | What Tower will this Generation go up, and what does the Village bring? | What will this Party pay for, now, on this Floor? |
| When | Succession (phase OFFER), sealed at Begin Generation | At a Halt inside an Expedition, or at Extraction |
| Who chooses | The Tower imposes Mutations; the player picks Omens, the Pact, Inheritances, Dowry, Heir | The player picks from a written list of at most three; the seed picked the list |
| What changes | The Tower's rules (Mutations, Tower Memory) and the Village's starting terms (Roll) | The Party's terms for one Reach or one Expedition; one Unit's record; one Floor's memory |
| Visibility | Hidden until met (Omens, Mutations) | Gains and Prices always visible; only a first-contact outcome may be hidden |
| Currency | Legacy, Load, Toll Marks | Supplies, Loot in hand, hp, Unit names, later Salt and Spores. Never Legacy. |
| Scoring | Load multiplies the Toll (+5% per point, cap 8) | None. No Load, no Toll counter of its own (2.4 lists what moves anyway). |
| Survives Succession | Roll, Epitaphs, Atlas, Chronicle, Lifetime Ledger, Residue | Atlas pages and Chronicle names only. Everything else ends. |
| Guards against | Samey Generations [TW §4] | Samey Expeditions |

### 2.2 Border rules

1. **Authority runs one way.** Prestige sets the Tower. The layer only offers the Party terms inside that Tower. No Order, Find or Lesson changes a Garrison's budget, a Guardian, Easing, a Shortcut, a Floor's Architecture or an Architecture lever. (A Find that adds a band to a Garrison, such as the Deserters, adds it as a Find rule with its own fixed size; it never edits the Floor's budget.)
2. **No Load, no Toll.** Loot a Find pays goes into Loot in hand and banks through the normal path at Extraction, so the Toll's counters stay correct. The Pact's Load pays only for Mutations met (P&P 3.4) and is untouched. Do not pay Load for meeting Finds.
3. **Where a Mutation meets a Halt, the Mutation wins.** In the first increments a Floor inside a Mutation's band raises no Find at all (one rule instead of eight). Orders are unaffected.
4. **One rules object, independent families, no silent clamp on someone else's numbers.** Roll Inheritances (Overreach, Heavier Packs), Architecture levers, Mutations (Long Stair, Spore Blight), Orders and Lessons all feed a single `ExpeditionRules` object, the same hook P&P R7 needs for `TowerModifiers`. Each source family keeps its own cap: Architecture's (Attrition combined at most x1.5, Supplies drain 0.85 to 1.2, Architecture 4.4), and Prestige's as designed. **This layer's family** (Orders and Lessons together) is clamped on its own in config (3.12). `ExpeditionRules` multiplies the families. A global ceiling exists only to catch bugs (Attrition x2.5, drain x3, damage taken x3) and is logged when it binds. It must not act as a rule: a single clamp over the product would silently cut Overreach (drain x1.3) times Long Stair (x1.5) from x1.95 to the layer's cap and change Prestige.
5. **One Atlas.** The Atlas that P&P designs for Mutations grows pages (Finds, Orders, Lessons). It does not get a rival record, and the Succession screen gains rows, not a screen. Namespace the keys from the first v2 build (`mutation:LONG_STAIR`), so widening the Atlas is not a migration.
6. **Succession ends everything Campaign-scoped.** Lessons die with the Units (only the Heir crosses), held Orders lapse, Finds' Once flags and Caches vanish with the Tower (P&P 3.3: the Tower is rebuilt).
7. **No new Inheritance.** The Roll stays 17 Inheritances for 64 Legacy. Pool entries unlock from the Lifetime Ledger and first contact, never from Legacy (6.4).
8. **One per-Unit progression earned in play.** The Lesson. The Heir (Roll) and Names on the Wall (Epitaph) stay as designed, and the Roll never raises a Lesson. Package C's Grievous (D5) uses the same 25% line but a different measure (hp at Extraction, against the lowest hp reached). Keep both thresholds in config so a near-death survivor is not both penalised and rewarded by one number by accident.
9. **The look differs.** Omens are hidden and named only when met. Orders are fully written and never hidden. Do not give Orders the Omen look.
10. **Reinforcement Waves are outside Orders and Lessons.** `advanceReinforcements` drains Supplies and starves Waves from config on its own. An Order affects the Party and any Wave from its Merge onward. A Wave in transit is unaffected, and the slab says so.
11. **Package B owns Salt and the Salt Pack.** The Salt Pack (Loadout, Carrying Capacity 10, a Salt-bearer who draws the Chymus Insect) is the Chymus counter. No Order may duplicate it. A Party-wide +50% for 2 Salt, taken after the fact with no bearer and no capacity, would make the Pack and the targeting hook pointless, so that sample Order was dropped (5.3).

### 2.3 Mutation by Mutation

| Mutation (P&P 3.8) | Interaction |
|---|---|
| Long Stair (drain x1.5 in a band) | A separate family. Forced March (drain x0.8) multiplies with it to x1.2. |
| Veil (Garrison hidden) | No Find on a Veil Floor. Veil never hides The Gaze's figure count; it suppresses itself there. A Learn Order that reveals a Guardian's hp is worth most under Veil. |
| Hollowing | No interaction. Guardian-named Orders count "slain" as the Atlas does. |
| Severed Stair | No interaction. |
| Looping Rooms | No Find on a looped Floor. The pay-once guard (4.4) already handles Loot paid on the second clear. |
| Spore Blight | Spore contents are void in the band ("Nothing grows here"). |
| Unburied (Guardian rises once) | The Orders Halt opens after the second death, with Easing and Loot. |
| Chymus Spread (Chymus Insect from Floor 23) | Handled by the Salt Pack, not an Order. |

Tower Memory is not a Mutation. A Deserters band is a fixed-size Find rule, so Memory's budget growth does not scale it. The Guardian Record and Tower Memory both act on Guardians but on different things (damage by Attack Type; health); their stacking is bounded in 3.9.

### 2.4 Impact on the Toll and on E0

The Toll's par values are fitted to today's loot formulas (P&P 3.4), and E0 will size Party strength against the Guardians (P&P 3.11: N is about `sqrt(HP / (1.25 d))`). The question is what each item can move.

| Item | Counter it can move | Bound | Verdict |
|---|---|---|---|
| Low Supplies, Burn Materials | Materials banked (down), Ascent (up) | -140 Materials of par 17,000 per use, about -0.04 Marks; +12 s of life, about 2 Floors, up to about +1 Ascent Mark, and only while the Party is at Highest Floor | Small. A real trade of Credits for height, not a Toll exploit. |
| Finds, Loot | Materials, Credits, Relics banked | Within +/-3% of a Reach's Floor Loot (G7a); square roots halve it | Safe. |
| Deserters' Share | Credits and Materials banked (down) | At most 25% of Loot in hand on each pass of Floor 25: at most about -13% of those two counters' Marks, about -2 Marks (about -0.2 Legacy) per Campaign at P2-scale numbers | Safe. Intended. |
| Cache | Materials, Credits banked (up) | 25% of what a Wipe lost; about +0.05 Marks per Wipe; always a net loss for the player | Safe. |
| Orders and Lessons | None directly. They move Party power, so they move Ascent (about 41% of `S` by Generation 4) and G | The layer's family clamp (3.12): damage at most x1.25, damage taken at least x0.85, drain 0.75 to 1.25, Attrition at most x1.25 | **Not neutral.** A x1.25 damage cap lowers the N a Guardian needs by about 10%. E0 must be fitted with the layer off, then refitted (P&P R13) with the layer at full take-up. Never ship it before that refit. |
| Guardian Record | G and Ascent for a one-note Roster | At step 4 the dominant Attack Type does x0.80, so Guardian health is effectively x1.25 for that Roster. With Tower Memory's x1.32 that is about x1.65, which P&P's 3.10 tables do not model, and about x1.28 more Units by the sizing rule | **Not neutral.** Cap the effect on Guardian Floors above 70 until E0 (as P&P does for Memory), and keep the combined Memory-and-Record effect to a config ceiling (3.12). |
| Upper-Tower trivialisers | None | Eliquo's Arrowhead is one fixed hp number, once per Campaign, for the Floor 70 Guardian only, never the Summit, refitted after E0. No percent-of-max-health effect is allowed anywhere in the effect set (4.5). | Safe. |

---

## 3. The recommended core

### 3.1 Config anchors

| Anchor | Value | Source |
|---|---|---|
| Lifecycle tick / Attrition tick | 1,000 ms / 10 ms. Attrition is `interval / 1000`, so 1 hp per second per Unit, hard-coded in `GameState.passingTime` | [Code] |
| Supplies drain | 1.5 per Unit per second. Read in two places: `updateInvasion` and `advanceReinforcements` | [Code] |
| Starvation | 5 hp per second per Unit once Supplies are empty. It is applied in the same tick the drain empties them | `starvation-damage`, [Code] |
| Supplies at set-out | 100 base + 60 per Porter + 25 per Supply Lines level + 25 Relic Lore, limited by what the Pantry holds | [Code] |
| Loot per cleared Floor f | 2f Credits, 5f Materials, +3 Supplies, no Relics (they come from surplus items); all x3 on a Guardian Floor | `reward.*` |
| Guardian Floor / Surge Floor | every 10 Floors (and the Summit) / every 5 Floors, x1.5; Floors 10, 20... are Guardian Floors, so Surge Floors in practice are 5, 15, 25...95 | `boss.interval`, `surge-interval` |
| Garrison budget | 3 + 0.8f + 0.04f^2, +/-25%. Floor 9 about 13.4; Floor 25 is 48 x 1.5 = 72 | `enemies.*` |
| Guardian health | base x (3.0 + 0.05 x Floor): Floor 10 52.5, Floor 20 160, Floor 40 500, Floor 50 1,650 | P&P 3.11 |
| Sell prices | Food 1, Materials 2, Relics 25 Credits | `reward.*`, `Village.sellFood` |
| Unit hp | Archer, Mage, Rogue 10; Warrior and Necromancer 20; Porter 25; Healer, Captain 30; Tank 50; Draco-Metamorph 100. A Warrior costs 3 Credits | `UnitStats` |
| Mana | max 5, 1 per Cast, Tower Recharge 0.05 per tick, home 0.25 | `magic.*` |
| Pace | **Seconds per Floor are unmeasured.** The docs assume 4 to 6. By construction a Floor costs at least 2 s: one tick kills the Garrison, the next tick pays the Floor and moves on, and combat on the new Floor starts the tick after. Six Units burn 100 Supplies in 11 seconds, and Archers, Mages and Rogues die to Attrition alone in 10. | P&P 3.10, [Code] `updateInvasion` |

Consequence for every price below: **a second is the unit of cost.** Six Units pay 9 Supplies and 1 hp each for each second a Dwell lasts. Anything priced per Floor or per Reach assumes a Floor takes 5 seconds and is written "if a Floor takes 5 s".

The Party drains Supplies as the sum over its Units, so any price written as a flat number of Supplies is free at 50 Units. Prices that stand for time are written in seconds of Party drain.

Honest value of a Unit: a Warrior costs 3 Credits, and a Floor-10 Expedition banks about 900 Credits (375 Materials at 2, plus 150 Credits). A death costs almost nothing. That is why the Units need history (Core 4).

### 3.2 Conflicts between the 40 ideas, and the ruling

| Conflict | Ruling |
|---|---|
| Timing: a 25 s Halt with the Village clock running, an 8 s live window, a 30 s wall-clock window, and a clock-stopped Low Supplies case | One model: the Party's clock stops, no deadline, no live windows (section 4). The Village clock stops too by default (open decision 2). |
| Placement: Kind pinned to a Floor per Generation (Signs) vs drawn by counter, "never Floor 7 again" (Stair Events) | Hybrid. The **Kind** is pinned to a Floor per Generation, so the Atlas can learn it. The **Face** (stakes, variant) is derived from the seed and (Floor, `expeditionsCompleted`), then stored. Finds are Once by default, so decisions concentrate at the frontier. |
| Two drafts on a slain Guardian (Orders and Spoils) | Orders only. Spoils' Mask, arrowhead and pendant become Guardian-named Orders. |
| Four per-Unit trait systems (Lessons, Marks, Old Hands, Imprints) | One: the Lesson. "Marks" is dropped (the Toll already has Marks). |
| Two codices (Atlas and Folio) | One Atlas with pages. |
| Three Wipe markers (Risen Carry, Graves, Known Dead) | Build the per-Floor record once (Core 5). Graves and Known Dead reuse it later. |
| Rival bands: Package B D6's flat levy on Extraction, the "Toll Band", Tribute | One: Deserters at Floor 25 as a Find. D6 is not built. D6 also uses the word "Toll" for its levy, which now collides with Prestige's Toll, so rename D6's term if D6 is ever revived. |
| Heir: Roll Heir, Named Heir, Candidates | Candidates extend the Roll's Heir. A Unit does not cross Generations. |
| Two Gatekeepers (an in-Expedition pursuer and a ladder marker) | The ladder marker, later (The Standing Figure). |
| Survivors: Package B R14, "Callers", Strays | None. R14 already delivers Survivors from the Floor above without a choice, so a Caller case is dropped. Strays rejected. |

### 3.3 Core 1. The Halt and Low Supplies (increment H1)

**Role.** The primitive every other item reuses, shipped alone first, with no randomness.

**Low Supplies.** At the start of a tick, before `drainSupplies`, when Supplies are at or below one tick of drain (Party size x 1.5; 9 for six Units), once per Expedition, both clocks stop and a flat slab appears: "The Party has stopped." The condition is "at or below", and the check runs before the drain, because the drain that empties Supplies also applies Starvation in the same tick. Three options, each with its Price:

- **Extract now.** Loot is banked, "Came home: 6 of 6."
- **Burn Materials.** Materials in hand become Supplies at 0.75 each, once per Expedition, clamped to Carrying Capacity. The option is absent when no Materials are in hand. A Party on Floor 8 has cleared Floors 1 to 7 and carries 140 Materials: 105 Supplies, about 12 seconds for six Units. At the sale price that is 280 Credits for 12 seconds of life. The rate stays below the Materials' value as Food (0.75 Credits against 2), so Burn can never be an exchange. (The slab says "Materials" because Loot also holds Credits and Relics, which Burn never touches.)
- **Press on.** Starvation 5 plus Attrition 1 is 6 hp a second: Archers, Mages and Rogues last 2 seconds, Warriors 4, Tanks 9.

**Why this first.** It decides nothing by chance, rests only on existing rules (Supplies, Starvation, Attrition, Extraction), and forces the work every later item needs: the stop predicate, the Decision record, the `DECIDE` action, the slab, the copy lint and the tests. Size S for the case, M for the first build of the foundation (section 7).

### 3.4 Core 2. Orders after a Guardian (H2)

**What.** When a Guardian is slain (the branch that calls `tower.markBossCleared`), the Party Halts after the Loot is paid and before it goes up. Three Orders are laid out: one **Spend** (pay Supplies or Loot in hand now), one **Press** (accept a harsher rule for the Reach ahead), one **Learn** (see something of the Reach ahead), so each Halt asks a different question. "Take none" is listed. An Order lasts until the next Guardian is slain. One Order is held at a time in H2; a second slot and Long Orders (two Reaches) are a later knob.

**Pool.** 9 Orders at the start, plus one named for each distinct Guardian Floor slain for the first time in the lifetime (at most 10), so the pool reaches 19. P&P's New Ground is a count (`G - G_best`) computed at Succession, not a per-Floor event, so this layer keeps its own set of Guardian Floors slain in the Lifetime Ledger (a small addition to P&P's `LifetimeLedger`) and opens the Order at the next Halt after the first slaying.

**Numbers.** About 30 to 40 Order Halts per Campaign (1 to 3 per Expedition), unmeasured. Offers are derived from the seed and the key (Generation, Floor, `expeditionsCompleted`) and stored. Reload and import replay the same offer; the next Expedition draws a new one, including after a Wipe, because `expeditionsCompleted` advances. Six samples are in 5.3. Two checks go in the telemetry log from day one: no Order may be taken in more than 70% of Halts for one Party composition, and at least one of the three offered must be a poor pick for the Party that sees it (this needs a pure `fit(order, party)` function that scores Gain minus Price in Party seconds; define it with the content).

**Limits.** An Order may change the layer-family scalars inside the family clamp. It may not change a Garrison, a Guardian, Easing, a Shortcut, the Loot formulas, or spend a Unit. A Learn Order must reveal something the player cannot already compute: a Guardian's Enemy Type follows from Floor number, Floor Difficulty and Easing in config, so only a Veil Floor or a stored Find Face gives a Learn real value. The Atlas teaches the rest.

**Needs.** The pay-once guard (4.4), `ExpeditionRules` (damage, damage taken, supply drain, Attrition, Starvation damage, capacity), a config key for Attrition (today hard-coded), the keyed random source (4.6), and characterisation tests for `unitsAttack`, `enemiesAttack` and `updateInvasion` (all private static with no tests today). Size M.

### 3.5 Core 3. Finds with Tells (H3)

**What.** A **Find** is a fixture on an ordinary cleared Floor: baggage, a barred way, a band. Its **Kind** shows as a shaped glyph on the Floor strip once the Floor is discovered (the same rule-glyph system as the Architecture catalogue), never by colour alone. On arrival at a cleared Floor the Party Halts and the slab offers up to three options. Nothing else on the Floor changes. The strip is already crowded (Architecture glyphs, seal ring, Works, Risen notches, Mutation signs), so review the 128 x 50 cell with every glyph present.

**Placement.** Floor 4 or higher, not a Guardian Floor, not a Surge Floor, at most one per Reach, at least 3 Floors apart, never on a Floor inside a Mutation's band. That is at most 10 placed per Generation (one per Reach, Reach 10 included). The Deserters are pinned to Floor 25, which is a Surge Floor: a pinned Find is exempt from the Surge rule and takes Reach 3's single slot. In Generation 1 the placement is a shipped constant, like the Architecture plan ("the Tower the player knows"), and the Faces come from the player's sealed seed. From Generation 2 the Kind placement is derived from the sealed seed. The Face is derived from (seed, Floor, `expeditionsCompleted`) and stored when presented.

**Classes.** Most Kinds are **Once**: once resolved, the Find is gone for the Campaign. This keeps decisions at the frontier and makes first contact the dread. A few are **Returning** because a standing relationship is the point (the Deserters). A Find the player Extracts away from is not Once-resolved, and meets the same Face next time.

**Tells.** A Find shows up to three **Tells**, one clause each, each supplied by a different Unit Type present in the Party (a Rogue reads the lock, a Healer the breath, a Captain the boots). A Tell is always true and always partial. The Atlas names what a Tell implies only after the Kind has been met once. This is what lets Party composition matter in a decision: a player builds the Party that can read the Tower, not only the one that survives it. Content debt: every Face needs at least three Tells keyed by Unit Type.

**Gambles print their worst case and never their odds.** "The owner may stand." A Price is never hidden; only a first-contact outcome may be.

**Neutrality.** Two tests (G7). Reach 1's Loot is 900 Credits (750 for Materials, 150 Credits), so the Loot ceiling is about 27. Reach 2's is 2,340 (Floors 11 to 20: 975 Materials, 390 Credits). Finds pay mainly in goods the Toll does not count (Supplies, later Salt and Spores). The Toll's square roots halve any drift: +3% on a counter is +1.5% of its Marks.

**Launch Kinds (4).** The Fallen's Baggage, The Missing Baggage, Deserters on the Stair, The Barred Room. Second wave: The Crossing, The Sleeper, The Gaze (needs Architecture and E0), Dorias (needs the Ring), The Standing Figure (needs a glossary ruling, see Case 11).

**Deserters (Floor 25, Returning).** This replaces Package B D6's flat levy. A rival band stands on the stair on arrival, before combat. Pay a Share of Credits and Materials in hand (never Relics, Supplies or Units): 10% the first meeting, +5 points per payment, cap 25%. Fight: the Deserters join the Floor's Garrison, sized as a fixed 25% of the Floor's base budget (18 of 72 at Floor 25), as Captain-grade bodies (30 hp, 3 damage each, weight 3, so six of them: 180 hp and 18 damage per tick). A win ends the band for the Campaign. Parley, with a Captain in the Party: the Share is halved. Never on a Wipe, never on a Summit Conquered.

Two problems apply. First, **the Deserters cannot be a new `EnemyStats` constant.** `TowerFloor.populateBoss` picks a Guardian by `values()[min(difficulty, length - 1)]` and `Tower.buildFloor` derives Floor Difficulty from `EnemyStats.values().length`, so inserting an entry shifts every Guardian archetype. Use a spawn template outside the enum. (This also means the Glossary's Enemy Type list is unchanged.) Second, **Fight may always be right.** Floor 25's Garrison is about 54 bodies (Floor Difficulty 3: Slime, Dead Soldiers, Tower Beast) holding about 1,100 hp and about 340 damage a tick, so 180 hp and 18 damage is about +16% hp and +5% damage. A Share of 10% is worth about 432 Credits to a Party that went up from the Base holding 720 Credits and 1,800 Materials (Floors 1 to 24 pay 600 Credits and 1,500 Materials, plus the x3 on Floors 10 and 20). That Party is not reachable on today's Supplies (one Expedition from the Base is about 11 s of Supplies for six Units). A Party that started at the Deep Anchor Shortcut (Floor 15) holds about 470 Credits and 1,175 Materials, so 10% is worth about 282. The Share follows Loot in hand, not a fixed sum. Treat "Fight chosen in more than 80% of meetings" as a telemetry failure, and tune by raising the band's budget share or lowering the Share. Floor 25 is also a Surge Floor (budget 72 before variance).

**Needs.** The Halt framework; content as data (4.5); a combat-free "pay time" step (4.5); a `takeShare` that touches only Credits and Materials in hand; a spawn template for Deserters; a per-Floor Once and Returning record on `Tower`. Size M for the engine and four Kinds.

### 3.6 Core 4. Lessons (H5)

**What.** A **Lesson** is a permanent Gain with a Price, taken by a Unit that came close to dying, kept until the Unit dies or Succession. A Unit that holds one is an **Old Hand**.

**Trigger.** At Extraction, a survivor is eligible if its lowest hp fraction in the Expedition was 25% or less and the Expedition's Highest Floor is at least 5 Floors beyond that Unit's own record. The Tower picks the eligible Unit that came closest to dying, not the player. One offer per Extraction. The offer is two Lessons and "Decline", derived from the seed and the key (Unit id, `expeditionsCompleted`) and stored. The Floor-record gate stops a 10-hp Archer farming near-death on shallow Floors, where Attrition alone leaves it under 25% after 8 seconds.

**The offer does not block.** It spends nothing and stops no clock. It waits on the panel and lapses at the next Set Out, which counts as Decline; the panel says so. (This is the one case where a Decision is non-blocking, see 4.5.)

**Caps.** 3 Lessons per Unit, 12 Old Hands at once. Every other Unit stays a fungible hand. The family clamp applies per Unit and per scalar.

**Size of effect.** One Lesson is +/-15 to 25% on one scalar of one Unit. In a Party of six that is about 3% of Party strength (20% of one Unit in six), and about 0.4% at 50 Units. Meaningful to the Unit, never decisive.

**Every Lesson has a Price, and none touches the Village's Food.** None is a pure reward and none is an injury (a lost Body Part is its own system, ADR 0006). Six samples are in 5.3.

**Needs (this is the large part, and the reason for size L).**
1. Stable Unit identity. `BaseUnit.id` is a random `Integer` from `ThreadLocalRandom` (collisions are possible and it is not stable across a restore), and `Unit` equality includes mutable hp and mana through Lombok `@Data`, so `removeUnit` relies on `List.remove(equals)`. Add a persisted `UnitRecord` and compare by id.
2. Named selection, for the Party and for Waves. `InvadeActionDTO` and `ReinforceActionDTO` carry `Map<UnitStats,Integer>`, `GameState.enlistRandom` picks at random, and `sendReinforcement` takes the first N of each type, so a chosen Old Hand cannot be sent today. The Loadout needs unit-id lists.
3. A per-Unit rules stack. Damage, Attrition, heal potency, mana and Recharge are one-line per-Unit hooks. Supplies drain (`drainSupplies(partySize x 1.5)`), Recovery and carry touch Party-wide sums. Max hp touches `stats.getHealth()` in two places (`getHomePartyWounds` and `RoundRobinFoodDistributionStrategy`), which silently mis-report once a Lesson changes max hp.
4. A per-Unit Expedition log (lowest hp fraction, deepest Floor stood on).
5. An Old Hand in the Reserves can still die to Village Starvation, which removes a random Reserve (`GameState.triggerLifecycle`). Keep that random, and write the Lesson lost on the Ledger line with the name.

**Why not Marks, Old Hands or Imprints as separate systems.** They are the same device (a trait draft on survivors) with different triggers. One trigger, one cap, one pool. Imprints (event-triggered traits) would be a second stat-inflation source that the scene cannot show at 11 to 12 px figures.

### 3.7 Core 5. What the Risen Carry (H6)

**What.** A Wipe still loses all Loot in hand, but the Tower is seen to keep it. D1 raises Risen on the Wipe Floor (one third of the Units that fell there, rounded up, capped at min(3, 30% of the Floor's eased base budget)). The Loot the Party held is divided among them. Laying them to rest, which clearing the Floor does anyway, gives **25%** of it to the Party that clears the Floor, as Loot in hand (never straight into the Vault: only Extraction banks, so a Wipe loses it again). The Tower keeps the other 75%. The claim lapses after 3 Expeditions (the Risen remain, as D1 says). One **Cache** per Floor, at most 3 tracked.

**Example.** Six Units Wipe on Floor 9 having cleared Floors 1 to 8: 180 Materials and 72 Credits lost, 432 Credits at sale price. Floor 9's budget is 13.44, so the cap is min(3, 4) = 3, and ceil(6/3) = 2 Risen. Clearing the Floor puts 45 Materials and 18 Credits into the next Party's Loot in hand (108 Credits). The player is still 324 Credits down. Ledger: "They stay on the Floor. They carry 180 Materials." then "Floor 9. Two laid to rest. 45 Materials, 18 Credits taken up." (Not "recovered": Recovery is the Reserves' term.)

**Why 25% and not the 40% one idea proposed.** The Wipe must stay a net loss at any percentage. Roadmap row 8 settled that a Wipe costs the Party, its Loot and the Risen it raises, and nothing more, so it is already one of the milder options. 25% keeps it a loss. It is a config value; 40% is the ceiling. For a bleak register, "none" is a valid answer (open decision 4).

**Needs.** D1's per-Floor Risen counter on `Tower`, with the fallen Units recorded by name, Unit Type and Floor (today only a count, `runUnitsLost`, exists). `buildRunSummary` already runs before `forfeitLoot` in `endRunOnWipe`, so the amounts are known and can be split instead of discarded. `Tower.restore` and the snapshot carry the Floor memory. The Floor is rebuilt on every arrival, so bearers live on `Tower` and are re-added in `buildFloor`. Verify Toll counters count only at Extraction, so a Cache cannot be farmed. If D1 is cut, this item has no mechanism. Size M.

### 3.8 Core 6. The Atlas, widened

P&P's Atlas is `meta.atlas`, mutationId to the Generation first met. Generalise it to namespaced keys: `mutation:LONG_STAIR`, `find:missing-baggage`, `order:forced-march`, `lesson:counts-the-stairs`. It is a ratchet. The Succession screen lists entries as plain rows (no new screen).

**First contact is blind; a Price never is.** On first contact a Find's slab shows only its kind line and its certain Prices; the outcome of any gamble reads "unknown". After it resolves, the full text enters the Atlas and later meetings show it in full. The redaction is a pure server-side function (`redact(Decision, Atlas)`), so the hidden text is never on the wire. Fairness: no blind Find removes a Unit outright (no fall, no gaze) below Floor 12.

**Pool control.** Unseen entries weigh x1.0, seen x0.25 (the repeat weight P&P uses for Mutations), so each Generation leans toward the unseen. Some entries open only when the Lifetime Ledger crosses a threshold (6.2).

**Optional Knowledge sink.** "Read ahead": spend 10 to 15 Knowledge to read one unseen entry without meeting it. It matches Survey's 15 (10 with Reader of Omens) and gives Knowledge the second long-lived sink the Tower Wizard audit asks for [TW §3b]. It marks the entry **read**, not met: the weight stays x1.0 until it is met, otherwise spending Knowledge would make the entry less likely. It counts against the foresight ration (G10). The Stockroom cap must stay above 15.

### 3.9 Core 7. The Guardian Record (Campaign-level)

**What.** At the end of a Campaign, the server totals the damage the Party dealt to Guardians by Attack Type, after Weakness (Melee, Ranged, Magic; Heal excluded). If one type did at least 55% of it, the next Generation's Guardians take less from that type: x0.95 per Tower Memory step, floor x0.80 (m = min(Generation - 1, 4), so x0.95 at Generation 2 and x0.80 from Generation 5). It sits on top of the Weakness table: a Dragon's x0.5 from Melee becomes x0.40 at step 4. The record covers only the previous Campaign, so the pressure rotates. The Succession line: "Guardians fell to Melee, 71%. Melee does 10% less on Guardian Floors." (step 2, x0.90).

**Why.** Five of ten Unit Types are Melee (Warrior, Captain, Tank, Rogue, Porter), and the Rogue (5 damage, Melee, no mana) is the best mana-free damage per Unit on most Guardians. Not all: the Floor 50 Stone Golem takes x0.25 from Melee and x2.0 from Magic, and Floor 30's Chimera favours Magic. So the optimal Party is often, not always, one-note. A mixed Party leaves nothing on the record: the only free answer is a Roster decision, not a number. It is the cheapest answer in the set to P&P 3.12's verdict that v1 is samey: server-only, no timers, no pause, no RNG.

**Bounds (new).** With Tower Memory's x1.32 Guardian health at Generation 5, the Record makes a one-note Roster face about x1.65 effective Guardian health. Cap it: the Record does not apply on Guardian Floors above 70 until E0 (P&P already caps Memory there), and a config ceiling keeps the combined Memory-and-Record factor to x1.5 until the refit.

**Needs.** A per-Campaign copy of the Guardian Weakness maps (Tower Memory needs the same plumbing for per-Campaign Boss and Enemies config), one hook in the Guardian damage path, three counters in the Campaign Ledger, a `learned` record in `pending`. Config under `game.prestige.guardian-record.*`. Re-fit after E0 (the Floor 70 to 100 Guardians are not beatable on current numbers). Size S. It may ship with Prestige v1.

**Risk.** Melee Rosters will trigger it almost every Generation; verify the average Campaign is not simply taxed each time. Copy must be a record, never "the Tower learned" (G8).

### 3.10 Core 8. Heir Candidates (H7)

**What.** At Succession, if the Roll holds The Heir (Course II), the Heir stops being only "choose Warrior, Archer or Porter". The Chronicle keeps the five Old Hands with the most Lessons (then deepest Floor), alive or dead. Up to three are offered as Candidates: "Brann's Heir", a new Unit named for the Old Hand, carrying the namesake's first Lesson in full (its Gain and its Price), counted as the Heir's first. The Heir keeps +50% hp, enters as the only Unit, and the choice is stored in `campaign.choices` during OFFER. Fewer than three Old Hands means the plain Roll choice. The Chronicle entry gains an `oldHands[]` field (at most 5, with name, Unit Type, first Lesson, deepest Floor, alive or dead), separate from P&P's list of up to 5 names of Units lost.

**Why.** It turns the Chronicle from an obituary into a choice that costs a name, and it is the only way a Unit's history crosses a Succession. Ranking includes living Old Hands so it does not become "die with Lessons". Compare the Heir's value against the Dowry in Credits so it does not dominate. No new Inheritance. Size S. Depends on Lessons and Prestige v1.

### 3.11 How the eight fit

A Generation 2 Campaign. At Succession the player read "Guardians fell to Melee, 71%" and brought a Healer and an Archer. In Expedition 4 the Party is stopped at Low Supplies on Floor 11 and burns 140 Materials for 12 seconds. At Floor 10 it took Forced March, which a Tank and a Healer can pay for and an Archer cannot. At Floor 17 baggage lay on the stair; the Rogue's Tell said the straps were cut, not undone. A Warrior came home at 2 hp and took Counts the Stairs; in Expedition 9 it fell on Floor 12 and the Wall of Names wrote its Lesson beside it. The Wipe left 2 Risen on Floor 12 with 25% of what the Party held. At Succession, "Brann's Heir" is one of three Candidates, and the Atlas has a new row: "Missing Baggage. Met in Generation 2."

### 3.12 Config sketch

```yaml
game:
  decisions:
    enabled: false
    low-supplies: { burn-rate: 0.75 }
    orders: { held-max: 1, pool-start: 9 }
    finds: { min-floor: 4, max-per-reach: 1, min-gap-floors: 3, loot-neutrality-ceiling: 0.03, seconds-neutrality-per-reach: 5 }
    deserters: { floor: 25, share-first: 0.10, share-step: 0.05, share-cap: 0.25, parley-multiplier: 0.5, budget-fraction: 0.25 }
    lessons: { low-water-fraction: 0.25, ascent-step-floors: 5, per-unit-cap: 3, old-hand-cap: 12 }
    risen-cache: { recover-fraction: 0.25, expiry-expeditions: 3, max-tracked: 3 }
    # This layer's own family (Orders + Lessons). Other families keep their own caps.
    layer-clamps: { damage-max: 1.25, damage-taken-min: 0.85, drain-min: 0.75, drain-max: 1.25, attrition-max: 1.25 }
    # Bug catchers only. A clamp that binds here is logged and fixed, not relied on.
    global-ceiling: { attrition: 2.5, drain: 3.0, damage-taken: 3.0 }
    foresight: { max-sources-per-reach: 1 }
  prestige:
    guardian-record: { threshold: 0.55, step: 0.05, floor: 0.80, max-floor: 70, combined-with-memory-ceiling: 1.5 }
```

---

## 4. The decision-point model

### 4.1 Why nothing is timed

Take six Units. Supplies drain 9 a second and each Unit loses 1 hp a second. Archers, Mages and Rogues have 10 hp, and the base Carrying Capacity is 100 Supplies. A 10-second timer on the Tower therefore costs 10 hp per Unit and 90 Supplies, nearly all of it. A 25-second timer costs 25 hp per Unit, which wipes every Unit of 25 hp or less before the player can read. Every timer on the Party is a Wipe. So the only workable form is a **stopped clock with time as a Price**: an option may cost "2 seconds", and that is 18 Supplies and 2 hp per Unit, paid on resolution.

### 4.2 The rule

- **The Halt stops the Party's clock**: Attrition, Supplies drain, combat, Recharge, Reinforcement Waves in transit.
- **The Village clock stops too, by default.** Twelve Reserves against 20 Villagers is net -2 Food a second, so the Pantry's 100 Food empties in about 50 ticks and then a Villager and a Reserve die every tick (P&P 2.6). A Halt that lets that run punishes reading. Open decision 2 offers the dread alternative. If the Village clock is to run, `isClockStopped()` is not enough: the Party and Village clocks need two predicates, because `gameLoop` runs `triggerLifecycle` (Village) and `updateInvasion` (Party) in one call.
- **No deadline.** The default is the status quo option, and nothing applies it. `EXTRACT` is always legal, discards the Decision, and applies the exit Price of that Decision if it has one.
- **What a blocking Halt allows.** `DECIDE`, `EXTRACT` and Village purchases and sales. `REINFORCE` is refused until the Halt is answered (the same allow-list P&P gives the Held Party, with `RESUME` replaced by `DECIDE`). Otherwise a Wave could be mustered, taking Pantry Food, while both clocks are stopped.
- **One stop predicate.** Both `@Scheduled` loops (`gameLoop` at 1,000 ms for `triggerLifecycle` plus `updateInvasion`, and `invasionTimer` at 10 ms for `passingTime`) run through `forEachPlayer` on Boot's single default scheduler thread. Add `GameState.isClockStopped()` and return early in both. The same predicate serves phase OFFER (R2), the Held Party (P6) and a pending blocking Decision, so there is one predicate, not three. It is called the stop predicate, not a "gate", because Gate is an Architecture and a Door synonym.
- **Instantiate at presentation, persist the instance.** A Decision carries its own numbers, so retuning content or config never breaks a stored one.
- **The pause is accepted as a planning tool.** It cannot heal or undo anything, and `EXTRACT` stays the way out. It is the same trade P&P already accepted for the Held Party.

### 4.3 Hooks

Four hooks cover the whole layer. Three sit in `GameService.updateInvasion`; one is at Extraction.

| Hook | Where | Used by |
|---|---|---|
| `SUPPLIES_LOW` | at the top of `updateInvasion`, **before `drainSupplies`**, when Supplies are at or below one tick of drain | Low Supplies |
| `FLOOR_CLEARED` | in the cleared-Floor branch, after the Loot is paid, before `moveToNextFloor`. Skipped for the Base (Floor 0), where the branch pays nothing and moves on | Orders (after `markBossCleared`), Finds raised on a cleared Floor |
| `FLOOR_ENTERED` | right after `moveToNextFloor` and `recordFloorReached`, before combat. A Shortcut Start Floor has no entry hook (it is built by `startNewRunAt`) | Deserters, a figure on the stair |
| `EXPEDITION_ENDED` | after `buildRunSummary` in extract, wipe and victory | Lesson offers, the Cache |

### 4.4 The pay-once guard

The cleared-Floor branch pays Credits and Loot (`gatherCredits`, `gatherFloorLoot`), records the Guardian, and calls `moveToNextFloor`, all in one tick, then returns [Code]. A Halt that simply skips the move would pay the same Floor again on every tick the branch runs. This is the same defect P&P 2.8 #9 found for the Door and ADR 0002 records. The rule: pay once, mark the Floor settled, raise the Halt and return; on resolution call `moveToNextFloor`. The mark clears when the Party moves. **It is the same mark P&P R4 needs for the Door (`doorSeen`): build one `floorSettled` flag, not two.** Resolution runs in the `DECIDE` handler under `callLocked`, so the move happens on the action thread while the loops are stopped.

### 4.5 Decision record, content and wire

- **Decision (immutable).** `{ id, blocking, defId, contentVersion, title, body, options[{id, label, prices[], worst, effects[]}], defaultOptionId, rolls[] (server only) }`, held in `GameState.pendingDecision` with `@JsonIgnore` and exposed as a `DecisionView` projection, because `renderState` serialises the live `GameState` with Jackson. No rolls and no effect payload on the wire. `blocking = false` is used only for Lesson offers and Candidate offers; they never stop a clock.
- **DecisionPoint.** `Optional<Decision> check(GameState, Hook, KeyedSeed)`; Spring injects `List<DecisionPoint>`. It runs inside the 10 ms and 1,000 ms tick path on the single scheduler thread, so `check` must be O(1) and allocation-light.
- **Effects are a closed, id-stable set.** `SUPPLIES`, `LOOT_PCT` (Credits and Materials only, never Relics, never a percent of a Guardian's maximum health), `PARTY_HP`, `LOSE_UNIT`, `ADD_RULE` (a clamped `ExpeditionRules` entry with `{sourceId, until}`), `TICKS(n)`, `GRANT_LESSON`. There is no effect that removes a Villager, no effect that changes a Food rate, and no effect in the Village at all.
- **`TICKS(n)`** is n seconds of Attrition, drain and Recharge with no combat. It needs a combat-free "pay time" step extracted from `updateInvasion`, which Architecture's Traverse also needs.
- **Content is data.** `src/main/resources/content/*.json` with `contentVersion`, loaded into immutable records and validated at boot (every effect id exists, weights above zero, Floor ranges sane, copy lint passes), failing fast. A removed id affects future draws only.
- **Wire.** `GameAction.DECIDE` with `DecideActionDTO(decisionId, optionId)` through the existing `GameActionDTO<T>` switch in `GameWebSocketHandler`. No new server message: state frames already go out every 100 ms and carry `decision`, so the design is idempotent and reload-safe. A stale or wrong `decisionId` is ignored and logged. Fix P&P 2.8 #3 first (`handleTextMessage` catches only `JsonProcessingException`), so a bad `DECIDE` cannot tear down the session.

### 4.6 Randomness

Only the meta layer is seeded, and it is **stateless**. Every draw is `new SplittableRandom(mix(seed, stream, key...))`, the same primitive P&P already uses for Floors (`SplittableRandom(seed ^ floor * 0x9E37...)`), where `seed` comes from the sealed `towerSecret` and the key is a tuple: Orders (Generation, Floor, `expeditionsCompleted`), Find Faces (Generation, Floor, `expeditionsCompleted`), Lessons (Unit id, `expeditionsCompleted`). Named streams are strings in the mix (`order-draft`, `find-place`, `find-face`, `lesson-draft`), so a new consumer never shifts another stream. Persisted counters are not used: with counters a retry draws the next value, and with a key that includes `expeditionsCompleted` the next Expedition draws a new offer by design. Reload and import of the same moment replay the same offer, because the inputs are the same. No `rng.counters` field is needed in the snapshot. A `SeedSource` bean (`SecureRandom` in production, fixed in tests) is the injectable random source the roadmap already asks for. Combat, garrison variance, targeting and Datafaker names stay on `ThreadLocalRandom`. Document that.

### 4.7 Decision budget

| Source | Per Campaign (15 to 17 Expeditions) |
|---|---|
| Orders | 30 to 40 |
| Finds | 9 to 12 (Once by default, plus Deserters) |
| Lesson offers | about 10 (non-blocking) |
| Low Supplies | about 5 |
| **Total blocking** | **about 50** |

At about 12 seconds each that is about 10 minutes of a 75 to 95 minute planned Campaign (P&P 3.10), roughly 11%, with the Lesson offers on top. It is a first guess to measure. The knob if it is too heavy: offer Orders only at every second Guardian Floor.

### 4.8 Held Party, Presence, Extraction

A pending Decision is part of the in-flight snapshot, so a disconnect holds it and `RESUME` re-presents it. Both stops (Held Party and Halt) are the one predicate. "Decision presented" **enqueues** a write on the writer thread and is not awaited, and it is not a commit point: the offer is derived from the sealed seed and stored keys (4.6), so a crash before the write replays the same offer from the last snapshot, as long as `contentVersion` is in the snapshot. The Presence rule (hidden five minutes counts as away) bounds an open slab. Low Supplies can occur at Floor 99 at the Door; it offers the same three options.

### 4.9 Frontend

A `DecisionSlab` standalone component in the sticky scene column (the right-hand column; the left column scrolls, so a pending Decision must never sit in the scrolling flow). Flat 1 px bordered options, keys 1 to 3, tracked by option id, with `computed(...)` equality on the Decision id so the 10 Hz `setState` does not rebuild the buttons or drop focus. Party sprites frozen. No animation, so `prefers-reduced-motion` needs no special case. A golden `DecisionView` JSON is asserted by the backend test and imported by a Jasmine spec. The slab uses panel tokens and carries a word with every state (ADR 0003). Focus order, a visible focus ring and a text equivalent for every key are required.

---

## 5. Content samples

### 5.1 Register

Ledger lines are at most 12 words, flat, with no praise:

```
Order taken: Forced March. Attrition x1.25 to Floor 20.
Taken on Floor 25: 72 Credits, 180 Materials.
They stay on the Floor. They carry 180 Materials.
Floor 9. Two laid to rest. 45 Materials, 18 Credits taken up.
Brann, Tank, home at 2 hp. Lesson taken: Counts the Stairs.
Guardians fell to Melee, 71%. Melee does 10% less on Guardian Floors.
```

### 5.2 Eleven cases

All numbers are for six Units: a Dwell costs 9 Supplies and 1 hp per Unit per second. "Slab" lines follow the 12-word rule. Tags: [Manga §n] for what a source supports; everything else is [Invented]. (They are called cases, not "events": "event" is a Find `_Avoid_` term and E-numbers collide with the roadmap's E0 scaling pass.)

**Case 1. Low Supplies** (Supplies at or below one tick of drain). Tag: [Invented]. Slab: "The Party has stopped. Supplies: 6. Drain: 9 a second." Options: "Extract now. Loot is banked." / "Burn Materials. 140 Materials become 105 Supplies. Once." / "Press on. Starvation 5 a second. Archers last 2 seconds."

**Case 2. The Halt after a Guardian** (Floor 10 slain). Tag: [Invented]. Slab: "Floor 10. The Guardian is slain." Options (one Spend, one Press, one Learn, and "Take none"): "Burn Lamp Oil. 4 seconds of drain, 36 Supplies. Damage x1.15 to Floor 20." / "Forced March. Drain x0.8. Attrition x1.25 to Floor 20." / "Read the Guardian. 1 second, 9 Supplies." See 5.3.

**Case 3. The Fallen's Baggage** (Floor 4 or higher, Once). Tag: [Manga §11, review] (smashing pots and chests yields valuables; falling is fatal) for the register; contents [Invented]. Slab: "Baggage on the stair. No owner in sight." Options: "Open. 2 seconds, 18 Supplies, 2 hp each." / "Leave." Worst case printed: "The owner may stand." Contents (design values, never printed): 55% +30 to +45 Supplies, 20% Materials equal to 3 x the Floor number, 10% one Relic (Floor 10 and up; below Floor 10 this share becomes Supplies), 15% the owner stands (2 Dead Soldiers, 15 hp and 5 damage each). Tells: Rogue "The straps are cut, not undone." Archer "One set of boots, leaving." At Floor 8 the expected goods are about 30 Credits for 18 Supplies: about +12 Credits (about +1.4 s of Party life, plus the hp), well under Reach 1's 29-Credit ceiling and inside the 5 s bound.

**Case 4. The Missing Baggage** (Warren or Colonnade Floor, Reaches 1 to 6, Once). Tag: [Manga §5, wiki] (Ruins-Dwellers are intelligent and harmless; one stole the squad's luggage, apologised and returned it with compensation; they cook insects with salt). Do not present them as traders. Slab: "Something small is at the baggage." Options: "Watch. 2 seconds, 18 Supplies, 2 hp each." / "Leave." Outcome, unknown on first contact: it takes 25 Supplies (25% of 100, rounded up) and returns 60: net +17 Supplies for the 18 paid. After the Stockroom exists it returns 50 and 1 Salt (1 Spore from Floor 20): net +7 Supplies and a Salt. Leaving costs nothing, which keeps G1: the theft happens only if the player watches, and Salt is paid only once a Stockroom exists. Tells: Rogue "The lock was opened, not broken." Porter "The strap was cut clean." The first meeting is dread (an unknown outcome behind a visible Price), and the Atlas turns it into quick knowledge.

**Case 5. Deserters on the Stair** (Floor 25, Returning). Tag: [Manga §4, official PRH vol 3 blurb] (Royal Guard deserters ambush Yuva's party on Floor 25), [Manga §11, review] (rival parties ambush for money); Share, band size and ladder [Invented]. Slab: "Six on the overwatch. They were in the Guard once." Options: "Pay 10%: 72 Credits, 180 Materials." (the figures are the Loot in hand at that moment) / "Fight. Six Deserters join the Garrison." / "Extract." With a Captain: "Pay half." Next meeting: "They want 15%." Tells: Archer "Six on the overwatch." Captain "They salute badly." Rogue "Their packs are light." Use "rival band" and "Deserters", never "delvers". See 3.5 for the numbers, the spawn-template requirement and the Fight-always risk.

**Case 6. The Crossing** (Span or Nave Floor; second wave). Tag: [Manga §11, review] for crates and fatal falls; the Mimic is a species name only [Manga §5, wiki, no description], so keep it a flavour flag; weights, supports and numbers [Invented]. Slab: "A crate on the far stub. Three supports." The supports are the weight the span holds: a loaded Porter weighs two, anyone else one. Options: "Smash the near pots. 1 second. 40% of the contents." / "Pick the lock. Rogue only. 3 seconds." / "Rope. 20 Materials, 2 seconds. Everyone crosses." / "Send Units." If the weight sent exceeds the supports, the heaviest Unit falls. It is deterministic, which answers D10's rejection of random falls. Contents: 0.75 x the Floor's own Materials and Credits, plus 1 Salt (1 Spore from Floor 20). At Floor 12 that is 45 Materials and 18 Credits (108 Credits); gross 3.8% of Reach 2's Loot, net about 2.4% after a Rope (20 Materials, 40 Credits). A seeded Face holds a Mimic (Chimera-grade, 70 hp and 25 damage) that takes a lone crosser; the dust Tell (Rogue or Archer) removes the surprise.

**Case 7. The Barred Room** (Floor 14 or higher, Once). Tag: [Manga §4, wiki, single source] (Gatekeepers can lock doors even against royal blood); contents [Invented]. Slab: "A barred way. The bar is not rust." Options: "Open with 2 Relics. No time." (absent if fewer than 2 Relics are in hand) / "Force. 5 seconds, 45 Supplies, 5 hp each." / "Leave." Contents are a seeded Face: 60 Supplies, or four Dead Soldiers behind it carrying 4 x the Floor number in Materials (Salt and Spores join when the Stockroom exists). Two Relics are 50 Credits at sale price; the average contents are about 86 Credits, so Open is about +36 Credits, inside Reach 2's ceiling. Force is the poor pick. It gives Relics a run-time sink inside an Expedition, the open Package B gap. Tell: Rogue "Scratches on the inside." (The case is not called "The Sealed Door": Door is Prestige's sealed way at Floor 99, and "Sealed" is the word the Door uses.)

**Case 8. The Sleeper** (Warren or Waterway Floor; second wave). Tag: [Manga §7, §11, low-trust fan list] (Lilicen's mana recovers only while she sleeps), [Manga §11, review] (herbs and antidotes heal quickly); no UI line asserts the sleep fact, and the figure is not named or gendered, so it does not read as Lilicen. Never on the Floor 20 Well, so no Floor holds two Halts. Slab: "A sleeper. A pendant at the throat." Options: "Sleep 3 seconds. Casters recharge 1.0 mana a second." / "Sleep 6 seconds." / "Gather. 2 seconds: 1 Spore, 10 hp to the most wounded." / "Leave." Worst case printed: "It may wake." Hidden wake window 4 to 9 seconds; past it, a Garrison of half the Floor's budget arrives. Healer Tell: slow breath means 9 s, fast breath 4 s. It pays only for Necromancers (3.5 per Cast) and Draco-Metamorphs (10 per Cast); a Mage gains about 4.5 damage for 27 Supplies, so the slab says "No one here needs sleep."

**Case 9. The Gaze** (Floor 59 pinned on the Statuary Architecture; one more seeded per Reach from Reach 5; needs E0). Tag: [Manga §5, review: Anime Fire vol 5] (basilisks on Floors 59 to 60 petrify; a victim turned to stone is smashed and infected by maggots; Sargan's mask negates petrification); figure count and gaze rate [Invented]. (It is not called "The Statuary", which is the Architecture's name.) Slab: "Seven figures. They did not finish the step." The gaze takes max(1, ceil(figures x 2% of Party size)). For a Party of 50 and 7 figures, that is 7. Options: "Cross seen. The gaze takes 7. You choose which." / "Cross blind. 3 seconds." / "Wear the Mask. The gaze takes 4." Blind costs 4.5 Supplies and 3 hp per Unit, 225 Supplies for 50. A blind crossing does not also cut Ranged and Magic damage on the Floor, since that would duplicate Architecture's Sightline lever and exceed its 0.8 cap. Veil never hides the count.

**Case 10. Dorias** (a cleared Floor, once per Reach from Reach 2; second wave, needs the Ring). Tag: [Manga §3, §6, §7, wiki] (Dorias is a telepathic living statue who knows the layout and traps; the wiki, not the official blurb, says so), the Thought-Binding Ring deepens with trust [official vol 7 blurb]. That Dorias errs is [Invented]; do not claim the manga's Dorias misleads. Slab: "A statue speaks. It knows the stair." Option: "Ask. 1 second, 9 Supplies. The next three Floors' Find Kinds." Worst case printed: "It may be wrong about one Floor." Reliability rises with the Ring. Counts against the foresight ration (G10).

**Case 11. The Standing Figure** (second wave; after E0 and Gate Floors). Tag: [Manga §2, ch.20 title and official vol 6 blurb] (Gatekeepers hunt the squad), [Manga §5, wiki] (they hoard corpses), [Manga §11, review] (FOE-like roaming elites); movement and numbers [Invented]. Slab: "A figure on the stair. It has not moved." Options: "Fight. It replaces this Floor's Garrison." / "Step back one Floor. 3 seconds." / "Extract." It pays no Guardian Loot and no Easing. **Needs a glossary ruling first**: Architecture treats Gatekeeper figures on Gate Floors as scenery that is not an Enemy, and `CONTEXT.md` defines an Enemy as part of a Garrison or a Guardian. A fightable Gatekeeper changes both. (There is no case for a Caller from the Floor above: Package B R14 already delivers Survivors from the Floor above as Tillers with no choice and no Upkeep.)

### 5.3 Twelve drafts: five Orders (of nine) and six Lessons

(Design words only: "boon" and "curse" are not used in copy. "Curse" is a Mutation `_Avoid_` term and "modifier" an `_Avoid_` term for Weakness. Copy says Gain and Price.)

**Orders** (each shows its Gain and Price; each is a Spend, a Press or a Learn):

| Order | Sort | Gain | Price | Tag |
|---|---|---|---|---|
| Forced March | Press | Supplies drain x0.8 (1.5 to 1.2 per Unit) to the next Guardian | Attrition x1.25 | [Invented] |
| Burn Lamp Oil | Spend | Damage x1.15 to the next Guardian (Sharpened Arms gives +10% a level) | 4 seconds of Party drain, 6 Supplies per Unit (36 for six). Priced per Unit so it scales with the Party: a flat 15 Supplies would be free at 50 Units, and for six Units a 15% shorter fight over a Reach is worth about 6 Supplies per Unit if a Floor takes 5 s | [Invented] |
| Read the Guardian | Learn | The next Guardian's Enemy Type, Weakness line and, under Veil, its hp | A 1 s Dwell: 9 Supplies, 1 hp each | [Invented] |
| Close Ranks | Press | Damage taken x0.85 to the next Guardian | No Reinforcement Wave may be sent until it is slain | [Invented] |
| The Mask (named for the Floor 50 Guardian, so it is held before Floor 59) | Spend | The Gaze takes half as many Units, rounded up | The held slot until Floor 60; a Mage must stand in the Party; the Mask is used up when worn | [Manga §5, review] (Sargan's mask negates petrification); numbers [Invented] |

"Salt the Stair" (2 Salt for Party-wide x1.5 against Chymus Insects) was dropped: it duplicates the Package B Salt Pack without its Carrying Capacity cost or its bearer (2.2, rule 11). "Sargan in the Party" was corrected to "a Mage": a Companion makes a Unit Type recruitable, and Sargan is not a Unit in the Party.

Forced March, per second: it saves 1.8 Supplies for six Units and costs 0.25 hp per Unit. If a Floor takes 5 s, over a 50-second Reach it saves 90 Supplies (the base Carrying Capacity) and costs 12.5 hp per Unit, which is more than the 10 hp of an Archer, Mage or Rogue. It is a Tank-and-Healer Order. A second Guardian-named entry, Eliquo's Arrowhead (named for the Floor 70 Guardian: once per Campaign, a fixed hp number for one Archer's shot, never a percent of maximum health, never the Summit), rests on [Manga §5] (the dragon-slaying arrowhead was the Royal Guard captain's trump card), and waits for E0.

**Lessons** (two offered, plus Decline; every one has a Price; none touches Food):

| Lesson | Unit | Gain | Price | Tag |
|---|---|---|---|---|
| Counts the Stairs | any | Own Supplies drain 1.5 to 1.275 | Max hp -10% | [Invented] |
| Has Seen the Guardian | any | +25% damage against Guardians | Max hp -15% (a Warrior 20 to 17) | [Invented] |
| Sure-Footed | any | Attrition x0.8 for this Unit (10 hp lasts 12.5 s, not 10) | Damage x0.9 | [Invented] |
| Sparing Hand | Caster | Tower Recharge 0.05 to 0.15 per tick | Max Mana 5 to 4 | [Invented] |
| Thin-Blooded | Healer | Heal x1.2 | Max hp -25% (30 to 22.5) | [Invented] |
| Broad Back | Porter | +20 Carrying Capacity (60 to 80) | Attrition x1.25 for this Porter (25 hp lasts 20 s, not 25) | [Invented] |

Counts the Stairs carries no Recovery Price: Recovery costing x1.5 Food per hp would raise the Recovery spend ceiling from 5 to 7.5 Food a tick and feed the Pantry-holds formula, a new Food rate that Package B forbids (G5). No Lesson is named "Light Sleeper" or "Deep Pack", which collide with The Sleeper and the Salt Pack. Tone support: [Manga §11, review] (Soulslike difficulty); the existing Milestone text "Survival is the best teacher".

### 5.4 Tell vocabulary rules

One clause, always true, always partial. Supplied only by a Unit Type present. Never names an outcome. The Atlas names what a Tell implies after first contact. Copy never explains the Tell.

---

## 6. Unlock pools and meta-progression coupling

### 6.1 Pools

| Pool | Starts with | Grows by | Recorded in | Cap |
|---|---|---|---|---|
| Orders | 9 | +1 per distinct Guardian Floor first slain in the lifetime (a set in the Lifetime Ledger) | `meta.atlas` (`order:`) | 19 |
| Find Kinds | 4 at launch | Second-wave Kinds as their dependency ships; unseen weighted x1.0, seen x0.25 | `meta.atlas` (`find:`) | about 12 |
| Lessons | 6 samples, about 8 at launch | Type-specific entries later | `meta.atlas` (`lesson:`) | about 24 |
| Atlas "Read ahead" | none | Spend 10 to 15 Knowledge to read an unseen entry | Stockroom, Atlas | optional |

### 6.2 Thresholds from the Lifetime Ledger

Like Epitaphs, but for pool entries only (nothing is power): about a third of Find Kinds and Lessons open at Generation 2 or higher, and a few at Lifetime Guardians slain of 5 or more. Thresholds need roughly 24 entries to be meaningful; without them they are padding, so do not add them below that. (They are not called "gates": Gate is an Architecture and an `_Avoid_` word under Door.)

### 6.3 What may touch the Toll

Nothing directly. Loot a Find pays (The Crossing, the Baggage) goes into Loot in hand and banks at Extraction through `gatherLoot` and `gatherCredits`, so the counters stay correct; the Loot ceiling (3.5) and the table in 2.4 bound the drift. Salt, Spores and Supplies are not Toll counters. Skipped or Shortcut-started Reaches do not skip Orders unfairly: an Order is offered on slaying a Guardian, wherever the Expedition started. Skipped Floors (a Ways Up idea) are parked.

### 6.4 Coupling to Legacy

| Coupling | Rule |
|---|---|
| Legacy buys decision content | Never. The Roll stays 17 Inheritances, 64 Legacy. |
| First slaying of a Guardian Floor in the lifetime opens its Order | Yes, from the layer's own set (see 3.4). New Ground itself is unchanged. |
| Heir Candidates | The Chronicle entry keeps up to five Old Hands per Campaign (name, Unit Type, first Lesson, deepest Floor, alive or dead). The Chronicle caps at 50 entries, so at most 250 records. |
| Succession screen | Atlas pages and the Guardian Record line, as plain rows. |
| Exodus, Door, Defeat | Unchanged. Low Supplies can fire at the Door. |
| Epitaph "Names on the Wall" (+5% hp at 100 Units lost) | Unchanged. Lessons stack under the family clamp. |

The result is that the layer widens what a returning player knows (the Atlas), what they carry across (one Candidate's Lesson), and how the Tower answers their last Campaign (the Record), without a new currency.

---

## 7. A staged path

Increments are named H0 to H8, because "slice" collides with Architecture's "Slice 1: the Section" and P&P's "Mutations slice A and B", and "stage" is a Floor `_Avoid_` term. Sizes follow the roadmap: S small, M medium, L large. Everything ships behind `game.decisions.enabled`, off by default.

**Position in the roadmap.** The layer is a future improvement, after Phase 6 (Prestige v2) has been played, because v2 is the answer to sameness and the layer is a second, smaller answer. Only H0 is earlier, and it adds nothing the earlier phases do not already need.

| Increment | Work | Size | Depends on |
|---|---|---|---|
| **H0 (existing roadmap, no new work)** | `backend/src/test`; the injectable random source; fix P&P 2.8 #3; a telemetry log of ticks per Floor and per Expedition; the stop predicate (built once for the Held Party and OFFER, P6 and R2); `ExpeditionRules` with per-family caps (built once for Mutations, R7). | in Phases 1, 2a and 6 | none |
| **H1. The Halt and Low Supplies** | Pending-Decision stop in `isClockStopped()`, `Decision`/`DecisionView`, `DECIDE`, the slab, copy lint, freeze-invariant tests, `payTime(n)`. In memory only until persistence. | M (S for the case) | H0, Prestige v2 played |
| **H2. Keyed seed, Orders** | The layer's family clamp and a config key for Attrition, the keyed seed, the pay-once guard (shared `floorSettled`), 9 Orders, characterisation tests. | M | H1; persistence P2 for held-Order fields |
| **H3. Finds engine and four Kinds** | Content JSON loader, boot validation and lint, the Baggage, the Missing Baggage, Deserters, the Barred Room, Tells, both neutrality tests. The Atlas page is Campaign-scoped until Prestige v1 `meta` exists. Contents are Supplies, Materials and Relics only. | M | H2. Salt and Spores contents wait for the Stockroom (Package B). |
| **H4. Guardian Record** | Counters, per-Campaign Weakness copies, Succession line, the Floor-70 cap and the combined ceiling. May ship with Prestige v1. | S | Prestige v1 (Tower Memory plumbing); refit after E0 |
| **H5. Lessons** | Stable Unit identity, named selection (Party and Waves) in the Loadout, per-Unit rules, `UnitRecord` in the snapshot, equality by id, non-blocking offers. | L | Persistence P2; the Loadout panel; H2's rules object |
| **H6. What the Risen Carry** | Fallen Units recorded by name and Floor, Cache on `Tower`, 25% into Loot in hand. | M | D1 (Risen), `Tower.restore`, the roadmap row 8 ruling |
| **H7. Heir Candidates and "Read ahead"** | Chronicle Old Hand record, Candidate choice at Succession, Knowledge sink. | S + S | Prestige v1 Succession; H5; Stockroom Knowledge |
| **H8. Second wave** | The Crossing, The Sleeper (Architecture); The Gaze (E0); Dorias (Ring); The Standing Figure (L, after E0, Gate Floors and a glossary ruling); Known Dead and Graves (reuse Core 5). | S to L each | as listed |

Rough weight for the core: four M, one L, three S.

**Tests that must exist before each increment ships.** H1: freeze invariants (no Attrition, drain, combat, Recharge or Wave movement while stopped), `DECIDE` idempotence, a stale `decisionId`. H2: characterisation tests for `unitsAttack`, `enemiesAttack` and `updateInvasion`; keyed-seed determinism and stream independence; the pay-once guard. H3: content lint, both neutrality tests, the "Leave costs nothing" test. H5: equality by id, named selection, per-field snapshot accounting. H6: a Cache cannot be farmed (Toll counters count only at Extraction).

**Persistence additions**, each with a field-accounting test entry: `expedition.pendingDecision`, `expedition.decisionLog` (a ring of 20), `expedition.orders`, `UnitSnapshot.lessons[]`, `tower` per-Floor Find and Cache state, `meta.atlas` pages (namespaced keys), `meta.chronicle[].oldHands`, `meta.lifetime.guardianFloors` (a set), `contentVersion`. A removed content id needs a migration entry (the released-ids freeze test). No counters: the seed is derived (4.6).

**ADR candidates** when built: "A pending Decision stops both clocks" and "The meta layer is seeded by keyed derivation, not by counters".

Dependencies in one line each:
- **Persistence.** H2, H3, H5, H6 and H7 add snapshot fields; before it exists they are in memory, which `closeSession` drops on disconnect today.
- **Injectable random source.** H2 onward. Combat stays unseeded.
- **Loadout panel.** Required by Lessons (named selection), by Orders and Finds that spend or give Salt and Spores, and by the Loadout line for a Cache.

---

## 8. Risks, what to cut, and the 40 ideas

### 8.1 Risks

| Risk | Mitigation |
|---|---|
| Pace is unmeasured; every per-Floor figure is a guess. Six Units run out of Carrying Capacity in 11 seconds, which may make Reaches shorter than the docs assume; a Party from the Base cannot reach Floor 25 in one Expedition on today's Supplies. | H0 logs it. Prices are per second or per Unit. Reach-length figures are labelled "if a Floor takes 5 s". Shares follow Loot in hand. |
| Upper-Tower content is beyond reach on current numbers (P&P 3.11: Floor 70 to 100 Guardians are not beatable). | Gate Floor 60+ content (The Gaze, the Arrowhead, The Standing Figure) behind E0. |
| Orders and Lessons add Party power, which moves Ascent and G. | The layer's family clamp (3.12); E0 fitted with the layer off, then refitted with it on (2.4). |
| A free pause can be used as a planning tool. | Accepted: it cannot heal or undo, `EXTRACT` stays legal, `REINFORCE` is refused, and Presence bounds it. |
| Content debt: cases, Orders and Lessons are copy, every Face needs three Tells, and thresholds need about 24 entries. | Start with 4 Kinds, 9 Orders, about 8 Lessons; lint and both neutrality tests are the guard. |
| Toll par values drift (the Toll's par values are fitted to today's loot formulas). | 2.4: bounded Loot cases, goods the Toll does not count, no Loot lever [roadmap ruling 6]. |
| A solved optimum (always Forced March with Tanks; always Fight at Floor 25). | Pick-rate telemetry, composition-dependent Prices, retune against E0. |
| Four foresight sources dilute the unknown. | G10, one per Reach. |
| Decision fatigue: about 50 blocking decisions, about 11% of a Campaign. | Cap per Reach; the every-second-Guardian knob; keep Order Halts to Guardian Floors. |
| The Unit identity refactor changes balance silently (random enlistment, max-hp reads, equality over hp). | Characterisation tests first, as R7 requires for `unitsAttack` and `enemiesAttack`. |
| It softens a Wipe that roadmap row 8 already kept mild (Risen only). | 25% is config; cut to nothing for a harder Wipe. |
| Lessons favour fragile Units (an Archer is near death often). | Fraction rule plus the Floor-record gate; check the telemetry for "feed Lessons to Archers". |
| Tone drift into a loot game. | The copy lint, no icons, one Ledger line, Prices before Gains. |
| The first-contact blind outcome can read as unfair. | Prices always visible; no blind Find removes a Unit outright below Floor 12. |
| The Floor strip carries too many glyphs. | Review the cell with every glyph present; the DOM panel stays authoritative. |

### 8.2 What to cut, in order, if capacity shrinks

1. "Read ahead" and Heir Candidates (H7).
2. The Cache (Core 5), if the author wants a harder Wipe.
3. Lessons (Core 4). It is the heaviest item and the one that makes losses personal; cutting it leaves Units fungible but removes the largest refactor.
4. Finds beyond the Baggage and the Deserters.
5. Orders, down to the three cheapest Spends.
6. The Halt and Low Supplies last: the stop predicate is shared with the Held Party and OFFER anyway.

### 8.3 Disposition of the 40 ideas

The 40 source ideas are not in the repository, so this table is the only record of them; keep it. The tables list 20 adopted (some merged), 11 parked (Tower Codes and Bare Descents counted as two) and 10 rejected, with "Two Voices" appearing in both Parked and Rejected as two halves of one idea, so 20 + 11 + 9 = 40.

**Adopted into the core (20, grouped).** Left-hand names are the ideas' working names; renames are in the verdict.

| Idea | Verdict | Reason |
|---|---|---|
| The Dry Stair | Adopted as Low Supplies (Core 1) | The foundation, with no randomness and no lore claim. Renamed: "The Stair" is Package C's repair term. |
| The Halt at the Gate: draft one of three Orders | Adopted (Core 2) | Framework folded into Core 1. The 25 s deadline and "Village clock runs" dropped; "Wager" renamed Press (Pact `_Avoid_`). |
| Finds on a cleared Floor | Adopted (Core 3) | The frame, plus the Baggage and the Barred Room. |
| Signs and the Reading | Adopted (Core 3) | Tells, Kind-versus-Face placement, the Atlas of Signs folded into the Atlas. |
| Stair Events | Adopted (Core 3 and 2) | The engine: the keyed seed, content as data, lints. Counter-drawn placement not adopted. |
| Encounters on the Stair | Merged | The pause is folded into Core 1; the 30 s wall-clock window rejected; blind first contact moves to Core 6. |
| The Toll Band | Merged | Becomes the Deserters Find. Its name collides with Prestige's Toll. The repeat once per Reach from Reach 3 is cut to Floor 25 for now. |
| Tribute: parleys where the Price is a body | Merged in part | The 10% pay-or-fight at Floor 25 is kept. The Floor 60 "leave a Unit to pass" is rejected: it skips a Guardian, removes the wall Easing and Prestige rely on, and a Necromancer parley is unsourced. |
| The Return | Adopted as a Kind | Becomes The Missing Baggage. The skip of 2 Floors is dropped (see Ways Up). |
| The Far Cache | Adopted as The Crossing, second wave | The best deterministic counting puzzle; needs Span and Nave. Renamed: Cache is the Risen term. |
| The Sleeper | Adopted, second wave | Low value at today's mana; low-trust source. |
| The Statuary | Adopted as The Gaze, second wave | Needs E0 and Architecture. Renamed: Statuary is an Architecture. |
| Lessons | Adopted (Core 4) | The surviving trait system. |
| Marks on the Returned | Merged into Lessons | The Cost idea kept; the name collides with Toll Marks. |
| Old Hands | Merged into Lessons | Old Hand is the term for a Unit holding one; the three-Extractions trigger dropped. |
| What the Risen Carry | Adopted (Core 5) | At 25%, not 40%. |
| The Folio | Adopted as the Atlas widened (Core 6) | The name rejected: one codex. |
| The Guardian Record | Adopted (Core 7) | Cheapest answer to a samey v1, with the Floor-70 cap. |
| The Heir's three Candidates | Adopted (Core 8) | Extends the Roll's Heir; no new Inheritance. |
| Spoils | Merged into Orders | Guardian-named Orders carry the Mask, the arrowhead and the pendant. A separate Loadout-item pool, 30 items and an Atlas of Spoils rejected: a second draft on the same moment, size L. |

**Parked (11), each with a trigger to revive.**

| Idea | Why parked | Revive when |
|---|---|---|
| Graves | Reuses Core 5's record across Generations; needs a `meta` write in the end-of-Campaign transaction. | Core 5 is built. |
| The Known Dead | Strong on tone; death-spiral risk; needs Lessons and D1. | Core 4 and Core 5 exist. |
| Two Voices (Dorias half) | Needs the Ring. That Dorias errs is invented. | R20 Trust and the Ring ship. |
| The Gatekeeper (ladder marker) | L; needs E0, Gate Floors, per-Floor state and a glossary ruling. | After E0. |
| Charges | Reads as a task list; pays Trust (Package B step 9). | H1 to H3 are played and Expeditions still feel the same. |
| The Caller at the Gate | A second implementation of R14, which already delivers Survivors with no choice. | Never, unless R14 adds a choice. |
| Links | A third Wipe-side loss (roadmap row 8); a Trust sink for the Ring. | The Ring has no other use. |
| Oaths | Reopens P&P 3.1, which collapsed Vow, Pact and Aspect into the Pact. Party-size caps are unwinnable before E0. | The author wants a known, Village-side Omen kind in v2. |
| Tower Codes | Post-Roll replay, no Legacy; small audience; honour-system record. | The author wants replay after the 64-Legacy arc. |
| Bare Descents | As Tower Codes. | As Tower Codes. |
| Ways Up | L; overlaps Shortcuts and Narrow Stair; needs the Architecture plan and `FloorRules`; skipped Floors raise P&P decision 12. | After Architecture levers and E0. |

**Rejected (9 distinct, 10 rows with Two Voices' second half).**

| Idea | Reason |
|---|---|
| The Walk Down | It changes every number (Loadout, Quarry Heap, Door, Held Party) and is a core-loop change, not a layer. The Last Order inside it is a live window. Revisit separately after E0. |
| The Last Resort | Real-time, size L, a new unit-targeted action and ten class effects. The kernel (a Unit given up for a stand) can return as a Halt option. |
| The Hunt | A pursuer in the 1,000 ms loop whose tuning depends on unmeasured Floor time; replaced by the ladder marker. |
| Imprints | A second per-Unit trait system; stat inflation; unreadable at 12 px; needs the Spore Cellar. Grievous (D5) stays. |
| Strays at the Gate | Weakest on dread; a loot box; free Units on the Food margin; needs Imprints. |
| The Named Heir | A Unit crossing Generations contradicts "all Units are lost at Succession", adds a +10%-per-Generation power track beyond the Roll, and invites save-scumming. |
| The Unrecorded Floors | Contradicts the Summit's definition and the finite shape; theatre before E0; the Summit is open in the manga [Manga §8]. |
| The Packing | A second draft per Expedition beside Orders and the Loadout; a mandatory extra click at every Set Out that conflicts with Muster Roll; "Provision" is a Food `_Avoid_` term. The seam it needs is kept (Core 2). |
| Two Voices (the Screen half) | No source in `docs/research` for a voice on a screen; a deferred Debt is cross-Expedition state that overlaps the Pact. |
| The Order of Ascent | Not a roguelite device: it is a targeting change. But it records a real finding (below). |

**Separate finding.** `GameService.enemiesAttack` shuffles Unit Types, takes the first, then picks a random Unit of that type. Every Unit Type is therefore equally likely to be hit regardless of headcount, and a lone Healer draws as many hits as 20 Warriors. That is probably accidental. Rank weights, the Salt-bearer and Chymus Spread all need a deliberate targeting hook (P&P 3.8), so it belongs to Package B, with a measured before and after. It is not part of this layer.

**Out of scope, not a roguelite idea.** Generation Palettes: decided separately (roadmap row 3), grim.

---

## 9. Glossary terms proposed

Written to `CONTEXT.md` style, with `_Avoid_` lists. Terms the roadmap already lists (Held Party, Risen, Atlas, Survey) are used as is. Proposed terms were checked against the planned terms in the roadmap, P&P, Package B and Architecture, and the collisions found are listed after the entries.

**Halt**: The Party's clock stopped on a Floor so the player can answer one written choice; it ends in an answer or an Extraction. Not the Held Party, which is a disconnect. _Avoid_: Pause, Popup, Prompt, Event.

**Order**: A written term the Party takes on after a Guardian is slain, with a Gain and a Price, lasting until the next Guardian is slain. Each is a Spend, a Press or a Learn. _Avoid_: Boon, Buff, Perk, Blessing, Card.

**Find**: A fixture on a cleared Floor that the Party may stop for (baggage, a barred way, a rival band); its Kind shows before it is opened. A Find is Once (gone when resolved) or Returning. _Avoid_: Chest, Treasure, Event.

**Face**: The seeded detail of one Find on one Floor in one Expedition (its stakes and variant), fixed when first presented. The Kind is what the Floor strip shows; the Face is what the slab shows. _Avoid_: Variant, Roll.

**Gain** and **Price**: What an option gives and what it costs, both written before it is taken. _Avoid_: Reward, Drawback, Penalty.

**Tell**: One true, partial clause that a Unit Type reads from a Find. _Avoid_: Hint, Clue (and never Omen, which is a foretelling at Succession).

**Dwell**: Seconds the Party stands still on a cleared Floor and pays Attrition and Supplies for a choice. Not Traverse, which is approach time before combat on an Architecture. _Avoid_: Wait, Rest.

**Lesson**: A permanent Gain with a Price, taken by a Unit that came close to dying; lost with the Unit. _Avoid_: Mark (a Toll unit), Habit (Hard-Won Habits), Perk, Trait, Skill, Quirk.

**Old Hand**: A Unit that holds at least one Lesson. _Avoid_: Veteran, Elite, Named Unit.

**Cache**: Loot lost with a Party and held by the Risen on a Floor; a quarter goes to the Party that lays them to rest, as Loot in hand. _Avoid_: Corpse run, Bloodstain.

**Share**: The part of the Loot in hand a rival band takes. _Avoid_: Tax, Tribute, Toll.

**Guardian Record**: The Succession line that states which Attack Type did most of the Guardian damage in the previous Campaign, and the small reduction the next Generation's Guardians take from it. _Avoid_: Adaptation, Learning.

**Candidate**: An Old Hand of the ended Campaign offered at Succession as the Heir's namesake. _Avoid_: Successor.

**Once** and **Returning**: Whether a Find is gone for the Campaign once resolved, or meets the Party again. (Renamed from Spent and Recurring: "Spent" collides with the Spend Order and with Legacy spent on the Roll.)

Existing entries to edit when this ships: **Expedition Summary** gains "decisions taken", "Old Hands" and "Cache" lines; **Ledger** is unchanged; **Atlas** (when it enters `CONTEXT.md`) is defined as the record of what the Tower has shown, not only Mutations; **Enemy Type** is unchanged (the Deserters are a Find rule, not an Enemy Type).

Terms deliberately not added: Folio (use Atlas), Mark (Toll), Wager (Pact), Boon and Curse (design words; Curse is a Mutation `_Avoid_`), Provision (Food), Named Unit, Stray, Caller, Charge, Oath (parked), Gate (an Architecture, and a Door `_Avoid_`).

**Collisions found in the critic pass, and the fix applied in this text.**

| Draft word | Collides with | Now |
|---|---|---|
| The Sealed Door (a Find) | Door (the sealed way at Floor 99); "Sealed" is the Door's word | The Barred Room |
| The Statuary (a Find) | Statuary (an Architecture, pinned on Floor 59) | The Gaze |
| Far Cache (a Find) | Cache (the Risen term) | The Crossing |
| Pack of the Fallen, Missing Pack, Deep Pack, "base pack" | Salt Pack (Loadout); Loadout `_Avoid_` "pack list" | The Fallen's Baggage, The Missing Baggage, Broad Back, "base Carrying Capacity" |
| Dry Stair | The Stair (Package C repair term) | Low Supplies |
| Light Sleeper (a Lesson) | The Sleeper (a Find) | Sparing Hand |
| Spent (a Find class) | The Spend Order sort; Legacy spent on the Roll | Once |
| Slice 1 to 8 | Architecture's "Slice 1: the Section"; P&P's "slice A and B" | H0 to H8 |
| E1 to E12 (cases) | E0, the scaling pass | Case 1 to 11 |
| C1 to C8 (core items) | Package C | Core 1 to 8 |
| "Toll Band", D6's "Toll" | Toll (Prestige) | Share; rename D6's word if D6 is revived |
| "gate" (thresholds, the clock) | Gate (an Architecture), Door `_Avoid_` "Gate" | threshold, stop predicate |
| "Roll node" | Roll `_Avoid_` family (Inheritance is the term); Find `_Avoid_` "Node" | Inheritance |
| "event", "chest", "run-time", "climb" | Event, Chest (own `_Avoid_`); Expedition `_Avoid_` run and climb | case, crate, in-Expedition, go up |
| Halt | Held Party (near), "Hold" (Labour, Hold, Wait) | Kept; alternative "Stop" (open decision 14) |

---

## 10. Decisions only the author can make

1. **Schedule.** Confirm the layer is a future improvement after Prestige v2 has been played, with only H0 (the stop predicate, the keyed seed, the rules object) built earlier because Persistence and Prestige need them. Recommended: yes.
2. **Do both clocks stop during a Halt?** Recommended: yes (consistent with the Held Party; a Village that kills during reading punishes thought). The alternative: the Village clock runs, so thinking costs Food (dread), at the risk of starvation while a slab is open, and it needs a second predicate.
3. **Is a stopped clock acceptable at all in a real-time game?** Recommended: yes, because every timer on the Party is a Wipe (4.1). If not, the layer shrinks to choices made at Set Out and Succession.
4. **How much should a Wipe give back?** The Cache at 25%, 40%, or none (the bleakest). Row 8 settled that a Wipe costs the Party, its Loot and Risen only, so this answer sets how mild a Wipe is.
5. **Placement of Finds.** Kind pinned per Generation and Once by default (recommended: decisions at the frontier, first contact is the dread) or counter-drawn every Expedition (more variety, nothing learnable).
6. **Lessons: near-death trigger** (recommended) or three Extractions; Campaign-scoped with only one Candidate crossing (recommended), and whether to build them before the Loadout supports named selection (recommended: no).
7. **Deserters replace D6's flat levy.** Build one, not both. Recommended: Deserters, with the Fight-always telemetry check.
8. **Guardian Record before E0?** It is cheap and can ship with Prestige v1; multipliers are refitted after E0, it does not apply above Floor 70, and the combined Memory-and-Record factor is capped at x1.5. Recommended: yes.
9. **Adopt "Read ahead" as Knowledge's second sink?** Recommended: yes, after the Stockroom exists, and as "read", not "met".
10. **Reading chapters 24 to 31** (roadmap decision 7) before writing copy for Dorias or any Necromancer voice. Recommended: yes; nothing in `docs/research` supports a speaking screen.
11. **Which parked ideas to revive, and when:** Oaths (a known Village-side Omen kind), Tower Codes (replay after the Roll), Charges, Ways Up, the Standing Figure, and the Unrecorded Floors (needs the Summit defined).
12. **Orders cadence.** Every Guardian Floor, every Expedition (30 to 40 Halts, recommended to start) or every second Guardian Floor if the roughly 11% decision-time budget is too heavy.
13. **No new Inheritance.** Confirm the Roll stays 17 Inheritances and 64 Legacy, or allow one (for example, one re-drawn Order per Expedition, parallel to Reader of Omens).
14. **Glossary.** Accept "Halt" (with its near-collision with Held Party; the alternative is "Stop"), Lesson, Old Hand, Order, Find, Face, Tell, Dwell, Cache, Once and Returning, and one widened Atlas without a Folio.
15. **E0 fit.** Fit E0 with the layer off, then refit with it at full take-up before it ships (recommended), rather than fitting E0 with it on.
16. **Foresight ration.** One foresight source per Reach (recommended), shared by Survey, Survey Maps, Read ahead, a Learn Order and Dorias.
17. **A set of Guardian Floors in P&P's Lifetime Ledger**, so Orders can open per Floor (a small P&P addition), and namespaced Atlas keys from the first v2 build. Recommended: yes to both.
18. **Old Hands in the Reserves can die to Village Starvation** (a random Reserve is removed). Keep it random (recommended) and name the lost Lesson in the Ledger, or protect Old Hands.
19. **The Walk Down.** Whether Extraction should stop being a teleport at all, as a separate core-loop design after E0 (the layer does not need it).
