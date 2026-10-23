# Climbing together: multiplayer for Tower Dungeon

Written 2026-10-10. Design only. No repository file was written and `CONTEXT.md` has not been edited. Critic pass applied 2026-10-10: arithmetic recomputed against `application.yml`, `EnemyStats`, `UnitStats`, `GameService` and `GameState`; the withdraw game re-derived; glossary and ADR conflicts corrected.

**Labels.** [code] read in the repo. [measured] from the netcode research's throwaway harness (order of magnitude only). [research] from the verified co-op and netcode reports. [inferred] my arithmetic or reasoning. [Invented] original to this design. No manga fact is claimed anywhere. Anything that borrows the Risen fiction follows Package B D1 and keeps whatever tag it carries there.

**Every number is a placeholder.** `backend/src/test` does not exist, there is no telemetry, and nothing here has been played. Floors 70 to 100 cannot be beaten on today's numbers (prestige doc 3.11), so every figure above about Floor 50 is shown for shape only. The Loot at risk figures in section 4 (7,065 Credits at sell price after Floor 29) are an upper bound: a 12-Unit Party eats 18 Supplies per tick, so a full 220 lasts about 12 ticks, and reaching Floor 29 needs Waves and Shortcuts. A Party that starts at Floor 15 holds 5,365 at the same point.

**Word use.** The title keeps the author's verb. In the body, "climb" is not used as a noun (`_Avoid_` under Expedition). The act of leaving is **Extraction**. "Withdraw" appears only for the dilemma. "Stage" means a build step and never a Floor (`_Avoid_` under Floor). "Depth" is not used for Floors (`_Avoid_` under Floor): the text says high Floors. "Carried loot" is written **Loot in hand**, as in the roguelite document (`_Avoid_` under Loot). Values are written "N Credits at sell price" (Credits, never "credit"). A Party is the Units of one player on the Tower, so players are called players, never "party members". Orderings are written "order" or "table", never "rank" or "win" (`_Avoid_` under Victory).

---

> **Decided by the author (2026-10-10): stop at Stage 4.** Only huge enemies, the Guardians, make everyone fight together. Ordinary Garrisons stay private to the player who climbed that part of the Tower: the other players cannot see them and they do not attack anyone else. Stage 5 (a shared front on every Floor) is out of scope unless Stage 4 plays badly. Wherever the sections below describe Stage 5 or a shared Garrison for several Parties, read it as a deferred option. Open decisions about Stage 5 (the shared Garrison scaling, Echoes beyond Wipes on shared Floors, and the like) no longer need answers.

## 1. What the author asked for

Two ideas, in the author's words:

1. "Create an MCP server so agents can talk to the server."
2. "Add a multiplayer feature where several players can work together to climb the tower by fighting the floor level together, but compete to withdraw from tough battles to prevent losing resources, and see who gets to the top first."

Restated in glossary terms:

| Phrase | Reading used here |
|---|---|
| "work together ... fighting the floor level together" | Several players' Parties fight the same Garrison or Guardian on the same Floor. |
| "compete to withdraw from tough battles" | Each player decides alone when to Extract. Leaving early saves your own Loot and Supplies, but the Garrison does not shrink, so it costs the Parties that stay. |
| "prevent losing resources" | A Wipe loses all Loot in hand (glossary). Units and Supplies are lesser stakes on the high Floors. |
| "see who gets to the top first" | A race to the Summit. This is the hard part. See below. |
| "agents can talk to the server" | Ambiguous. It could mean agents as players, or agents as tooling for the author (inspect, simulate, replay). Both are covered in section 8. Neither was researched. |

**The core tension.** A shared front cannot be a speed race, because nobody outruns a Garrison they share. The three rule angles each resolve it differently (section 2). The design below resolves it with a ladder. The speed race comes first and is parallel. Shared fights come later and are limited to Guardian Floors, so the race continues between them. Only if that plays well does the shared front extend to every Floor, where the contest becomes one of nerve, shown as two columns (Floors Stood, Cost) and never as one number.

**The MCP server was not designed.** No stream produced an MCP design. Section 8 is an inference-labelled sketch and a list of constraints the multiplayer design places on it. It needs its own research pass before anyone builds it.

---

## 2. Three rule angles and the technical constraints

### 2.1 The three angles

| | **A. Roped party** | **B. Parallel race first** | **C. Withdraw dilemma** |
|---|---|---|---|
| Structure | Parties merge into one Garrison or Guardian. | Every player climbs the same seeded Tower in their own simulation. Contact is added in stages. | A rule layer on a shared front. It is not a different structure. |
| What "first" means | Nerve. Floors Stood, with Cost beside it. | Speed. Order of Arrival at a Finish Floor, with a Cost column beside it. | Floors Stood, then Cost, never summed. |
| Where the dilemma lives | A 3-tick channel, a transfer to those who stay, a sealed Guardian Threshold. | Stages 0 to 3: the clock, the Board and the walk back up. Stage 4 onward: the shared rule. | Everywhere. It is the whole design. |
| Cheapest thing it ships | Nothing until the shared engine exists. | A comparable competition from Stage 0, on today's loop. | Nothing alone. It needs a shared Floor. |
| What it gets right | The shared fight is the author's literal ask. | It reuses the persistence design, the Held Party rule and the existing loop unchanged until Parties share a Floor. | The game theory is worked out: a stag hunt with a war of attrition inside it. |
| Main weakness | Cannot be a speed race. Needs the full Company engine. | "Single-player with names" until contact arrives. Full state streams per player cap concurrent Trials at about 20. | Tuning-sensitive. Instant free Extraction would make the dilemma vanish, so every number matters. |
| Main risk | Safe co-op inflates Extraction (ARC Raiders, [research, primary]). | A seeded Tower is solvable by sharing notes. | Mass call at first contact, or a standoff to death. |

**Verdict.** They are a ladder, not rivals. B is the structure that postpones the hard problems. A and C are the rules for the stage where Parties meet. The recommended design is B's ladder with C's leaving rules and A's targeting, scaling and Door rules. Where the inputs disagreed, section 3.9 records the ruling.

### 2.2 Technical constraints that shape every choice

| # | Constraint | Consequence |
|---|---|---|
| 1 | Each connection owns a `GameState` mutated under its own monitor. Both loops run on Boot's single-thread default scheduler (`@EnableScheduling`, no `TaskScheduler` bean). [code] | A shared fight touching several players' state cannot take several monitors. Use single-writer confinement and never run Company steps on that scheduler. |
| 2 | Combat resolves only in the 1 s `gameLoop`. The 10 ms tick only applies Attrition (0.01 hp per step, 1 hp/s per Unit). [code] | The real simulation rate is 1 Hz. A Company can tick at 1 Hz with Attrition applied as `dt`. Times have 1 s resolution. The equivalence test (7.7) must apply Attrition as 100 sub-steps, or accept a documented sub-second difference. |
| 3 | Every client gets a full serialized `GameState` every 100 ms, about 40 to 47 KB, so 400 to 470 KB/s per client. [measured] | About 25 to 30 clients fit on an assumed 100 Mbit/s uplink. A compact 1 Hz frame is required before shared fights, and concurrent Trials are capped at 20 until it exists. |
| 4 | Nothing is replayable. `ThreadLocalRandom` is everywhere, `units` is a `HashMap` keyed by an enum, and ids are random ints. [code] | Seeded Garrisons, golden replays and fair simultaneous Extraction all need an injected `RandomGenerator`, `EnumMap`, and sequence ids first. |
| 5 | Persistence, the Campaign Key and the Held Party rule are designed but not built. Today `closeSession` deletes the `GameState`, so nothing "already stops" offline. [code] | Without them a refresh destroys a Trial. Races ship after the Key handshake. A throwaway prototype is possible earlier. |
| 6 | ADR 0001 (status proposed) freezes the Village offline and holds a disconnected Party. | A shared real-time Floor cannot wait. A Company never holds. Held survives only where nobody else can be wronged. This amends ADR 0001 (task G0). |
| 7 | Boot 3.5 had no OSS patches after 2026-06-30 and Boot 4 drops Undertow. The repo is now on Boot 4.1.1 and keeps Undertow through the community starter (ADR 0005, an unreleased snapshot). [code, ADR 0005] | The server line is settled: the WebSocket layer is built on Undertow and Boot 4.1.1. If the starter proves unreliable, Reactor Netty is the fallback. |
| 8 | The upper Tower is unreachable (E0 scaling pass pending). | Race Finish Floors are limited to Guardian Floors 10, 20 or 30. The Door and the Summit race are blocked. |
| 9 | `enemiesAttack` picks a Unit Type first, then a Unit. [code] | One Porter is as likely a target as 29 Warriors. That is a fairness defect once Parties share a Floor. Make targeting Unit-uniform across the union. |
| 10 | Loot in hand can Recruit mid-Expedition (`getSpendableCredit` = credit + carriedCredits), and REINFORCE draws from the Pantry. [code] | Both are cross-boundary calls. A Company step must never reach into a Campaign. |
| 11 | `backend/src/test` does not exist. | Snapshot-first characterisation tests come before any refactor. |
| 12 | `completeExpedition()` counts every Expedition, including a launch followed at once by Extraction. Shortcuts (Expeditions 3 and 8, kills 25 and 100) and Milestones (Captain at 3 Expeditions, Seasoned Delvers +10% damage at 8) read that counter. [code] | Eight instant launch-and-Extract cycles open the Floor 15 Shortcut, +10% damage and the Captain. A speed Race would reward this first. Trials must count only Expeditions that cleared at least 3 Floors (the Toll's own rule, prestige doc 3.4), or pin Shortcuts in the Terms. |

---

## 3. The recommended design: a ladder in six stages

### 3.0 Shape

| Stage | Name | Players do | What is shared | Extraction |
|---|---|---|---|---|
| **0** | The Blind Trial | Play the same seeded Tower in separate sealed Villages and read a finish table. | Terms and results only. | Unchanged (instant, free) |
| **1** | The Board | See rivals live: marks on the pillar, status, Ledger lines. Spectate. | Public rows at 1 Hz. | Unchanged, now social |
| **2** | Echoes | Meet the named dead of other players. | Records of the dead. | Unchanged |
| **3** | Aid (optional) | Send a Reinforcement Wave to another player. | A command inbox between slots. | Unchanged |
| **4** | Joint Guardians | Fight one Guardian together on Guardian Floors below the Finish Floor. **Blocked on E0 or a Terms-level Guardian override (3.5).** | The Guardian, for the length of the fight. | **Timed, visible, with a Rearguard Share** |
| **5** | The Company | Depart together and share the front on every Floor. | One Floor, one Garrison. | Timed on shared Floors, including when only one Party remains (decision 12) |

Each stage is playable without the next. Stage 3 is the first thing to cut. Stage 5 is built only if Stage 4 plays well.

**Terms.** A **Race** is a competition on shared **Terms**: the rules, the seed, the Finish Floor and the window. A **Contender** is a player's slot in a Race. A **Trial** is a sealed Village played under Generation-1 rules (Tower Memory 0, no Roll), kept in its own file and never touching the player's real Campaign. It is not a Generation: a Generation is one Campaign in a lineage. A **Company** is the players who share a fight and the server object that simulates it. Section 11.3 lists the new glossary terms and the collisions found.

### 3.1 Stage 0: The Blind Trial (shared seed and a finish table)

**Why a Trial and not a real Campaign.** A race on real Campaigns would order hours played and Roll power, not play. A Trial gives everyone the same start.

**Start kit, identical for all** [code, `application.yml`]:

| Item | Value |
|---|---|
| Credits | 10 (buys 3 Warriors: the Recruit loop spends while `spendable > cost`, 10 > 3, 7 > 3, 4 > 3, then 1) |
| Villagers, Food | 20 Villagers, 100 Food |
| Roster | Warrior, Archer, Porter recruitable; the four Founders (Yuva five times stronger, as in Generation 1) already in the reserves, with strengths and phobias drawn from the Terms so every Contender starts with the identical four |
| Roll, Epitaphs, Tower Memory | None. Tower Memory m = 0. |
| Shortcuts, Upgrades, Milestones | None open at the start. They are earned inside the Trial by the usual thresholds, with the counting rule below. |
| Package B systems (Trades, Stockroom, Trust, Loadout) | Whatever has shipped, at Village Tier I rules, identical for all. |
| Mutations (v2) | Sealed in the Terms and the same for all, or none. |

**Terms.** `raceId`, `kind` (SEASON or SPRINT), `planVersion` (pins the Tower plan so old results stay valid), `configHash` (pins the balance config, so results are valid only for the config they were played on), `leversVersion` (pins which Architecture gameplay levers are on; they are off by default, architecture doc 4.1), `seed` (server only, never sent), `finishFloor`, `opensAt`, `startsAt` (Sprint), `closesAt`, `limit`, `maxAttempts`, `maxConcurrent`, and later `echoes`, `aidEnabled`, `jointGuardians`.

**Rules**

1. The Trial plays like Generation 1, with a seeded Tower. Recruit, Upgrade, Depart, Reinforce and Extract are unchanged.
2. **Expedition counting.** For Shortcut and Milestone thresholds a Trial counts an Expedition only if it cleared at least 3 Floors (constraint 12). Otherwise the first to find eight instant Extractions wins the Order.
3. **Finish Floor.** A Guardian Floor, kept separate from `max-floor`. Do not shrink `max-floor`: with `max-floor` 20, Floor 20's base difficulty would clamp to 9 and put a Dragon (5,000 x (3 + 0.05 x 20) = 20,000 hp) on Floor 20. [code] The Trial ends when the Finish Floor's Guardian is slain: the result freezes at that tick, the Party is Extracted, and the Trial is closed. That is the Trial's own Summit Conquered, not a new Expedition Outcome.

   | Preset | Finish Floor | Guardian | Planned time [estimate] | Needs [estimate] |
   |---|---|---|---|---|
   | **Short (first Terms)** | 10 | Dead Soldiers, 52.5 hp, 6.25/tick | 10 to 15 min | A few Warriors and Archers |
   | Standard | 20 | Tower Beast, 160 hp, 15/tick | 35 to 50 min | About 20 mixed Units and 2 to 3 Porters |
   | Long | 30 | Chimera, 315 hp, 31.25/tick | 70 to 100 min | About 40 Units including Mages (Chimera takes x1.5 from Magic and x0.75 from the rest), 7 or more Porters |
   | Blocked until E0 | 40 and above | | | |
4. **Seeded Garrisons.** The Garrison of Floor `f` on the `k`-th arrival (a Start Floor counts as an arrival) is `floorSeed(f, k) = splitmix64(seed ^ f * C1 ^ k * C2)`. The k-th arrival at Floor f draws the same random numbers for every player, so a re-arrival rerolls the same way for all. Combat dice stay unseeded: equal Garrisons, not equal luck. The same split applies to the per-unit draws: the Terms seed the items the tower yields on each floor and the four Founders' rolled strengths and phobias, so every Contender starts with the identical four; combat dice, Aim and miss rolls, who finds an item in a Bundle, and the strengths and phobias of units recruited during the Trial stay unseeded luck. The Garrison is equal **before** Easing and Risen: Easing, which a Trial earns by slaying Guardians, still thins it, and a Wipe's Risen thicken it. That is earned history and is kept. Because Easing changes the Floor Difficulty, the same random numbers can map to different Enemy Types for different players, and the text says "the same draw", not "the same Enemies".
5. **Two ways to run it.**
   - **Season:** open 7 to 14 days. A player starts a Trial whenever they like, with up to `maxAttempts` = 3 attempts, and the best finish counts. The clock is `ticksSinceStart` (lifecycle ticks). Under the persistence design the Village and the Expedition are frozen while nobody is connected (ADR 0001; the current code deletes the state instead), so connected time orders the table and nobody races a calendar. Cap: 90 minutes of ticks. The attempt counter lives in the server's results store keyed by `(raceId, playerId)`, never in the Trial file, and a Trial file cannot be exported or imported, so an older file cannot buy another attempt. The cap `maxConcurrent` limits connected Trials (20 until the compact frame lands), not registrants.
   - **Sprint:** a scheduled start, wall clock from `startsAt`, no pause. A Held Party freezes the Village but not the Race clock. After 20 minutes without a connection the Contender is marked LOST_CONTACT.
   - Ship Season first. It needs no lobby, no scheduling and no live fan-out. Sprint arrives with Stage 1.
6. **Result.** Two columns, no score (the glossary avoids "score"):

   | Column | Meaning |
   |---|---|
   | **Order of Arrival** | By time to Finish. Non-finishers come after, by Highest Floor, then earlier arrival at it. |
   | **Cost** | Always shown beside it, never summed into it: Expeditions, Wipes, Units lost, Loot banked (at sell prices, Materials 2 and Relics 25). The Tower keeps count. |

   The second competition the author asked for, who withdraws well, is the Cost column. It is a report, not a second incentive. The incentive to withdraw well is already in the clock: a Wipe loses the Loot, the Loot buys Units, and fewer Units means a slower Order. Two players with the same arrival time and 12 versus 112 Units lost are told apart on sight.
7. **Ties.** Times have 1 s resolution. Equal times are shown as tied and the Cost column sits beside them but never breaks the tie. The order the sweep visits slots (`ConcurrentHashMap` key order) must never decide a result.
8. **Record.** One plain line in the player's real Campaign, in a **Race record** shown on the Chronicle screen: "Race of 2026-10-24. Floor 20. 3rd of 17. 41:08. 112 fallen." The Chronicle's glossary entry covers ended campaigns only, so this is a new section and a glossary decision (11.3), not a Chronicle entry. Plus a cosmetic pillar mark on the Finish Floor, shown only on Floors the player has discovered. No Legacy, no Toll, no mechanical effect in v1.
9. **Hands and Agents.** A Contender that connects through the MCP endpoint is labelled AGENT by the server and ordered in a separate table (section 8). A script on the plain WebSocket cannot be told from a human, so the separation is declared, not detected.
10. **One Contender per Campaign Key per Race.** Anonymous keys are free, so there is no prize and no global order worth farming. One person with 20 keys can fill `maxConcurrent`; rate limits per IP apply to Race entry.
11. **Results store.** Append-only, rotated JSONL, written atomically. Not a leaderboard.
12. **One connection, one slot.** Entering a Trial detaches the player's real Campaign slot (frozen, as ADR 0001) and attaches the Trial slot; leaving reverses it. The Key handshake therefore needs a slot selector (task R12).
13. The Board shows only a presence count ("14 on the Tower now"), then after Finish the finished table and everyone's per-Floor arrival timeline.

**Server gains:** seeded Tower, a per-Campaign config copy, `finishFloor` rule in `updateInvasion`, `ticksSinceStart`, Terms registry, results store, the Expedition counting rule, `RACE_JOIN` and `RACE_LEAVE`, a `race` block in the planned `GameFrame`. Combat, Loot, Extraction and Wipe do not change.

**How it feels without contact:** the same named Floors for everyone, a table of aliases, and a timeline ("Anaxagoras reached Floor 15 at 18:20, you at 22:41").

### 3.2 Stage 1: The Board (shared presence)

1. **Gathering.** A Sprint has a lobby. A private Race has a host and a 6-symbol invite code; a public Race has a schedule. At `startsAt` every slot activates at once.
2. **The Board.** One row per Contender: alias, Highest Floor, status in words (never colour alone, ADR 0003), a coarse Party band (FEW 1 to 5, SMALL 6 to 15, LARGE 16 to 40, VAST 41 and up), and elapsed time.
3. **Hidden stake, visible danger** [research, R3]. Never sent: Loot, Credits, Vault, Supplies, Loadout, exact Party size. Public: Highest Floor, status, the band.
4. **Ledger lines**, plain, losses written like gains:

   | Event | Line |
   |---|---|
   | Wipe | "Floor 14. The Party of Hesiod was lost. 23 Units." |
   | Extraction | "Thales extracted from Floor 22." |
   | First arrival at a Floor | "Hesiod reached Floor 20. The Guardian stands." |
   | Finish | "Hesiod slew the Guardian of Floor 20. 38:41. 112 Units are dead. The Tower keeps count." |
   | Lost contact | "Anaxagoras was lost to the Tower." |
   | First contact with a Mutation (v2) | "Floor 34. Something there. The Party of Philon did not return." The Mutation stays unnamed. |

5. **Marks on the shaft.** A rival's mark is one short scratch beside the reference pillar's numeral on the rival's Highest Floor. Above the viewer's Highest Floor it appears only as a numeral and initials on the pencil shaft, never as a lamp or a Floor, because lamps are human labour and the unknown above is drawn as an absence (architecture doc).
6. **Names on the Wall.** The Wall of Names gains the last 12 Units lost by any Contender, with the alias.
7. **Aliases.** Party aliases come from a catalogue separate from the Unit and Enemy names. Today `BaseUnit` and `Enemy.nameFor` both draw from Datafaker `greekPhilosopher`, so "the Party of Hesiod" could meet a Unit named Hesiod and a Dead Soldier named Hesiod on the same Wall. Aliases are deduplicated within a Race.
8. **Spectating.** A separate read-only `watchCode` connects without a key and receives the Board and the Ledger lines at 1 Hz, about 4.4 KB/s [estimate], roughly one hundredth of a player frame. "Follow one Party" is not offered until the compact frame exists, and then only as a public projection delayed by 30 s. A Contender who can pause (a Held Party) while watching rivals live gains information without paying clock, so in a Sprint the Race clock keeps running during Hold and the watch view is the delayed projection.
9. **Recorded Pacers (cuttable).** Earlier Season results replayed as labelled "recorded" rows, so a lone player on a quiet day still races a Board.
10. **Cap:** `race.max-concurrent` = 20 until the compact frame lands.

**Server gains:** a `RaceBoard` aggregate written by event and read once per second into one serialized frame, a single-thread emitter, lobby with scheduled start, `watchCode`, Origin allow-list, 64 KB message cap, per-connection rate limits, presence (a hidden tab for 5 minutes detaches the slot).

### 3.3 Stage 2: Echoes (ghosts)

1. When a Party is Wiped on a Floor, the names of the Units that fell there are recorded against that Floor and the Race. A later arrival meets them as **Risen**, using Package B D1: Dead Soldiers, one third of the Units that fell there (rounded up), capped at min(3, floor(30% of the Floor's eased base budget)), never fewer than 1, never above the viewer's Highest Floor, cleared when the Floor is cleared once. **D1's Package B slice is Wipe-only and raises nothing on Guardian Floors.** Echoes from Extractions and from deaths on earlier Floors need D1 in full (Package C, built after Package B is played). Worked cap at Floor 34: budget 76.4, 30% is 22, cap 3, about 4% of the weight. A rival's death warns more than it hinders.
2. The graves belong to the Race, not to a Campaign. No live coupling: B reads only records A has finished writing, in a deterministic order (time of ending, then alias).
3. **Fairness cost.** Echoes make Garrisons depend on who played earlier. Make `echoes` a Terms flag, **off in public Seasons and Sprints, on in private Races**.
4. Optional ghost bands on Toll Floors (Package C, D6): cut unless D6 ships.

**Server gains:** the per-Floor modifier hook in `Tower.buildFloor` (the same hook Risen and Mutations need), a graves store.

### 3.4 Stage 3: Aid (optional)

1. Aid is a Reinforcement Wave addressed to another Contender in the same **private** Race. At most 6 Units and a Supply load. They leave the sender's Reserves and Pantry at once.
2. It appears at the recipient's Base, climbs 1 Floor per tick, drains 1.5 Supplies per Unit per tick, and Merges like any Wave. Units stay owned by the sender and Recall to the sender's Reserves through an idempotent settlement.
3. Loot gathered belongs to the recipient. Credits, Materials and Relics cannot be sent. One Aid per 5 minutes. Off in public Races.
4. **Priced by the real Wave rules** [estimate]: a Wave of 6 drains 9 Supplies per tick, so reaching a Party on Floor 18 needs about 162 Supplies. With two Porters (capacity 100 + 2 x 60 = 220) it arrives with about 58, and costs about 220 Food. Early help is cheap and late help is dear.
5. **Sybil note.** A player with two keys can feed one from the other. Private-only, Units and Supplies only, a cooldown and the cost make it pointless, not impossible.

**Server gains:** a command inbox per slot applied under the receiver's own lock at the start of its sweep (no cross-slot locking), a Unit `ownerPlayerId`, idempotent settlement keyed by `aidId`. Size L. **First thing to cut.**

### 3.5 Stage 4: Joint Guardians (the first shared fight)

Guardian Floors are the natural meeting point. A Guardian pays triple, and the choice to stay or leave is real there. Everywhere else, Floors are still separate, so the speed race continues between Guardians. **Private Races only.**

**One engine.** A Stand is a Company engine instance (section 7) with one Floor and rolling entry. Stage 5 uses the same engine with Departure-together.

**The test-bed problem.** Entry is blocked until a Party has stood 3 ticks (Brace) and a call takes 3 ticks (channel), so a Party cannot leave a Stand in under 6 ticks. The Guardian of Floor 10 dies in about 2 ticks and the Guardian of Floor 20 in about 5 (worked case below). On the Standard Terms (Finish Floor 20) the only Joint Guardian is Floor 10. **No call can complete before the kill at Floors 10 and 20, so the withdraw dilemma never starts there.** The first Floor where a joint fight outlasts the channel is Floor 30 (about 8 ticks for a 40-Unit Party [inferred]), which is above the Finish Floor of every Terms that today's numbers allow. Stage 4 therefore needs one of: the E0 pass, or a Terms-level `guardianHealthScale` that lengthens joint fights (to be set by the E2 simulator). Until then Stage 4 is a plain team fight with no leaving dilemma.

**Rules**

1. **The Stand.** The first Party to reach a Guardian Floor below the Finish Floor opens a Stand. A later Party sees "A Stand is open: n Parties, the Guardian at H" and answers **Enter** or **Pass** within one tick (no answer means Pass). Pass means it fights its own seeded Guardian. Entry closes when the Guardian is below half health (H < 0.5): a late entrant adds 0.75 x base health, so unlimited late entry would let a griefer re-inflate a nearly dead Guardian on the Parties that stayed. A Party arriving after the Guardian is dead fights its own seeded Guardian. No waiting window: waiting costs Attrition, and a 10 hp Unit dies in 10 ticks.
2. **Scaling.** Guardian health x(1 + 0.75(n-1)) for n Parties entered. The Guardian strikes once per Party present per tick (A's `s(n) = n`), where **present** means entered and not yet through the stair. Otherwise per-Party risk would be about half of solo at n = 2. With the strike rule, per-Party exposure is (1 + 0.75(n-1))/n of solo: 0.875, 0.833 and 0.813 for n = 2, 3, 4 [inferred]. Co-op is meant to be 12 to 19% safer here, no more. **Leaving never shrinks the Guardian and it never heals.** H is the Guardian's remaining hp as a fraction of its current maximum, including entrants' additions.
3. **Targeting.** The Guardian picks a random Unit across all Parties present (union, Unit-uniform).
4. **Loot.** Instanced per Party present **at the kill** (the roster as it stands at the end of the killing tick), unchanged formulas (Guardian x3). Paid in the next tick's payout step, so a channel that completes in the killing tick still counts as present. Each Party's fraction is `f = min(1, Effort / (0.5 x highest Effort among Parties present))`. A Party below 0.2 earns no Floor Loot, no Easing credit and no Highest Floor or Toll credit for that Floor. The ramp is relative to the highest Effort, not a cliff at 20% of the mean: the mean falls as passengers are added, so a cheap Party could pass a cliff, and a cliff invites tuning to the edge. **Effort** = hp actually removed from Enemies after Weakness (overkill excluded) + hp actually restored (overheal excluded) + 0.5 x hp lost to Enemy hits (Attrition and Starvation excluded). Multiplied by `L(n) = 1 - 0.05(n-1)`, n being the Parties present at the kill (ARC Raiders: safe co-op needs lower Loot [research]). `loot-yield` (1.0) is the valve. Guardian slain is credited to Easing for every present Party with `f >= 0.2`.
5. **Calling the stair.** A Party leaving a Stand ends its Expedition as an Extraction. Channel: 3 ticks, announced to all, still fighting and taking Attrition and hits. All calls in one tick resolve together before combat. Cancel allowed before completion, then a 5-tick cooldown, and a cancelled call voids the follow window. **Brace:** no call in the 3 ticks after entering. **Pressed hold** (core, not optional): while another Party is Pressed (half its Units dead, or its mean hp under 30%), a channel is held until that Party recovers or is lost, for at most 3 extra ticks. A leaver cannot walk out on a comrade at the moment it goes under.
6. **Rearguard Share** (section 4.4): a transfer of Loot in hand and Supplies to the Parties that stay. H is read at channel completion, not at the call, so the damage the leaver does during its channel counts. No new Loot enters from nowhere.
7. **The Share banks only for those who stand to the kill.** A recipient that calls the stair before the Guardian dies forfeits the Share it received to the Tower ("It stays on the Floor"). This closes the free ride in 4.5 (collect the Share, then leave alone at no Share).
8. **The stair is shut behind a Party that left.** It cannot rejoin that Stand. Leaving cannot be used to scout or cash out.
9. **Lone Party.** If only one Party remains it may call at no Share, still through the 3-tick channel (decision 12).
10. **Contact lost.** The Party fights on under Hold for 10 ticks after contact is declared lost (heartbeat, 7.4). A reconnect cancels it. At expiry it calls the stair on its own, at the same Share as a deliberate call.
11. **The Finish Floor's Guardian is never shared.** "The Summit does not take two." The race is decided alone and ties at the line cannot occur.
12. **Architecture width.** If the Architecture levers are on (`leversVersion`), the Guardian Floor's melee width limit applies per Party, so a second Party is not locked out.
13. **Hand-off.** A Party enters a Stand mid-Expedition, so a third record crosses the boundary (7.1): a **Stand record** (Party, Supplies, Loot in hand, Waves in transit) moved from the slot to the Stand, and a **Stand result** moved back when the Guardian dies (the Party continues in its own simulation) or on a call (the Party's Expedition ends as an Extraction through the normal result).

**Worked case, Floor 20** [recomputed. A Party of 10 Warriors and 10 Archers deals 10 x 1.0 + 10 x 2.25 = 32.5 per tick to the Tower Beast (Archer 1.5 x Ranged x1.5), which hits for 15, so each hit kills an Archer (Melee x1.0) and takes 7.5 from a Warrior (Melee x0.5)]:

| Case | Guardian hp | Ticks to kill | Strikes on the Parties | Units lost per Party (about) |
|---|---|---|---|---|
| Alone | 160 | 5 (4.9 rounds up) | 4 (the kill tick has no strike) | 2 to 3 |
| Two Parties | 280 | 5 (4.3 rounds up) | 8, so 4 per Party | 2 to 3 |
| Two Parties, one calls after tick 1 (its channel runs ticks 2 to 4 and it keeps fighting) | 280 | 5 | 8 | 2 to 3. The leaver deals 4 of the 5 ticks; 20 hp remain for the stayer, who needs 1 more tick. At completion H = 0.07, so the Share is 0.40 x 0.5 x 0.07 = 0.014. |

At Floor 20 the rounding removes the benefit of joining (the joint fight is as long as the solo one) and a call costs the stayer almost nothing. The externality is real only where the fight is longer than the channel (above). The stayer pays for the leaver's exit in time and Units only then, which is why the formula in 4.4 is priced to the pressure and not a flat 25% of Supplies.

### 3.6 Stage 5: The Company (the shared front)

Built only if Stage 4 plays well. The Company Gathers, Departs together and shares the front on every Floor.

**Gathering and Departure**

1. **Open.** From the Village, with no Expedition active or Held, a player opens a Gathering. Server-minted 6-symbol code (32-symbol alphabet, 30 bits), valid 10 minutes in the Gathering, dead at Departure. Join by message over the authenticated connection, never in a URL. Join attempts are rate-limited per IP and per key.
2. **Size.** 2 to 3 players at launch, 4 behind `game.company.max-parties`. A 4-player Company on a Floor 44 Garrison is about 225 Enemies against about 66 solo (budget 115.6 x 3.4 / mean weight 1.75) [inferred], which is a payload and readability problem before it is a rules problem.
3. **Commit.** Each player commits a Party (at least 4 Units from Reserves, and a Unit cost total of at least 40% of the largest committed Party's, shown in the Gathering), Supplies up to their Carrying Capacity, a Loadout, a Start Floor from their own open Shortcuts, and a **Wave stock** (at most 8 Units and one Wave of Supplies). The Company departs from the highest Floor that every player has open, else the Base. The Unit cost floor stops a Party of four Porters from joining to raise the Garrison for the others.
4. **Departure is the commit point** (ADR 0001): each Campaign file is written before the first tick. Closing a tab undoes nothing.
5. **Sealed.** No one joins after Departure in v1. A late joiner would be credited Highest Floor for Floors they did not work.
6. **Host** controls only the Gathering (start, kick). No host migration. If the host leaves the Gathering, it disbands.
7. **Pre-set answer.** At Commit each player sets a default answer for Thresholds (Stand or Take the stair), changeable until the Threshold opens. It is used instead of "no answer". This serves players who cannot answer inside 4 ticks (a fixed duration for everyone, so not a handicap) and removes the incentive to go silent.

**The shared Floor**

| Mechanic | Rule |
|---|---|
| Garrison | One shared Garrison. Budget x`g(n) = 1 + 0.8(n-1)`, set when the Floor is built. n counts Parties that held `f >= 0.5` on the previous Floor (and every Party on the first Floor), so a Party that stops contributing stops raising the Garrison. Guardian health x(1 + 0.75(n-1)), striking once per Party present per tick. |
| Per-Unit damage vs solo | `(g(n)/n)^2`: 0.81 at n = 2, 0.75 at n = 3, 0.72 at n = 4 [inferred]. Co-op is meant to be 20 to 30% safer, no more. At k = 0.6 it would be nearly twice as safe, and should not ship. |
| Enemy targeting | Each Enemy picks a Party weighted by its living Unit count, then a Unit uniformly. A Company of one equals solo. |
| Heal | A Healer's Cast targets a random other Unit on the whole Company. Mana is the Healer's own. Healing counts as Effort only for hp actually restored. A knob restricts it to the own Party. |
| Supplies, Attrition | Per Party, as today. Attrition 1 hp per Unit per tick. |
| Kills | Credited to the Party whose Unit landed the killing blow. |
| Recruit | Loot in hand cannot Recruit inside a Company. |
| Surge Floors | Multiply on top of `g(n)`. They pay nothing extra, so they raise the reason to leave without raising the reason to stay. |
| Risen | None in a Company in v1. The Company Tower is not carried over, and adding Enemies mid-fight for a comrade's Wipe is a rule D1 does not define (D1 adds Risen to a later arrival). |

**The Threshold.** Before every Guardian Floor, the Floor is quiet for **4 ticks**: no attacks, Attrition continues (4 hp per Unit, about 20% of a Warrior). Each Party seals **Stand** or **Take the stair** (or the pre-set answer). At tick 4 all answers are shown together (the Incan Gold simultaneous reveal [research]).
- Those who take the stair leave at once, with no channel and no Share. They harm nobody, because the Guardian is built after the reveal.
- The Guardian is built for the Stand count, so a Party that leaves here adds no pressure.
- **Standing is a promise.** After the Threshold the full Share rules apply, plus the Brace.
- **No answer and no pre-set answer means Take the stair.** An AFK Party cannot inflate the Guardian. Leaving at a Threshold costs nothing but 4 ticks of Attrition, so the Threshold is the one free exit in a Company, and it is not an exploit.
- Surge Floors also get a Threshold only if the author turns it on (about +12% time).

**Easing, Tower Memory, Highest Floor**

| Item | Rule |
|---|---|
| Tower | A fresh Company Tower. Floors are built as the Company reaches them. No Floor is carried over from any Campaign. |
| Tower Memory | The highest `m` among the Parties (cap 4). The Tower answers the oldest lineage. A Generation-1 player with a Generation-4 friend meets +15% Garrison and +24% Guardian health (prestige doc 3.3: x(1 + 0.05m) and x(1 + 0.08m)). The Memory bonus on Guardian Floors above 70 stays capped until E0, as the prestige doc requires. |
| Easing | Starts as the intersection of the players' cleared Guardians. Guardians slain are credited to each present Party with `f >= 0.2`, in that Party's Campaign. |
| Highest Floor, Ascent, Milestones | Per Campaign, on arrival **and only for Floors on which the Party held `f >= 0.2`**. A Party that is carried does not earn Highest Floor, Ascent Marks or Highest Floor Milestones for the Floors it did not work. Otherwise a strong player can tow a second key to Floor 60 for 0.4 Marks per Floor and permanent Milestones. No join in progress. |
| Open Breach, Epitaphs, Heir | Personal. They affect only the owning Party's Supplies, Loot, Start Floor or Units. |
| Mutations, Pact | None in v1. In v2 they are seeded per Company with the Load from the highest Generation. They are not in any player's blueprint, so they add nothing to any Campaign's Toll multiplier `L` (prestige doc 3.4 counts only the Campaign's own imposed and Pact Mutations), and meeting one writes to each present player's Atlas only if the author allows it (decision). |
| Roguelite Halts | Disabled when the Company has more than one Party. A shared front cannot stop two clocks. Orders, Finds and Lessons are Halt-based and are disabled with them. |

**The Door, per Party** (ADR 0002 stays intact). Floor 99 pays once. Parties whose Roll holds Blood of the Door go up to Floor 100 and the Summit Dragon is built for that count. The others stand at the Door: Attrition continues, no fighting, Highest Floor stays 99. They may Extract at no Share ("the Door cut the Rope, not the Party"). Victory is credited only to Parties with the Door open present at the Dragon's death. This is the clearest dread moment of the Company: friends part at the Door. It is out of reach until E0.

**What decides the order (section 5).**

---

### 3.7 What a player sees (all stages)

| Viewer | Own Party | Other Parties | Shared |
|---|---|---|---|
| Player | Every Unit: id, type, hp, Mana. Supplies, runway, Loot in hand, Waves. | A silhouette: alias, Unit counts by type, a 0 to 4 vitality bucket, a status (FIGHTING, CALLING with eta, HOLD with grace left, GONE for an Extraction, LOST for a Wipe), Highest Floor, a Generation numeral, a connection flag. | Floor, Garrison, events. |
| Spectator | None | Silhouettes | Same shared bytes as a player. |

Hidden from everyone but the owner: Vault, Credits, Reserves, Loadout, Loot in hand, Supplies, whether the Door is open, the seed. Danger is public and the stake is private. The server decides; the client never filters. The Generation numeral does hint at the Roll (Blood of the Door needs at least Generation 2); accepted.

### 3.8 Pacing, then what comes after

After Stage 5 plays, the campaign-long **Lineage Race** (shared seed and sealed Mutations across Generations, first Victory first in the table, ordered by connected minutes) becomes possible. It needs Prestige v1, Mutations (v2) and E0, and is last.

### 3.9 Where the inputs disagreed, and the ruling

| Question | Inputs | Ruling |
|---|---|---|
| Name for the players together | Company (research, architecture, C), Rope (A). | **Company.** The prestige doc's "The Second Company" is only Defeat flavour text. Rope survives as a drawing: one line of ink between the lead figures of the Parties, slack while a Party calls the stair, drawn cut when one leaves. |
| Name for the lobby | Muster (research, A, architecture), Gathering (architecture alternative). | **Gathering.** `Muster Roll` is a planned Inheritance and the village doc uses "Muster" as a verb. |
| Name for what a leaver leaves | Cache (A), Rearguard Cache (research, C). | **Rearguard Share.** The roguelite layer already has a Cache (a Cache per Floor, and the Cache that gives back 25% as Loot). |
| Drop rule name | Standing Order (research, A), Lost-Contact rule (architecture). | **Lost-contact rule.** Orders belong to the roguelite layer. |
| Gate | B's "Shared Gate". | Not used. `Door` lists Gate under `_Avoid_`. The stage is "Joint Guardians". |
| Haul | C's ledger name. | Not used. `Loot` lists Haul under `_Avoid_`. Use "Loot banked". |
| Garrison scale k | 0.85 (A), 0.8 (C), 0.75 (architecture), 0.6 (B, Guardian). | 0.8 for the Garrison and 0.75 for the Guardian, with the Guardian striking once per Party. Run the simulator before trusting either. |
| Externality shape | One squared form (A). | **Two shapes.** A fixed Garrison costs the stayers (n/m)^2 (more enemies per Unit and a longer fight). A Guardian that strikes once per Party present costs them n/m (the same strike rate per Unit, a longer fight). The Share uses the shape of the fight (4.4). |
| Loot split | Instanced per Party x L(n) (A, C), a sublinear pot (architecture). | Instanced x L(n), scaled by the Effort ramp. Each Party paid for Units and Supplies it risked. The pot is a decision (section 11.4). |
| Relics in the Share | Exempt (A), included (C). | Included at the same rate. Relics reach the Vault only as surplus items at Extraction, so none are in hand on the stair; the row matters only if a Relic source in hand is added. A decision. |
| Who may collect a Share | Anyone present (C). | Only Parties with `f >= 0.5`, and only if they stand to the kill (3.5 rule 7). A silent tank would otherwise collect a bounty from every leaver. |
| Contact-lost hold | 6 ticks (C), 20 to 30 s (research, A), 20 s (architecture). | **10 ticks after contact is declared lost.** An Archer has 10 hp and a full Supplies load lasts about 12 ticks, so 20 to 30 s is a Wipe sentence. 6 ticks is harsh on a wifi blip. Contact is declared lost by a server heartbeat (7.4). |
| Door | Per Party (A), only if all hold it (architecture). | **Per Party.** The "all must hold" rule makes one player a hostage-taker. |
| Easing | Lowest (A), intersection (architecture), credited only to those present (C). | Intersection to start, credited to those present with `f >= 0.2`. |
| Crash mid-Company | Wipe (prestige doc), refund (architecture). | Refund as `CONTACT_LOST`: Units and Supplies as at Departure, no Loot. The server failed, not the player. Caveat: a refund is a free reroll if a player can trigger the fault, so faults are attributed from the journal (7.6). A decision. |
| Trial vs real Campaign (Stage 0) | Trial in its own file (B), real Campaign with a standard Party (architecture). | Trial. The Standard Party variant has no economy and so no withdraw dilemma. |
| Solo hosting | Company of one on `SoloHost` (architecture). | Keep solo exactly as today until the engine split is proven. |
| Order of a Company | One Place by Effort (A), Floors Stood then Effort (draft). | **Two columns, never summed:** Floors Stood and Cost. Effort is shown, not ordered by (section 5). |

---

## 4. The withdraw dilemma

### 4.1 What is at stake

Reference Party: 6 Warriors, 4 Archers, 2 Porters. It costs 50 Credits (18 + 20 + 12) and carries 220 Supplies (100 + 2 x 60). [config]

| Thing in hand | On Extraction | On Wipe | Size against Loot |
|---|---|---|---|
| **Loot** | Banked in the Vault. | All lost (glossary). | The stake: up to 7,065 Credits at sell price after Floor 29 from the Base (5,365 from a Start Floor of 15). |
| **Units** | Come home hurt. Recovery costs Food. | Lost. | About 0.7% of that. |
| **Supplies** | Leftover returns to the Pantry. | Lost. | At most 220 Food. |
| **Easing, Highest Floor, Tally** | Highest Floor is recorded. | Highest Floor is still recorded. | Easing only if present at the Guardian's death. |

**Finding.** On the high Floors the Units are almost free. Time, Loot and the lasting Easing credit are the real stakes. The dread therefore sits on Loot, on time spent exposed, and on the names left on the Floor, not on the cost of replacing Units.

### 4.2 One Party deciding alone [inferred]

Stay beats leave when `p >= (1 - tau) x A / (A + g)`, with `A` the Loot in hand, `g` the next Floor's pay, `tau` the fraction of `A` that leaving costs, and `p` the survival chance. A row is the Floor the Party stands on, deciding whether to fight the next one: row 9 faces the Floor 10 Guardian.

| Floor | tau = 0 | tau = 20% | tau = 40% | tau = 0, Start Floor 15 |
|---|---|---|---|---|
| 9 | 0.554 | 0.443 | 0.332 | n/a |
| 19 | 0.771 | 0.617 | 0.463 | 0.586 |
| 29 | 0.844 | 0.675 | 0.506 | 0.804 |
| 39 | 0.881 | 0.705 | 0.529 | 0.866 |

With free Extraction a Party must be 77 to 88% sure to stay once it holds Loot from Floors 1 to 19 or beyond. Only a Guardian's triple pay pulls that down (Floor 9 needs 55%). Surge Floors raise the Garrison and pay nothing extra, so they are a reason to leave.

### 4.3 The baseline: instant, free Extraction on a shared Floor

Floor 30 Guardian (Chimera, 315 hp). Two Parties each hold A = 7,065 with g = 1,305. Stay-Stay pays `p x (A + g x L(2))` with L(2) = 0.95; a lone stayer pays `p x (A + g)`.

**Panel 1: p = 0.70 if both stay, 0.25 if one stays alone against a Guardian built for two.**

| Row / Column | Stay | Extract |
|---|---|---|
| **Stay** | 5,813 / 5,813 | 2,092 / 7,065 |
| **Extract** | 7,065 / 2,092 | 7,065 / 7,065 |

Extract strictly dominates, and (Extract, Extract) is also the best joint outcome. But the reason is not the shared Floor. At p = 0.70 a lone Party would not stay either, because the break-even at Floor 29 is 0.844 (4.2). The panel shows that deep Loot makes everyone leave, not that sharing breaks cooperation.

**Panel 2: p = 0.90 if both stay, 0.25 alone.**

| Row / Column | Stay | Extract |
|---|---|---|
| **Stay** | 7,474 / 7,474 | 2,092 / 7,065 |
| **Extract** | 7,065 / 2,092 | 7,065 / 7,065 |

Now Extract is no longer dominant: Stay is the best reply to Stay (7,474 > 7,065) and Extract the best reply to Extract. It is a stag hunt, and (Extract, Extract) is risk-dominant (a deviation from it costs 4,973, a deviation from (Stay, Stay) costs 409). **With the stair free, cooperation never needs trust, and leaving costs the others nothing the leaver pays for.** This is the ARC Raiders warning in numbers [research]. "Extract strictly dominates" holds only while `p_both <= 0.844`.

### 4.4 The rules that create the dilemma

**The externality.** The Garrison does not shrink when a Party leaves. With n equal Parties and m left, the remaining fight costs each remaining Unit P times as much, and P depends on the fight:

- **Garrison Floor (Stage 5).** A fixed number of Enemies each strike once per tick. Fewer Units take the same strikes (n/m) and the fight lasts longer (n/m), so P = (n/m)^2.
- **Guardian (Stage 4 and Guardian Floors in Stage 5).** The Guardian strikes once per Party present, so the strike rate per Unit does not change when a Party leaves. Only the fight's length does, so P = n/m.

| n to m | 2 to 1 | 3 to 2 | 3 to 1 | 4 to 3 | 4 to 1 |
|---|---|---|---|---|---|
| Pressure P, Garrison | 4.00 | 2.25 | 9.00 | 1.78 | 16.00 |
| Share rate, Garrison, H = 1.0 | 0.300 | 0.222 | 0.356 | 0.175 | 0.375 |
| Share rate, Garrison, H = 0.6 | 0.180 | 0.133 | 0.213 | 0.105 | 0.225 |
| Share rate, Garrison, H = 0.2 | 0.060 | 0.044 | 0.071 | 0.035 | 0.075 |
| Pressure P, Guardian | 2.00 | 1.50 | 3.00 | 1.33 | 4.00 |
| Share rate, Guardian, H = 1.0 | 0.200 | 0.133 | 0.267 | 0.100 | 0.300 |
| Share rate, Guardian, H = 0.6 | 0.120 | 0.080 | 0.160 | 0.060 | 0.180 |
| Share rate, Guardian, H = 0.2 | 0.040 | 0.027 | 0.053 | 0.020 | 0.060 |

**The Rearguard Share** prices that externality: `lambda = 0.40 x (1 - 1/P) x H`, where H is the fight's remaining hp as a fraction of its maximum **at channel completion**, P is the pressure of the fight type, n the Parties on the Floor at the call, and m the Parties not calling. The 0.40 is the dial. It is the same shape as the externality, so "you pay what your leaving costs them" can be said on screen. It is a heuristic schedule, not an equilibrium price: a rate is a fraction of Loot, an externality is a multiple of risk, and only the simulator can relate them.
- It applies to Loot in hand (Materials, Credits and Relics) and to 50% of leftover Supplies.
- It is **escrowed at the call and settled at channel completion.** It moves in kind to the Parties that did not call and have `f >= 0.5`, split equally, Supplies capped by the recipient's Carrying Capacity.
- **Banked only by those who stand.** A recipient that later calls the stair before the Guardian dies, or on a Garrison Floor before the Floor is cleared, forfeits what it received to the Tower. A Share paid into the carried Loot of a Party that is then Wiped is lost with it (the Tower keeps it).
- **Recipient cap:** a Party cannot receive more than 50% of its own Loot in hand. Nothing is a gift: it is the only cross-Party transfer and is bounded by what the leaver earned.
- **Follow window:** if every remaining Party also calls within 3 ticks of the first call, all Shares are refunded. A Company that turns back together pays nothing. That is cooperation. A call that is later cancelled voids the window.
- It is a transfer (or, when forfeited, a loss to the Tower), never minted, and it never touches the Vault, so the Vault boundary stays clean.
- Because it shrinks with H, leaving late in a fight that is being won is cheap, and leaving early in a fight that is going badly is expensive. Example, a Garrison Floor: a leaver at Floor 29, H = 0.6, n = 2 to 1 owes 0.18 of 2,475 Materials and 990 Credits, about 1,069 Credits at sell price. The same leaver at the Floor 30 Guardian owes 0.12: about 713.

**What a Share does to the break-even.** With the break-even formula of 4.2, a leaving cost between 18% and 20% of A holds the break-even between about 0.45 and 0.60 at any height of the Tower, against 0.88 at Floor 40 without one. That keeps the dilemma alive across the whole Tower, if the Share is that large. At the Guardian shape and H = 0.6 it is 12%.

### 4.5 The designed game

Floor 30 Guardian, two Parties, A = 7,065, g = 1,305, L(2) = 0.95, mid-fight H = 0.6, Share 0.12 x A = 848 (Guardian shape, 2 to 1). Survival placeholders: p = 0.70 if both stay, 0.30 for a Party alone with a Guardian built for two, 0.90 for a caller covered by the stayers, 0.85 if both call at once (a call is a 3-tick channel). V is the unpriced value of Easing and Highest Floor. Payoffs in Credits at sell price:

| Row / Column | Stay | Call |
|---|---|---|
| **Stay** | 5,813 + 0.70V / 5,813 + 0.70V | 2,765 + 0.30V / 5,595 |
| **Call** | 5,595 / 2,765 + 0.30V | 6,005 / 6,005 |

(Stay beside Call is 0.30 x (7,065 + 1,305 + 848); the caller is 0.90 x (7,065 - 848); both calling is 0.85 x 7,065.)

With V = 500, Stay and Stay pays 6,163 each. Results:
- **Two equilibria, (Stay, Stay) and (Call, Call): a stag hunt.** Staying beats calling when `p >= 0.674` at V = 0 and `0.636` at V = 500 (earlier: 0.623 and 0.588).
- (Stay, Stay) is payoff-dominant from V >= 274. At V = 0 the Call pair wins by 192. The unpriced gains are what tip it.
- (Call, Call) is risk-dominant. Deviating from (Stay, Stay) costs 218 at V = 0 and 568 at V = 500. Staying alone against a calling partner costs 3,240 at V = 0 and 3,090 at V = 500. **Trust and the visible stair record are the coordination device.** The weak point is that deviating from (Stay, Stay) costs only 218: at the Guardian shape and a 0.40 coefficient calling is nearly free. Doubling the coefficient to 0.80 raises it to 981. The coefficient is what the simulator must set.
- **The war of attrition, and the free ride.** Without the forfeiture rule (3.5 rule 7), if A calls and B waits out the channel, B receives the Share and may then leave alone at no Share: 0.85 x (7,065 + 848) = 6,726 against A's 5,595 and the 6,005 both would get by calling together. That makes waiting strictly better than calling together, so nobody calls first. With forfeiture B's payoff for leaving is 6,005 whenever it leaves, waiting buys nothing, and the stall costs only risk. A hard window still has a cliff: a spiteful B who calls at tick 4 instead of tick 3 makes A pay 848 and gains nothing. That is accepted and bounded by the Share; a cancelled call voids the window.

| Rule set | Dominant strategy | What decides |
|---|---|---|
| Baseline (instant, free), p both stay up to 0.84 | Extract. | Nothing. No dilemma. |
| Baseline, p both stay above 0.84 | None. Stag hunt, (Extract, Extract) risk-dominant. | Nothing: leaving is free. |
| Designed, p at least 0.67 | None. Two equilibria. | Trust. |
| Designed, p below 0.67 | Call is the best reply to Stay. (Call, Call) is the only equilibrium. | Who must call first (it costs the first caller about 410 over calling together, with forfeiture). |

All p, V and q here are placeholders. The headless simulator (section 10) is built to replace them.

### 4.6 Staggers

Core (ship with Stage 4):
- **Channel**, 3 ticks, public, interruptible by cancel.
- **Brace**, no call in the 3 ticks after entering.
- **Stair shut** behind a Party that has left.
- **Pressed hold**, up to 3 extra ticks while another Party is Pressed. Promoted from optional: withdrawing at the worst moment is the vector the author's request invites, and the Share is small exactly when H is low, which is when a nearly dead stayer needs the leaver most.
- **Forfeiture** of a Share by a recipient that leaves.
- **Threshold** (Stage 5), sealed and revealed.

Add after simulation shows they are needed (all cuttable):
- **Narrow stair queue**: one Party per 2 ticks in order of call.
- **Cover**: for the length of a channel, Enemies weight the leaver's Units at 0.5, so the stayers draw the fire that would have hit the leaver. This raises the leaver's survival and the stayers' risk, so it must be priced into the Share, or left out.

### 4.7 The dilemma stage by stage

| Stage | What leaving costs |
|---|---|
| 0 | The clock and the walk back up: 130 to 200 s between Expeditions [P&P 3.10], then from the highest open Shortcut at 4 to 6 s per Floor. The Cost column reports banking well. |
| 1 | The Board makes it a bluffing game. A rival banking looks like safety or fear. A Wipe line warns which Floor is deadly. |
| 2 | A Wipe is no longer private. It leaves named dead for the next player, so Extracting keeps the Tower cleaner for everyone. |
| 3 | Aid costs real Food. Giving it slows you. |
| 4 | The first stage where leaving harms others directly: a timed, announced channel, a Share, a Guardian that does not shrink. Only where the fight outlasts the channel (3.5). |
| 5 | The same rule on every shared Floor, plus the sealed Threshold. |

### 4.8 Guard rules: griefing, collusion, abandonment

**Griefing**

| Vector | Rule |
|---|---|
| Harming another Party | There is no inter-Party damage. The only levers are timing, absence and bulk. |
| Extraction to hurt stayers | The Share transfers to the stayers, so leaving costs the leaver and compensates the stayers. |
| Withdrawing at the worst moment | A Pressed Party holds the channel for up to 3 extra ticks. H is read at completion. The Share is small at low H, so the guard is the hold, not the price. |
| Entering at the worst moment | A late entrant adds 0.75 x base health and a strike per tick. Entry closes below H = 0.5, and an Enter is a choice. |
| Peek then leave | Brace, free exit only at a Threshold (sealed), stair shut behind a Party that has left. |
| Bait call (call, let others follow, cancel) | A cancelled call voids the follow window, and followers can cancel their own calls until completion. Cost to the followers: ticks. |
| Cash out and rejoin as a Wave | Not allowed. A Wave Merges only into its own Party and a Party that left cannot re-enter. |
| Stalling | No pause exists. The only holds are the Threshold (4 ticks, fixed) and a Pressed hold (at most 3 extra ticks). |
| Call spam to hog the queue | A 5-tick cooldown after cancel. |
| Diluting the Garrison | `g(n)` counts only Parties with `f >= 0.5` on the previous Floor. A Commit needs a Unit cost floor. A passenger raises the Garrison for one Floor at most. |
| Free text | None. Aliases come from their own catalogue (3.2). Emotes are 8 fixed phrases ("Holding", "Falling back", "Need hands", "Wait", "Going on", "Wounded", "Pressed", "Alone"; placeholders), 1 per 2 s and 8 per minute. |
| Host abuse | The host can kick only in the Gathering and has no other power. |

**Collusion**

| Vector | Rule |
|---|---|
| Mass call to strand one Party | The Share grows as m falls (0.30 x H at Guardian 4 to 1, 0.375 x H at Garrison 4 to 1) and goes to the stranded Party. The lone Party may call free. The Garrison does not heal, so the strand is bounded to the fight already begun. The callers pay and gain nothing. |
| Dummy-key funnel | The Share only moves what the leaver earned in this Company Expedition. A recipient takes at most 50% of its own Loot in hand and only if it has `f >= 0.5`. No gifts of Credits or Materials. Residual: one person can shuffle value between their own keys, which creates none. |
| Riding on others' damage | The Effort ramp (3.5 rule 4): `f = min(1, Effort / (0.5 x highest Effort))`. A Party at 4 damage per tick beside one at 32 earns 0.25 of the Floor Loot, not the full instanced pay. Relative to the mean, as drafted, a tiny Party passed, because the mean falls as passengers are added. |
| Multiplying Loot with several keys | Loot is minted per Party, so one operator running k keys in a Company earns about k x L(k) times the Loot per Floor, for k x (g(k)/k) times the fighting. With g(k)/k near L(k) (0.85 at k = 4) the Loot per unit of fighting is about solo, so the gain is attention, not yield. It is bounded by the ramp, by one Contender per key and by the lack of a prize. Revisit before any Toll bonus exists. |
| Kill stealing | None. Loot is minted per Party, so nothing is contested. |
| Sybil farming | Anonymous keys are free, so there is no prize and no global order. Credit only the deeds that happened. |
| Boosting a weak Party past Guardians for Easing, Highest Floor and Ascent | Gated by `f >= 0.2` for Easing, Highest Floor, Ascent and Milestones (3.6). Not prevented for a Party that contributes just enough. |
| Toll bonus for place | Cut (section 5.2): two keys in one Company guarantee first and second. |

**Abandonment and AFK**

| Vector | Rule |
|---|---|
| Disconnect | The lost-contact rule: contact is declared lost by a server heartbeat, then Hold 10 ticks, a reconnect cancels, then the Party calls the stair at the same Share. Newest connection wins (close code 4001 for the old one). |
| Deliberate disconnect to dodge the Share | The lost-contact call pays the same Share, so pulling the plug buys no discount. |
| Hidden tab | 60 s hidden counts as lost contact inside a Company, but `PRESENCE` is a client signal a script can fake. The server also uses the heartbeat. |
| **Silent but connected (the AFK exploit)** | A Party that only fights is not harmless. It is paid Loot automatically, it collects Shares from every leaver as a "stayer", and it raises the Garrison for the others. Fixes: Effort ramp, a Share is paid only to `f >= 0.5`, `g(n)` counts only `f >= 0.5`. |
| AFK at a Threshold | The pre-set answer, else Take the stair. |
| Server restart | Graceful shutdown auto-Extracts every live Party. A crash settles as `CONTACT_LOST` (refund, no Loot). |
| Fault as a free undo | A Party about to be Wiped could try to trigger a Company FAULT to get a `CONTACT_LOST` refund. Faults are attributed from the command journal. A fault whose last command came from one key counts against that key, three in 24 hours settle as a Wipe, and a single such fault settles as a Wipe for the offending key only. |
| Reputation washing | Accepted. A stair record (`stood N, called N, contact lost N`) is shown beside each alias in the Gathering. It has no mechanical effect and a new key resets it. |

### 4.9 A short play-through, Floor 29 to 31

Three Parties stand at the Floor 30 Threshold. The Chimera is scaled 315 x (1 + 0.75 x 2) = 788 hp.
1. Ticks 0 to 4: dormant. All three Stand. Attrition costs 4 hp per Unit.
2. Tick 8 (Brace done): the Guardian is at H = 0.70. Hypatia's Party is Pressed (4 of 12 Units dead).
3. Tick 9: Zeno's Party calls. n = 3, m = 2, Guardian shape: `lambda = 0.4 x (1 - 1/1.5) x 0.70 = 0.093`. At completion H may be lower, since Zeno fights through the channel, and the Share is read then. At H = 0.70, Zeno owes about 554 Credits at sell price (231 Materials, 92 Credits) and half its leftover Supplies. Hypatia is Pressed, so the stair holds, up to 3 extra ticks.
4. Tick 13: Hypatia's Party is lost (a Wipe) while the stair is held. Its Loot in hand is lost, and the Pressed hold ends. Zeno's channel completes at once. The Share goes to Crates's Party alone, subject to the 50% cap. Zeno's stair is shut. Crates's Party is the only Party left and may call at no Share.
5. Crates stays, clears, and is paid 1,305 x L(1) = 1,305 (n is the Parties present at the kill) plus the Share plus Easing.


---

## 5. The race

### 5.1 What decides the order, by stage

| Stage | What decides it |
|---|---|
| 0 to 3 | **Order of Arrival** at the Finish Floor, with the Cost column beside it. A speed race, so a real race. |
| 4 | The same. Joint Guardians sit on the way and may cost time or Units, but the Finish Floor's Guardian is fought alone. |
| 5 | Two columns, never summed. **Floors Stood** (the last Floor the Party was present on, with Summit Conquered above all). **Cost** (Loot banked, Units lost, Share given and received). Equal Floors Stood are shown as equal. There is no single Place. |

A Company is not ordered by Effort at the Dragon's death, nor by Floors Stood and then later departure tick, because that rewards the wrong things. Effort would turn the Summit into a damage meter that favours whoever brought the most Units. The last Floor stood plus "later tick" would place a Party Wiped at tick 300 above one that Extracted at tick 280, which would make withdrawing strictly worse in the order and remove the author's second competition. "First to the Summit" has no meaning on a shared front: every Party present arrives at the same tick. Everyone with the Door open present at the Dragon's death conquers it together. Effort is shown beside the columns and orders nothing.

**Everyone with the Door open may reach Victory.** The race is non-zero-sum. Nothing is ordered across Companies. There is no global board.

### 5.2 What the record pays

- A plain Ledger entry for every Party (it is an Expedition summary, not a Chronicle entry): "Company of three, to Floor 31. Crates stood to the end. Zeno called the stair on Floor 30. Hypatia fell on 30." No banner, no sound.
- **Company Expeditions feed the Toll** through the existing hooks (Expeditions completed if at least 3 Floors were cleared while present, Guardians slain and Ascent only for Floors with `f >= 0.2`). Nothing in the Toll's formulas changes.
- **No Toll bonus for first or for place (recommended).** The Toll already pays Ascent and Guardians. A bonus is farmable: two keys in one Company guarantee first and second. If the author wants one, cap it at 4 Marks per Campaign (a little under half a Legacy against a first Campaign's `S` of about 38 Marks, so 4/9 = 0.44), only for Companies of at least two keys that cleared at least 3 Floors together, ordered by Floors Stood (decision, 11.4).
- **Stage 0 to 3 Races pay nothing mechanical** (3.1, rule 8).

### 5.3 How the Door, Prestige, Generations, Mutations and the palette fit

| Element | In a Race (Stages 0 to 4) | In a Company (Stage 5) |
|---|---|---|
| **Door** | Out of range. Finish Floors are at most 30. | Per Party. Without Blood of the Door a Party stands at Floor 99 and the Company is cut there. Pays Floor 99 once. |
| **Prestige, Exodus** | Outside it. No Legacy, no Roll, no Exodus. Exodus is unavailable while an Expedition is out. | Feeds the Toll as above. Exodus requires no Expedition active or Held, which now includes in a Company. Succession cannot happen while an Expedition is in a Company. |
| **Generations** | A Trial is always under Generation-1 rules and Tower Memory 0, so every player sees the same ramp of difficulty and the same Tower. | Mixed. Tower Memory is the highest Generation. A Generation-1 player with a Generation-4 friend meets +15% Garrison and +24% Guardian health. The Board shows each player's Generation numeral. |
| **Roll, Inheritances** | None in a Trial. | Personal. They alter only the owner's Party. Tower-level Inheritances do not apply. |
| **Mutations (v2)** | Sealed in the Terms and the same for all, or none. First contact is a Ledger line that does not name the Mutation. | None in v1. Later seeded per Company. They pay no Load into any Toll. |
| **Omens, Pact** | None. | Pact adds a Mutation to the next Generation of one player's Campaign, so it does not apply inside a Company. |
| **Palette per Generation** | **Conflicts with ADR 0004.** ADR 0004 says the ramp follows the Tower's state, changes only when a new Generation begins, and "cannot be earned, bought, chosen, previewed or reversed", and that the ground only gets darker. A Generation-4 player entering a Trial would see the lighter Generation 1 ramp, which is a reversal. | **Same conflict.** A Company that takes "the ramp of the darkest Generation present" recolours a Generation-1 player's scene at Departure and reverses it at settlement, and ADR 0004 says new variety "must not recolour the ramp". |

**Palette ruling.** Recommended: no recolour. A Trial and a Company keep each player's own ramp, and the Tower's Memory is shown by numeral and marks at the base, which ADR 0004 already uses so that Generation never relies on hue. The alternative needs ADR 0004 amended ("the ramp is the Tower in view") and a decision. The palette is cosmetic and the palette document verifies contrast, so no fairness argument needs a shared ramp.

**Lineage Race** (campaign-long): a Race-scoped record per Contender with its own Roll and Legacy, the Terms' seed for every Generation 1, the same sealed Mutations, the same three Omens at each Succession and a Pact chosen by each player. Order of Victory orders connected minutes. It needs Prestige v1, Mutations and E0, and is last.

### 5.4 Shared versus personal

| Shared (one copy per Race or Company) | Personal (each player's own) |
|---|---|
| The Terms and the seed. | The Village, Pantry, Trades, Stockroom. |
| The Tower plan and Architectures (a pure function of the seed, with palette levers off and `leversVersion` pinned). | The Vault, Credits, Reserves, Roll, Trust. |
| Floors and Garrisons (seeded). | The Party, its Units, hp, Mana. |
| The Guardian during a Stand or Company fight. | Supplies, Carrying Capacity, Loadout. |
| The event log and, in a Company, the tick counter. | Loot in hand, Waves, Reinforcement stock. |
| Easing set and Tower Memory inside a Company. | Easing and Memory in the Campaign. |
| Public silhouettes. | Tally, Chronicle, Race record, Milestones. |

---

## 6. Loot and resource rules

| Item | Rule |
|---|---|
| Floor Loot | Unchanged formulas: 5f Materials, 2f Credits, +3 Supplies, no Relics, all x3 on a Guardian Floor. Paid to each Party present, once per Floor (a per-Floor `settledFor` set), scaled by the Effort fraction `f`. In `updateInvasion` Loot is paid in the tick after the last kill (the "garrison is empty" branch), so "present" is read at the kill (3.5 rule 4). |
| Sharing multiplier | `L(n) = 1 - 0.05(n-1)` in a Stand or Company, n being the Parties present at the kill. `loot-yield` is the single valve: if telemetry shows co-op Parties extract more or bank more per Floor than solo, lower it (try 0.9) before touching `g`. |
| Relics | Not paid by Floors: they come from surplus items at Extraction, so there is no relic pot to share. |
| Supplies | Per Party. 1.5 per Unit per tick against the Party's own Carrying Capacity. Starvation per Party. Floor Supplies (+3) not scaled. |
| Mana, Heal | Mana is personal and never shared. Heal targets the whole Company by default. |
| Reinforcement Waves | Per Party. A Wave carries its own Supplies, climbs from the Base and Merges only into its own Party. In a Company they draw only on the stock committed at Departure. REINFORCE sends through an **outbox**: the Campaign debits Units and Food, records the Wave, and posts it. At settlement the Campaign returns any Wave the result does not mention. In a Stand a Wave in transit travels with the Stand record (7.1). |
| Recruit from Loot in hand | Not possible inside a Company. Solo keeps it. A rule difference, flagged in section 11. |
| Gifts | None. Units, Supplies and Loot never pass between Parties, except by the Rearguard Share (and Aid in private Stage 3 Races, Units and Supplies only). |
| Vault | Touched only at settlement. The Share moves Loot in hand, never Vault contents. |
| Quarry Heap, Trust, Salt, Dragon Spores | Quarry Heap delivery rides settlement. Loadout travels in the Departure record. Trust (Package B R20) is personal: earned only on a Party's own Extraction from its own survivors, and a Wipe neither earns nor burns any of it. The Share moves no Trust. |
| Wipe | All Loot in hand of that Party is lost. Its Highest Floor still counts where `f >= 0.2` (3.6). A Wipe does not end the Company. Only a Party's own Expedition ends. |
| Risen on a Wipe | In a Race Trial: Package B D1 as written (Wipe only, the Floor where the Party fell, never on a Guardian Floor, 15 hp and 5 damage Dead Soldiers). In a Company: none in v1 (3.6). |
| Easing | Credited to each Party present with `f >= 0.2` at the Guardian's death. A leaver forfeits it. This is the strongest non-Loot reason to stay. |
| Highest Floor | Recorded on arrival, kept through a Wipe, 0.4 Marks per Floor above baseline, in a Company only for Floors with `f >= 0.2`. |

**Why Loot is paid per Party and not as a pot.** Paying a pot of `(1 + a(n-1))` split equally mints less than paying every Party in full. Paying every Party mints n times the economy, which an operator with several free keys can farm. The counter is that each key must also supply its own Village, Units and Supplies, the Effort ramp pays a rider in proportion to its work, and the Share is the only transfer. If telemetry disagrees, switch to the pot.

---

## 7. Technical architecture

### 7.1 The split: Campaign vs Company

Today `GameState` fuses the Village, the Party and the Tower. After the split:

| Part | Owner | Writer | Lifetime |
|---|---|---|---|
| **Campaign** | Per player: Vault, Village, Reserves, Upgrades, Milestones, Tally, Prestige `meta`, persisted as the player's file. | The player's slot. | Years |
| **PartyState** | Per player, per Expedition: Units on the Tower, Supplies, Loot in hand and Credits in hand, Waves, counters. | The Company thread (or the slot, in solo). | Minutes |
| **TowerRun** | One per Company: Floors, Garrison, Guardian, tick counter, event ring, one seeded RNG. | The Company thread. | Minutes |

`Tower.clearedBosses` (Easing) moves into the Campaign. A Company gets a Company-local set. The `checkGameOver` hazard: after the split the Campaign has no Party, so `hasAnyUnits()` must also be true while `expedition.phase != NONE`, or a player whose whole force is inside a Company can be declared in Defeat. Test it.

Three immutable records cross the boundary, passed by move and never shared:
- **Departure record** (Campaign to Company): chosen Units moved out of Reserves, Supplies drawn, the Wave stock, Damage Multiplier and Carrying Capacity bonus, Loadout, the open Start Floors, and whether the Roll holds Blood of the Door.
- **`ExpeditionResult`** (Company to Campaign): outcome (Extraction, Wipe, Summit Conquered, or `CONTACT_LOST`), survivors, Supplies left, Loot and Credits to bank (zero on a Wipe), counters, Highest Floor, Guardians slain, Share given and received, Effort.
- **Stand record and Stand result** (Stage 4 only): the mid-Expedition hand-off of a running Party into a Stand and back. Without it a Party that wins a Stand could not continue its Expedition in its own simulation.

`Campaign.applyExpeditionResult` must be the **single Extraction path** for solo and Company. Every Package B and Prestige hook attaches there once. Settlement is idempotent, keyed by `(companyId, playerId)`, and clears the Campaign's `expedition.companyId` atomically.

**Lock-order invariant** (architecture test): Campaign code reaches a Company only through `executor.execute(...)`. The Company thread never calls a Campaign slot synchronously: it posts the result to the slot's own inbox, and the slot applies it under its own monitor. A synchronous call would let a slow serialization of a 40 KB frame under a player's monitor, at 10 Hz today, hold up the whole Company's tick. The graph is acyclic, so no deadlock is possible. Settlement for a slot that has been evicted loads the file and writes it through the store with `expectedSeq`.

### 7.2 One engine for solo and Company

A pure `ExpeditionEngine.step(...)` serves both. Solo runs on a `SoloHost` on the player's own slot, so nothing regresses. A Company of one is identical to solo by construction, and an equivalence test proves it. Keep solo as it is until the engine split is proven.

**Step order** (1 Hz, game time per step always 1.0 s):

1. Drain the inbox. Commands in canonical order: EXTRACT_CANCEL, EXTRACT, COMMIT, modifier changes, AddWave, REINFORCE, emotes, then by player. Same-tick commands are order-independent.
2. Attrition (1 hp x `dt`, applied as 100 sub-steps of 0.01 if the equivalence test is to be byte-exact) on every Unit, then remove the dead. Moved from the 10 ms ticker.
3. Per Party, in stable order: drain Supplies, recharge Casters, advance Waves, apply Starvation, check Wipe.
4. If the Garrison is empty: pay Loot per Party (present at the kill, scaled by `f`), mark a Guardian slain, Summit and Door checks, build the next Floor once, record Highest Floor, and return with no combat this tick.
5. Otherwise Healers first, then every Unit attacks, then every Enemy attacks.
6. Advance channels. Complete them in player order, then apply the Share.
7. Advance lost-contact timers, check Company end, build events.
8. Emit: serialize the shared block once, then each player's private block.

Simultaneous Extraction is fair because commands are accepted into tick N+1 with an acknowledgement carrying `appliedTick` and are resolved together.

### 7.3 Threading

| Thread | Owns |
|---|---|
| I/O thread (Undertow today) | Parse, validate, rate-limit and route a command. Never touches a world. |
| K single-thread `ScheduledThreadPoolExecutor`s, `K = min(4, max(1, cores/2))` | Every Company pinned to it: commands, steps, serialization, sink emission. |
| Campaign slot (existing monitor) | Campaign mutations. Never calls a Company synchronously. |
| Lifecycle scheduler (today's single-thread default) | The Campaign's 1 s lifecycle only. |

- Each Company is pinned to the executor with the fewest Companies for life.
- **Catch `Throwable` around every step.** A periodic task that throws is silently cancelled. On a fault: log `(companyId, seed, tick, journal tail)`, mark the Company FAULTED, settle every player as `CONTACT_LOST`, and free it.
- Self-reschedule against a fixed epoch. If a tick is more than 250 ms late, re-anchor instead of bursting. The simulation stays identical for every player under overload.
- Outbound: `byte[]` frames, a latest-only slot per connection with `onBackpressureLatest()`. A slow client skips frames and is never disconnected for being slow. Sinks are emitted only from the Company thread.
- Pre-warm a throwaway Tower at boot: the first Floor build in a cold JVM took 16.5 ms [measured].
- Capacity [inferred, to be load-tested]: one executor can run on the order of 1,000 Companies at 1 Hz in CPU terms. `company.max-active` defaults to 100.
- One process, one instance. Companies and Boards are in memory and cannot be spread over several instances, consistent with ADR 0001's single small instance.

### 7.4 Protocol and frames

Transport stays one WebSocket at `/api/ws`. Replace the static `switch` with a `CommandRouter`: one codec per action, central validation, rate limiting and per-message `try/catch`. A bad payload yields a `notice`, never an exception.

New client messages: `RACE_JOIN`, `RACE_LEAVE`, `COMPANY_CREATE` (opens a Gathering), `COMPANY_JOIN {code}`, `COMPANY_COMMIT` (including the pre-set Threshold answer), `COMPANY_READY`, `COMPANY_KICK` (Gathering only), `COMPANY_LEAVE` (Gathering only, in flight leaving is Extraction), `COMPANY_DEPART`, `EXTRACT`, `EXTRACT_CANCEL`, `STAND_ENTER {enter}` (Stage 4), `COMMIT {stand}` (Threshold), `EMOTE {id}`, `PING`, `WATCH {watchCode}` on `/api/ws/watch`. (There is no `RULE {onDrop}` message: the lost-contact rule is fixed.)

**Heartbeat.** Contact is declared lost by the server, not by the OS: a `PING` from the client every 2 s, lost after 6 s of silence, because a half-open mobile connection can look open for minutes. The 10-tick Hold starts then, so the whole path to an automatic call is about 6 + 10 + 3 ticks.

Server frames carry `kind` (absent means the current campaign frame, for compatibility): `campaign` (on change, at most 1 Hz), `company` (shared bytes, 1 Hz), `own` (private, 1 Hz), `roster` (on change), `ack`, `notice`. A reconnecting client gets a full frame at once.

**Compact frame** [measured, synthetic]: 4 Parties of 30 Units, a 58-Enemy Garrison, 3 events: shared block 1,582 B, private block 1,284 B, frame about 2.9 KB, **1.1 KB gzip**, against the 40 to 47 KB at 10 Hz today. Static stats leave the frame and are fetched once from `/api/unit-stats/` and a new `/api/enemy-stats/`. Serialize once per Company per tick and send the same shared bytes to every player and spectator.

| Scenario | Rate | Uplink [inferred, assuming 100 Mbit/s] |
|---|---|---|
| Today, one client | 400 to 470 KB/s | 100 Mbit/s holds 25 to 30 clients. |
| Today's frame at 1 Hz | 40 to 47 KB/s | 260 to 310 clients. |
| Compact frame at 1 Hz | about 2.9 KB/s per player, 1.6 KB/s per spectator | 100 Companies of 4 players plus 8 spectators each, about 19 Mbit/s. |

No delta compression in v1. Frames are full, idempotent snapshots tagged `tick`. State carries all meaning and events are cosmetic (the last 3 ticks, de-duplicated by `(tick, idx)`). Animate Attrition on the client by dead reckoning from `tickMs`.

### 7.5 Identity, invites, spectators

- Identity is the Campaign Key. The wire uses an ephemeral per-Company `memberId` (an engine word) and an alias from its own catalogue (3.2), seeded from `(companyId, ordinal)` and deduplicated.
- **Newest connection wins** (close code 4001). Add 4101 COMPANY_FULL, 4102 COMPANY_ENDED, 4103 CODE_INVALID. Use 1008 for policy and 1009 for oversize messages.
- Invite code: 6 symbols from the 32-symbol Crockford alphabet, 30 bits. With 100 live Companies a blind guess hits at about 1e-7 [inferred]. `watchCode`: 8 symbols, read-only, anonymous, at most 8 spectators per Company and 4 per IP.
- The transport for the Campaign Key (subprotocol, as the prestige doc chose, or first message, as OWASP recommends) is an author decision.

### 7.6 Disconnects, restart, persistence

- Company of one, including a Company reduced to one Party whose player drops: the Held Party rule applies, because nobody can be wronged. A Race clock still runs.
- Departure is **two-phase with no nested locks**: for each player in order and one at a time, `prepareDeparture` under that slot's own monitor, write `PREPARED(companyId)` durably, return the record. If any fails, compensate the prepared ones (idempotent). Then create the world and flip to `IN_COMPANY`.
- No Company state is persisted mid-flight in v1, beyond a command journal (`data/companies/journal-YYYYMMDD.jsonl`, rotated, 7 days, 50 MB cap, about 120 KB for a 30-minute 4-player Company [inferred], a player id truncated to 8 hex characters and never a Key).
- Graceful shutdown auto-Extracts every live Party. Whether Undertow graceful shutdown waits for upgraded sockets is unverified, so nothing depends on it.
- Crash or FAULT: settle on attach as `CONTACT_LOST` (Units and Supplies as at Departure, no Loot, no Toll credit). ADR 0001 and the prestige doc say "there is no free reroll" for a crash after Departure, so this is a deliberate exception: the server failed, not the player. It becomes a free undo only if a player can cause a fault, so faults are attributed from the journal (4.8).
- Later: a Floor-clear checkpoint turns Contact lost into a `HELD_ALL` restore.
- The Campaign clock stays frozen offline, except for the Party inside the Company, which is committed. The UI must say so.
- Campaign import is refused (409) while an Expedition is active, Held or in a Company; a Trial file is not exportable.

### 7.7 Determinism and tests

**Prerequisites:** an injected `RandomGenerator` and a `SeedSource` replacing every `ThreadLocalRandom` (Tower, TowerFloor, Enemy, BaseUnit, GameState, GameService), `EnumMap` for `units` and `unitsOnTower`, sequence ids per Company, seeded name generation, a per-Company `TowerRules` copy.

**RNG model:** Company randomness is keyed per tick, `SplittableRandom(mix(seed, "tick", tick))`. The Garrison is keyed `mix(seed, "garrison", floor)`. For Races, the key is `floorSeed(f, k)` (3.1). No RNG state is persisted.

| Test | What it proves |
|---|---|
| Snapshot-first transcripts | Before the refactor, run today's `updateInvasion` with a stubbed RNG for 20 seeds x 200 ticks. After the split, `SoloHost` reproduces them byte for byte (Attrition as 100 sub-steps). This is the acceptance gate for the engine split. |
| Golden replays | `(seed, departures, command script)` to a hash of the event lines. |
| Permutation | Shuffling same-tick command order gives the same result. |
| Properties | No Unit in two places. Loot conserved (Share in equals Share out plus forfeited). Settlement exactly once per `(companyId, playerId)`. Garrison scaling monotone in n. A Party that never attacks earns no Loot and collects no Share. `hasAnyUnits()` holds while a Party is out. |
| Equivalence | A Company of one on a Company host equals `SoloHost`. |
| Canary | A private field in `Campaign` never appears in any `company`, `own` or `roster` frame. |
| WebSocket integration | Handshake, 4001 replacement, `doFinally` cleanup, Gathering, Depart, drop, grace, auto-Extraction, 1009. Includes the suspected clean-close leak. |
| Load | 100 Companies x 4 fake clients x 60 s in process. Targets: step p99 under 5 ms, tick lateness p99 under 50 ms. |

### 7.8 Refactors required first

| # | Refactor | Why it blocks |
|---|---|---|
| R1 | `backend/src/test`, test dependencies, characterisation transcripts for `updateInvasion`, `unitsAttack`, `enemiesAttack`, Waves, Extraction. | The engine split has no safety net otherwise. |
| R2 | Injectable `RandomGenerator`, `SeedSource`, `EnumMap`, sequence ids, seeded names. | A Company cannot be replayed, and random ids can collide. |
| R3 | An immutable `TowerRules` copy per Company. | One Company's rules must not leak into another. |
| R4 | Split `GameState` into `Campaign`, `PartyState`, `TowerRun`. | The Company has no Campaign to own. |
| R5 | A pure `ExpeditionEngine`, `LootPolicy` and `FloorRules` hooks, Unit-uniform targeting. | One engine for solo and Company. `FloorRules` is the same seam Prestige v2 wants. |
| R6 | A slot registry keyed by player, find-only `access()`, `doFinally` cleanup, per-message error isolation. | Reconnect and the lost-contact rule need stable identity. A race can resurrect a ghost `GameState` today. |
| R7 | Campaign Key handshake, close codes, `GameFrame` with `kind`, a reconnecting client socket. | The frontend socket today has no key, no reconnect and a hard-coded `ws://localhost:8080`. |
| R8 | `CommandRouter` with codecs, a size cap and rate limits. | Unauthenticated input becomes shared-state input. |
| R9 | A `CompanyScheduler`, confinement assertion, `Throwable` guard, tick re-anchoring, heartbeat. | The default pool has one thread. |
| R10 | **The server and the Boot line (settled).** The repo moved to Boot 4.1.1 and keeps Undertow through the community starter (ADR 0005). Reactor Netty, Boot's default WebFlux server, remains the fallback. | None: the transport is built once, on the settled stack. |
| R11 | `WebSocketConfig` has `@EnableWebFlux`, so Boot's auto-configuration backs off and `spring.codec.max-in-memory-size` has no effect. | A trap for any REST body limits. |
| R12 | Slot selector in the handshake (real Campaign or Trial), server-side attempt counter, Trial file not exportable. | One connection serves one slot, and an old file must not buy attempts. |

---

## 8. Fairness and agent seats (the MCP design)

### 8.1 Fairness levers

| Lever | Rule |
|---|---|
| Equal start | The Trial kit (3.1). No Roll, Epitaph or Tower Memory. |
| Equal Tower | One seed. Architectures, pins, levers and Mutations are pure functions of the Terms. |
| Equal Garrisons, unequal luck | Garrison seeded by `floorSeed(f, k)`. Combat dice unseeded. Equal before Easing and Risen. |
| Equal counting | Shortcut and Milestone thresholds count only Expeditions of at least 3 Floors, so the first player to find instant Extraction does not win (constraint 12). |
| Equal ignorance | Only a Sprint gives it. A Season is knowledge-sharing by design. Mitigation: rotating Terms, Mutations met-to-be-known, first contact marked on the pillar. |
| Simultaneous Extraction | Resolved together before combat. Arrival order never decides. |
| Hidden stake | Never sent: Vault, Credits, Loot, Supplies, Loadout, seed. A canary test guards it. |
| Handicap (off by default) | A previous first-placed Contender may start with Tower Memory 1 or 2. Public if enabled. Breaks "identical Garrisons" for that player. |
| One Contender per key | Three Season attempts, counted by the server. No prize. |
| Mixed Generations in a Company | Memory is the highest and Easing the lowest. The Effort ramp stops pure riding. It does not make a Generation-4 and a Generation-1 Party fair. |
| Seed secrecy | The Race `seed` is never any player's `towerSecret`. Exports do not carry it. |
| Client time | Never trusted. The server decides everything and clients send only intents. |
| Reaction time | A 4-tick Threshold and a 3-tick channel are fixed for everyone. The pre-set answer (3.6) lets a player who cannot answer in time take part on equal terms. |

### 8.2 The MCP server

**Status.** No stream researched the MCP server. This section is [inferred] and states what the multiplayer design requires of it. The transport and library choice are unresearched. The options below are from memory and must be verified before use [unverified]: the MCP transports are stdio and Streamable HTTP; the official Java SDK and Spring AI's MCP server starters (including a WebFlux one) exist; the HTTP form expects OAuth-style authorization.

**Two meanings of "agents can talk to the server".**
1. **Agents as players.** An agent drives the same command set as a human.
2. **Agents as tooling for the author.** Inspect state, run simulations, replay journals, read telemetry.

**Two shapes, in order.**
1. **A sidecar bridge (first).** A separate local process speaks MCP over stdio to the agent and the existing WebSocket protocol to the server, as one more client. It needs no server change and opens no new attack surface. The AGENT label is then declared by the bridge in the handshake, so it is honour-based.
2. **An in-process endpoint (later, for remote agents).** The MCP server is one more client of the same `CommandRouter` and view surface. It adds no rule of its own. A connection that arrives through it is labelled by the server. Only this shape can be separated by the server rather than declared.

| Tool group | Examples (names are placeholders) | Notes |
|---|---|---|
| Read, public | `race_terms`, `race_board`, `race_ledger`, `company_view` | The same public projection a spectator gets. Needs no Contender slot. |
| Read, own | `my_state` | The agent's own private view, exactly as a human's. |
| Act | `act(command)` with typed commands: Recruit, Depart, Extract, Reinforce, Upgrade, Village actions, Commit | Same validation, same rate limits, same Brace. |
| Dev, local only | `simulate(seed, departures, script)`, `replay(journal)`, `telemetry` | Over the pure step function. Bound to a local, authenticated admin endpoint. Never public. |

**Rules for agent seats**
- **Declared, not detected.** A connection opened through the MCP endpoint is labelled AGENT by the server. A script on the plain WebSocket is not detectable, so the Board's badge and the separate tables cover declared agents only. The real fairness lever is that there is no prize.
- **Credential.** An agent must not hold the Campaign Key: the key is the whole Campaign and cannot be recovered or revoked (ADR 0001), and an agent's context, tool logs and transcripts are a leak path (prompt injection can ask for it). Use a **scoped, revocable agent token** derived from the Campaign: no export, no import, no key display.
- **Same information as a human.** No hidden state, no free reads of other players' stake.
- **Same speed, and no free pause.** The same command rate limit and tick resolution. A tool-call-per-turn design that detaches between calls would give an agent a free Held Party pause that a human playing in real time does not have. An agent seat holds its slot attached for its whole session, so game time runs while the agent thinks.
- **Races:** Hands and Agents are ordered in separate tables. House Pacers (a scheduled AGENT Contender labelled as such) can fill a quiet Sprint. They give agents a reproducible benchmark: same seed, same kit, a finish line and a Cost column.
- **Companies:** an agent seat is a sybil tool, since one person could fill every seat. Companies are invite-only, so the host decides. Agent seats are labelled in the Gathering roster. They are not allowed in any public matchmaking, which does not exist.
- **Injection surface is small.** All text an agent reads from the game is server-generated from fixed catalogues and aliases. There is no player free text and no chat, which is also why emotes are fixed.
- **Not before the protocol is stable.** The command set must be validated field by field first.

**Needs its own research:** the transport, how an agent authenticates (the scoped token), what a safe tool granularity is, and whether the dev tools are worth building before the simulator exists.

---

## 9. Tone: copy and Ledger lines

**Principles.** Serious, plain, no praise. Losses are written like gains. The Tower does not care who is watching. No banner, no fanfare, no sound. No icons or animation on the call confirmation. Use the glossary: Extraction, Wipe, Floor, Garrison, Guardian, Loot, Party, Unit. Never write retreat, run, climb (as a noun), boss, score, rank, win, Gate or Haul. Do not use "Standing" (an `_Avoid_` under Village Tier). In UI copy the act of leaving in a Company is "call the stair" (copy only, not a glossary term). **That phrase collides with five planned terms:** the Stair (Package C R16, with Repairs), Narrow Stair (an Inheritance), Severed Stair and Long Stair (Mutations), and "The Toll on the Stair" (D6). If the author prefers one word, "Extract" is the glossary's own and needs no new term (decision).

**Colour.** ADR 0004 fixes Guardian crimson as a role colour. The village doc's slabs already use crimson for "Units lost" and "Loot lost". This design's debts (Share owed) should probably take weight or underline, not crimson, to keep crimson meaning loss of what is carried, not an obligation. Danger must never rely on colour alone (ADR 0003).

**Time wording.** The Village says "Time passes only while you are here." A Sprint says "The Race does not wait." A Season says "Only the hours spent here are counted." Inside a Company: "Time passes only while you are here, except for those on the Tower."

| Moment | Copy |
|---|---|
| Call confirmation (Garrison Floor) | "Calling the stair takes three seconds. The Party stays in the fight until it is through. The Garrison is at 62 percent. One Party remains. Leaving now adds four times the pressure to the one that stays." For a Guardian the last sentence reads "twice". |
| Cost line | "You will owe the Rearguard: 195 Materials, 78 Credits and 30 Supplies." |
| Keep line | "You keep: 855 Materials and 342 Credits." |
| Buttons | "Call the stair" and "Stay" |
| To the others on a call | "Zeno's Party has called the stair. It is through in three seconds. The Guardian is not." |
| Completed | "The stair is shut behind Zeno's Party. It left the Rearguard 231 Materials, 92 Credits and 25 Supplies." |
| Follow window | "Zeno's Party has called the stair. If the rest of you call within three seconds, no one is left behind and no one owes." |
| Share forfeited | "Crates's Party called the stair with what Zeno left it. It stays on the Floor." |
| Threshold | "The Floor is quiet. It is waiting for the whole Company. Stand, or take the stair. Every answer is shown together." |
| Reveal | "Stood: 2. Took the stair: 1 (Zeno)." |
| Pressed comrade | "Hypatia's Party is going under. The stair holds for it for three seconds." |
| Lone Party | "No one is left on the stair with you. You may leave at no cost. The Guardian has not changed." |
| Contact lost | "Contact is lost with Crates's Party. It holds for ten seconds, then calls the stair on its own." |
| A Wipe in the Company | "Hypatia's Party is gone. Its names stay on Floor 30." |
| Door, no key | "The door does not open for you. The others go on." |
| Ledger, the leaver (Garrison Floor) | "Called the stair on Floor 19 with the Garrison at 62 percent. Owed to those who stayed: 195 Materials, 78 Credits, 30 Supplies. Banked: 855 Materials, 342 Credits." |
| Ledger, a stayer | "Received from Zeno: 231 Materials, 92 Credits, 25 Supplies." |
| Ledger (Company) | "Company of three, to Floor 31. Crates stood to the end. Zeno called the stair on Floor 30. Hypatia fell on 30." |
| Race record | "Race of 2026-10-24. Floor 20. 3rd of 17. 41:08. 112 fallen." |
| Gathering stair record | "Stair record: stood 7, called 2, contact lost 1." |

(The Floor 19 lines were checked: 195 + 855 = 1,050 Materials and 78 + 342 = 420 Credits are the Loot of Floors 1 to 19, and 195/1,050 = 0.186 is the Garrison Share at H = 0.62.)

Race Ledger lines are in 3.2. The four tugs idea (Hold, Press, Falling back, Wounded) is subsumed by the eight fixed emotes.

---

## 10. Staged build, sizes and dependencies

**Sizes (author working alone):** S up to 1 day, M 2 to 4 days, L 5 to 10 days.

### 10.1 Dependencies on planned work

| Planned work (roadmap) | What multiplayer needs from it |
|---|---|
| Glossary and ADR text (Phase 0) | Rule on the terms in 11.3 and on ADR 0004 (palette) before any code. |
| Phase 1: tests, injectable random, config copies | Seeded Floors for Stage 0. Characterisation transcripts for the engine split. |
| Phase 2a: persistence P1 to P8 (store port, slots, Campaign Key, Held Party) | Stage 0 needs the Key handshake and a slot per Trial. A refresh destroys a Trial without it. |
| Phase 2b: Section slice | The Board's pillar marks and the Company UI. |
| Phase 3: Package B | The Wipe-only Risen slice (D1) for Race Trials. **Stage 2 Echoes beyond Wipes need D1 in full (Package C).** Loadout travels in the Departure record. |
| Phase 4: Prestige v1 and the Door | The per-Party Door check. Toll hooks. |
| **E0 scaling pass** | **Blocks the Door, the Summit race, Finish Floors above 30, every Floor above about 50 in a Company, and (without a Terms-level override) Stage 4's dilemma itself.** |
| Phase 6: Prestige v2 | Mutations in Races and Companies, the Lineage Race. |
| Roguelite layer | Halts disabled in Companies. The shared `SeedSource`. |

### 10.2 The build

| # | Work | Size | Needs |
|---|---|---|---|
| G0 | Glossary proposals, an ADR 0004 ruling, and two ADRs: "A Company never holds for a disconnected Party" (amends ADR 0001's Held Party) and "A Company has one writer and meets Campaigns through its records". | S | none |
| Seams | Roadmap Phase 1 and persistence P0 to P4, shared (R1, R2, R3, R6, R7, R8, R12). | about 5 M, shared | none |
| R10 | The server and the Boot line: settled (ADR 0005). | done | none |
| **Stage 0** | Terms registry, Trial slot and kit, seeded `floorSeed(f, k)`, `finishFloor`, the Expedition counting rule, clock, `ticksSinceStart`, Contender status. | M | Seams |
| 0 | Results store, per-Floor timelines, tie rules, Race record line, aliases. | S | Stage 0 core |
| 0 | Frontend: join screen, Race HUD (clock, Finish Floor, Cost), finished table and timeline. | M | Stage 0 core |
| **Stage 1** | Board hub (single-writer, 1 Hz), Ledger lines, lobby, invite codes, scheduled Sprint start, `watchCode`, Origin allow-list, rate limits. | M | Stage 0 |
| 1 | Frontend: Board panel, Ledger feed, pillar marks, Wall of Names across Contenders. | M | Section slice |
| 1 | Recorded Pacers (cuttable). | S | Season results |
| F1 | Compact 1 Hz frame serialized once, static catalogs, then measured. Raises the Contender cap. | M | Stage 1 |
| **Stage 2** | Per-Floor modifier hook in `Tower.buildFloor`, graves store, deterministic ordering. | M | Package B D1 (Wipe slice) |
| **Stage 3** | Command inbox, Unit ownership, `AidWave`, idempotent Recall, caps. **Cuttable.** | L | Stage 1 |
| E1 | Make enemy targeting Unit-uniform and add Effort metering (with the ramp). | S | R1 |
| E2 | Headless simulator over the pure step function: 10,000 seeded Floors for n = 1 to 4, recording p2, p1, mean call tick, standoff deaths, joint-fight length against the 6-tick minimum exit. Tune the Share coefficient, `lambda_max`, k, L(n) and `guardianHealthScale`. | M | R2, R5 |
| E3 | Engine split: `Campaign`, `PartyState`, `TowerRun`, `ExpeditionEngine`, the three records, `SoloHost`, `ViewComposer`. Acceptance: transcript equivalence. | L | Seams, best combined with persistence P2 |
| **Stage 4a** | Company engine core: K executors, the Stand, canonical order, channel, Brace, Pressed hold, Share and forfeiture, Garrison and Loot scaling, Effort ramp, standings. Driven by fake clients. | L | E1, E2, E3, F1 |
| 4b | Gathering, invite, protocol, heartbeat, two-phase Departure, acks, rate limits, emotes, client adapter for the existing scene. | M | 4a |
| 4c | Lost-contact rule, graceful-shutdown auto-Extraction, crash settlement, presence, journal. | M | 4b |
| 4d | Frontend: Company UI, silhouettes, channel countdown, standings. | L | 4b, F1 |
| **Stage 5** | Departure-together mode, the Threshold, `g(n)`, Door per Party, Easing and Memory scope. Optional staggers (queue, Cover). | M | Stage 4 played and tuned |
| 5 | Spectators endpoint. | S | 4d |
| 5 | Door per Party, with the pay-once rule. | S | Prestige v1, E0 |
| Harden | Load test, metrics, `max-active`, per-key FAULT counters. | S | 4c |
| Later | Floor-clear checkpoints and `HELD_ALL`. | M | 4c |
| Later | Lineage Race. | L | Prestige v1 and v2, E0 |
| MCP | Sidecar bridge, then gateway, read tools, AGENT label, scoped token. Needs its own research. | M | Stable protocol, R8 |

**Rough total, excluding the roadmap seams, "Later" and the MCP:** about 68 working days at midpoint sizes (S 1, M 3, L 7.5). Adding the two "Later" items (checkpoints, Lineage Race) gives about 78. Cutting Aid and Recorded Pacers saves about 8, so about 60. Uncertain by half again either way.

**Order of play.** Stages 0 and 1 can ship while the engine split is still being built, because they need no shared simulation. The engine split (E3) is the single biggest step and is best combined with persistence P2.

---

## 11. Risks, cuts, glossary and decisions

### 11.1 Risks

1. **Co-op may be safer than solo, which flattens the dilemma.** ARC Raiders' official post says safe sessions see higher extraction and lowers loot to compensate [research, primary]. At k = 0.8, damage per Unit at n = 4 is 0.72 of solo. If the safety is larger, tension drops. Mitigation: `loot-yield`, then `g`.
2. **The upper Tower is unreachable.** A Company of three at the Floor 70 Dragon needs about 46.5 Rogues per Party against about 51 solo, only 9% fewer [inferred; Dead Dragon 13,000 hp, Rogue 10 per tick]. E0 blocks the Door and the Summit race.
3. **Tuning failure both ways.** If the Share is too high nobody calls. If it is too low everyone calls at first contact, as the 218 deviation cost in 4.5 already suggests. A standoff to death is also possible. All p, V and q are placeholders. The simulator (E2) comes before the engine.
4. **Attrition clock.** An Archer lives 10 ticks and a full Supplies load lasts about 12. A 3-tick channel plus a 10-tick hold may be a Wipe for many Parties. The numbers need play, not only arithmetic.
5. **A seeded Season is solvable.** Players can publish what each Floor holds. Only a Sprint gives equal ignorance.
6. **Single-player with names.** Until Stage 4 the author's core ask (fighting together) is absent. Empty Boards are likely in a small game.
7. **Stage 4 has no test bed.** The Floor 10 and 20 Guardians die before any call can complete (3.5). Stage 4 waits on E0 or a Terms-level override.
8. **Bandwidth.** About 25 to 30 clients today at an assumed 100 Mbit/s, so concurrent Trials are capped at 20 until the compact frame lands. The compact frame size is an estimate and permessage-deflate on Undertow is unverified.
9. **Splitting `GameState` is the largest hidden cost.** Engine methods are `private static` with no tests. Skipping the transcript equivalence gate can silently change solo balance.
10. **`checkGameOver`.** A missed edge case can declare Defeat on a player whose whole force is inside a Company.
11. **Sybil and automation.** Anonymous keys are free. The rules bound it and declare agents. They cannot prevent one person filling several seats.
12. **Echoes break "equal Tower"** if enabled in public Races (3.3). Hence the flag.
13. **Mixed Generations.** A Generation-4 Party and a Generation-1 Party share a Garrison tuned by Parties, not strength.
14. **Tone drift.** A Board of names and times can become a fanfare leaderboard. Copy review is needed.
15. **Sprint clock contradicts the Village copy.** The wording changes by format.
16. **Crash semantics rest on unverified behaviour:** Undertow graceful shutdown with upgraded sockets, and clean-close cleanup in `Mono.zip`.
17. **Server line.** The WebSocket layer runs on an unreleased Undertow community starter (ADR 0005); if it proves unreliable, moving to Reactor Netty costs a dependency swap and a transport recheck.
18. **Glossary and ADR friction** (11.3, and ADR 0004 in 5.3).
19. **MCP is unresearched.** Everything in section 8.2 is inference.
20. **Every capacity figure** comes from a synthetic harness (no JMH, no network, JDK 26 against a JDK 24 target).
21. **Instant Extraction farms thresholds.** Already true in solo play (constraint 12). Any Race that ignores it rewards it first.

### 11.2 What to cut, in order

1. **Aid (Stage 3).** An L-sized cross-slot write for a sybil channel.
2. **Recorded Pacers and ghost bands on Toll Floors** (Package C, D6).
3. **The Lineage Race.** It needs everything else.
4. **Sprint** if Season alone is enough. Season is cheaper and needs no scheduling.
5. **Follow-one-Party spectating and the handicap for previous first-placed Contenders.**
6. **The optional staggers** (queue, Cover) until simulation shows they are needed.
7. **The relic pot, the Rearguard (Units who stay and never come home), Thresholds on Surge Floors.**
8. **Stage 5 itself,** if Joint Guardians play well. They already give the shared fight, the Share and the race.
9. **Checkpoints,** since the crash refund is enough for v1.
10. **Free-text chat,** permanently.

### 11.3 Glossary terms to add

All are [Invented] and pending a ruling. `CONTEXT.md` is not edited.

| Term | Meaning | `_Avoid_` and collisions |
|---|---|---|
| **Company** | The players who share a fight, and the server object that simulates it. A **Company Expedition** is one joint outing. Each Party's own Expedition still ends in its own Expedition Outcome. | `_Avoid_`: squad, party (a Party is one player's Units), group. Collides mildly with "The Second Company" (Defeat flavour, prestige doc) and "a company, not a village", and is near Companion. Rule on it. |
| **Gathering** | The lobby before Departure. | `_Avoid_`: lobby, room (`_Avoid_` under Architecture), Muster (collides with `Muster Roll` and the village doc's verb). |
| **Departure** | The commit point where Units and Supplies leave the Campaign. | Already used in the prestige doc and the architecture doc. Register it. |
| **Race**, **Terms**, **Contender**, **Trial**, **Season**, **Sprint** | The shared-seed competition, its rules, a player's slot in it, a sealed Village under Generation-1 rules, and the two ways to run it. | A Trial is not a Campaign (a Campaign is the whole game for one player) and not a Generation. `_Avoid_` for Race: run, climb. For Terms: Charter (Village Charter is planned). For Contender: Entry (Ledger and Chronicle use "entry"). Checked: none of these words appears in the other designs, except "Season" in prose. |
| **Finish Floor**, **Finish** | The Race's Summit, and reaching it. | Not "Summit", which stays the Tower's top. |
| **Order of Arrival**, **Cost** | The two columns of a Race result. | `_Avoid_`: score, rank, rating. |
| **Race record** | A player's line per Race, shown on the Chronicle screen. | The Chronicle's entry covers ended campaigns only. Either extend it or keep this separate. |
| **Board** | The live rows of Contenders. | `_Avoid_`: leaderboard, scoreboard, ranking. |
| **Pillar mark** | A scratch on a reference pillar. | New. |
| **Echo**, **Aid**, **Pacer** | A record of a dead Party, a Wave sent to another player, a recorded or agent Contender. | New. |
| **Joint Guardian**, **Stand** | A Guardian fought by several Parties; the engine instance and the choice to stay. | `_Avoid_`: Gate (`Door` avoids it, and R14 is "The Gate"), boss. |
| **Threshold** | The 4-tick quiet before a Guardian Floor in a Company, with a sealed Stand or Take-the-stair answer. | The prestige, roguelite, palette and village docs use "Threshold" only as a table column. |
| **Rearguard Share** | What a leaving Party transfers to the Parties that stay. | `_Avoid_`: Cache (the roguelite layer has one), Levy (engine word only), Toll (taken), tax. |
| **Effort** | Hp removed from Enemies + hp restored + 0.5 x hp lost to Enemy hits, on one Floor, with a ramp deciding the Loot fraction. | New. `_Avoid_`: score, contribution. |
| **Floors Stood** | The last Floor a Party was present on. | Used in place of **Place**, which would need `_Avoid_` entries for rank and standing and invites a single order. |
| **Lost-contact rule** | The Hold then automatic call when a connection drops. | `_Avoid_`: Standing Order (roguelite Orders). |
| **Stair record** | Counts of stood, called and contact lost, shown in the Gathering. | New. |
| **"Call the stair"** (copy) | The timed Extraction. | Collides with the Stair (R16), Narrow Stair, Severed Stair, Long Stair and "The Toll on the Stair". See section 9. |

**Words that stay engine-only:** Levy, Brace, Cover, Stand, `memberId`, `Muster` (the record type, renamed `Departure record` in docs), Stand record.

**Extend, don't contradict:** `Expedition Outcome` stays Extraction, Wipe or Summit Conquered. `CONTACT_LOST` is an engine settlement state, not a fourth outcome. `Easing` is defined as campaign-wide, so it needs a per-Company rule. `Extraction` gains a timed form in a Company, and `Loot`'s "lost on a wipe" and `Vault`'s "only extraction can add loot" stay true because the Share moves Loot in hand only. `Extraction`'s "carried loot is banked" is reduced by the Share in a Company, which is a redefinition to record.

### 11.4 Decisions only the author can make

Recommendation first in each.

1. **Which reading of "first to the top"?** The ladder (parallel race first, shared fights on Guardian Floors, a shared front only if it plays well), or a shared front only. Only the ladder keeps a real speed race.
2. **Season before Sprint?** Yes. Cheaper, asynchronous, no lobby.
3. **A Trial in its own file, or a race on real Campaigns?** A Trial.
4. **Finish Floor.** Floor 10 for the first Terms (the Short preset; Floor 20 follows once the MCP baseline has measured it), separate from `max-floor`.
5. **What a Race pays the real Campaign.** A Race record line and a cosmetic pillar mark only. The alternative is a capped Toll (at most 1 Legacy per Finish and 3 per Season) after the par values are fitted.
6. **Seed the Garrison dice everywhere,** or only in Race mode. Recommend everywhere, with a random per-Campaign seed. It departs from prestige doc 3.8, which seeds only the layout.
7. **Echoes in public Races.** Off in public, on in private.
8. **Aid.** Cut it, or keep it private-only.
9. **Stage 4 private-only,** or public Joint Guardians too. Private, since an invite code stands for a friendship.
10. **The Share.** Loot in hand and Supplies, priced to the fight's shape (Garrison (n/m)^2, Guardian n/m), Relics included, a transfer with a 50% recipient cap, banked only by those who stand, forfeited to the Tower by a recipient that leaves. The alternatives are to let a recipient keep it on leaving (this makes waiting win), to destroy it, or to exempt Relics.
11. **The follow window refund.** Allowed. A Company that turns back together pays nothing. A cancelled call voids it.
12. **A lone Party may call at no Share through a channel,** or instantly as in solo play.
13. **Easing credited only to Parties present at the Guardian's death with `f >= 0.2`,** or to all Parties.
14. **Door per Party** (recommended), or open for the whole Company if any Party holds the key (breaks mandatory Prestige).
15. **Threshold on Guardian Floors only,** or also on Surge Floors. And whether Surge Floors get an explicit reward.
16. **Contact-lost hold.** 10 ticks after a 6 s heartbeat timeout, then call at the same Share. Or a Wipe at expiry.
17. **Crash or FAULT settlement.** Refund (`CONTACT_LOST`) with attribution from the journal, or Wipe (the prestige doc's interim rule).
18. **Loot paid per Party with `L(n)` and the Effort ramp,** or a pot.
19. **Company size.** 3 at launch and 4 behind a flag.
20. **Heal scope.** The whole Company or the own Party.
21. **Tower Memory and Easing scope in a Company.** Memory highest, Easing intersection, or start from none for a simpler first cut.
22. **Words.** Company, Gathering, Rearguard Share, Lost-contact rule, Floors Stood, and whether "Company" may coexist with "The Second Company". And whether "call the stair" stays given five collisions, or the copy says "Extract".
23. **Transport for the Campaign Key.** The subprotocol (the prestige doc's choice) or the first message (OWASP's).
24. **Server.** Settled: Boot 4.1.1 with Undertow through the community starter (ADR 0005); Reactor Netty is the fallback.
25. **Solo hosting.** Keep solo as today through `SoloHost`, or unify on Company rules.
26. **Race Seed fairness shortcut.** A Standard Party for an advisory board in memory, as a cheaper Stage 0, or the Trial. Recommend the Trial.
27. **MCP scope.** Is the MCP server in scope for Races, for Companies, or only as author tooling? Recommend its own research first, a local sidecar bridge for author tooling before any in-process endpoint, scoped agent tokens (never the Campaign Key), and agent seats only in Races and labelled in private Companies.
28. **Toll bonus for place.** None (recommended, farmable by two keys), or at most 4 Marks per Campaign ordered by Floors Stood.
29. **Roguelite Halts in a Company.** Disabled when there is more than one Party.
30. **Palette in a Trial and a Company.** Keep each player's own ramp and carry the Tower's Memory by numeral (recommended), or amend ADR 0004.
31. **Where a Race result lives.** A new Race record on the Chronicle screen, or an extension of the Chronicle entry.
32. **Shortcut and Milestone counting in a Trial.** Count only Expeditions of at least 3 Floors (recommended), or pin Shortcuts in the Terms.
33. **Stage 4 test bed.** Wait for E0, or add a Terms-level `guardianHealthScale` so joint fights outlast the 6-tick minimum exit.
34. **Towed Parties.** Highest Floor, Ascent, Milestones and Easing only for Floors with `f >= 0.2` (recommended), or credit arrival.
35. **Company Mutations and the Atlas.** Does meeting one write to each present player's Atlas?
36. **Pre-set Threshold answers.** Allowed (recommended), or only live answers.
37. **Whether to read the sources the manga claims depend on.** Nothing in this design claims manga fact, so none is needed.
