# Tower Dungeon: Persistence and Prestige, one recommended design

Status: design only. No repository file was written or edited. The village document is being edited by a parallel tone pass and is not touched here.

Source tags: **[Manga §n]** is a fact in `docs/research/tower-dungeon-since-vol6.md`, with the research doc's own confidence label where it matters (wiki, review, official). **[TW §n]** is `docs/research/tower-wizard.md`. **[Invented]** is original to Tower Dungeon. The manga is inspiration, not canon. Every section number cited below was re-checked against those two files in the critic pass.

Code facts were re-verified against the source in the critic pass (`GameWebSocketHandler`, `WebSocketConfig`, `GameService`, `GameState`, `Village`, `Tower`, `TowerFloor`, `Enemy`, `Reinforcement`, `EnemyStats`, `UnitStats`, `ShortcutType`, `MilestoneType`, `GameProperties`, `application.yml`, `pom.xml`, the Angular socket service and `game-component`). Confirmed: `GameOutcome` is `PLAYING | VICTORY | DEFEAT`; the frontend already sends a `PRESTIGE` action (`game-component.html:83`, `game-component.ts:109`, `game-action.enum.ts:11`); the backend has no `PRESTIGE` case and its `GameAction` enum lacks the value; `GameState.prestigePoints` is an unused `Integer` that the template still prints (`game-component.html:171`).

Every difficulty, pacing and point number below is a starting knob. No telemetry exists and `backend/src/test` does not exist. The model figures come from the loot formulas (Floor f pays 5f Materials, 2f Credits, f/10 Relics from Floor 10, all x3 on Guardian Floors), not from play. Section 3.11 shows that the upper half of the Tower (Floors 70 to 100) is not beatable on the current enemy and Supplies numbers, so the Highest Floor targets in 3.10 are targets for a scaling pass that does not exist yet.

---

## 1. Decisions made, and what this document decides

### Already decided by the author (from the brief)

| Decision | Consequence here |
|---|---|
| Package B is the base: Trades (one Villager pool across Fields, Quarry, Scribe's Study), a Stockroom (Salt, Dragon Spores, Knowledge), an Expedition Loadout panel | Prestige must start these over and count them. Mutations are countered by them. |
| "Tier" is preferred over "Standing" | The progression level is **Village Tier** (I to IV). Conflict with `CONTEXT.md` resolved in section 5. |
| Persistence and Prestige are in scope | Both are designed together. Prestige is not shippable without persistence. |
| Tone: serious, bleak, attrition-driven, fear of the unknown | The Tower keeps count. Nobody is congratulated. Mutations stay unnamed until met. |

### Decided by this document

1. **One persistence stack** (section 2). It supersedes the identity and disconnect rules in all three Prestige designs.
2. **One Prestige design** (section 3), built from three parts:
   - the **Ledger/Toll** design as the spine (point bar, formula, gated Roll, the Door);
   - the **Generations** design as the in-world frame (Succession, Heir, Dowry, Writ, Tower Memory, Chronicle);
   - the **Mutating Tower** design reduced to a modest variation layer (8 Mutations, Omens and a Pact, Atlas, Survey).
3. **Staged delivery.** Prestige v1 is the Toll, the Roll, the Door, Tower Memory and the Succession screen, with no Tower Mutations. Variation is v2. v1 is a complete Prestige loop, but it is the **samey** loop (section 3.12): the Tower is the same layout every Generation, only harder. v2 is the answer to sameness, so it is not optional polish.
4. **What is dropped** from the three designs (section 3.1), to keep the whole thing finishable.

---

## 2. Persistence

### 2.1 Recommended approach (summary)

| Question | Decision |
|---|---|
| Identity | Anonymous **Campaign Key**: 128-bit random token minted in the browser, stored in `localStorage['towerdungeon.key.v1']`, sent as WebSocket subprotocol `['td.v1', <key>]`. The server stores only `playerId = lowercase hex of the first 16 bytes of SHA-256(key)` (32 chars, safe on case-insensitive filesystems such as the default macOS one). No query string, so no proxy-log leak. |
| Lost key | No server recovery, by design. Mitigations: key shown in Settings, **Campaign file** export/import, a gate screen ("Begin / Resume with Campaign Key / Import Campaign file") so cleared site data never looks like a new player, and `POST /api/campaign/peek` so a typo cannot start a blank Campaign. |
| Storage | Versioned JSON files on local disk behind a `CampaignStore` port. Atomic temp, fsync, rename. Checksum header. 5 rolling backups. Automatic fallback. Scored 80/90 against H2, SQLite, PostgreSQL (R2DBC and JDBC) and Redis. PostgreSQL is the later swap via the port's `expectedSeq`. |
| Record | `PlayerRecord { schema, meta, campaign }` in one file. Ending a Campaign (award and Chronicle entry) is one atomic write, and so is beginning the next Generation. `meta` survives any `campaign` migration failure. |
| Offline | **Frozen.** Nothing produces, eats or starves while nobody is connected. |
| In-flight Expedition | **Held Party** (2.6). |
| Double session | Newest connection wins. The old tab is closed with code 4001 and does not auto-reconnect. |
| Spoilers in the file | `towerSecret` and the stored `blueprint` are **sealed** (AES-GCM, key from config) inside the file and the export, so a player cannot read the Tower's Mutations out of their own export (2.2). Open decision 14 offers the alternative of accepting that. |

Hosting caveat: the data directory must be a mounted volume. On an ephemeral filesystem every deploy deletes every Campaign.

### 2.2 Identity, handshake, close codes

- **Minting:** `crypto.getRandomValues(new Uint8Array(16))`, base64url, 22 chars. The server validates `^[A-Za-z0-9_-]{22}$` before it touches the filesystem, which blocks path injection. Base64url characters are valid WebSocket subprotocol token characters.
- **Handshake:** `GameWebSocketHandler.getSubProtocols()` returns `["td.v1"]`. The server selects `td.v1` and never echoes the key. It reads the key from `session.getHandshakeInfo().getHeaders()` under `Sec-WebSocket-Protocol`. The header can arrive as one comma-joined value (`td.v1, <key>`), so split on commas and trim before taking the second token.
- **Transport:** require `wss` in production. Derive the URL from `location`, replacing the hard-coded `ws://localhost:8080/api/ws` in `GameWebSocketService`. In dev, add an Angular `proxy.conf.json` (`/api` to `localhost:8080`, `ws: true`). Add a handshake `Origin` allow-list (`game.security.allowed-origins`).
- **Close codes:** 4001 REPLACED, 4400 BAD_KEY, 4426 SCHEMA_NEWER (the file is left untouched), 4500 STORE_FAILURE.
- **Frame:** `GameFrame(@JsonUnwrapped GameState state, SessionMeta session)` where `session = { resumed, savedAt, schema, notices[], held }`. The existing frontend parser (`JSON.parse`, then `setState`) ignores the extra field and keeps working.
- **Pristine Campaigns are never written** (no purchase, no Expedition, no Trade change), so bots that open sockets create no files. Import and first-purchase are the file-creating paths and need a quota (P12).
- **Export/import:** `GET /api/campaign/export` (bearer key) returns `towerdungeon-g{generation}-{yyyyMMdd-HHmm}.tdc`, which is the disk container (a header JSON line plus a payload JSON line, so not a single JSON document and therefore not named `.json`) with no Campaign Key inside. `PUT /api/campaign/import` (at most 1 MiB):
  1. verify magic, byte count and SHA-256;
  2. refuse `schema > CURRENT` with 422;
  3. migrate, then `validate()`;
  4. refuse with 409 while an Expedition is active or Held;
  5. rotate the current head into backup 1;
  6. swap under the state lock, then write and await durability.
- Import is undoable (the previous Campaign is backup 1). It allows re-rolling a Wipe by importing an older file, as Tower Wizard's export does [TW §2: export and import]. The checksum protects against corruption, not editing. It cannot duplicate Legacy, because the file carries `meta` with it: importing an older file also restores the older Legacy.
- **Sealed fields.** The export is handed to the player, so anything in it is readable. `meta.towerSecret` and `campaign.blueprint` (imposed Mutations, Omens, Pact) are therefore stored as one AES-GCM sealed block (JDK `javax.crypto`, no dependency), keyed by `game.persistence.seal-key`. Without this, the "unknown" of Veil, Survey and the Atlas could be read out of the export. The key must be stable across deploys or old files become unreadable, so it is deployment configuration and is documented next to the volume caveat. This does not stop a determined player who reads the public source and the seed formula, so it is a spoiler guard, not security.

### 2.3 What is recorded: facts, never derived numbers

Rule: **store facts, derive numbers, ratchet anything promised as permanent.** The client JSON is not the record: `purchasedUpgrades`, `achievedMilestones`, `unlockedUnits` and `Tower.clearedBosses` are `@JsonIgnore`, and `Unit`, `Enemy`, `Tower` and `TowerFloor` have final fields (`Enemy` also has final health and damage multipliers and a boss flag, `BaseUnit` has a random `Integer` id and a name drawn from Datafaker). `Village` has private setters. A separate immutable snapshot model and explicit restore constructors are required.

Existing fields (condensed from the persistence design, with the in-flight fields the critic found missing):

| Persist (campaign) | In-flight only (`expedition`) | Derived on load | Transient / config |
|---|---|---|---|
| `credit`, `reserves[]` (id, name, type, hp, mana), `vault{MATERIALS,RELICS}`, `upgrades{name:level}`, `milestones[]`, `shortcutsOpened[]` (ratchet), `clearedBosses[]` (Easing), `highestFloor`, `expeditionsCompleted`, `enemiesDefeated`, `outcome`, `lastExpeditionSummary`, `village.food`, `village.villagers` | `party[]`, `currentFloor`, `supplies`, `carriedLoot`, `carriedCredits`, `runStartFloor`, `runEnemiesDefeated`, `runUnitsLost`, `lastFoodReturned`, garrison (each Enemy with id, type, current hp, health and damage multipliers, boss flag), Reinforcement Waves (units, supplies, capacity, current floor), `loadout`, `held`, `doorSeen` | `maxSupplies`, `unlockedUnits`, damage multiplier, Carrying Capacity, available Upgrades | `tower.floors` (rebuilt as stubs; `Tower` is created lazily on the first Departure, so a never-launched Campaign has none), food-distribution cursor, `config` |

`GameState.upgrades` (`List<String>`, never read) and `prestigePoints` are deleted and replaced. The frontend interface and the template line that prints `prestigePoints` change in the same step.

Package B and Prestige fields are added to the snapshot **in the same change that adds the feature**. A reflection field-accounting test fails the build if a field is neither mapped nor on a reasoned `TRANSIENT` list.

**Prestige-specific additions** to the persistence design's data shapes:

```java
record PlayerMeta(Instant createdAt, int generation,            // 1 on the first Campaign
                  SealedBlock sealed,                           // towerSecret (128-bit, server-only; derives Tower seeds)
                  PrestigeState prestige,                       // below
                  double residue,                               // Toll Marks carried: T mod 9
                  LifetimeLedger lifetime,                      // never starts over, drives Epitaphs
                  Set<String> epitaphs,                         // granted set (ratchet)
                  Map<String,Integer> atlas,                    // mutationId -> generation first met
                  List<CampaignRecord> chronicle,               // cap 50
                  long lifetimePlaytimeSeconds, Map<String,String> savedLoadouts) {}
record PrestigeState(int pointsEarned, int pointsSpent, Map<String,RollEntry> roll) {}   // roll: id -> {paid}
record RollEntry(int paid) {}
record LifetimeLedger(double foodGrown, double materialsBanked, double creditsBanked, double relicsBanked,
                      int expeditions, int unitsLost, int guardiansSlain, int bestG, int highestFloor,
                      int victories, int exoduses, int defeats) {}
// CampaignSnapshot gains:
//   String phase;                  // ACTIVE | OFFER   (the loop ticks only on ACTIVE)
//   LedgerSnapshot ledger;         // Campaign Ledger + qualifyingExpeditions
//   int inheritedStartUsed;        // highest inherited Start Floor an Expedition launched from (rule B, 3.4)
//   Set<String> metMutations;      // Mutations that took effect at least once (rule L, 3.4)
//   SealedBlock blueprint;         // generation, imposedMutations[], omens[], pact; null in Generation 1
//   SuccessionReport pending;      // computed once at the end, idempotent; null while ACTIVE
//   OfferChoices choices;          // dowry, heirType, breachFloor, pactIds (OFFER phase only)
// GameOutcome gains EXODUS.
```

The persistence design's `blessings{id:{level,paid}}` becomes `roll{id:{paid}}`. Roll nodes have no levels here, so `level` is dropped.

Rules inherited from persistence that matter for Prestige:

- `pointsEarned` is stored, never re-derived from the formula, so a rebalance cannot rewrite history.
- `paid` is stored per Inheritance, so a removed or retuned node refunds exactly what was paid.
- Epitaphs and Village Tier are ratchets.
- The Toll bar is derived live from the Campaign Ledger and is not stored. The Succession award is stored once it is computed.

### 2.4 Write cadence

| Event | Timing | Backup rotation |
|---|---|---|
| Departure (`INVADE` accepted) | immediate, fsync (commit point) | no |
| Extraction, Wipe, Summit Conquered, Defeat, Exodus | immediate | yes |
| Village Tier up, Milestone, Shortcut, Epitaph | immediate | yes |
| **Campaign end (award, Chronicle), Roll purchase or respec, Begin Generation, import** | immediate and **awaited**: the player gets an error if the write fails | yes (bypasses the interval) |
| Purchases (Upgrade, Recruit, Villager, sells, Trades) | 3 s debounce, 10 s max wait | no |
| Autosave | 30 s attached, 5 s during an Expedition | only if settled and 10 minutes since the last rotation |
| Disconnect | final flush | as above |
| Shutdown | `@PreDestroy` / `SmartLifecycle` flush of every attached slot with a bounded wait (20 s). `server.shutdown=graceful` is added as a helper only: whether Undertow graceful shutdown waits for upgraded WebSocket exchanges is unverified, so the flush must not depend on it. Devtools restarts in dev go through the same hook. | as above |

Backups hold settled states only, never mid-Expedition. A minimum 10 minutes between rotations makes 5 backups span about 50 minutes. Hard-kill cost: at most 30 s of idle accrual (about 300 Food at 10 Food/tick) or 5 s of an Expedition.

I/O stays off the game threads. `gameLoop` (1000 ms) and `invasionTimer` (10 ms) are both `@Scheduled` with no scheduler configured, so they share Boot's default single-thread scheduler. Snapshots are therefore captured under the state lock (copy only, no serialization) and serialized and written on a dedicated single-thread writer. Loads run on `boundedElastic`, never on the Undertow I/O thread that calls `handle()`. The 100 ms `Flux.interval` render runs on the parallel scheduler and only takes the same lock briefly, as it does today.

### 2.5 Backups, migration, corruption

- **File:** a header line (`magic`, `schema`, `seq`, `savedAt`, `gameVersion`, `bytes`, `sha256`) plus the payload. The hash covers the exact bytes written. The directory fsync after the rename is best effort (it works on Linux, not on every platform).
- **Layout:** `data/players/ab/<playerId>/campaign.tdc`, `backup/1..5.tdc`, `pre-migration-v{n}.tdc` (kept forever), `ended-last.tdc`, `corrupt/{ts}.tdc` (at most 3). `data/.lock` (`FileChannel.tryLock`) stops a second instance.
- **Load order:** head, then backups 1 to 5, then pre-migration copies, then a fresh Campaign **with `meta` kept if anything is recoverable**. Bad files are quarantined, never deleted. The player sees "Your last record was unreadable. Resumed from 14 minutes ago."
- **Migrations** run on Jackson `ObjectNode` trees (`CampaignMigration{from, migrate}`), chained to `CURRENT`. A copy is made before each step. Ids are stable enum names, and a `released-ids.txt` freeze test fails the build on any removal or rename that lacks a migration. Unknown ids are dropped with a visible notice.
- **Tower Wizard lesson** [TW §2: 2024-12-22, old saves mostly break; prestige points carried over]: if `campaign` cannot be migrated, the Campaign is cleared **but `meta` (Legacy, Roll, Chronicle, Lifetime Ledger) is kept**, with the message "Campaign lost to a redesign. Legacy kept." Same file, so this costs nothing.
- **Write failure:** the slot stays dirty and retries (1, 2, 4 ... 30 s). The player keeps playing from memory. After 3 failures the notice is "Records are not being kept". A `HealthIndicator` reports DOWN (this needs `spring-boot-starter-actuator`, which `pom.xml` does not have today; the `management.*` block in `application.yml` is currently inert). The end of a Campaign, Roll purchases and import return an error and change nothing.

### 2.6 Offline policy and the in-flight Expedition

**Offline: frozen.** While nobody is connected the Village does nothing: no production, Upkeep, Starvation, Trades, Stockroom growth or Recovery. A detached slot that lingers in memory (2.7) does not tick either. Real-time consequences are lethal on a scale of seconds. With 12 Reserves and 20 Villagers the net is 10 - 12 = -2 Food/tick, so the Pantry's 100 Food empties in about 50 ticks and then kills one Villager and one Reserve per tick. With 30 Reserves everyone is dead in about 30 ticks. A bounded simulation with production but no Starvation is free money: 10 minutes is 6,000 Food, which is 6,000 Credits when sold, and a Floor-10 Expedition banks about 900 Credits when its 375 Materials are sold at 2 (150 Credits plus 750), so that is about 6.7 Expeditions. Frozen matches Tower Wizard [TW §3, §3b: no offline progression, secondary] and is one sentence in the UI: **"Time passes only while you are here."**

**Presence rule (S, recommended before any public play).** The client sends `PRESENCE {visible}`. Hidden for 5 minutes, or no input for 15 minutes, counts as away: the slot detaches exactly like a disconnect. This closes an existing leak that persistence would make durable: an open idle tab earns 10 Food/tick, about 288,000 Credits per 8 hours at 1 Credit per Food (`Village.sellFood`). It also protects the Toll's Food counter, though the square root already keeps that source small (3.4). It is a nudge, not security: a script can fake input.

**In-flight Expedition: Held Party.**

- On disconnect the clock stops and the full in-flight state is written: Party, exact garrison hp, Loot and Credits in hand, Supplies, Waves in transit, Loadout.
- On return the Party stands still until the player sends the new `RESUME` action. Copy: "The Party has not moved." While Held the Village clock is also stopped. Allowed actions are `RESUME`, `EXTRACT` and Village purchases. `REINFORCE` is refused until `RESUME`.
- **Extraction while Held is allowed.** Closing a tab gains nothing, because it cannot undo damage.
- Enemies are stored with their hp, so a refresh cannot reroll a frightening Floor.
- Departure is a commit point, so a crash after departure restores the in-flight Expedition. There is no free reroll.
- A disconnect is therefore a pause button. That is accepted: it cannot heal or undo anything, and the Presence rule bounds how long a tab can sit open.
- Interim, if the in-flight snapshot ships later than the rest: a record written at departure with no in-flight data settles on load as a Wipe with the notice "Contact lost". This is temporary and documented.
- This **replaces** the three Prestige designs' rules ("freeze 60 s then Wipe", "roll back to pre-launch snapshot"). The first punishes a network blip, because Expeditions last only tens of seconds to about two minutes. The second is an exploit.
- Implementable: every in-flight field is reachable from `GameState` (`unitsOnTower`, `reinforcements`, `tower.currentFloor`, the current `TowerFloor.enemies`, `supplies`, `carriedLoot`, `carriedCredits`, the `run*` counters), which the table in 2.3 now lists in full.

### 2.7 Multi-tab and concurrency

`players: Map<wsSessionId, GameState>` becomes `slots: Map<PlayerId, PlayerSlot>`. Details:

- `attach()` is newest-wins. `detach` is compare-and-detach on the websocket id. Single-flight load via `cache()` on `boundedElastic`. OFFER Campaigns do not tick. A detached slot lingers 30 s without ticking, then is evicted.
- The Campaign phase has two values, `ACTIVE` and `OFFER`. A Campaign enters `OFFER` in the same atomic write that computes its award (Victory, Defeat or Exodus), so there is no separate "ended but not yet offered" state to recover from.
- Fix the existing defects first (details in 2.8): `closeSession` is wired only to `doOnError` and `doOnCancel` and must hang off `doFinally`; `access()` uses `computeIfAbsent` and must be find-only; a bad action must not kill the session.

### 2.8 Code feasibility review (critic findings)

| # | Finding in the code | Fix in this plan |
|---|---|---|
| 1 | `GameService.access()` calls `players.computeIfAbsent`. `renderState` (10 Hz per session) and `forEachPlayer` (which iterates `players.keySet()` and calls `access`) both reach it, so a race with `closeSession` resurrects an empty `GameState`. A ghost could be written over a real Campaign. | `access()` is find-only. Only the handshake loads or creates a slot. |
| 2 | `closeSession` is called only from `doOnError` and `doOnCancel` on `Mono.zip(receive, send)`. When the client closes cleanly, `receive` completes empty, the zip completes, and neither callback fires. The state stays in the map. Likely leak, to be confirmed with a `WebSocketClient` test. | `doFinally` (all signal types) drives `detach`. |
| 3 | `handleTextMessage` catches only `JsonProcessingException`. Any `RuntimeException` thrown inside `doOnNext` errors the receive side, which tears down the session and, today, deletes the Campaign. `GameService.sendToTower` throws `InvalidInvasion` from inside a `forEach` after earlier Unit Types have already been moved to the tower, so the failure also leaves a half-sent Party that persistence would then write. | Validate every requested count first, then mutate. Catch per message, log, and answer with a notice. |
| 4 | `WebSocketConfig` is annotated `@EnableWebFlux`, so Boot's WebFlux auto-configuration backs off. `spring.codec.max-in-memory-size` then has no effect, and the default 256 KB buffer would reject a 1 MiB import. | Set the limit through `WebFluxConfigurer.configureHttpMessageCodecs`, or remove `@EnableWebFlux` and retest the WebSocket mapping; re-check under Boot 4.1.1. |
| 5 | `pom.xml` has no `spring-boot-starter-actuator` (the `management.*` yml block does nothing), no `reactor-test`, and a dead runtime `h2` (`spring-boot-starter-test` is present). Java is 24, Boot 4.1.1 with Jackson 3 (`tools.jackson`), Undertow through the community starter (ADR 0005). | P0 adds actuator and `reactor-test` (test scope) and removes `h2`. Nothing else is added: SHA-256, AES-GCM, `FileChannel` and `SplittableRandom` are in the JDK, and Jackson with JSR-310 comes with `spring-boot-starter-json` through WebFlux. No rate-limit library: a per-IP token bucket is a few lines, or deferred to the reverse proxy. |
| 6 | `GameState` is built by `new GameState(config)` and `Village` has private setters. `Tower` is created lazily on the first Departure with the shared `GameProperties.Boss` and `Enemies` objects. | `GameState.restore(snapshot, config)`, `Village.restore(food, villagers)`, `Tower.restore(...)`, and a per-Campaign copy of the two config objects (needed for Tower Memory in v1 and Mutations in v2). |
| 7 | The frontend opens `new WebSocket('ws://localhost:8080/api/ws')` in the constructor of a root service: no key, no reconnect, no close-code handling. The Prestige button is always visible, the template prints `gameState().prestigePoints`, and the Victory banner ends "Ignelia is free. Prestige to raise a new tower", which contradicts the Door and the Succession copy. | P4 and R5 replace the socket service, gate the button, and replace the banner copy. |
| 8 | `Tower` reads `ease-difficulty-per-boss` as an `int`. Hollowing needs 0.5. | Change the config type to `double`; the existing cast to `double` in `Tower.buildFloor` already tolerates it. |
| 9 | `updateInvasion` pays loot on a cleared floor and then moves on. A Door that merely skips `moveToNextFloor` would pay Floor 99's loot every tick. | The Door rule in 3.7 pays once. |

---

## 3. Prestige

### 3.1 The three angles compared

| | **Generations** (frame) | **Mutating Tower** (variation) | **Ledger conversion** (spine) |
|---|---|---|---|
| Idea | A Campaign is inherited. Legacy buys a Roll of 18 Inheritances. The Fallen wait on the Floors where they died. Tower Memory hardens after Victories. | Each Prestige is a Descent into a seeded, different Tower with 17 Mutations, Omens, a Pact, a Sealed Floor and Seal Fragments. | Counters fill a Toll bar through square roots. Rows are gated by points spent. A sealed Door at Floor 99 makes Prestige mandatory. |
| Strengths | Strong dread frame. Real decisions at each Succession. New Ground pays for new Guardians. Cheap name-keeping gives an emotional hook. | The only angle that attacks "samey" at its root. It makes the **unknown** a mechanic (nameless first contact, Atlas, Survey). The Village's Trades are the answer key. | Smallest build. Tied to real loot numbers and Package B counters. Anti-idle by construction. Faithful to Tower Wizard's gating [TW §3b, §5b]. The pacing table is derived from the config. |
| Weaknesses | 270-point tree, about 14 new terms. Fallen is the parked R18 (per-Floor persistent state). Defeat is nearly unreachable, so Hollow and the Second Company have no audience without Exodus. Memory only on Victory. | The largest build (a `FloorRules` hook extraction in private static methods with no tests, a Sealed Floor, a Phase II ending, a heavy frontend). All difficulty numbers untuned. Invented Phase II. | Risks being the same loop with bigger numbers. Par values are fitted to a model. Dead buried and Units lost reward deaths and starvation (perverse). The Door is invented. |
| **Samey** [TW §4] | Choices each Succession. | Rebuilt Tower each time. Strongest. | Weakest: only the Succession choices vary it. |
| **Winnable without prestige** [TW §3, §5] | Gen 1 stays winnable. Memory then forces the Roll. | Residual: a Gen-1 Victory is complete. | Sealed Door makes it mandatory. Strongest. |
| **Slow late game** [TW §4] | The Roll shortens openings. | Hidden Stairs start high. | Shortcuts (Floor 5, Narrow Stair). |
| **Saves broke** [TW §2] | Separate lineage record, refunds. | Migrator, points field never changes. | `pointsEarned` stored, refunds. |

All three agree on: frozen offline, five backups with fallback, export/import, and a points field that never changes. All three needed identity. Those parts are now supplied by section 2.

**Recommendation: the Ledger Toll and Door are the spine, the Generations frame is the story, and a modest Mutation layer is the variety.**

What is **dropped** from the inputs, so nothing competes:

- *Generations:* the 18-node Roll and its Lines (replaced by the 17-node Roll below), Vows, Seals X1/X2, the Binding Rite, Last Light, and the **Fallen as a mechanic** (parked, section 6).
- *Mutating:* the Sealed Floor encounters, Seal Fragments, Phase II, Vestige slots, Hidden Stairs, Disorder (Tower Memory replaces it), and 9 of the 17 Mutations.
- *Ledger:* Dead buried and Units lost as Toll counters (perverse incentives), the Offering Stone's Credit penalty, Field Dressings (it can starve a thin Pantry), Aspects and Chart the Tower, the post-tree repeatable "Weight of the Dead" (linear, the very thing criticised in [TW §3b, §4]), and Old Ledgers.
- *All three:* separate Vow, Pact and Aspect systems. They collapse into the single **Pact**.

### 3.2 When it triggers, and the in-world frame

A Campaign ends in one of three ways. Each ends in the same atomic write: the award is computed and stored (`pending`), Lifetime Ledger, Chronicle and residue are updated, the Campaign enters phase `OFFER`, and the **Succession** screen opens. The Roll can then be bought and the choices made. `BEGIN_GENERATION` starts the next Campaign. The existing `PRESTIGE` action is retired in favour of `EXODUS` (from `PLAYING`), `ROLL_BUY`, `ROLL_REFUND`, `CHOOSE` and `BEGIN_GENERATION`. "Prestige" is the name of the whole process, not an action.

| Ending | Trigger | Fiction (the Crown is [Invented]) | Next Generation starts |
|---|---|---|---|
| **Victory** | Summit Guardian slain. Requires the Door to be open (3.7). | **"The Heirs."** *"The Dragon is down. The Tower did not fall. It only listened. Your survivors' children will climb it again, because someone must."* | Normal. |
| **Defeat** | `checkGameOver()`: no Units, no Villagers, nothing to sell or spend. | **"The Second Company."** *"No one is left to ring the bell. In spring a second company finds the graves and the Ledger and reads the names aloud."* | **Hollow with a Writ:** 15 Villagers (not 20) and +100 Credits ("the Crown pays for a company, not a village"). Checked against `Village.buyVillager`: the n-th Villager costs n Credits, so Villagers 16 to 20 cost 90 Credits. The Writ is 10 Credits above the replacement price, and 5 fewer Villagers is 2.5 Food/tick less. Roughly a wash in cost, but it makes the start different. |
| **Exodus** (new) | Voluntary, from `PLAYING`, once the Toll forecast is at least 3 points **and no Expedition is active or Held** (Extract first, so Exodus cannot be used to abandon a Party on the Tower). | **"The Flight."** *"The Village leaves in the night. The Tower keeps whoever stayed."* | Normal. |

Why Exodus exists: Defeat is nearly unreachable today. `canRecover()` stays true while any Credits, Materials, Relics or more than 1 Food remain, and Credits in practice never reach zero by Recruiting (a purchase needs `credit > cost`, so a positive remainder is left). A stalled player would have no end. Exodus is the escape valve, and the Toll's own gate stops it being spam (3.4). The Defeat content (Hollow, Writ, the Second Company) is cheap and kept because the outcome already exists in code and in the UI copy, but expect almost no one to see it.

Defeat adds a **pity floor of 1 point**, but only if the Campaign completed at least 3 qualifying Expeditions (`defeat-pity.min-qualifying-expeditions`). Without that condition a deliberate five-minute Defeat loop would farm 1 Legacy per cycle, which is faster than honest play (4.7 to 11 Legacy per planned hour in 3.10).

The Succession screen's copy never congratulates. It shows the Toll as plain numbers and ends with *"The Tower keeps count."*

Supporting manga facts:

- [Manga §4, wiki] humans who die in the Tower become undead (basis for the Tower's dread and any later Fallen).
- [Manga §4, §7, wiki] royal blood opens some doors and breaks seals, but Gatekeepers can lock doors even against it (basis for the Door).
- [Manga §8] the Tower's origin, why it descended, and what is at the Summit are open. `CONTEXT.md` already says the Summit is "for now" a Dragon.
- [Manga §2, ch.31, official, partly seen] "Miligore's strike begins to break down the tower's order" (basis for the Tower changing).

### 3.3 What is lost and what persists

| Thing | At the start of Generation N+1 | Where it lives |
|---|---|---|
| Credits | 10 (config `start.credit`), plus Roll | `campaign.credit` |
| Villagers, Pantry | 20 Villagers, 100 Food, all Farmers (Defeat: 15, plus Writ) | `campaign.village` |
| **Trades, Stockroom (Salt, Spores, Knowledge), Quarry Heap, Tomes** | Emptied. All Fields. | `campaign.village` |
| Vault (Materials, Relics), Loot in hand | Emptied | `campaign.vault`, `expedition` |
| Units, Party, Waves, Supplies | All lost, with their items, sanity and phobias. The next Generation begins with its four **Founders** (Yuva is five times stronger in Generation 1 only, an ordinary Warrior after), plus the **Heir** if owned. | `campaign.reserves` |
| Upgrades, Roster, Milestones | Cleared. Roster back to Warrior, Archer, Porter. | `campaign.upgrades`, `milestones` |
| Shortcuts opened by kills or Expeditions | Re-earned | `campaign.shortcutsOpened` |
| **Village Tier** | Back to I (Village Charter: II) | `campaign.villageTier` |
| Highest Floor, Expeditions, kills, summary | Zeroed | `campaign.*` |
| **Campaign Ledger** | Zeroed | `campaign.ledger` |
| **Tower, discovered Floors, Easing (`clearedBosses`)** | A new Tower. Easing is forgotten. | `campaign.clearedBosses`, `blueprint` |
| **Legacy, the Roll, spent and unspent** | **Persist** | `meta.prestige` |
| **Residue** (Toll Marks carried) | **Persist** | `meta.residue` |
| **Lifetime Ledger, best G, Epitaphs** | **Persist** | `meta.lifetime`, `meta.epitaphs` |
| **Chronicle** (every ended Campaign, up to 5 names of Units lost per entry) | **Persist** | `meta.chronicle` |
| **Atlas** (Mutations met) | **Persist** | `meta.atlas` |
| **Generation number, `towerSecret`, saved Loadouts** | **Persist** | `meta` |

**The Tower forgets and remembers.** Floors and Easing are cleared, so Guardians return at full strength. But the Tower **hardens**. Tower Memory `m = min(Generation - 1, 4)` is derived, never stored:

- garrison budget x(1 + 0.05m);
- Guardian health x(1 + 0.08m).

Tower Memory is a tax on repeating the same Floors, so a later Generation is not just Generation 1 with a head start. It does **not** by itself force Prestige: Generation 1 has `m = 0`. What forces Prestige is the Door (3.7). Memory ships in v1: it only needs a per-Campaign copy of the `Boss` and `Enemies` config objects passed to `new Tower(...)`, with no `FloorRules` refactor.

Worked against the real config:

- Floor 44 garrison budget is `3 + 0.8*44 + 0.04*44^2 = 115.6`. At m = 3 it is 132.9.
- A Floor 50 Stone Golem Guardian is `300 * (3.0 + 0.05*50) = 1650` hp. At m = 3 it is 2,046.
- The Summit Dragon is `5000 * (3.0 + 0.05*100) = 40,000` hp. At m = 4 it is 52,800. See 3.11 before accepting that number.

### 3.4 The Toll bar and point formula

Everything the Village does is counted in the **Campaign Ledger**. Counters only go up. Spending never lowers them. The **Lifetime Ledger** is the sum of Campaign Ledgers and drives Epitaphs.

| Counter | Counted when (hook) | Anti-exploit |
|---|---|---|
| Food grown | each lifecycle tick by `Village.getProductionRate()` (`triggerLifecycle`) | square root, no offline accrual, frozen when away |
| Materials banked | Extraction or Victory only (`bankLoot`); Quarry Heap deliveries pass through it | a Wipe adds 0; selling never lowers it |
| Credits banked | Extraction or Victory only; Credits **found on Floors** only | Credits from selling Food, Materials or Relics never count |
| Relics banked | Extraction or Victory only | selling never lowers it |
| Expeditions completed | counts only if at least 3 Floors were cleared (`RunSummary.floorsCleared`) | relaunch-and-extract spam adds 0 |
| Ascent | Floors above the baseline `B` (below), measured by Highest Floor | maximum, not a sum; capped at Floor 99 (Floor 100 is paid through its Guardian) |
| Guardians slain | distinct Guardian Floors cleared (the Easing set), at most 10 | |

Highest Floor is recorded on arrival (`recordFloorReached`), so a Wipe still pays Ascent for Floors reached. That is deliberate: the Wipe already costs all the Loot, and pushing to die higher is a real gamble.

```
S  = sum over 5 counters of  M_i * sqrt(c_i / p_i)  +  0.4 * max(0, min(HighestFloor,99) - B)  +  1.5 * G
T  = S * (1 + 0.05 * min(L, 8))  +  Residue
NG = max(0, G - G_best)                       // "New Ground": Guardians beyond the lifetime best
P  = floor(T / 9) + NG + Offering             // Legacy gained
Residue_next = T mod 9                        // the remainder is never lost
```

- `B` is the **inherited Start Floor actually used**: the highest Start Floor that came from the Roll or an Epitaph (not one earned inside this Campaign) from which an Expedition launched at least once. Ascent Marks then pay only for Floors the Campaign worked. This keeps Shortcut-heavy later Generations from being paid for Floors they skipped, so Narrow Stair is a real trade. Shortcuts opened by kills or Expeditions inside the Campaign do not count toward `B`, because the Floors below them were worked to open them. A player who owns Narrow Stair but always launches from the Base (for example with a Party over its limit) has `B` = 0 and is paid for what they climbed.
- `L` is the total **Load** of the Mutations that **took effect at least once** in this Campaign (imposed or Pact, 3.8). It is 0 in Generation 1 and in v1. Counting only Mutations met stops a Mutation placed beyond the Floor where the player stopped from paying a bonus.
- `Offering` is from the Offering Stone (at most +3).

| Counter | M_i (Marks at par) | p_i (par) |
|---|---|---|
| Food grown | 3 | 60,000 |
| Materials banked | 6 | 17,000 |
| Credits banked | 3 | 6,800 |
| Relics banked | 4 | 320 |
| Expeditions completed | 3 | 17 |

The Ascent weight is 0.4 per Floor (39.6 at most) and Guardians 1.5 each (15 at most). **The par values are fitted to a modelled first Campaign and must be refitted after the first real playtest.** They are internally consistent with the real loot: 17 Expeditions banking 17,000 Materials, 6,800 Credits and 320 Relics is about 1,000 Materials, 400 Credits and 19 Relics each, which is what clearing roughly Floors 1 to 20 (or 15 to 30) pays (Floors 1 to 20 is 1,350 Materials, 420 Credits and 22.5 Relics by the loot formulas).

Sanity checks:

- **One Floor 1 to 10 Expedition** banks 375 Materials, 150 Credits and 3 Relics, for 0.89 + 0.45 + 0.39 + 0.73 (the Expedition) + 4.0 (Floors) + 1.5 (Guardian) = **about 8 Marks**. With the first Guardians that is roughly one point.
- **Idle does not pay.** 20 Villagers at 0.5 Food/tick is 10 Food/tick. 90 minutes is 54,000 Food, worth `3*sqrt(0.9) = 2.85` Marks, under a third of a point. An 8-hour abandoned tab is 288,000 Food, worth 6.6 Marks, under one point.
- **Doubling a source gives 1.41x its Marks.** Ascent (Floors plus Guardians) is the one near-linear source, so the player must climb.
- **Early Prestige is not strictly best.** Legacy per **model** hour is flat inside Generation 1 (about 6.2/h at Floor 20, 5.8 to 6.0/h at Floor 35) and rises in later Generations through New Ground and Mutation multipliers. The pacing table in 3.10 uses **planned** hours (model hours times 1.25), which is why it shows 4.7 for the same Campaign.
- **Exodus requires a forecast of at least 3 points** (about 27 model minutes in Generation 1). Victory and Defeat always convert and are not gated.
- **NG is bounded.** A Guardian counts for New Ground once in a lifetime, so New Ground totals at most 10 Legacy across all Generations (10 of the 46 earned by the first Victory in the v2 table).

**Display:** a thin pixel bar reads "Toll 37.9 / next Legacy at 45.0", with a hover breakdown per counter and a "New Ground: +3" line. It never empties on spending, because only counters feed it. The HUD also shows the forecast "If you left now: about N Legacy". This teaches the terms and creates tension: the forecast is a standing offer to leave while the Village still stands.

### 3.5 The Roll (the permanent tree)

**Legacy** is spent on **Inheritances**. They sit in four **Courses** (rows). A Course opens when enough Legacy is already **spent** (cumulative gating, so spending never hurts) [TW §3b, §5b: rows gated at 5, 9, 13, 18]. Gates here: Course I 0, II 5, III 12, IV 21. The Roll is **17 Inheritances for 64 Legacy**, plus 3 free **Epitaphs**. In v1 two nodes have nothing to act on (Reader of Omens, Second Pact) and show as locked, so v1 offers 15 nodes and 55 Legacy.

Kind: P = power, D = decision or access, Q = quality of life. The count is 6 P, 9 D and 2 Q, so most nodes add options rather than bigger numbers. Free respec is allowed while the Succession screen is open (`paid` refunds). The Roll seals when the Generation begins.

| Course | Inheritance | Kind | Cost | Effect (tied to config) |
|---|---|---|---|---|
| I | Seed Grain | P | 1 | Pantry starts at 150 Food (config 100). Start Credits 20 (config 10). |
| I | Muster Roll | Q | 1 | One click resends the last Party and Loadout. Extraction stays manual. No autobuy [TW §3b]. |
| I | Hardy Stock | P | 2 | Start with 25 Villagers (config 20), so 12.5 Food/tick. Worth 90 Credits at the n-Credit Villager price. |
| I | Whetstone Habit | P | 2 | +5% Party damage (Sharpened Arms gives +10% per level for 35 Materials, x1.7 per level). |
| II | The Dowry | D | 3 | Each Succession choose one: 80 Credits, 40 Materials (the exact cost of the Healer Unlock Upgrade) or 3 Relics (the exact cost of the Necromancer Unlock Upgrade). All three are worth 75 to 80 Credits at the config sell prices (Materials 2, Relics 25), so none is dominated. |
| II | Heavier Packs | P | 3 | +25 base Carrying Capacity (one Supply Lines level, which costs 30 Materials in a Campaign). |
| II | Village Charter | P | 3 | Start at Village Tier II (Trades and Salt Pans open). Depends on Package B. |
| II | The Heir | D | 3 | Choose Warrior, Archer or Porter. Start with one extra free Unit of that Unit Type, on top of the four Founders. Its total health is +50% (a Warrior has 30 instead of 20) before it is divided across Body Parts. |
| III | **Blood of the Door** | D | 6 | Opens the Door at Floor 99 (3.7). |
| III | Narrow Stair | D | 5 | A Shortcut starting at Floor 21 (the first Floor after the Floor 20 Guardian, not a Surge Floor, garrison budget 37.4), usable only by a Party of at most 12 Units. [Manga §4, review and wiki: hidden spiral staircase from floor 1 to 80.] The Floor and the Party limit are [Invented] placeholders (see below). |
| III | Overreach | D | 4 | A switch in the Loadout panel per Expedition: supply drain x1.3 (1.5 to 1.95 per Unit per tick), Starvation damage x2 (5 to 10), all Loot x1.5. |
| III | Quartermaster's Tally | Q | 3 | Three saved Loadouts and a Supplies-runway forecast. |
| IV | Hard-Won Habits | P | 8 | All four Milestones are pre-granted each Generation (Captain Eliquo joins, Sargan joins, Seasoned Delvers, Relic Lore: the whole Milestone track, which a player normally clears in the first 8 Expeditions). |
| IV | The Open Breach | D | 6 | Choose one Guardian Floor slain in an earlier Generation. It holds only its garrison, with no Guardian and no x3 loot. It counts as slain for Easing (a free Easing step for every Floor below it) but **not** for G. |
| IV | Reader of Omens (v2) | D | 4 | Reroll the three Omens once per Succession. Survey costs 10 Knowledge (not 15). |
| IV | Offering Stone | D | 5 | At the end of a Campaign, Relics still in the Vault are offered: 300 Relics is +1 Legacy, at most +3 per Generation. They still count as banked. Pays for itself only after about four Generations of keeping 300 or more unsold Relics (300 Relics sell for 7,500 Credits). |
| IV | Second Pact (v2) | D | 5 | Accept two Omens as Pacts (subject to the Load cap, 3.8). |

**Narrow Stair numbers.** A Floor 35 Shortcut with a Party of 5 does not work on the real config: Floor 35 is a Surge Floor (budget 80 x 1.5 = 120, about 70 enemies and 2,300 hp of garrison), a 5-Unit Party deals at most 25 damage per tick, and 5 Units drain 7.5 Supplies per tick against a capacity of 100 plus 60 per Porter. Floor 21 and 12 Units are placeholders chosen to be survivable on paper. The real values need the measured Supplies and damage figures from 3.11.

**What the Roll is worth, in config terms.** By the end of Course I and II (15 Legacy), a Generation starts with about 90 Credits of Villagers (Hardy Stock), 10 Credits and 50 Food (Seed Grain), about 80 Credits of Dowry, a free Heir, one Supply Lines level, half a Sharpened Arms level, Village Tier II and the Floor 5 Shortcut. That is roughly 250 Credits of head start against about 900 Credits (when sold) from one Floor 1 to 10 Expedition. **The Roll is a head start, not a power spike**: a player cannot trivialise Floors 1 to 50 after two Prestiges, because Floor 50 has a garrison budget of 143 and a 1,650 hp Guardian that the Campaign's own economy still has to beat. The same fact is the design's weak point (3.11): if the climb is limited by strength rather than time, the Roll cannot lift it.

**Epitaphs** (free, granted when the Lifetime Ledger crosses a threshold, stored as a ratchet):

| Epitaph | Threshold | Effect |
|---|---|---|
| First Footing | Lifetime Highest Floor at least 10 | The Vanguard Camp Shortcut (Floor 5, an existing `ShortcutType`) is open from the start. |
| Names on the Wall | Lifetime Units lost at least 100 | All Units +5% maximum health. |
| Ten Seals Broken | Lifetime Guardians slain at least 15 | Easing x1.25 (`ease-budget-per-boss` 0.15 to 0.1875). |

**Finite on purpose.** After the 64 Legacy there is no sink. A definite ending is a Tower Wizard strength [TW §4], and a repeatable linear multiplier is the criticised pattern [TW §3b, §4]. After the first Victory the arc is complete. Further Generations only finish the Roll and add Chronicle entries.

### 3.6 Succession choices (the decision nodes)

At each Succession the player may face, in order: Roll purchases and respec, the Dowry, the Heir's Unit Type, the Open Breach Floor, Overreach as a standing option, and in v2 the three **Omens** and the Pact (3.8). Two Generations on the same Roll can therefore play differently. Choices are stored in `campaign.choices` during the OFFER phase.

### 3.7 The Door (mandatory Prestige)

The Party can clear Floor 99 and cannot climb further. **Highest Floor stays 99** and the UI reads *"The door does not open."* **Blood of the Door** (6 Legacy, Course III) opens it, permanently, for every later Generation. [Manga §4, §7: royal blood opens doors and breaks seals; Gatekeepers can lock doors even against it, wiki-grade.] Everything here is [Invented]: no number is canon.

Implementation is one check in `GameService.updateInvasion` before `tower.moveToNextFloor()` when `clearedFloor == maxFloor - 1` and the Door is sealed. **The check must not simply skip the move**, because the same branch pays the Floor's Credits and Materials on every tick it runs. Instead set `doorSeen` on the Expedition after the first payment and skip the whole branch afterwards. The Party then stands on a cleared Floor 99 while Attrition (1 hp/s per Unit) and Supplies drain continue. The only exits are Extraction and Wipe. Lingering costs lives, which is the intended feeling.

The Door is foreshadowed from the first Campaign (the Floor 90 Guardian fight, a line on the Floor 99 screen). The Door position follows `max-floor` in config and moves if the scaling pass in 3.11 changes the Tower's height or shape.

**Earliest Victory.** Blood of the Door needs 12 Legacy already spent in Courses I and II plus its own 6, so **18 Legacy in total**. That is at least one Prestige (a Generation 1 that reaches Floor 99 earns about 19 and could open the Door at Generation 2) and typically three (Generation 4, 3.10). A strong player reaches Generation 3 only by earning 9 and 9 across the first two Campaigns.

### 3.8 Tower variation (v2): Mutations, Omens, Pact

Generation 1 is the Tower the player knows: no Mutations. From Generation 2, the Tower has a deterministic **layout** from a seed:

```
seed     = first8bytes( SHA-256( meta.towerSecret + ":" + generation ) )
floorRng = new SplittableRandom( seed ^ (floor * 0x9E3779B97F4A7C15L) )   // layout only
```

Garrison rolls keep their existing `ThreadLocalRandom` variance. The imposed Mutations and the three Omens are **fixed at the end of the previous Campaign and stored** (sealed) in `campaign.blueprint`, so a reload or import never rerolls them. The seed and `towerSecret` never go to the client.

**Imposed Load** by Generation: 2, 4, 5, 6 for Generations 2, 3, 4, 5 and later. A Mutation weighs 1, but x0.25 if it was imposed in the previous Generation, so the Tower rarely repeats itself. Drawing stops when the target is reached, never exceeding it by more than 1. **Total Load never exceeds 8**, and that includes Pacts: an Omen that would take the total above 8 is shown greyed and cannot be accepted. (Imposed 6 plus a Load-3 Pact is 9, so from Generation 4 the cap does bind.)

**Omens:** after the imposed set is fixed, three Omens are offered, one each of Load 1, 2 and 3. The player accepts one as the **Pact** (two with Second Pact). It becomes a Mutation of that Generation. An Omen not yet in the Atlas shows only its kind, Load and "unknown". **The player is betting on a number**, which is the fear of the unknown made into a decision. Load pays out only if the Mutation is met (3.4).

**The eight Mutations.** Every number lives under `game.prestige.mutations.<ID>.*`. Garrison weights are the real `EnemyStats.weight` values (Slime 1, Dead Soldiers 1, Tower Beast 2, Chimera 3, Chymus 10, Golem 8, Necromancer 20, Dead Dragon 50, Dragon 90).

| Mutation | Load | Placement | Effect | Counterplay (Village link) | Source |
|---|---|---|---|---|---|
| **Long Stair** | 1 | A 10-Floor band starting in [12, 81] | `supply-drain-per-unit` 1.5 becomes 2.25 in the band (Salted 1.25 becomes 1.875) | Porters, Supply Lines, Salted Supplies (Salt), extract at the band's end | [Invented] |
| **Veil** | 1 | Two 8-Floor bands | Garrison size and Floor Difficulty are hidden until half the garrison is dead. No numeric change, only the unknown. | **Survey** (Knowledge). Experienced Atlas. | [Invented] |
| **Hollowing** | 2 | Whole Tower | Easing weakens: `ease-budget-per-boss` 0.15 becomes 0.05 and `ease-difficulty-per-boss` 1 becomes 0.5 (config type change, 2.8). After 5 Guardians a lower Floor keeps 77% of its budget (not 44%). | Shortcuts and Narrow Stair, so the player does not depend on Easing | [Invented] |
| **Severed Stair** | 2 | Whole Tower | Only 2 of the 4 base Shortcuts exist (Floors 3, 5, 10 and 15 today; the 2 kept are seeded). Their thresholds x1.5. | Reinforcement Waves, Porters, First Footing | [Manga §4, review and wiki: the staircase's damaged top section was repaired] for the idea; numbers [Invented] |
| **Looping Rooms** | 2 | 3 non-Guardian Floors in [14, 70] | Clearing the Floor rebuilds it once at 0.6x budget. Credits, Materials and the 3 Supplies pay on the second clear only. About 1.6x the fighting and 2x Attrition (1 hp/s per Unit) there. | Salting, Porters, Supply Lines; extract before a known Floor; Survey | [Manga §4, wiki: floors 14-15 have "looping identical rooms"] for the idea; numbers [Invented] |
| **Spore Blight** | 2 | A 20-Floor band starting in [20, 40] | Heal casts x0.5 in the band. No Spore Loot is found. A Panacea already in the Loadout is unaffected. | Panacea and Remedy from the Stockroom (Spore Cellar, Village Tier III) | [Manga §4, wiki: Dragon Spores on floor 20; §7: healing is a resource]. Effect [Invented]. |
| **Unburied** | 3 | 3 Guardian Floors, not the Summit | At 0 hp the Guardian rises once at 35% health and +25% damage. Easing and loot come after the second death. A Floor 50 Golem goes from 1,650 to about 2,230 effective hp. | Burst damage (Rogue 5 per tick), Mana held in reserve | [Invented]. Loosely echoes [Manga §7, wiki: the Azza-lugh-Novael relic as a one-use resurrection power] and the Necromancer's wish to revive the Upper Body Dragon, which [Manga §11] reports inconsistently and partly as reader speculation. |
| **Chymus Spread** | 3 | All garrisons from Floor 23 | A Chymus Insect is added to the pool whenever Floor Difficulty is 3 or more, so from **Floor 23** instead of 45. Capped at 30% of the budget by weight (at Floor 23: budget 42.6, cap 12.8, so about one Chymus). Each deals 100 damage per tick and has 100 hp, so one Chymus kills about one Unit per tick until the Party kills it. Guardian types unaffected. | **Salt Pack** (+50% damage on Chymus) carried by a cheap Porter or Tank, which draws the attack (needs the Package B targeting hook, since `enemiesAttack` picks targets at random today). Salt Pans open at Village Tier II (Highest Floor 5), before Floor 23. | [Manga §5, wiki and review: Chymus Insect; salt dissolves the mucus; it targets the salt carrier]. Numbers [Invented]. |

(Standard Floor Difficulty `d = floor(9 * floor/100) + 1` brings Tower Beast from Floor 23, Chimera 34, Chymus 45, Golem 56, Necromancer 67, Dead Dragon 78, Dragon 89. A Guardian takes the Enemy Type at index `d`, one step above the garrison pool, which is why Floor 70 already holds a Dead Dragon, 3.11.)

**Fairness rules** (no unanswerable Mutation):

- A Mutation's first effect lands no earlier than its counter's unlock Floor plus a margin.
- The first Mutation effect of a Generation never lands below Floor 12.
- Hollowing and Severed Stair are never imposed together.
- Total Load is capped at 8, Pacts included.

**Pact payoff.** `L` multiplies the Toll by `1 + 0.05 * min(L, 8)`, so +40% at most. A Load-3 Pact is +15%, worth about 1 to 1.6 points at Generation 2 to 4 numbers. Imposed Mutations also raise `L`: the multiplier is the compensation for a harder Tower.

**Atlas.** The first time a Mutation is met, its entry gets a terse record. From then on its name, Load and counterplay show on the Omen screen, but **never its location**, unless the player spends **Survey** (3.9).

### 3.9 Interaction with the Village systems

| System | What Prestige does or needs |
|---|---|
| **Trades** | Start all-Fields each Generation. The Atlas tells the player which Trade to reassign first. The Trade allocation is the second half of every Generation's puzzle. |
| **Stockroom, Quarry Heap, Pantry** | All emptied. Mutations only touch Loot in hand and never the Vault or Stockroom, so none punishes the Village directly (the brainstorm says to ship few punishers). Quarry Heap deliveries count in Materials banked only at Extraction. |
| **Salt** | The insurance for Chymus Spread (Salt Pack) and Long Stair (Salted Supplies). Under neither, Salters are a waste, which makes the Trades a choice each Generation. |
| **Dragon Spores** | The answer to Spore Blight (Panacea, Remedy). Under no Spore Mutation a Cellar is a luxury. |
| **Knowledge** | Gains its second long-lived sink, **Survey**: 15 Knowledge (10 with Reader of Omens) reveals the kind and Floor of the next mutated Floor above Highest Floor. At the brainstorm's 0.05 Knowledge per Scribe per tick, ten Scribes make 0.5 per tick and pay for one Survey every 30 ticks. The Stockroom cap must stay above 15 (the brainstorm already keeps it above the next Tome's cost). [Invented] |
| **Village Tier** | Back to I (Village Charter: II). Gate counters come from the Campaign Ledger, so Tier is re-earned each Generation. Village Tier and the Toll read the same counters, with no inheritance, so the Toll cannot be inflated. |
| **Shortcuts** | Cleared. Re-opened by kills and Expeditions, or by Roll and Epitaph (First Footing, Narrow Stair). Severed Stair cuts them. |
| **Easing** | Forgotten each Generation. Restored in small ways: Ten Seals Broken (x1.25), the Open Breach. Weakened by Hollowing. |
| **Guardians** | Harder through Tower Memory and Unburied. Skippable by the Open Breach. Distinct Guardian Floors feed `G`. |
| **Loadout** | Overreach is a Loadout switch. Saved Loadouts persist in `meta.savedLoadouts`. |

### 3.10 Pacing: the first three Prestiges, plus the first Victory

The model assumes 4 to 6 seconds per Floor, 15 to 17 full-climb Expeditions per Campaign and 130 to 200 seconds between them. "Planned" minutes are model minutes plus about 25% for recruiting and thinking. I took the Ledger model's counter values and assumed the Roll offsets the Mutations, so the underlying counters stay the same and the Mutations act only through `L` (assuming they are met). Real Generations 2 and later probably run 10 to 20% longer.

**The Highest Floor row (35, 55, 75, 99) is a target, not a prediction.** The Roll adds a head start of about one early Expedition (3.5), and Easing is forgotten each Generation, so the model silently assumes the climb is limited by **time**, so that a faster opening buys more Floors. If it is limited by **strength** (3.11), no Generation will reach the Floors in the table. Treat the table as the shape the scaling pass has to produce.

**v2 table (with Mutations).** Marks counters use the same sources as 3.4.

| | **P1 (end of Gen 1)** | **P2 (Gen 2)** | **P3 (Gen 3)** | P4 (Gen 4) |
|---|---|---|---|---|
| Ending | Exodus | Exodus | Exodus | **Victory** |
| Planned time | 90 min | 80 min | 75 min | 95 min |
| Highest Floor | 35 | 55 | 75 | 99 then 100 |
| Guardians slain, G | 3 | 5 | 7 | 10 |
| Baseline `B` | 0 | 5 (First Footing, used) | 5 | 5 |
| Load met (imposed plus Pact) | 0 | 2 + 2 = 4 | 4 + 2 = 6 | 5 + 2 = 7 |
| Marks: Food, Materials, Credits, Relics, Expeditions | 3.4, 6.0, 3.0, 4.0, 3.0 | 3.8, 9.4, 4.7, 6.4, 2.9 | 4.0, 12.3, 6.2, 8.5, 2.8 | 4.7, 16.6, 8.3, 11.4, 2.9 |
| Ascent Marks, Guardian Marks | 14.0, 4.5 | 20.0, 7.5 | 28.0, 10.5 | 37.6, 15.0 |
| `S` | 37.9 | 54.7 | 72.3 | 96.5 |
| `1 + 0.05L` | 1.00 | 1.20 | 1.30 | 1.35 |
| Residue in | 0 | 1.90 | 4.54 | 8.53 |
| **T** | **37.9** | **67.54** | **98.53** | **138.81** |
| floor(T/9), NG | 4, 3 | 7, 2 | 10, 2 | 15, 3 |
| **Legacy earned** | **7** | **9** | **12** | **18** |
| Residue out | 1.90 | 4.54 | 8.53 | 3.81 |
| Cumulative | 7 | 16 | 28 | 46 |
| Legacy per planned hour | 4.7 | 6.8 | 9.6 | 11.4 |

(Counting the Summit Guardian gives 15.0 Guardian Marks, `S` 96.5 and `T` 138.81. The Legacy earned is 18.)

**v1 table (no Mutations, `L` = 0).** Same counters. v1 ships first, so this is the table the first playtest will see.

| | P1 | P2 | P3 | P4 |
|---|---|---|---|---|
| `S` | 37.9 | 54.7 | 72.3 | 96.5 |
| Residue in | 0 | 1.90 | 2.60 | 2.90 |
| **T** | 37.9 | 56.6 | 74.9 | 99.4 |
| floor(T/9), NG | 4, 3 | 6, 2 | 8, 2 | 11, 3 |
| **Legacy earned** | **7** | **8** | **10** | **14** |
| Residue out | 1.90 | 2.60 | 2.90 | 0.40 |
| Cumulative | 7 | 15 | 25 | 39 |
| Legacy per planned hour | 4.7 | 6.0 | 8.0 | 8.8 |

The same spending plan works in v1 (below), because Generation 4 still holds 10 Legacy at its start.

The whole arc to the first Victory is 340 planned minutes, about 5.7 hours, inside the 3 to 8 hour target **if** the target is read as the whole arc (decision 1).

**Spending plan** (shows the gates and the Door; the same in v1 and v2 up to Generation 3, and in Generation 4 v1 holds 10 where v2 holds 13):

| At start of | Held (v2) | Buys | Spent | Gate reached |
|---|---|---|---|---|
| Gen 2 | 7 | All of Course I (6) | 6 | Course II opens (5) |
| Gen 3 | 10 (v1: 9) | Three Course II nodes (9): Village Charter, Dowry, Heavier Packs | 15 | Course III opens (12) |
| Gen 4 | 13 (v1: 10) | Blood of the Door (6) and The Heir (3) | 24 | Door open, Course IV opens (21) |
| Gen 5 (after Victory) | 22 | Narrow Stair (5), Hard-Won Habits (8), more | | |

The whole Roll (64) completes around Generation 7, about 9 to 10 hours, as post-game. Skipping nodes is a real choice: Course I (6) plus two Course II nodes (6) is exactly 12 spent, so a rushing player can reach Course III with one Course II node unbought.

**What each Generation starts with** (using the plan above):

| | Gen 2 | Gen 3 | Gen 4 |
|---|---|---|---|
| Villagers, Food, Credits | 25, 150, 20 | 25, 150, 20 | 25, 150, 20 (plus Dowry) |
| Units | The four Founders | The four Founders | The four Founders and the Heir |
| Shortcut at start | Floor 5 (First Footing) | Floor 5 | Floor 5 |
| Village Tier | I | II (Village Charter) | II |
| Door | sealed | sealed | **open** |
| Tower Memory m | 1 | 2 | 3 |
| Mutations (v2) | 1 imposed (Load 2) plus a Pact | 2 imposed plus a Pact | 3 imposed plus a Pact |

### 3.11 Feasibility of the climb on the real config

This section exists because the pacing table assumes Floors the current enemy numbers do not allow. It uses only values in `application.yml`, `EnemyStats`, `UnitStats` and `Tower.buildFloor`. None of it requires a playtest.

**Guardian health and damage by Floor** (archetype index = `min(floor difficulty, 8)`, health = base x (3.0 + 0.05 x Floor), damage x1.25):

| Floor | Guardian | Health | Damage per tick |
|---|---|---|---|
| 10 | Dead Soldiers | 52.5 | 6 |
| 20 | Tower Beast | 160 | 15 |
| 30 | Chimera | 315 | 31 |
| 40 | Chymus Insect | 500 | 125 |
| 50 | Stone Golem | 1,650 | 38 |
| 60 | Necromancer | 1,200 | 125 |
| 70 | Dead Dragon | 13,000 | 250 |
| 80 | Dragon | 35,000 | 625 |
| 90 | Dragon | 37,500 | 625 |
| 100 (Summit) | Dragon | 40,000 | 625 |

**Summit arithmetic.** The Dragon takes x0.5 from Melee, Ranged and Magic. The best sustained damage per Unit is a Rogue at 5 x 0.5 = 2.5 per tick (a Draco-Metamorph deals 5 but has 5 Mana, so about 5 casts). The Dragon kills one Unit per tick (625 is above any Unit's health). With N Rogues at the start and one dying per tick, total damage is about 1.25 N^2, so 40,000 hp needs about **N = 179 Rogues**. Supplies drain 1.5 per Unit per tick, which is about 0.75 N^2 = **24,000 Supplies** over that fight alone. Carrying Capacity is 100 plus 60 per Porter, plus 25 per Supply Lines level and 25 from Relic Lore, and Supplies refill only 3 per Floor cleared. That is about 400 Porters. Ten Sharpened Arms levels (about 4,200 Materials for the last level) halve the figures and still leave about 190 Porters. The Floor 80 Dragon is already past reach. Floor 70 (Dead Dragon, x2 from Melee and Ranged) needs about 51 Rogues and about 1,950 Supplies, which is hard but conceivable.

**Conclusions.**

- The Door at Floor 99 is not the binding wall. Floor 70 to 80 is. Highest Floors of 75 and 99 in 3.10 are unreachable until the enemy and Supplies numbers are reworked.
- Tower Memory's Guardian health bonus (x1.08 per step, up to x1.32) is a rounding error next to this, but on the Summit it adds 12,800 hp to an already unreachable target. Cap the Memory bonus on Guardian Floors above 70 until the pass is done.
- A sizing rule for the pass: the Party size N needed for a Guardian is about `sqrt(HP / (1.25 x d))`, with `d` the best per-Unit damage per tick after Weakness. For about 80 Units against a Dragon (d = 2.5), HP should be near 20,000, half the current value, and Supplies per Floor need a matching rise.
- The Supplies limit applies earlier too. The brainstorm computes an Expedition lasting about 11 ticks on 100 Supplies for 6 Units. Reaching Floor 99 in one Expedition is impossible. It is a chain of Reinforcement Waves and high Start Floors, which is why Shortcuts, Narrow Stair and Village Charter matter more than the table suggests.

This is **decision 13**: the scaling pass has to happen before any par value in 3.4 or any Highest Floor in 3.10 is trusted.

### 3.12 Tower Wizard failure-mode audit

| Failure mode [TW §4, §5] | v1 | v2 | Verdict |
|---|---|---|---|
| **Linear** | Marks are damped by square roots except Ascent and Guardians. The Roll is finite (64) with no repeatable sink. P nodes are one-off head starts. | Adds a multiplier that depends on met Mutations. | Mostly avoided. The finite Roll and the sqrt curves stop it being a pure number-goes-up loop. |
| **Samey** | **Not avoided.** Each Generation is the same Tower layout, harder by Tower Memory, with a head start. The decision nodes (Dowry, Heir, Breach, Overreach, which Trades) vary the opening only. | Mutations, Omens and the Pact change the layout and force different Trades. | v1 is the weak point. Playtest v1 specifically for sameness before deciding how much of v2 to build. |
| **Avoidable (winnable without it)** | Avoided by the Door: Victory needs 18 Legacy, so at least one Prestige. Before the Door a player may skip Prestige by staying in one long Campaign, which costs only a head start. | Same, plus Memory. | Avoided, with one caveat: a player who never meets the Door (never reaches Floor 99) can ignore Prestige indefinitely. That is acceptable, since they also never see an ending. |
| **Strictly dominant (always prestige at once)** | No: Exodus needs 3 forecast points, and Legacy per model hour is flat in Generation 1 (3.4). | Rises in later Generations. Cycling shallow Campaigns at the 3-point gate earns about 6.7 Legacy per hour, below the deep-run rates in 3.10, so deeper runs win. | Not dominant on the model. Log the Toll per Expedition to confirm. |
| **Slow late game** | Shortcuts, Village Charter and Narrow Stair shorten openings. | Same. | Depends on 3.11. If the late Floors are unreachable, the late game is not slow, it is blocked. |
| **Perverse incentives** | Exodus can end a Campaign at a rate that is slower than deep play. Defeat farming is closed by the 3-Expedition condition. Names on the Wall rewards loss mildly. | Load is paid only for Mutations met. | Acceptable. |

### 3.13 Config (`game.prestige.*`)

```yaml
game:
  prestige:
    points-divisor: 9.0
    min-points-to-exodus: 3
    defeat-pity: { points: 1, min-qualifying-expeditions: 3 }
    ascent-marks-per-floor: 0.4
    guardian-marks: 1.5
    qualifying-expedition-floors: 3
    door-floor: 99
    load-mark: 0.05
    load-cap: 8
    counters:
      food-grown:       { marks: 3.0, par: 60000 }
      materials-banked: { marks: 6.0, par: 17000 }
      credits-banked:   { marks: 3.0, par: 6800 }
      relics-banked:    { marks: 4.0, par: 320 }
      expeditions:      { marks: 3.0, par: 17 }
    course-gates: { 2: 5, 3: 12, 4: 21 }
    roll: { cost-scale: 1.0 }        # the one knob for retuning every Inheritance cost
    dowry: { credits: 80, materials: 40, relics: 3 }
    heir: { health-bonus: 0.5 }
    narrow-stair: { floor: 21, max-party: 12 }
    offering: { relics-per-point: 300, max-points: 3 }
    defeat-start: { villagers-lost: 5, writ-credits: 100 }
    memory: { max-steps: 4, budget: 0.05, guardian-health: 0.08, guardian-health-max-floor: 70 }
    imposed-load: { 2: 2, 3: 4, 4: 5, 5: 6 }
    repeat-weight: 0.25
    mutations: { }            # per-mutation keys (3.8)
```

Per-Generation overrides (Memory, Hollowing, Long Stair) cannot read the shared `GameProperties` as is. They need a per-Campaign copy of the config objects the `Tower` is built from, or one session's Mutation would leak into another.

---

## 4. How Prestige and persistence depend on each other, and the build plan

### 4.1 Dependencies

| Prestige needs | From persistence |
|---|---|
| A stable identity | The Campaign Key and `playerId`. Without it a refresh destroys the Campaign, and the Succession, the Roll and the Chronicle with it. |
| An atomic Succession | `PlayerRecord{meta,campaign}` and the awaited write. The award, the Chronicle entry and the `OFFER` phase land together or not at all, and so do the next Generation and its blueprint. |
| An idempotent award | The `pending` report stored in the ended Campaign. A refresh on the end screen shows the same report. `BEGIN_GENERATION` on a consumed Generation is a no-op. |
| A terminal state that sticks | Victory, Defeat and Exodus are written at once and stay in the file as an `OFFER` Campaign. Closing the tab never silently restarts. |
| Stable seeds (v2) | The sealed `towerSecret` and the stored blueprint, so no reroll by reload or import. |
| Survival across redesigns | `meta` is kept if `campaign` fails to migrate. `paid` refunds a removed Inheritance. |
| Ledger counters in the snapshot | `campaign.ledger` and `meta.lifetime` under the field-accounting test. |
| A paused loop | `phase` (`OFFER`) gates the ticking loops. |

| Persistence gains from Prestige | |
|---|---|
| A reason to exist | Without a 3 to 8 hour arc and Prestige, much of the persistence cost is only convenience. |
| A shape for `meta` | The ratchet-and-`paid` rules were written for exactly these nodes. |
| A concrete schema test | The first real migration test case: a node removed after release. |

Prestige does **not** depend on the Held Party, export/import or Presence, but all three should ship before any public play. A Succession on a Campaign with a disconnect-rollback exploit would be undermined.

### 4.2 Build order

Sizes: S small, M medium, L large. "B1 to B8" are Package B's steps as the persistence design numbers them (step 1 instrumentation, step 2 Trades, step 6 Village Tier and Ledger); I did not re-derive Package B's order.

| # | Step | Size | Needs |
|---|---|---|---|
| G0 | `CONTEXT.md` edits and a short ADR (section 5). Do this **before** code, to avoid the Standing-to-Tier rewrite the village document needed. | S | none |
| B1 | Package B step 1 (instrumentation) | existing | |
| P0 | Test dependencies, actuator, characterization tests, remove `h2` and dead `upgrades`, fix defects 1 to 3 of 2.8 | S | B1 |
| P1 | `CampaignStore` port, container, `FileCampaignStore` | M | P0 |
| P2 | Snapshot records, `toSnapshot/restore`, `Tower.restore`, field-accounting test | M | P1 |
| P3 | `PlayerSlot` registry, find-only render, `doFinally` detach, newest-wins | M | P2 |
| P4 | Handshake, key, close codes, `GameFrame.session`, frontend key service, reconnecting socket, gate screen | M | P3 |
| P5 | `PersistenceCoordinator` (debounce, autosave, writer thread, shutdown flush, health) | M | P3 |
| P6 | Held Party, `RESUME`, Extract-while-Held, departure commit | M | P5 |
| P7 | Migration framework, golden fixtures, released-ids freeze, "meta kept" path | S | P2 |
| P8 | Export/import (with the codec limit fix), sealed block, key display, peek | S | P7 |
| B2 to B8 | Trades, Stockroom, Tiers and the rest, **each adding its snapshot fields (P9)** | per Package B | P2 |
| **R1** | Campaign and Lifetime Ledger counters (with Package B step 6) and a **pure `TollCalculator`** with golden tests. Add per-Expedition Toll logging for balance. | S to M | B6, P2 |
| **R2** | `GameOutcome.EXODUS`, `EXODUS`/`ROLL_BUY`/`ROLL_REFUND`/`CHOOSE`/`BEGIN_GENERATION` actions (retiring `PRESTIGE`), `phase` gate, end-of-Campaign transaction (`PlayerRecord` swap, Chronicle, residue, `ended-last.tdc`, awaited write), idempotent `pending`. This is persistence's P10. | M | P5, P7, R1 |
| **R3** | Roll registry (`InheritanceType`), purchase, free respec, gates, refunds, and Course I and II effects (config hooks) | M | R2 |
| **R4** | The Door (with the pay-once rule) and Blood of the Door. Defeat start (Writ and Hollow). Exodus forecast HUD. **Tower Memory** (per-Campaign config copy passed to `new Tower`). | S to M | R2 |
| **R5** | Frontend: Toll bar and breakdown, Succession screen, Roll tree, Chronicle, confirm, new Victory, Defeat and Exodus copy, gate the Prestige button, remove the `prestigePoints` line | L | R2, R3 |
| R6 | Course III and IV effects: Overreach (needs Loadout panel), Narrow Stair, Open Breach, Offering Stone, Hard-Won Habits, Epitaphs, Heir (per-Unit health) | M to L | R3, B Loadout |
| **E0** | **Enemy and Supplies scaling pass** for Floors 40 to 100 (3.11), with measured Party size, damage and Supplies per Floor. Blocks every par value and every Highest Floor in 3.10. | M | v1 telemetry |
| | **Prestige v1 is shippable here.** Playtest (including for sameness), then refit the par values from logged data (M). | M | R1 to R6 |
| R7 | `TowerBlueprint` plus the `FloorRules` hook extraction from `updateInvasion`, `unitsAttack`, `enemiesAttack` (no behaviour change for Generation 1), per-Campaign `TowerModifiers` | L | v1 |
| R9 | Mutations slice A, config-only: Long Stair, Veil, Hollowing, Severed Stair | M | R7 |
| R10 | Omens, Pact, Atlas, Survey, seeded draw, `DescentView`-style projection (no seed on the wire) | M | R9 |
| R11 | Mutations slice B, combat hooks: Looping Rooms, Chymus Spread, Spore Blight, Unburied | L | R7 |
| R12 | Frontend: Omen screen, Atlas, effect overlays, Veil silhouettes, palette per Generation | M | R10 |
| P11 | Presence (hidden or idle means away) | S | P5 |
| P12 | Retention purge, quotas, metrics, persistence ADR | S | |
| R13 | Balance pass from telemetry (Toll, `L`, Memory, Load budget) | M | v1, R11, E0 |

Rough weight: persistence P1 to P6 is about six M. Prestige v1 (R1 to R6) is about two M, one S, one M-to-L and one L, and Tower Memory moved here from v2 (S). Variation v2 (R7 to R12) is about three L-equivalents.

---

## 5. Glossary: new terms for `CONTEXT.md`, and the conflicts

### 5.1 The conflicts and the recommended resolutions

**1. "Tier" versus Floor Difficulty (the author's request).** `CONTEXT.md` lists `_Avoid_: Tier, threat level` under **Floor Difficulty**. The code uses "tier" in two comments: `GameProperties.Enemies.easeDifficultyPerBoss` ("a floor's max enemy tier") and the `TowerFloor.populateEnemies` Javadoc ("which tiers can spawn at its difficulty"). The author prefers "Tier" for the Village's progression.

**Recommendation: adopt "Village Tier" and amend Floor Difficulty.**

1. Add **Village Tier** (5.2). Number it I to IV. In prose, UI and code, "Village Tier" is written in full on first use. A bare "Tier II" is acceptable once the context is the Village (a screen header, a label). **"Tier" on its own always means Village Tier and never Floor Difficulty.**
2. In **Floor Difficulty**, change `_Avoid_: Tier, threat level` to `_Avoid_: Enemy tier, Floor Tier, threat level`.
3. Reword the two code comments above to "Floor Difficulty".
4. In code, use `villageTier` and keep `difficulty` for Floors, never a bare `tier`.
5. The village brainstorm's "Standing" is reconciled after the parallel tone pass lands. It is added to Village Tier's `_Avoid_` list.

Alternative if the author would rather not reopen the Floor Difficulty entry: keep `_Avoid_: Tier` there and always write "Village Tier". This works but leaves a standing trap.

Illustrative Village Tier thresholds, carried from the brainstorm sketch and all [Invented]: II needs 1,000 Food grown and Highest Floor 5. III needs Highest Floor 10, 1,000 Materials banked and 6 Expeditions. IV needs Highest Floor 25 and 60 Relics banked. Each Tier opens a Trade or building (Salt Pans at II, Spore Cellar at III).

**2. "Campaign" means one attempt, not the whole game.** The current `CONTEXT.md` entry says "The whole game for one player ... until victory or defeat", while its own Prestige entry says "Starting the campaign over after it has ended". The Prestige entry is the one that matches this design: a **Campaign** is one attempt on the Tower, and a player's Campaigns form a chain of **Generations**. The entry must be reworded (5.3). This makes the "3 to 8 hour Campaign target" ambiguous, which is decision 1. The Tower Wizard comparison [TW §3: 3 to 6 hours across 4 to 5 runs] supports reading the target as the whole arc.

**3. "Ledger" collides with the brainstorm's "Lifetime Ledger".** The village document's R02 calls the Village's counters the Lifetime Ledger. Here the Campaign Ledger starts at zero each Campaign and the Lifetime Ledger is the cross-Generation sum. After the tone pass lands, rename the brainstorm's term to Campaign Ledger.

**4. "Legacy" replaces the glossary's "prestige points".** A wording change the author should approve (decision 6).

**5. UI copy.** The frontend Victory and Defeat banners use words the glossary avoids ("The Village has Fallen", "Tower is Conquered") and make a canon claim ("Ignelia is free") that [Manga §8] leaves open. The Succession copy replaces them.

### 5.2 New terms (proposed entries)

**Village Tier**: The Village's progression level, I to IV, reached for free by crossing Campaign Ledger and Highest Floor thresholds and never lost within a Campaign. Each Village Tier opens a Trade or building. Tier on its own always means Village Tier. _Avoid_: Standing, Settlement Tier, Level, Rank.

**Ledger** (Campaign Ledger / Lifetime Ledger): The Village's counters of what it has grown, banked and lost. The Campaign Ledger starts at zero each Campaign. The Lifetime Ledger is the sum of all of them and never starts over. Counters only rise. _Avoid_: Stats, score.

**Toll**: The Prestige bar, in Marks, computed from the Campaign Ledger, with Ascent (Floors above the baseline) and Guardians slain counted alongside. Every 9 Marks is one Legacy. _Avoid_: Prestige bar, XP.

**Legacy**: What the Toll converts into when a Campaign ends: the points spent on Inheritances. _Avoid_: Prestige points, Blessing, perk.

**Residue**: The Marks left over after converting the Toll, carried into the next Generation.

**New Ground**: Legacy awarded for Guardian Floors slain beyond the best count of any earlier Campaign.

**Generation**: The ordinal of a Campaign in a player's chain (Generation 1, 2, ...). **Succession** is the in-fiction name of the screen and ceremony between Generations. _Avoid_: Run, cycle, New Game Plus.

**Exodus**: A Campaign ended voluntarily by the player, from the Village with no Expedition out, once the Toll forecast reaches 3 Legacy. _Avoid_: Abandon, quit, Concession.

**The Roll**: The permanent tree bought with Legacy, in four **Courses** gated by Legacy already spent. Its entries are **Inheritances**. _Avoid_: Blessing, Upgrade (Upgrade stays Campaign-scoped).

**Epitaph**: A free Inheritance granted when the Lifetime Ledger crosses a threshold. _Avoid_: Milestone (Milestones are Campaign-scoped).

**Heir**: The free Unit carried into a new Generation by the Heir Inheritance.

**Writ** and **Hollow**: After a Defeat, the Crown's payment (100 Credits) and the reduced starting Village (15 Villagers).

**Door**: The sealed passage between Floor 99 and the Summit. Opened by Blood of the Door.

**Tower Memory**: The Tower hardening with each Generation: larger garrisons and tougher Guardians. Derived, capped at Generation 5.

**Mutation**, **Load**, **Omen**, **Pact**, **Atlas**, **Survey** (v2): A named, seeded change to the Tower that lasts one Generation, with a Load of 1 to 3. An Omen is a Mutation offered at Succession, and the Pact is the one the player accepts. The Atlas records Mutations met. Survey spends Knowledge to reveal the next mutated Floor. _Avoid_ for Mutation: modifier, affix, curse.

From persistence:

**Campaign Key**: The secret kept in the browser that identifies a player's Campaign. _Avoid_: token, account, login, player id.

**Campaign file**: The exported artifact of a Campaign. (The glossary's `Campaign` entry avoids "save" and "session"; this keeps that. "WebSocket session" stays as a transport term only.)

**Held Party**: An Expedition frozen because the player is away. It resumes only when the player says so. _Avoid_: paused expedition, suspended.

**Chronicle**: The record of every ended Campaign, kept across Prestige.

### 5.3 Edits to existing entries

- **Prestige:** replace with "Ending the Campaign by Victory, Defeat or Exodus, and beginning the next Generation in exchange for Legacy. The Roll, Legacy, the Lifetime Ledger and the Chronicle survive. Everything else is lost. _Avoid_: Reset, new game." Remove "Intended but not yet built" when it ships.
- **Campaign:** "One attempt on the Tower by one player, from the first Recruit until Victory, Defeat or Exodus. A player's Campaigns form a chain of Generations." Keep `_Avoid_: Game, save, session`.
- **Victory:** note that it requires the Door to be open.
- **Shortcut:** widen "opened by kills or Expeditions" with "or granted by the Roll or an Epitaph".
- **Highest Floor:** keep it Campaign-scoped. The Lifetime Highest Floor lives in the Ledger entry.
- **Easing:** add "forgotten when a new Generation begins".
- **Floor Difficulty:** `_Avoid_` line per 5.1.
- **Milestone:** do not widen.

This document itself avoids the glossary's `_Avoid_` words: it uses "Ascent" (not depth), "Floors" (not levels), "Loot" and "Loot in hand" (not carried loot), "starts over" and "cleared" (not reset), and "Mutation effect" (not hazard). The words "save" and "session" remain only inside code identifiers and the transport term.

---

## 6. Risks, and decisions only the author can make

### 6.1 Risks

1. **Every number is untested.** Par values, the 9 Marks per point, the Roll's 64 Legacy, Memory, Load and the Generation lengths come from a model with no telemetry (no 4 to 6 second Floor measurement, no waiting time, no Reinforcement or recruiting limits in the model). Build `TollCalculator` as a pure function and log the Toll per Expedition before tuning.
2. **The upper Tower is unreachable on current numbers (3.11).** The Floor 80, 90 and 100 Guardians (35,000 to 40,000 hp, x0.5 from every attack, 625 damage per tick) need about 180 Rogues and about 24,000 Supplies against a Carrying Capacity of 100 plus 60 per Porter. Until the scaling pass (E0) lands, the Door is not the wall and Highest Floors above about 70 in the pacing table are fiction.
3. **Time-limited or strength-limited?** The pacing table silently assumes a head start buys Floors. The Roll is worth about one early Expedition. If the Highest Floor is capped by Party strength, no Generation exceeds Generation 1 by much, and Prestige adds nothing. This is decision 13.
4. **Loot counters sit deep in their square-root tail**, because repeat Expeditions re-clear Floors. If the Quarry or Shortcuts add Materials (the Quarry Heap can add up to 5 Materials per Floor cleared, which is a second Materials stream equal to the base 5 per Floor, so up to 1.41x on that counter's Marks), the economy Marks inflate and Ascent shrinks as a share of `T`. Re-fit.
5. **Mandatory Prestige can read as padding.** Mitigated by acceleration (Shortcuts, Village Charter, Narrow Stair), foreshadowing the Door and a 7 to 18 Legacy climb per Generation. A player who hates starting over may still churn at the Door.
6. **Mutation hooks are the largest refactor.** `updateInvasion`, `unitsAttack` and `enemiesAttack` are private static with no tests. The `FloorRules` extraction (R7) must be behaviour-neutral for Generation 1, with characterization tests first. Chymus Spread also needs a targeting hook that does not exist.
7. **Stacking can death-spiral:** Memory, Hollowing, Unburied and Looping Rooms together. The Load cap of 8, exclusions and the anti-repeat weight are guesses. Playtest.
8. **First-contact masking versus fairness.** Counter-before-effect and no Mutation effect below Floor 12 reduce but do not remove unavoidable early losses. Too punitive turns dread into frustration.
9. **Deliberate-starvation nudging** is gone (Dead buried dropped), but Food grown can still be pumped by Villager spam. The square root keeps an abandoned 10 Food/tick Village near 7 Marks in 8 hours, and about 15 Marks with 100 Villagers (50 Food/tick, 1.44 million Food), and the Presence rule prevents the idle-tab case.
10. **Epitaph perverse incentive:** Names on the Wall rewards loss. It is a free +5% hp at 100 lifetime losses, which happens naturally, so I kept it.
11. **Per-Campaign config copies** are needed for Memory (v1) and Mutations (v2). Today `GameProperties` is shared by all sessions.
12. **Persistence is the largest hidden cost** and retrofitting is the main risk. The field-accounting test and "a feature is not done until its snapshot fields and test exist" are the guard. Persistence must precede Package B's Tier and Ledger steps.
13. **Import abuse (save-scumming)** is possible by design (the previous Campaign is backup 1). It undermines Wipe stakes but not Prestige, because the seed and Omens are stored and the file carries its own `meta`.
14. **Disk and hosting:** an ephemeral filesystem loses all Campaigns. Single instance only. Unauthenticated Campaign creation (any key that makes a first purchase, plus import) needs a per-IP and global quota once public. The seal key is deployment state.
15. **Glossary friction:** about 19 new terms and 8 edited entries, one of them (Campaign) a change of meaning. Land `CONTEXT.md` first. "Legacy" versus the existing "prestige points" is a wording change the author should approve.
16. **Manga grounding is thin.** The Door, Narrow Stair and Unburied rest on wiki-grade or single-source claims. Every number is invented. Nothing is canon. The Summit is deliberately left a Dragon.
17. **Overlap with the brainstorm's R18 "The Fallen":** it is parked here. If ever built, build it once, not as a second competing implementation.
18. **Disconnect is a pause.** The Held Party lets a player stop an Expedition at will. It cannot undo damage, but it removes real-time pressure from planning. Accepted, bounded by the Presence rule.

### 6.2 Decisions for the author, with recommendations

(also returned as `openDecisions`)

1. **How to read "Campaign length 3 to 8 hours".**
2. **Mandatory Prestige via the Door.**
3. **Exodus as a third ending.**
4. **Staging: ship Prestige v1 (with Tower Memory) first, then Mutations.**
5. **Mutation scope: 8 Mutations, one Pact.**
6. **Names: Legacy, Roll, Inheritance, Toll, Succession.**
7. **Village Tier glossary resolution, and the Campaign redefinition.**
8. **Defeat start (Hollow and Writ).**
9. **The Fallen as a mechanic.**
10. **Finite Roll (no repeatable sink).**
11. **Persistence decisions from the persistence design** (disconnect, offline, import, identity storage, storage engine, backups, retention, multi-tab).
12. **Whether Ascent Marks pay for skipped Floors.**
13. **Who owns the enemy and Supplies scaling pass, and when (3.11).**
14. **Sealed blueprint in exports, or accept readable spoilers.**
