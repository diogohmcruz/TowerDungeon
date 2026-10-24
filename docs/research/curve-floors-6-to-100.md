# Floors 6 to 100 as a smooth curve (#114)

The sizing of the upper Tower, measured with the floor duel harness (`floor-duel-harness.md`) on
the pacing table's reference plans (`backend/src/test/resources/balance/pacing-table.json`).
Design: `docs/brainstorm/prestige-and-persistence.md` sections 3.11 and 3.12. Terms follow
`CONTEXT.md`. The numbers below are the config shipped in `GameProperties` and `application.yml`.

## What this changes, and what it leaves

Floors 1 to 5 are untouched: the same budget formula, the same first-reach discount, the same
Supplies drain, the same enemy tiers (a test pins each). From `game.enemies.curve-from-floor`
(Floor 6) the Tower is read from five curves, each a handful of anchors on the Guardian Floors
(every 10) and the pinned Floors (50, 59, 60, 80, 100), read geometrically between them
(`FloorCurve`):

| Config | Meaning |
|---|---|
| `game.enemies.budget-curve` | the garrison budget of a regular Floor, replacing `3 + 0.8 f + 0.04 f^2`; Easing, step-ups, Tower Memory stay on top |
| `game.enemies.tier-min-curve`, `tier-max-curve` | the window of enemy tiers a garrison draws from (a Slime is 0); a deep Floor holds no Slimes |
| `game.enemies.surge-multiplier-upper` | the surge on Floors 10, 15, ... (Floor 5 keeps 1.5); 1.15 |
| `game.boss.health-curve` | a Guardian's **total** health (before Tower Memory); the archetype still sets weaknesses, damage and Body Parts |
| `game.loop.supply-drain-curve` | what a Unit eats per tick on a Floor, as a share of 1.5; 1 up to Floor 5 |
| `game.supply.carry-multiplier` | what Porters and Supply Lines carry, times this (3); the base and Milestones are not multiplied |

The Guardian interval stays 10. Shortcut Floors are not touched.

## Why the 3.11 arithmetic had to be redone

3.11 sized the Summit by letting a Dragon kill one Unit a tick and the Party grind 40,000 hp down
over about 180 ticks. That cannot happen: **Attrition takes 1 health per Unit per tick** and a
Warrior has 20, so a Party left to fight for more than about 20 to 25 ticks is gone before the
enemy touches it. The longest fights the harness measures are Guardians, at 20 to 35 ticks, and
they are won by killing quickly, not by outlasting. Total damage in a fight is therefore about
`d x N x T` for a Party of `N`, `d` damage per Unit per tick and `T` about 20 ticks, and the
sizing rule `N = sqrt(HP / (1.25 d))` is read on that clock. Measured on the Generation 12 plan
(80 Units, `d` about 1.0 after the Dragon's x0.5), a Floor 80 Dragon of **2,150 hp** is beaten
60% of the time; 20,000 is out of reach by a factor of ten, not a few Units. Its numbers sit in the
Guardian table below. The harness agrees with `N` on every Floor it measures: the column `N needed`
is `sqrt(HP / (1.25 d))` with the measured `d`.

Two other results of the same reading:

- **Enemy count is the lethal quantity, not enemy health.** Every enemy from a Tower Beast up kills
  a Unit per tick it lives (damage above a Unit's health). A garrison of 20 Chymus Insects kills
  20 Units a tick. A deep garrison therefore has to be few and tough, not a crowd, so the tier
  window narrows with depth and the budget is read as weight, not as a headcount.
- **Stone Golems and Dead Dragons are Guardian-only.** A Golem takes x0.25 from Melee and
  Ranged, which is all any plan fields (no Mage is ever unlocked in the table); in a garrison it
  is a wall no budget can set, and a Dead Dragon is 2,000 hp for 50 budget. The window stops at
  tier 5 (Chymus Insect), so garrisons are Slimes, Dead Soldiers, Tower Beasts, Chimeras and Chymus
  Insects and the Guardians keep the rest.

## The sizing rule, as used

For each Generation the reference plan is dueled on its target Floor with the garrison budget (or
the Guardian's health) bisected until the plan wins 75% of the time (60% on a Guardian, so the 50%
bar has room); the anchors are then drawn smoothly through those points, slightly under them (the
plan wins about 80 to 95% on its own target), and checked again over every Floor of every climb.
`PacingCalibrate` is that bisection. The step each Generation needs over the one before it is the
plan's own: `T` (the budget it holds at 75%) is about 8, 9, 11, 18, 19, 25, 28, 31, 41, 50 and 60
for Generations 1 to 11.

## The curve

### Garrison budget (weight; Floor 100 is a Guardian Floor, its garrison is the Floor 99 one)

| Floor | 5 | 10 | 20 | 30 | 40 | 50 | 59 | 60 | 70 | 80 | 90 | 100 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Budget | 6.5 | 9.2 | 17.8 | 24.8 | 30.0 | 38.0 | 45.1 | 46.0 | 52.0 | 58.0 | 60.0 | 65.0 |

The old formula gave 8, 15, 35, 63, 99, 143, 189, 195, 255, 323, 399 and 483 weight on the same
Floors (483 weight is 48 Chymus Insects). Tower Memory (x1.05 a step, up to step 4) and
Easing/step-ups stay on top; a Surge Floor beyond the fifth is x1.15.

### Enemy tier window (tiers `[min, max)`; 0 Slime, 1 Dead Soldiers, 2 Tower Beast, 3 Chimera, 4 Chymus Insect)

| Floor | 6 | 20 | 30 | 40 | 50 | 60 to 100 |
|---|---:|---:|---:|---:|---:|---:|
| max (exclusive) | 1.5 | 2.8 | 3.7 | 4.6 | 5.0 | 5.0 |
| min | 0 | 0 | 0 | 1 | 2 | 3 |

So Floors 6 to 12 are still Slimes (as before: the old difficulty did the same), Dead Soldiers join
at Floor 13, Tower Beasts at Floor 23 and Chimeras at Floor 34; then the low tiers fall away, Slimes
at Floor 40 and Dead Soldiers at Floor 50 (when Chymus Insects join) and Tower Beasts at Floor 60:
from Floor 60 a garrison is Chimeras and Chymus Insects only. Easing
lowers both ends by a tier per Guardian above, as it lowered the difficulty.

### Guardian health (total, before Tower Memory)

| Floor | Archetype | Health | Old formula | N needed (measured, Generation's plan) |
|---|---|---:|---:|---:|
| 10 | Dead Soldiers | 575 | 52 | 23.6 |
| 20 | Tower Beast | 825 | 160 | 26.0 |
| 30 | Chimera | 1,225 | 315 | 28.4 |
| 40 | Chymus Insect | 970 | 500 | 33.3 |
| 50 | Stone Golem | 590 | 1,650 | 33.2 |
| 60 | Necromancer | 1,455 | 1,200 | 36.1 |
| 70 | Dead Dragon | 7,025 | 13,000 | 39.3 |
| 80 | Dragon | 2,150 | 35,000 | 43.9 |
| 90 | Dragon | 2,140 | 37,500 | 43.7 |
| 100 | Dragon | 2,100 | 40,000 | 38.9 |

The health is not monotone because the archetypes are not: a Golem takes x0.25 from Melee and
Ranged, so 590 hp is an effective 2,360; a Dead Dragon takes x2. What is monotone is the Party it
asks for (`N needed`), and the Floor 80 Dragon is the highest of the ten: **Floor 80 stays the
hardest fight and is beatable.** Floors 90 and 100 are set a hair under it so the Summit does not
outdo the wall the Party met ten Floors before. Tower Memory's 5% and 8% steps and the
upper-Guardian cap (above Floor 50 at most x1.16) are unchanged and are inside these numbers (the
plans carry Memory step 4): Floor 50 itself is below the cap and takes the full x1.32.

### Supplies drain and Carrying Capacity

| Floor | 1 to 5 | 10 | 20 | 30 | 50 | 80 | 100 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Drain per Unit per tick | 1.5 | 1.05 | 0.9 | 0.75 | 0.50 | 0.26 | 0.20 |

(1.5 times 1, 0.7, 0.6, 0.5, 0.33, 0.17 and 0.13). A Party eats less per tick on deep Floors
because the fights there are fewer and heavier, not because anyone is fed less: the cost of a Floor
is a hundred to two hundred and fifty Supplies on a normal Floor from Floor 10 to Floor 100, as
the table of Floors shows, while the Party that pays it grows five times.

Carrying Capacity is `100 + 3 x (60 per Porter + 25 per Supply Lines level) + 25` (Relic Lore) and
`+ 25` with Heavier Packs. At 3 times, Generation 1 carries 1,100 Supplies (five Porters), enough
for its 862-Supply climb; Generation 12 carries 4,500 for a 3,227-Supply climb through the
Dragons. Without the multiplier the climb of Generation 1 alone (about 860) was more than twice
the 390 the first plan carried. No Floor from 1 to 5 changes: those numbers are the measured ones,
only what a Party can carry changed.

## The reference plans, and what was changed

`PacingTable` now loads into the harness's own `PartyPlan` (start Floor, Dowry and Roll added to
it, `easing` read as the Guardian Floors slain). The first duels of the provisional plans showed
three faults, fixed in the table:

- **Supplies and Carrying Capacity were too small** (the known concern: 390 against a climb of
  about 860 for Generation 1, 585 against 880 for Generation 4). The capacities are recomputed from
  the multiplier above, and Generation 1 holds a fifth Porter, which the plans of later Generations
  keep (the table's plans never lose a Porter).
- **Generations 2 and 3 could not hold their Floors.** Generation 2 loses Generation 1's stronger
  Yuva and meets Tower Memory step 1; at 16 Units it won 56%, 41% and 39% on Floors 7 to 9. It
  holds 24 Units (10 Warriors, 7 Archers, 2 Healers, 5 Porters) and Sharpened Arms 3. Generation 3
  holds 28 (10 Warriors, 8 Archers, 2 Healers, 2 Tanks, 6 Porters) and Sharpened Arms 4. Both stay
  inside their Generation's earnings: the economy checks of `PacingTableTest` pass with a wide
  margin (Materials earned are at least twice the cost, Credits many times).
- **Generation 4 was far stronger than its Floors asked** (the three Rogues Generation 3 lacked
  made it win 100% to Floor 19); it now holds 7 Warriors, 5 Archers, 2 Healers, 3 Tanks, 3 Rogues
  and 8 Porters (28 Units), and Generation 5 8 Porters. The plans' sizes still never fall.

Generation 12's Floor 80 duel uses Easing `[80]`, not `[80, 90]` as the provisional table had: with
the Floor 90 Guardian already slain, Easing turns the Floor 80 Dragon into a Dead Dragon one tier
down, which is not the fight meant to be the hardest.

Plan sizes (Units): 17, 24, 28, 28, 29, 32, 38, 43, 49, 58, 68, 80.

## Results

`PacingSweep`, 150 runs a Floor, seed 1, each Generation's plan from its start Floor to 8 Floors past
its target. The bars: a normal Floor about 70% or more, a Guardian Floor about 50% or more, up to
the target Floor.

| Gen | Target | Party | Normal Floors in range: lowest / mean | Surge Floors in range | Guardian Floors in range | Beyond: mean of +1 to +4 | First Floor beyond at 35% or less | Supplies of the climb |
|---:|---:|---:|---|---|---|---|---|---|
| 1 | 6 | 17 | 97% / 99% | 5: 78% | - | 64% | 10G (2%) | 862 of 1100 carried |
| 2 | 9 | 24 | 87% / 96% | - | - | 54% | 10G (0%) | 827 of 1175 carried |
| 3 | 12 | 28 | 94% / 97% | - | 10: 62% | 90% | 20G (0%) | 1101 of 1430 carried |
| 4 | 16 | 28 | 100% / 100% | 15: 100% | - | 76% | 20G (13%) | 825 of 1815 carried |
| 5 | 21 | 29 | 83% / 97% | - | 20: 60% | 58% | 25S (27%) | 1109 of 1890 carried |
| 6 | 27 | 32 | 88% / 97% | 25: 84% | - | 60% | 30G (0%) | 1142 of 1965 carried |
| 7 | 34 | 38 | 81% / 97% | - | 30: 59% | 68% | 40G (29%) | 1262 of 2220 carried |
| 8 | 42 | 43 | 75% / 94% | 35: 100% | 40: 60% | 62% | 50G (28%) | 1321 of 2475 carried |
| 9 | 52 | 49 | 79% / 96% | 45: 100% | 50: 59% | 69% | 60G (11%) | 1555 of 2910 carried |
| 10 | 64 | 58 | 79% / 94% | 55: 99% | 60: 60% | 67% | 70G (5%) | 1890 of 3345 carried |
| 11 | 78 | 68 | 85% / 95% | 65: 100%, 75: 67% | 70: 60% | 63% | 80G (11%) | 2116 of 3960 carried |
| 12 | 100 | 80 | 93% / 97% | 85: 90%, 95: 79% | 80: 60%, 90: 60%, 100: 65% | - | summit | 3227 of 4500 carried |

Every normal Floor in range is 75% or better and every Guardian 59% or better, with one exception
that is about 70%: the Floor 75 Surge for Generation 11 (67%). A Surge Floor in range is the
binding one: a Surge is x1.15 of the Floor's budget, so a plan with margin on its own target has
little on the Surge Floor five Floors below it.

### What "clearly stopped" means here, and what does not hold

Each Generation is stopped by a **wall** within 1 to 8 Floors of its target, and always a Guardian
or a Surge Floor: Guardians at 10 (Generations 1 and 2), 20 (3 and 4), 30 (6), 40 (7), 50 (8),
60 (9), 70 (10) and 80 (11), a Surge at 25 (5). The Guardian walls stop the plan below at 0 to 29%
and are cleared at about 60% by the plan above, as they were sized. **Between a target and its wall
the win rate falls only slowly** (the mean of the four Floors past the target is 54 to 90%).

That cannot be otherwise on a smooth curve, and the pacing table's spans are the reason. A plan
`g` holds its target at about 75%; the next plan holds the next target, `s` Floors on, and the curve
between is one curve for both. For `g` to fall to 35% within 4 Floors the budget must climb about
35% in 4 Floors (about 8% a Floor), which over a span of 10 or 14 Floors is a factor of 2 to 3 that
plan `g+1` does not have (the plans grow by about 1.2 a Generation in what they hold, and the sizes 17 to 80 by
about 1.15). Raising the curve past the target to stop plan `g` early fails plan `g+1` on its own
Floors. The surge multiplier (x1.15) and the Guardians carry the stop instead, and the Guardian
walls are as sharp as a wall can be. **Decision left to the author:** if "stopped a few Floors
beyond" must also hold on ordinary Floors, the spans of the table (or the plans' growth per
Generation) have to change, not the curve.

## Supplies of the climb

The Supplies column: the sum of the Supplies each Floor from the start Floor to the target costs
the Party, against what its plan carries (the climb does not resupply; a Floor pays 3 back).
Generation 1 spends 862 of 1,100 (78%), Generation 3 1,101 of 1,430 (77%), Generation 12 3,227 of
4,500 (72%). The Floor 80 Dragon fight itself costs 268 Supplies for the 80-Unit plan: a plausible plan, well
within the Carrying Capacity of that Generation.
The Guardian fights are the dearest single Floors (320 to 480 for Generations 3 to 7).

## Floors 1 to 5

Held by `ScalingCurveTest.floorsOneToFiveStayOnTheMeasuredFormula` and the existing `TowerTest`
cases: the budget is the old formula with the first-reach discount and the x1.5 Surge on Floor 5,
the drain is 1, the tiers are the old difficulty. The harness's numbers for those Floors
(`floor-duel-harness.md`) are unchanged. The seeded tests that moved are listed in the commit:
`TowerTest` (first-reach, step-up and Easing at Floor 11 now read the curve), `WipeConsequencesTest`
(the Risen cap tests moved from Floors 8 and 9 to Floor 12, where the garrison is again large
enough to cap at three), and `SaltTest` (the Loadout counts a Porter x3).

## Rerunning

From `backend/`, with no server (these tests run only when asked):

```
# the sweep: every Generation's plan over its climb and 8 Floors past it, as a table
mvn test -Dtest=PacingSweep -Dpacing.sweep=1 -Dpacing.runs=150 -Dpacing.beyond=8 -Dpacing.out=sweep.md

# the bisection that sized the curve: the budget each target Floor may hold (garrison) or the
# health each Guardian Floor may hold (guardian), for a 75% (60%) win
mvn test -Dtest=PacingCalibrate -Dpacing.calibrate=garrison -Dpacing.runs=150
mvn test -Dtest=PacingCalibrate -Dpacing.calibrate=guardian -Dpacing.runs=150

# what the Expeditions of each Generation earn and its plan costs
mvn test -Dtest=PacingSlack -Dpacing.slack=1
```

`pacing.gens` limits the Generations (`1-5`, `3,7`), `pacing.seed` changes the seed. One Floor, one
plan, from the command line: `docs/research/floor-duel-harness.md`. A sweep of all twelve takes
about 20 minutes on a laptop at 150 runs.

## Appendix: the sweep, every Floor

The Floors with a `+` are past the Generation's target. `Supplies` is what one Floor costs the
Party; `Health` the Garrison's health; `Lost` the Units lost on the Floor.

| Gen | Floor | Kind | Win | Ticks | Supplies | Health | Party | Lost | N needed |
|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | - | 100% | 3.2 | 80.9 | 17 | 17.0 | 0.2 | 6.6 |
| 1 | 2 | - | 100% | 4.0 | 99.2 | 23 | 17.0 | 0.3 | 7.3 |
| 1 | 3 | - | 100% | 4.9 | 121.5 | 30 | 17.0 | 0.7 | 8.1 |
| 1 | 4 | - | 100% | 6.2 | 147.9 | 39 | 17.0 | 1.7 | 8.9 |
| 1 | 5 | Surge | 78% | 17.4 | 219.6 | 71 | 17.0 | 12.8 | 12.1 |
| 1 | 6 | - | 97% | 12.1 | 192.9 | 60 | 17.0 | 8.2 | 10.8 |
| 1 | 7+ | - | 94% | 15.1 | 189.0 | 65 | 17.0 | 10.6 | 11.5 |
| 1 | 8+ | - | 87% | 17.1 | 179.6 | 69 | 17.0 | 12.2 | 11.9 |
| 1 | 9+ | - | 72% | 18.7 | 166.6 | 73 | 17.0 | 13.6 | 12.4 |
| 1 | 10+ | Guardian | 2% | 65.0 | 302.2 | 575 | 17.0 | 17.0 | 24.9 |
| 1 | 11+ | - | 36% | 20.0 | 143.8 | 84 | 17.0 | 15.9 | 13.3 |
| 1 | 12+ | - | 41% | 20.4 | 142.8 | 85 | 17.0 | 15.9 | 13.4 |
| 1 | 13+ | - | 67% | 17.8 | 140.8 | 135 | 17.0 | 13.1 | 13.9 |
| 1 | 14+ | - | 45% | 19.0 | 135.5 | 144 | 17.0 | 14.4 | 14.4 |
| 1 | sum to 6 | | | 862 of 1100 carried | | | | | |
| 2 | 6 | - | 100% | 6.7 | 206.2 | 62 | 24.0 | 3.5 | 10.9 |
| 2 | 7 | - | 100% | 7.7 | 211.7 | 68 | 24.0 | 5.3 | 11.4 |
| 2 | 8 | - | 99% | 8.5 | 207.3 | 73 | 24.0 | 6.7 | 11.7 |
| 2 | 9 | - | 87% | 9.8 | 201.8 | 77 | 24.0 | 9.5 | 12.1 |
| 2 | 10+ | Guardian | 0% | 26.8 | 390.0 | 621 | 24.0 | 24.0 | 21.1 |
| 2 | 11+ | - | 65% | 12.4 | 200.9 | 88 | 24.0 | 15.0 | 12.7 |
| 2 | 12+ | - | 65% | 12.0 | 195.8 | 89 | 24.0 | 15.1 | 12.7 |
| 2 | 13+ | - | 87% | 9.2 | 172.8 | 141 | 24.0 | 8.3 | 13.6 |
| 2 | 14+ | - | 77% | 10.6 | 178.0 | 151 | 24.0 | 11.2 | 14.0 |
| 2 | 15+ | Surge | 46% | 12.8 | 184.1 | 186 | 24.0 | 17.8 | 15.0 |
| 2 | 16+ | - | 61% | 11.8 | 179.1 | 169 | 24.0 | 14.7 | 14.5 |
| 2 | 17+ | - | 45% | 12.9 | 178.5 | 184 | 24.0 | 18.1 | 15.0 |
| 2 | sum to 9 | | | 827 of 1175 carried | | | | | |
| 3 | 9 | - | 100% | 5.2 | 159.6 | 61 | 28.0 | 1.1 | 10.7 |
| 3 | 10 | Guardian | 62% | 33.0 | 483.5 | 667 | 28.0 | 22.2 | 23.6 |
| 3 | 11 | - | 96% | 10.7 | 230.3 | 105 | 28.0 | 11.0 | 13.5 |
| 3 | 12 | - | 94% | 10.9 | 228.0 | 107 | 28.0 | 11.2 | 13.6 |
| 3 | 13+ | - | 99% | 7.8 | 187.6 | 169 | 28.0 | 5.5 | 14.3 |
| 3 | 14+ | - | 97% | 9.1 | 201.1 | 181 | 28.0 | 7.9 | 14.9 |
| 3 | 15+ | Surge | 75% | 13.7 | 228.4 | 222 | 28.0 | 15.4 | 16.8 |
| 3 | 16+ | - | 88% | 11.4 | 213.1 | 202 | 28.0 | 11.9 | 15.9 |
| 3 | 17+ | - | 73% | 14.1 | 221.4 | 221 | 28.0 | 15.9 | 16.8 |
| 3 | 18+ | - | 69% | 14.3 | 219.9 | 235 | 28.0 | 17.2 | 17.2 |
| 3 | 19+ | - | 48% | 15.9 | 217.4 | 247 | 28.0 | 21.2 | 17.7 |
| 3 | 20+ | Guardian | 0% | 31.2 | 377.4 | 957 | 28.0 | 28.0 | 29.2 |
| 3 | sum to 12 | | | 1101 of 1430 carried | | | | | |
| 4 | 12 | - | 100% | 6.5 | 171.7 | 98 | 28.0 | 3.2 | 11.7 |
| 4 | 13 | - | 100% | 5.6 | 148.1 | 154 | 28.0 | 2.0 | 12.8 |
| 4 | 14 | - | 100% | 6.0 | 155.4 | 165 | 28.0 | 2.2 | 13.1 |
| 4 | 15 | Surge | 100% | 7.8 | 182.1 | 203 | 28.0 | 5.5 | 14.4 |
| 4 | 16 | - | 100% | 6.9 | 167.5 | 185 | 28.0 | 4.0 | 13.9 |
| 4 | 17+ | - | 99% | 7.8 | 176.3 | 200 | 28.0 | 5.4 | 14.4 |
| 4 | 18+ | - | 97% | 9.0 | 184.4 | 214 | 28.0 | 7.1 | 15.1 |
| 4 | 19+ | - | 95% | 11.5 | 201.5 | 225 | 28.0 | 10.6 | 15.9 |
| 4 | 20+ | Guardian | 13% | 33.2 | 391.0 | 1023 | 28.0 | 25.9 | 29.5 |
| 4 | 21+ | - | 78% | 13.4 | 200.4 | 250 | 28.0 | 13.6 | 16.8 |
| 4 | 22+ | - | 83% | 13.8 | 207.0 | 254 | 28.0 | 14.1 | 17.0 |
| 4 | 23+ | - | 59% | 14.8 | 187.5 | 359 | 28.0 | 18.5 | 19.0 |
| 4 | 24+ | - | 51% | 15.3 | 186.3 | 386 | 28.0 | 19.8 | 19.7 |
| 4 | sum to 16 | | | 825 of 1815 carried | | | | | |
| 5 | 16 | - | 100% | 5.2 | 138.6 | 101 | 29.0 | 1.5 | 10.8 |
| 5 | 17 | - | 100% | 5.6 | 145.4 | 110 | 29.0 | 2.2 | 11.2 |
| 5 | 18 | - | 100% | 5.9 | 148.2 | 116 | 29.0 | 2.5 | 11.4 |
| 5 | 19 | - | 100% | 6.3 | 153.4 | 124 | 29.0 | 3.5 | 11.7 |
| 5 | 20 | Guardian | 60% | 22.7 | 329.9 | 1089 | 29.0 | 18.6 | 26.0 |
| 5 | 21 | - | 83% | 11.8 | 193.0 | 299 | 29.0 | 12.9 | 16.4 |
| 5 | 22+ | - | 83% | 13.0 | 193.2 | 304 | 29.0 | 14.3 | 16.8 |
| 5 | 23+ | - | 65% | 13.0 | 174.8 | 428 | 29.0 | 16.7 | 18.9 |
| 5 | 24+ | - | 55% | 14.3 | 179.3 | 459 | 29.0 | 20.1 | 19.5 |
| 5 | 25+ | Surge | 27% | 15.0 | 164.0 | 539 | 29.0 | 25.1 | 21.0 |
| 5 | 26+ | - | 46% | 14.9 | 170.0 | 483 | 29.0 | 22.3 | 20.0 |
| 5 | 27+ | - | 37% | 15.0 | 162.2 | 494 | 29.0 | 23.1 | 20.5 |
| 5 | 28+ | - | 35% | 15.1 | 160.0 | 510 | 29.0 | 24.0 | 20.7 |
| 5 | 29+ | - | 28% | 14.8 | 153.1 | 535 | 29.0 | 25.1 | 20.8 |
| 5 | sum to 21 | | | 1109 of 1890 carried | | | | | |
| 6 | 21 | - | 100% | 6.2 | 159.9 | 261 | 32.0 | 4.4 | 14.2 |
| 6 | 22 | - | 100% | 6.3 | 159.3 | 264 | 32.0 | 4.3 | 14.4 |
| 6 | 23 | - | 99% | 6.9 | 155.3 | 374 | 32.0 | 6.7 | 16.0 |
| 6 | 24 | - | 97% | 7.9 | 161.1 | 400 | 32.0 | 8.8 | 16.7 |
| 6 | 25 | Surge | 84% | 11.2 | 179.9 | 469 | 32.0 | 14.5 | 18.6 |
| 6 | 26 | - | 96% | 8.5 | 162.4 | 421 | 32.0 | 10.0 | 17.2 |
| 6 | 27 | - | 88% | 9.8 | 163.7 | 430 | 32.0 | 11.9 | 17.8 |
| 6 | 28+ | - | 91% | 9.9 | 165.2 | 446 | 32.0 | 12.3 | 17.9 |
| 6 | 29+ | - | 77% | 11.4 | 163.7 | 464 | 32.0 | 15.0 | 18.8 |
| 6 | 30+ | Guardian | 0% | 28.0 | 324.3 | 1617 | 32.0 | 32.0 | 35.5 |
| 6 | 31+ | - | 72% | 12.8 | 165.1 | 489 | 32.0 | 18.0 | 19.5 |
| 6 | 32+ | - | 71% | 13.0 | 160.7 | 500 | 32.0 | 18.5 | 19.5 |
| 6 | 33+ | - | 67% | 13.2 | 155.5 | 509 | 32.0 | 19.2 | 19.9 |
| 6 | 34+ | - | 19% | 11.4 | 121.4 | 631 | 32.0 | 28.5 | 21.2 |
| 6 | 35+ | Surge | 7% | 10.8 | 107.4 | 737 | 32.0 | 31.2 | 22.3 |
| 6 | sum to 27 | | | 1142 of 1965 carried | | | | | |
| 7 | 27 | - | 100% | 4.0 | 116.4 | 239 | 38.0 | 0.9 | 12.9 |
| 7 | 28 | - | 100% | 4.0 | 116.1 | 246 | 38.0 | 1.0 | 13.1 |
| 7 | 29 | - | 100% | 4.2 | 118.8 | 255 | 38.0 | 1.0 | 13.3 |
| 7 | 30 | Guardian | 59% | 19.0 | 320.8 | 1617 | 38.0 | 23.9 | 28.4 |
| 7 | 31 | - | 99% | 6.3 | 149.3 | 562 | 38.0 | 8.1 | 17.2 |
| 7 | 32 | - | 99% | 6.5 | 147.5 | 577 | 38.0 | 9.0 | 17.3 |
| 7 | 33 | - | 99% | 6.7 | 146.3 | 582 | 38.0 | 9.2 | 17.5 |
| 7 | 34 | - | 81% | 8.8 | 147.1 | 725 | 38.0 | 18.8 | 19.5 |
| 7 | 35+ | Surge | 51% | 10.3 | 143.8 | 847 | 38.0 | 28.4 | 20.6 |
| 7 | 36+ | - | 76% | 8.9 | 141.5 | 752 | 38.0 | 20.1 | 19.6 |
| 7 | 37+ | - | 75% | 8.9 | 138.3 | 764 | 38.0 | 20.6 | 19.8 |
| 7 | 38+ | - | 69% | 9.4 | 137.6 | 772 | 38.0 | 21.9 | 20.0 |
| 7 | 39+ | - | 61% | 9.7 | 132.0 | 795 | 38.0 | 24.1 | 20.3 |
| 7 | 40+ | Guardian | 29% | 29.2 | 336.7 | 1280 | 38.0 | 31.6 | 34.4 |
| 7 | 41+ | - | 61% | 9.7 | 123.9 | 887 | 38.0 | 24.6 | 20.9 |
| 7 | 42+ | - | 49% | 10.4 | 121.7 | 938 | 38.0 | 27.6 | 21.5 |
| 7 | sum to 34 | | | 1262 of 2220 carried | | | | | |
| 8 | 34 | - | 100% | 4.0 | 114.4 | 397 | 43.0 | 1.8 | 15.4 |
| 8 | 35 | Surge | 100% | 4.6 | 126.5 | 460 | 43.0 | 3.0 | 16.2 |
| 8 | 36 | - | 100% | 4.2 | 113.8 | 408 | 43.0 | 1.8 | 15.7 |
| 8 | 37 | - | 100% | 4.2 | 111.8 | 413 | 43.0 | 1.8 | 15.7 |
| 8 | 38 | - | 100% | 4.2 | 109.3 | 422 | 43.0 | 2.1 | 15.7 |
| 8 | 39 | - | 100% | 4.3 | 109.7 | 426 | 43.0 | 2.4 | 15.9 |
| 8 | 40 | Guardian | 60% | 23.9 | 351.3 | 1280 | 43.0 | 26.2 | 33.3 |
| 8 | 41 | - | 81% | 8.4 | 141.6 | 887 | 43.0 | 20.4 | 21.1 |
| 8 | 42 | - | 75% | 9.5 | 142.6 | 938 | 43.0 | 24.0 | 21.9 |
| 8 | 43+ | - | 77% | 8.5 | 135.5 | 935 | 43.0 | 22.1 | 21.3 |
| 8 | 44+ | - | 69% | 9.2 | 134.1 | 973 | 43.0 | 25.4 | 21.8 |
| 8 | 45+ | Surge | 39% | 10.2 | 123.0 | 1147 | 43.0 | 34.6 | 23.1 |
| 8 | 46+ | - | 61% | 10.0 | 130.7 | 1000 | 43.0 | 29.1 | 22.2 |
| 8 | 47+ | - | 53% | 10.1 | 125.8 | 1037 | 43.0 | 29.9 | 22.4 |
| 8 | 48+ | - | 45% | 10.9 | 124.1 | 1071 | 43.0 | 33.2 | 23.2 |
| 8 | 49+ | - | 43% | 10.0 | 115.7 | 1103 | 43.0 | 32.7 | 22.9 |
| 8 | 50+ | Guardian | 28% | 29.8 | 293.8 | 779 | 43.0 | 36.5 | 34.8 |
| 8 | sum to 42 | | | 1321 of 2475 carried | | | | | |
| 9 | 42 | - | 100% | 4.1 | 112.8 | 462 | 49.0 | 2.4 | 16.5 |
| 9 | 43 | - | 100% | 4.2 | 111.6 | 466 | 49.0 | 2.4 | 16.6 |
| 9 | 44 | - | 100% | 4.2 | 111.1 | 478 | 49.0 | 2.6 | 16.7 |
| 9 | 45 | Surge | 100% | 5.0 | 123.6 | 564 | 49.0 | 4.7 | 17.8 |
| 9 | 46 | - | 100% | 4.4 | 109.8 | 494 | 49.0 | 2.7 | 17.0 |
| 9 | 47 | - | 100% | 4.5 | 108.8 | 512 | 49.0 | 3.0 | 17.1 |
| 9 | 48 | - | 100% | 4.5 | 107.3 | 527 | 49.0 | 3.3 | 17.3 |
| 9 | 49 | - | 100% | 4.7 | 109.1 | 550 | 49.0 | 4.0 | 17.4 |
| 9 | 50 | Guardian | 59% | 22.9 | 296.8 | 779 | 49.0 | 29.8 | 33.2 |
| 9 | 51 | - | 88% | 10.9 | 180.9 | 792 | 49.0 | 19.1 | 21.0 |
| 9 | 52 | - | 79% | 12.4 | 183.3 | 817 | 49.0 | 21.8 | 21.8 |
| 9 | 53+ | - | 76% | 12.7 | 180.6 | 846 | 49.0 | 24.1 | 22.1 |
| 9 | 54+ | - | 79% | 12.9 | 181.8 | 835 | 49.0 | 24.0 | 21.9 |
| 9 | 55+ | Surge | 47% | 16.4 | 183.6 | 970 | 49.0 | 35.0 | 24.3 |
| 9 | 56+ | - | 73% | 13.9 | 175.2 | 873 | 49.0 | 27.1 | 22.6 |
| 9 | 57+ | - | 74% | 13.4 | 167.9 | 876 | 49.0 | 25.2 | 22.6 |
| 9 | 58+ | - | 70% | 13.6 | 165.4 | 886 | 49.0 | 27.0 | 22.6 |
| 9 | 59+ | - | 59% | 15.1 | 166.2 | 924 | 49.0 | 32.1 | 23.3 |
| 9 | 60+ | Guardian | 11% | 29.9 | 284.7 | 1688 | 49.0 | 45.9 | 39.0 |
| 9 | sum to 52 | | | 1555 of 2910 carried | | | | | |
| 10 | 52 | - | 100% | 4.4 | 111.9 | 759 | 58.0 | 5.2 | 19.9 |
| 10 | 53 | - | 100% | 4.7 | 115.6 | 786 | 58.0 | 6.2 | 20.2 |
| 10 | 54 | - | 100% | 4.7 | 112.6 | 795 | 58.0 | 6.9 | 20.4 |
| 10 | 55 | Surge | 99% | 5.7 | 124.3 | 934 | 58.0 | 10.7 | 21.7 |
| 10 | 56 | - | 100% | 4.8 | 109.6 | 814 | 58.0 | 7.0 | 20.5 |
| 10 | 57 | - | 100% | 5.0 | 108.1 | 820 | 58.0 | 7.7 | 20.6 |
| 10 | 58 | - | 100% | 5.1 | 109.0 | 839 | 58.0 | 8.4 | 20.8 |
| 10 | 59 | - | 100% | 5.2 | 108.6 | 882 | 58.0 | 9.1 | 21.1 |
| 10 | 60 | Guardian | 60% | 20.8 | 285.6 | 1688 | 58.0 | 36.0 | 36.1 |
| 10 | 61 | - | 84% | 12.4 | 182.3 | 908 | 58.0 | 25.1 | 22.9 |
| 10 | 62 | - | 85% | 11.8 | 173.8 | 909 | 58.0 | 24.4 | 22.7 |
| 10 | 63 | - | 87% | 11.9 | 174.3 | 901 | 58.0 | 24.4 | 22.5 |
| 10 | 64 | - | 79% | 13.2 | 173.9 | 944 | 58.0 | 29.0 | 23.3 |
| 10 | 65+ | Surge | 56% | 17.4 | 182.3 | 1060 | 58.0 | 40.4 | 26.0 |
| 10 | 66+ | - | 77% | 13.1 | 165.0 | 930 | 58.0 | 27.3 | 23.3 |
| 10 | 67+ | - | 73% | 14.3 | 166.5 | 949 | 58.0 | 31.0 | 24.0 |
| 10 | 68+ | - | 63% | 15.1 | 161.5 | 986 | 58.0 | 33.8 | 24.4 |
| 10 | 69+ | - | 67% | 14.9 | 159.1 | 998 | 58.0 | 34.5 | 24.4 |
| 10 | 70+ | Guardian | 5% | 30.5 | 271.8 | 8149 | 58.0 | 56.9 | 42.5 |
| 10 | 71+ | - | 67% | 15.2 | 154.9 | 998 | 58.0 | 34.0 | 24.6 |
| 10 | 72+ | - | 64% | 15.7 | 154.3 | 989 | 58.0 | 35.3 | 24.5 |
| 10 | sum to 64 | | | 1890 of 3345 carried | | | | | |
| 11 | 64 | - | 100% | 4.7 | 105.2 | 994 | 68.0 | 8.1 | 22.4 |
| 11 | 65 | Surge | 100% | 5.7 | 115.9 | 1134 | 68.0 | 13.7 | 23.6 |
| 11 | 66 | - | 100% | 4.9 | 103.0 | 1001 | 68.0 | 8.8 | 22.5 |
| 11 | 67 | - | 100% | 5.1 | 104.4 | 1031 | 68.0 | 10.3 | 22.8 |
| 11 | 68 | - | 100% | 5.2 | 104.3 | 1047 | 68.0 | 10.7 | 22.9 |
| 11 | 69 | - | 99% | 5.2 | 100.1 | 1062 | 68.0 | 10.8 | 23.1 |
| 11 | 70 | Guardian | 60% | 21.3 | 273.4 | 8149 | 68.0 | 44.3 | 39.3 |
| 11 | 71 | - | 95% | 9.6 | 156.8 | 998 | 68.0 | 21.3 | 23.0 |
| 11 | 72 | - | 97% | 9.3 | 153.2 | 989 | 68.0 | 19.9 | 22.8 |
| 11 | 73 | - | 93% | 9.8 | 150.3 | 1018 | 68.0 | 22.8 | 23.2 |
| 11 | 74 | - | 95% | 9.8 | 147.9 | 1011 | 68.0 | 21.5 | 23.1 |
| 11 | 75 | Surge | 67% | 14.9 | 165.0 | 1174 | 68.0 | 39.2 | 26.2 |
| 11 | 76 | - | 86% | 11.5 | 146.4 | 1059 | 68.0 | 27.5 | 24.2 |
| 11 | 77 | - | 91% | 11.5 | 147.9 | 1094 | 68.0 | 28.1 | 24.6 |
| 11 | 78 | - | 85% | 12.0 | 142.8 | 1069 | 68.0 | 28.9 | 24.5 |
| 11 | 79+ | - | 79% | 12.7 | 143.5 | 1098 | 68.0 | 31.5 | 24.9 |
| 11 | 80+ | Guardian | 11% | 30.6 | 259.5 | 2494 | 68.0 | 64.2 | 45.7 |
| 11 | 81+ | - | 83% | 12.2 | 138.0 | 1112 | 68.0 | 30.4 | 24.8 |
| 11 | 82+ | - | 80% | 13.0 | 137.4 | 1127 | 68.0 | 33.5 | 25.2 |
| 11 | 83+ | - | 77% | 12.9 | 133.5 | 1108 | 68.0 | 32.1 | 25.1 |
| 11 | 84+ | - | 81% | 13.0 | 135.5 | 1099 | 68.0 | 32.5 | 25.0 |
| 11 | 85+ | Surge | 55% | 16.9 | 139.8 | 1266 | 68.0 | 46.9 | 27.8 |
| 11 | 86+ | - | 73% | 13.9 | 132.3 | 1126 | 68.0 | 35.2 | 25.6 |
| 11 | sum to 78 | | | 2116 of 3960 carried | | | | | |
| 12 | 78 | - | 100% | 4.7 | 91.6 | 1151 | 80.0 | 9.4 | 24.3 |
| 12 | 79 | - | 100% | 4.8 | 90.9 | 1181 | 80.0 | 9.9 | 24.6 |
| 12 | 80 | Guardian | 60% | 22.8 | 267.7 | 2494 | 80.0 | 52.7 | 43.9 |
| 12 | 81 | - | 99% | 8.1 | 138.4 | 1112 | 80.0 | 17.8 | 23.9 |
| 12 | 82 | - | 95% | 8.8 | 139.7 | 1127 | 80.0 | 20.4 | 24.5 |
| 12 | 83 | - | 99% | 7.9 | 133.7 | 1108 | 80.0 | 16.8 | 23.9 |
| 12 | 84 | - | 97% | 8.7 | 135.5 | 1099 | 80.0 | 20.1 | 24.1 |
| 12 | 85 | Surge | 90% | 11.0 | 149.6 | 1266 | 80.0 | 31.2 | 26.0 |
| 12 | 86 | - | 98% | 8.5 | 132.5 | 1126 | 80.0 | 19.4 | 24.3 |
| 12 | 87 | - | 98% | 8.5 | 129.6 | 1104 | 80.0 | 19.0 | 24.1 |
| 12 | 88 | - | 97% | 8.7 | 129.6 | 1135 | 80.0 | 20.4 | 24.4 |
| 12 | 89 | - | 98% | 8.4 | 125.3 | 1110 | 80.0 | 19.1 | 24.0 |
| 12 | 90 | Guardian | 60% | 22.6 | 232.6 | 2482 | 80.0 | 52.0 | 43.7 |
| 12 | 91 | - | 98% | 8.7 | 124.1 | 1137 | 80.0 | 20.6 | 24.4 |
| 12 | 92 | - | 97% | 8.8 | 124.1 | 1126 | 80.0 | 20.8 | 24.3 |
| 12 | 93 | - | 97% | 9.6 | 128.1 | 1191 | 80.0 | 25.1 | 24.9 |
| 12 | 94 | - | 98% | 9.4 | 125.8 | 1185 | 80.0 | 24.1 | 24.9 |
| 12 | 95 | Surge | 79% | 13.8 | 140.8 | 1354 | 80.0 | 41.6 | 27.8 |
| 12 | 96 | - | 95% | 10.1 | 124.7 | 1200 | 80.0 | 26.6 | 25.4 |
| 12 | 97 | - | 96% | 9.7 | 121.0 | 1193 | 80.0 | 25.3 | 25.1 |
| 12 | 98 | - | 95% | 10.3 | 124.0 | 1223 | 80.0 | 28.1 | 25.6 |
| 12 | 99 | - | 93% | 10.4 | 119.2 | 1221 | 80.0 | 28.0 | 25.6 |
| 12 | 100 | Guardian | 65% | 21.3 | 198.1 | 2436 | 80.0 | 49.1 | 38.9 |
| 12 | sum to 100 | | | 3227 of 4500 carried | | | | | |
