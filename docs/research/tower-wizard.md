# Tower Wizard (barribob): what the game actually is

Research snapshot as of **2026-10-10**, covering <https://barribob.itch.io/tower-wizard>. Each report was re-checked by an independent skeptical pass.

Confidence labels: **[primary]** is the itch page, itch devlogs, the Steam page, and the developer's own Steam news posts and APIs. **[secondary]** is fan guides, reviews, Steam threads and itch comments. The developer documents almost none of the mechanics himself, so most of section 3 is [secondary] and some numbers conflict between guides.

## 1. The headline

**Tower Wizard is a short incremental (idle/clicker) game in which you build the tower upward. Nobody climbs it.** There are no expeditions, no party, no floors to clear and no failure state. The tower is the progress bar: tower levels unlock new rooms (buildings), and the only combat-like element is spirits and a dragon hitting a **Wall** (the last one is the Cosmic/Celestial Wall), whose destruction ends the game.

So the ideas that carry over to Tower Dungeon are the *structure around* the tower, not the tower-as-dungeon: prestige, milestones, spirit-summoning helpers, resource chains, a definite ending. If a Tower Dungeon idea was assumed to come from Tower Wizard, check it against section 3 first.

## 2. Facts [primary]

- Itch description: "Help a wizard construct the mightiest wizard tower in this short incremental game!" Tags: building, Idle, Incremental, upgrades. Genre: Simulation. The status field still says "Prototype", which is stale.
- Built with **Godot** (HTML5 and Windows). Controls: click and drag to pan, scroll or +/- to zoom. Saves export/import between the browser and desktop builds.
- **$2.99** minimum on itch (pay what you want above). Rating **4.6/5 from 782 ratings**, 271 comments.
- **Steam release 2025-06-19** (app 3372980), developer and publisher both Barribob. **Overwhelmingly Positive: about 96.7% of 8,861 reviews.** 12 achievements, cloud saves, 8 languages (localisation added May 2026). The itch full version followed on 2025-07-02.
- Steam text: "Gather magic and build your wizard tower. Summon spirits to help gather resources. Construct buildings using the resources you gathered. Master magic to create greatest wizard tower! An active incremental/upgrade game. Prestige to replay and progress faster. Tons of upgrades and unique mechanics. **Definite ending.**"
- **"After 8 months from the beginning as a tiny prototype, Tower Wizard is completed!"** (2025-06-19).
- Stated inspiration: **(the) Gnorp Apologue** (named as "one of the main inspirations"). The developer also notes similarities to his earlier game **Magic Archery**. His next game is **Feed The Void**.
- No post-mortem, interview or design essay was found.

### Update history that explains the design

| Date | Change |
|---|---|
| 2024-12 | Prototype is announced to become a full Steam game. |
| 2024-12-22 | Old saves mostly break. Prestige points carry over. |
| **2025-01-04** | **Tower requirements became milestones instead of resource-consuming upgrades.** Study and Forestry were reworked, and totems were added. |
| 2025-01-11 | Tooltips, hotkeys and navigation buttons. Forest totems now summon a few spirits. |
| 2025-03/04 | Steam demo 0.1.1 and 0.2.0: camera controls, rebalances, **Milestone Blessings** fixed. |
| 2025-06 to 08 | 1.0.1-1.0.4: tooltip and blessing rewording, a "too many totems" fix, an Observatory requirements button, and five backup saves with automatic fallback. |
| 2025-08 onward | No content patches found. Work was localisation (7 languages, May 2026) and the next game. |

## 3. Mechanics [secondary: kaijuguides, yekbot, FandomWire, reviews, itch comments]

Names and numbers differ between guides, so treat specifics as approximate.

**Core loop.** Hold or click the central **Orb** to gather **Magic**. Spend Magic to summon **Spirits** and assign them to roles: Cloudlings make Magic, Spirit Tomes make Knowledge, Druids make Wood. Knowledge buys buffs and unlocks buildings. Wood buys **Totems** (Magic, Knowledge and Forest types; pairing two of one kind boosts them). Spirit Houses can be specialised, and you get about 4 free houses per ascension. A run typically peaks around 100 spirits. Holding Shift while clicking builds in increments of 10.

**Tower levels unlock rooms.**

| Level | Unlock |
|---|---|
| 1 | Orb, Cloudlings |
| 2 | Study, Spirit Tomes (Knowledge, buildings) |
| 3 | Forestry, Druids (Wood, Totems) |
| 4 | Prestige |
| 5 | Academy (Sages, Research), Dragon Nest (Nest Keepers raise a Dragon) |
| 6 | Alchemy Lab (Alchemists, Magic Gold, Relics) |
| later, level unstated | Embassy (Diplomats, regional pacts), Sorcery (Shaman, Ifrit, Gorgon, Zephyr combat spirits), Runecraft (Runesmiths) |

**Walls and the ending.** Sorcery spirits, wall debuffs (Molten, Curse), the Dragon's Fireball and Spirit Sages damage Walls. The developer's own patch notes name a **Cosmic Wall with a health value** and an **Observatory**. Per guides, the final wall's protection is "effectively infinite but not quite". Attacking it yields Dust, which pays for the Observatory, and enough Knowledge lets you study it for a weakness. Breaking it rolls the credits, and you can keep playing afterward.

**Prestige.** Magic generation fills a Prestige bar. Resetting converts it to **Prestige Points** spent in a **Blessings** tree, which includes Milestone Blessings. Guides give per-run cumulative targets of roughly 5, 11 and 23 points, and some players take 4-5 runs. No formula is documented. Several reviewers finished without buying every upgrade.

**Length.** Reported full completions range from 1h20 to about 12h including idle time, and typical figures are 2-8 hours.

**Idle.** The game runs while open and players leave it overnight. **Players report no offline progression** (see 3b). "Active incremental" is how the developer describes it.

**Difficulty.** There are no failure states, and scaling is through costs and walls. Reviewers split on balance: "number go big" early, damage optimisation late.

## 3b. Resource economy (second research pass, 2026-10-10)

The developer publishes no production rates or cost formulas. The only quantitative statement anywhere is "costs increase exponentially, while bonuses (buildings, blessings) provide a linear multiplier" [secondary: kaijuguides, Quick Guide]. Everything below is from guides, Steam threads, reviews and the screenshots, and is marked [primary] only where it comes from the developer. The Steam screenshots show seven resources in the left-hand panel.

**Terminology, resolved as far as sources allow:**

- **Ascension** = climbing to the next tower level. It costs nothing and is gated by milestones (kaijuguides calls the same step "Tower Level N"). **Prestige** is the separate reset that grants Prestige Points.
- **Magic Gold** is the likelier name (four sources). Kaijuguides' "Arcane Gold" is a lone outlier.
- Run numbering differs by one between guides: both describe four runs.

### Resources

| Resource | Produced by | Spent on | Notes |
|---|---|---|---|
| **Magic** (droplet) | Orb (hold or click), Cloudlings, later Alchemy Mana Stones and Research | Spirits, tower milestones, prestige bar | Holding the Orb with Cloudlings active gives about +35%. Wizard Magic upgrade is +100% Orb output per level. |
| **Spirits** (sparkle) | Bought with Magic. Forest Totems add free ones (the developer says "a few"; three guides say 4). | Assigned as workers to buildings | A run caps near 100 Spirits without blessings. |
| **Knowledge** (brain) | Spirit Tomes (Study) | Buildings and buffs (e.g. "Increase Knowledge gain by 50%, costs 100") | Unlocks new buildings. |
| **Wood** (log) | Druids (Forestry) | Totems only | The "dead currency" problem: once totems cap, Wood is useless. Players asked for refunds and converters. |
| **Totems** | Crafted from Wood | Placed on poles. Types: Magic, Knowledge, Forest. Players also report Academy and Crafter totems from the Prestige Shop. | Screenshot-confirmed rule: **"for every 2 totems of a certain type carved, increase that type's passive effect by a compounding 35%."** Cost stopped growing after 100 totems, a bug fixed in 1.0.3. Effective cap about 50 per type [fan]. |
| **Houses** | Free (about 4 per ascension [guides]) | Housing, specialised for Magic, Knowledge or Wood | |
| **Research** (Academy) | Spirit Sages | Boosts Magic, Forestry, Dragon, Alchemy | Acts as an augment layer, not a spendable currency. |
| **Magic Gold** | Alchemists convert Magic | Relics (Mana Stone, Dragonite, Holy Grail, Philosopher's Stone, Ouroboros) | **The only explicit converter in the game.** No exchange rate is published. The Holy Grail needs 10 Dragonite and 10 Mana Stones first. |
| **Dust** | Attacking Walls | Observatory, Cosmic upgrades | About 42 lifetime Cosmic Dust to destroy the Cosmic Wall, and 50 for the final step [achievement guide]. |
| **Prestige Points** | Lifetime Magic plus lifetime damage | Permanent Blessings | Cumulative, so players never lose progress toward points by spending. |
| Trade Agreements, Runes, Dragon XP | Diplomats, Runesmiths, Dragon Keepers | Nations, rune upgrades, dragon abilities | Not re-verified. |

### Rules that shape the economy

- **Cumulative milestone gating [primary].** Tower requirements are "milestones instead of upgrades that consume resources", and the tower requirement bars in the screenshots show a total collected (e.g. 1.00T Magic, 500 Magic Gold) next to an Ascend Tower button. Players are never punished for spending.
- **One shared worker pool.** Every role draws from the same Spirit population, so each assignment has an opportunity cost.
- **Refundable assignments.** Diplomats return all Spirits once their building is maxed, the only fully refundable subsystem. Players asked for the same for Druids.
- **Pulses.** Totems multiply base income at intervals, giving a visible rhythm.
- **Bulk buys.** Shift-click buys 10. A reviewer complained the preview does not show the real 10x price.
- **Autobuy and Hide Maxed** both exist (screenshot). A 5/10 reviewer says autobuy removes decisions and the only choice left is when to prestige.
- **No offline progression**, per players (four Steam posts; no developer reply). A marketing site claims otherwise but is unaffiliated and unreliable. The browser build does not tick in a background tab.
- **Pacing:** typically 3-6 hours; one reviewer finished in under 2.5.
- **Quality-of-life gaps players named:** per-source rate breakdown, a per-second counter, clearer Academy selection, onboarding.

### Lessons visible in the economy (observations only)

- Give each currency at least two long-lived sinks or a converter, or it goes dead (Wood).
- Make sure cost curves and caps survive autobuy; the totem cost bug flooded a sink.
- Visible workplaces (a library fills with books, druids chop, the dragon grows) make the grind readable.
- Cumulative-total gating plus a small number of permanent reset rewards kept the game finite and well liked.

## 4. Reception

- **Praised:** finite scope with a clear ending ("The Dignity of a Finite Tower"), constant new systems at a good pace, interlocking resources, pixel art and sound, price, export/import saves and "keep playing" after the end.
- **Criticised:** too short and no New Game+, only 12 achievements, linear or samey prestige and a slow late game, no onboarding (Academy selection UI is unclear), no production stats, some crash and fullscreen reports.
- **Requested:** more prestige layers, more content, a hide-maxed setting, ports, localisation (delivered).

## 5. Observations that could map to Tower Dungeon

Facts only; deciding what to do with them belongs to the brainstorm.

- **Prestige** is the biggest overlap. `CONTEXT.md` lists Prestige as "intended but not yet built". Tower Wizard's version is a progress bar filled by cumulative output, a reset, and a permanent tree of blessings in tiers. Its criticisms (linear, samey, pointless if you can win without it) are warnings.
- **Milestone Blessings and milestone requirements** parallel the game's existing **Milestone** (free, one-time, automatic). Tower Wizard's change from "consumes resources" to "reach a milestone" is the same idea.
- **A definite ending** matches the game's summit Victory, and players valued it.
- **Totems and spirit houses** show a resource-to-helper conversion chain (Wood to Totem to Spirits), like Food to units.
- **Buildings as unlockable rooms** gated by tower level resemble the game's unlock upgrades and floor-based milestones.
- **Idle/active mix:** holding the Orb boosts passive helpers by about 35%, rewarding attention without requiring it. The game's real-time loops already run server-side.
- **Quality of life** that players asked for: onboarding, production stats, hide-maxed, backup saves.

## 5b. Visuals (observed directly from the six Steam screenshots)

- **Tight, limited palettes, swapped per tower ascension.** Each screenshot uses just 3-5 colours on a near-black base: pink/coral and steel blue on slate; gold and tan on deep violet; amber on dusk purple; green, yellow and blue on charcoal. Changing the palette after an ascension makes progress feel like a new chapter without new art.
- **One tall, hand-built pixel tower** drawn as two-tone vertical halves (a lit side and a shadow side). Rooms are not interior floors: they hang off it as small annex buildings and floating islands joined by bridges, such as a domed study, a floating forest, a lantern-like prestige room and a crystal cave at the base. The tower visibly grows taller and busier as you progress, and a village (houses, a clock-tower hall, a totem pole) clusters at its foot.
- **Layered, dithered background.** Parallax mountain ridges and pine-tree silhouettes in two or three tones of one hue, with checkerboard-dithered edges, scattered cloud sprites and drifting sparkles. Depth comes from dithering, not from extra colours.
- **Tiny sprites in swarms.** Spirits (Cloudlings, Druids and others) are small, distinct, colour-matched silhouettes that float around the Orb and the rooms. Seeing dozens of them is the visual "number go up".
- **UI is flat and consistent.** Black panels with a 1-px border in the current accent colour. A bitmap pixel font (Fusion Pixel). A small icon beside every resource (droplet, brain, sparkle, log, totem, house). A left-hand Resources panel. Per-row "Buy" buttons with the cost and its icon beside them. Tooltips in a second bordered box. "Hide Maxed" and "Autobuy" checkboxes. Progress bars for tower requirements and prestige.
- **Blessings screen:** a black starfield with two columns (Milestone Blessings on the left, Unlockable Blessings on the right) in rows gated by point thresholds (5, 9, 13, 18), using round icon nodes in a tree and a tooltip beside the hovered node.
- **Highlight cue:** the selected object gets a thick white outline (the Orb, a room), which makes clickable things readable at a glance.

## 6. Key sources

- Itch page and devlogs: https://barribob.itch.io/tower-wizard
- Steam store: https://store.steampowered.com/app/3372980/Tower_Wizard/
- Steam news (patch notes): https://steamcommunity.com/app/3372980/allnews/
- Developer site: https://barribob.github.io/
- Guides: https://www.kaijuguides.com/games/tower-wizard/guide/complete-walkthrough-and-guide-beat-the-game-in-under-5-hours, https://www.yekbot.com/tower-wizard-walkthrough-guide/, https://fandomwire.com/everything-i-wish-i-knew-before-starting-tower-wizard/
- Review: https://professionalmoron.com/2025/07/13/tower-wizard-game/
- Inspiration: *(the) Gnorp Apologue*

## 7. Gaps

No costs, thresholds, prestige formula or blessing values from primary sources. Itch devlog bodies were only available as summaries. Guide terminology is inconsistent (Cosmic vs Celestial Wall, and the others noted in 3b). The Reddit threads (including a 35-hour no-prestige run) were not reachable.
