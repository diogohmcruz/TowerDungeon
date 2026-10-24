# The floor duel harness (#112)

The tool the scaling pass (#8, `docs/brainstorm/prestige-and-persistence.md` section 3.12) sizes
Floors 6 to 100 with. Give it a Party plan and a Floor and it runs the real combat, Attrition,
Supplies drain and Starvation code against that Floor's Garrison, many times, and reports what
happens. No playthrough, no Village, no bots.

## What it runs

Each run is a Campaign of its own: a `GameState` on its own copy of the configuration (Tower Memory
applied by `GameProperties.withTowerMemory`, as a real Campaign applies it) and its own
`RandomSource` seeded from the run's seed. The plan's Units are raised by `GameState.recruit`
(or `recruitFounder`), the Upgrades are bought with `applyUpgrade`, the Tower is the real `Tower`
(Easing is `markBossCleared` on the Guardian Floors the plan lists), and the Floor's Garrison is
built by `Tower.startNewRunAt` from the per-floor stream. The Party is set out with `startRun` and
ticked a second at a time by the same two calls the Sandbox driver makes (`attritionTick(1000)`,
then `lifecycleTick`), until the Floor changes under it (cleared) or the Expedition ends (Wipe).
Nothing of the rules is copied: the only new code is the seam (`domain/services/DuelBench`) and the
harness itself (`agent/balance`).

Run `i` of a duel on seed `s` uses `PlaytestRunner.seedFor(s, i)`. A run depends on its seed alone,
and the same plan, Floor and seed give the same report on any JVM launch (checked: two launches of
the command line below print identical tables).

Two things stand in for what a duel leaves out:

- The Party arrives on the Floor at full health and sanity, as an Expedition that starts there
  would. A duel measures one Floor, not a climb.
- The Year starts at a random tick drawn from the run's seed, so Summer's lighter drain and
  Winter's heavier Attrition average out as they do over a Campaign.

## The plan

`agent/balance/PartyPlan` (a record, every field optional but the Units; defaults are a first
Generation's start):

| Field | Meaning |
|---|---|
| `units` | Unit count by type |
| `upgrades` | Upgrade level by type (Sharpened Arms and Supply Lines act; an unlock is 1) |
| `carryingCapacity` | The Party's Carrying Capacity at the start; null is what base, Supply Lines and Porters give. Set by moving the base, so a Porter that falls still takes its share |
| `supplies`, `saltedSupplies` | Supplies carried (null is a full load) and how many are salted |
| `towerMemoryStep` | Tower Memory step `m` (0 is Generation 1) |
| `easing` | Guardian Floors slain this Campaign |
| `aim` | Aim weights that replace a Unit type's configured ones |
| `risen` | Risen standing on the dueled Floor, as a Wipe there leaves them |
| `founders` | The first Warriors and Archer are the configured Founders (Generation 1's stronger Yuva) |

`with...` methods copy a plan with one field changed, so a pacing table is plain data.

## The report

`FloorDuel.duel(plan, floor, runs, seed)` returns a `DuelReport`: win rate (the Floor changed under
the Party), mean and spread (sd, min, max) of ticks, Supplies spent, Units lost, health taken by
combat, Attrition and Starvation, the Garrison's health, `d` (damage per Unit per tick, pooled as
the baseline's report pools it) and the sizing figure `sqrt(HP / (1.25 d))`. `fight(...)` returns
the single runs. `FloorDuel.standalone(config)` builds the harness without Spring (a test needs
nothing else); it is a Spring bean on the `agent` surface too.

On a Guardian Floor (or Floor 99 with the Door shut) a run counts as won when the Floor pays out
(the Door's "seen" state), and on Floor 100 when the Campaign is won.

## Command line

From `backend/`, no server (it runs, prints and exits):

```
mvn spring-boot:run -Dspring-boot.run.profiles=duel \
  -Dspring-boot.run.arguments="--duel.units=WARRIOR:4,ARCHER:2 --duel.founders=true --duel.floors=1-5 --duel.runs=200 --duel.seed=1"
```

Settings: `duel.units` (required), `duel.floors` (lists and ranges, default `1-5`), `duel.runs`
(100), `duel.seed` (1), `duel.upgrades` (`SHARPENED_ARMS:2,SUPPLY_LINES:1`), `duel.capacity`,
`duel.supplies`, `duel.salted`, `duel.memory`, `duel.easing` (`10,20`), `duel.risen`,
`duel.founders`, `duel.report` (a file the table is also written to). Aim overrides are API only.

## Against the baseline (`baseline-2026-10.md`)

The same plan as the greedy bot's first Expedition (4 Warriors and 2 Archers, Founders), seed 1,
400 runs, against the 400 first-Expedition Floor rows of the baseline's greedy batch with the same
Party:

| | Duel | Baseline rows |
|---|---:|---:|
| Floor 1 Garrison health | 17.4 | 17.5 |
| Floor 2 Garrison health | 23.3 | 23.3 |
| Floor 1 cleared | above 95% | 99% |
| Floor 1 ticks | within 10% of 5.49 | 5.49 |
| `d` on Floor 1 | within 10% of 0.567 | 0.567 |

Floors 1 to 5 with an eight-Unit Party have a mean Garrison health of 17.4, 23.3, 29.9, 38.6 and 70.5
(Floor 5 a Surge Floor no starting Party clears). The baseline's own per-Floor rows are higher on
Floors 2 to 4 (30 to 48) because they mix Expeditions that met a clean Floor with ones that met the
Risen a Wipe left there: each Risen is a Dead Soldier of about 15 health, and `risen=1` in the plan
reproduces it. Every baseline row lies between the clean duel and the one-Risen duel
(`FloorDuelTest.floorsOneToFiveHaveTheBaselinesShape`).
(Setting `game.wipe.risen-cap-max=0` does not turn Risen off: the game raises at least one.)

## Speed

Floors 6 to 100 with a 160-Unit Party, 100 runs each, take about 2.7 s per Floor on a laptop (9,500
runs in 260 s). A small Party is much quicker: 200 runs of Floors 1 to 5 take under 2 s. Runs are
serial.

## Tests

`FloorDuelTest` (backend): determinism, a run fixed by its seed alone, the baseline numbers above,
Floors 6 to 100 including Guardian and Surge Floors, Carrying Capacity and Supplies, Upgrades and
Party size, Tower Memory and Easing, Aim, refused plans, and the command-line parser.
