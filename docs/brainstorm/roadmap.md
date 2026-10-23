# Roadmap: reconciling the village, persistence/prestige and architecture designs

Written 2026-10-10. Design only; nothing here is built, and `CONTEXT.md` has not been edited. This page does not replace the three designs. It records where they disagree, what blocks what, and a single suggested order. Read the designs for the detail.

| Design | File |
|---|---|
| Village resources (Package B, serious/dread pass) | `docs/brainstorm/village-resources.md` |
| Persistence and Prestige | `docs/brainstorm/prestige-and-persistence.md` |
| Architecture and visual identity | `docs/brainstorm/architecture-and-visual-identity.md` |
| Palette per Generation (grim, contrast and colour-vision verified) | `docs/brainstorm/palette-per-generation.md` |
| A roguelite layer (future; nothing scheduled before Prestige v2 is played) | `docs/brainstorm/roguelite-layer.md` |
| An MCP server for agents (playtesters, developer tooling, agent players) | `docs/brainstorm/mcp-server.md` |
| Multiplayer: climbing together and the withdraw dilemma | `docs/brainstorm/multiplayer.md` |
| Sources | `docs/research/` (the two Nihei architecture reports there are unverified first-pass research and carry a warning header) |

Decisions made by the author: Package B is the base; Trades; the Stockroom; Knowledge (not Lore); the Loadout panel; Salt and Dragon Spores; **Village Tier** written in full (not Standing); **Tally** for the counters with **Ledger** kept as the event log; **persistence and Prestige are in scope**, with a **mandatory Prestige through the Door** and **Exodus** as a third ending; exports **seal** the tower's mutations; tone is serious and dread, with Tower Wizard used for mechanics and pixel technique only; Nihei's architecture shapes the look; a **grim palette per Generation** is wanted (designed separately); a **roguelite layer** is a possible future improvement.

## 1. Conflicts between the designs, and a proposed ruling for each

These are proposals. The ones marked **author** need your decision.

| # | Conflict | Proposed ruling |
|---|---|---|
| 1 | **Tier.** `CONTEXT.md` lists "Tier" as an avoid term under Floor Difficulty. The prestige doc says bare "Tier" means Village Tier and amends that avoid line to "Enemy tier, Floor Tier, threat level". The village and architecture docs write "Village Tier" in full. | **Decided.** "Village Tier" is written in full; Floor Difficulty's avoid line now reads "Tier (on its own...), threat level" in `CONTEXT.md`. |
| 2 | **Ledger words.** The village doc: Tally (never-spent counters) and Ledger (the event log and Expedition Summary). The prestige doc: Campaign Ledger and Lifetime Ledger (the counters). | **Decided.** Tally for the counters, Ledger for the log and the Summary. Totals across Generations: "Lifetime Tally" (proposed name; not in `CONTEXT.md` yet). |
| 3 | **Palette per Generation.** The prestige doc's frontend step lists a palette per Generation. The architecture doc rejected a whole-scene palette swap (Package B forbade it as a reward). | **Decided in favour of a palette per Generation, kept grim.** It is the Tower's state, set at Succession, never a reward. Design: `docs/brainstorm/palette-per-generation.md`: the ramp is a function of Tower Memory and changes only when a Generation begins. Its open decisions (a darker, indigo-tinted night ground so crimson stays visible under protanopia; which colour-distance metric gates it) need an on-screen look in the ink spike. |
| 4 | **The dead.** Architecture dropped its Taint and Remains. The prestige doc parks the Fallen as a mechanic. The village doc has Risen (D1) in Package B and the Pall in Package C. | Only Risen in B, as the village doc defines it. Everything else waits. The Chronicle keeps up to 5 names of lost Units per Campaign. |
| 5 | **Mutations vs Architectures.** Both change Floors. | Mutations are Prestige (per Generation, hidden until met). Architectures are the Floor's structural form. Where both loop a Floor, the Mutation's rule replaces the Architecture's. |
| 6 | **Loot.** Architectures could have changed loot. | Excluded. The Toll's par values and the Quarry Heap both depend on the existing loot formulas. |
| 7 | **Unreachable upper Tower.** The prestige doc shows Floors 70-100 cannot be beaten on the current numbers (Floor 80 Dragon: 35,000 hp, about 180 Rogues, 24,000 Supplies). | An enemy and Supplies scaling pass (E0) blocks Prestige par values, Highest Floor targets and the architecture gameplay levers. |
| 8 | **Wipe consequences.** Risen is the only new loss in B; there is no Trust burn. | **Decided.** Only Risen. |

## 2. Dependencies

- **Persistence** blocks Prestige. It is also needed by the Tally, the Stockroom, Trust, The Count, the Wall of Names and Risen. Every Package B step that adds state must add its snapshot fields (P9 in the prestige doc).
- **Test directory.** `backend/src/test` exists (issue #5). Village tests, determinism tests for the Floor plan and snapshot field-accounting tests build on it.
- **Seeded random streams.** A shared random source exists (#6); a per-Campaign `RandomSource` with three streams (#59) is needed for Architecture plans, Trials, Mutations and reproducible Sandbox runs.
- **Scaling pass (E0).** Needs telemetry from the first playable Package B and Prestige v1, and must follow the Unit depth work (phase 3a), which changes kill speed, damage and the Relic source.
- **Architecture visuals** (the "Section" slice) depend on none of the above. They can proceed in parallel.
- **Architecture gameplay levers** wait on the scaling pass and a balance review, and ship behind `game.architecture.enabled`.
- **Prestige v2 (Mutations)** waits on Prestige v1 being played, to see how samey v1 feels.

## 3. Suggested order

| Phase | Work | Source |
|---|---|---|
| 0 | Glossary edits and two short ADRs (Village Tier, Tally and Ledger, Stockroom), **before** code to avoid renaming twice. Optionally read ch.24-31 and record a prose summary. | prestige doc G0; architecture step 0 |
| 1 | Foundations: `backend/src/test`, characterisation tests for `Village`, actuator and test dependencies, injectable random, all new rates in `GameProperties`, read-only Stock panel with per-source rates. | village step 1; prestige P0 |
| 2a | **Persistence** P1-P8: store port, snapshots, session slots, handshake and key, coordinator, Held Party, migration framework, export/import. | prestige P1-P8 |
| 2b | **Visual "Section" slice**: panel tokens and pixel face, ink shader spike, backend `Architecture` plan (levers off), strip renderer, figures. In parallel with 2a. | architecture steps 1-5 |
| 3a | **Unit depth** (the Unit depth milestone, ADR 0006): four Founders and a gentle first reach, rolled strengths, per-part health with Aim, accuracy and evasion, items found in the Bundle, sanity and phobias. Every random draw uses the seeded streams of #59. Blocks the scaling pass. | `CONTEXT.md` (Bodies, gear and mind); ADR 0006 |
| 3 | **Package B economy**: Trades, Stockroom, Quarry Heap, Tally and Village Tier, Loadout, Salt, Dragon Spores, Trust, Risen slice. Each step adds its snapshot fields. Ship the village diorama with Trades. | village build order; architecture step 8 |
| 4 | **Prestige v1**: Tolls, Succession, Roll, the Door, Tower Memory, Exodus. Then the **scaling pass E0**. Then playtest for sameness and fit par values from logged data. | prestige R1-R6, E0 |
| 5 | **Easing visuals, Shortcut fixtures, camera shots**, then architecture levers family by family behind the flag after the balance review. | architecture steps 6, 9-12 |
| 6 | **Prestige v2**: `TowerBlueprint` and rule hooks, Mutations in two slices, Omens, Pact, Atlas, then Mutation signs, the Door fixture and Generation reseed on the Tower. | prestige R7-R12; architecture step 15 |
| 7 | Hand-drawn art for the 144 Architecture cells, quality levels, balance from telemetry. | architecture 16-17; prestige R13 |
| Later | **Roguelite layer.** Only its small enabling pieces (the single stop predicate, a keyed seed and a per-family rules object) are worth building earlier, because persistence and Prestige need them anyway. The rest waits until Prestige v2 has been played. | `docs/brainstorm/roguelite-layer.md` section 7 |

Rough weight: persistence about six M; Package B about nine M plus the diorama (L); Prestige v1 about two M, one S, one M-to-L, one L; the Section slice about three M; v2 about three L.

## 4. Open decisions, collected

Numbering is by design doc; each doc lists its own recommendations.

1. Tier and Ledger/Tally wording (rows 1 and 2 above).
2. Wipe-side losses in Package B (row 8, decided: only Risen).
3. **Prestige:** is a mandatory Prestige via the Door at Floor 99 acceptable? Is Exodus a good third ending (Defeat is nearly unreachable)? Which reading of the "3-8 hour" target (the whole arc, or each Generation)?
4. Do exports seal the Tower's Mutation data (AES-GCM, key in deployment config), or may a player read their own save?
5. Floor 80 and the Summit: keep the Dragon unless the manga resolves the Summit; keep Floor 100 a provisional Gate.
6. Which wiki-only manga claims may appear in UI copy as manga fact (12 pillars, Floor 14 loop, Floor 80 size, outer wooden stairs, reference pillars)? Recommendation: label them or make no claim.
7. Whether to read ch.24-31 (or let an agent use your browser for blocked sites) to close the manga content gap, or proceed with the broad strokes.
8. Light theme: replace the sky-blue `#9fc0e8` with an overcast ash. A single warm sound (a bell on a Wipe, off by default).

**Decided since:** the palette is the Tower's state, per Generation, never a reward (ADR 0004), and the indigo-tinted night ground (Generation 1 slot 0 `#080923`) is accepted pending an on-screen check in the ink spike; a roguelite Halt stops both clocks; the roguelite Cache gives back 25% as Loot in hand, provisionally, until the Wipe-losses ruling (row 8 above).

**Still open from the other designs:** the roguelite document lists 19 further decisions in its section 10 and the palette document 12 in its closing section; the recommendations in each stand until you say otherwise.

## 5. Added later: the MCP server and multiplayer

Both designs are complete drafts. The MCP server now has issues (#31 to #33, #59 and #63 to #65); multiplayer Stage 0 has #34 to #36. Neither edits `CONTEXT.md`; ADR 0007 (the MCP binding) is written as a proposal and ADR 0008 (grants) is not.

**MCP server (`mcp-server.md`).** A package inside the Spring app behind an `AgentFacade` port that calls `GameService`, with the tools defined once as data so the MCP library can change underneath. Two drivers of time share one tool set: real time for a human's Campaign, and a headless **Sandbox Campaign** that advances only when a driver calls the tick methods, with seeded random streams. Agents hold a scoped, revocable grant and never the Campaign Key; lineage actions (Exodus, Roll refunds, Begin Generation) need approval in the game UI. Scripted bots come before LLM playtesters for the scaling-pass telemetry (#8). Build the Sandbox first, because today a Campaign dies with its WebSocket and only persistence (#9 to #12) lets an agent attach to a human's Campaign. The binding is decided (M0 spike, ADR 0007): Spring AI 2.0.x's stateless server, with a hand-written endpoint as the fallback.

**Multiplayer (`multiplayer.md`).** A ladder in six stages, each already multiplayer on its own:

| Stage | What exists |
|---|---|
| 0 | A sealed **Trial**: the same seeded tower for everyone in their own simulation, with a **Season** board (asynchronous, ordered by connected ticks). Finish Floor 20 for the first Terms. |
| 1 | Presence: others' positions and a Ledger line when someone falls. |
| 2 | Echoes (Risen from other players' dead), private Races at first. |
| 3 | Aid between Parties (the first thing to cut). |
| 4 | **Joint Guardians**: shared fights on Guardian Floors only. |
| 5 | A shared front on every Floor, built only if Stage 4 plays well. |

The withdraw dilemma is worked out with a game-theory table: a Rearguard Share when a Party leaves, an Effort ramp against passengers, a Pressed hold against withdrawing at the worst moment, a server heartbeat with a contact-lost hold. About 68 working days in total, about 60 after cutting Aid and Pacers. It needs the Campaign/Company engine split first.

**Where they collide with decisions already made**

| Conflict | Proposed ruling in the designs |
|---|---|
| Halts (roguelite) stop both clocks, which cannot pause other players | Halts are disabled whenever a Company has more than one Party. |
| ADR 0002 (the Door) vs a race to the top | Door per Party, so the Company is cut at Floor 99. |
| ADR 0004 (palette is the Tower's state) vs a shared tower in view | Each player keeps their own ramp; the Tower's Memory shows by numeral and base marks. |
| ADR 0001 sends the Campaign Key as a WebSocket subprotocol | The multiplayer design recommends sending it in the first message (OWASP) and keeping the subprotocol for version negotiation. ADR 0001 would need an amendment. |
| A Race against Prestige and the Toll | A Race pays a record line and a cosmetic mark only. No Toll bonus (it is farmable with two keys). |
| Undertow on Boot 4 | Settled: the repo moved to Boot 4.1.1 and keeps Undertow through the community starter (ADR 0005); Reactor Netty is the fallback if the starter proves unreliable. |
| Agent seats in a competitive race | The MCP design asks for Standing Orders (auto-Extract on a threshold) for fairness, which softens the dread of manual Extraction. Both designs label agent seats and keep them out of public human races by default. |

**New glossary terms proposed, not added:** MCP: Agent Grant, Controller, Sandbox Campaign, Playtest, Approval, Standing Order, Seat. Multiplayer: Company, Gathering, Race, Terms, Trial, Season, Sprint, Board, Contender, Departure, Threshold, Effort, Joint Guardian, Rearguard Share, Lost-contact rule. Each document lists its own avoid-word choices.

**Decided:** the MCP server comes after persistence, starting with the Sandbox Campaign (the author agreed, 2026-10-10). **Decided:** multiplayer stops at Joint Guardians (Stage 4), for now. Stage 5 (a shared front on every Floor) is not built unless Stage 4 plays badly. The author's reasoning: only huge enemies (Guardians) should pull everyone into one fight; ordinary Garrisons stay hidden from the others and attack only the part of the Tower where a given player climbed.

**Decisions only you can make** are listed at the end of each document (16 for MCP, 29 for multiplayer). The recommendations in each stand until you say otherwise. The two that change the shape of the work most: whether the MCP server is in scope now or after persistence, and whether multiplayer stops at Joint Guardians (Stage 4) or builds the shared front (Stage 5).
