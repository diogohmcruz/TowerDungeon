# The Toll and the Roll refitted to the pacing table (#117)

The Toll's weights and the Roll's prices, fitted so each Generation's reference run pays the
Legacy of the pacing table (`prestige-and-persistence.md` 3.12,
`backend/src/test/resources/balance/pacing-table.json`) on the curve of #114, and Generation 1 can
still Exodus. `PacingTollTest` is the check; the numbers below come from it.

## What changed

| Setting | Before | After |
|---|---:|---:|
| `ascent-marks-per-floor` | 0.4 | **2.0** |
| `counters.expeditions.marks` (par stays 17) | 3.0 | **22.0** |
| `points-divisor`, `guardian-marks`, `min-points-to-exodus` | 9.0, 1.5, 3 | unchanged |
| `counters.food-grown`, `materials-banked`, `credits-banked`, `relics-banked` | marks 3, 6, 3, 4; par 60,000, 17,000, 6,800, 320 | unchanged |
| Roll `costs` and `gates` (0, 5, 12, 21) | | unchanged |
| Defeat pity floor, Hollow start | | unchanged |

The formula is unchanged. Only the Ascent weight and the Expeditions weight moved.

Table rows moved (Legacy only, Generations 8 to 12): 10, 11, 12, 13, 14 became **13, 15, 18, 21,
29**. See "What the Toll cannot do".

## The Tally a reference run reaches

The model is `PacingTable`'s economy plus a Food and a Guardian rule, written down in
`PacingTollTest.tally`:

- **Expeditions:** 12 (the table's `expeditionsPerGeneration`). The final push is not counted.
- **Materials and Credits banked:** the loot of the Floors the 12 Expeditions clear, at the config
  prices (`PacingTable.found`: the i-th Expedition clears `ceil((H - start) * i / 13)` Floors from the
  start Floor, Guardian Floors pay x3). Credits are the ones found on Floors only; the Credits held
  at the start (Seed Grain, Dowry) are not banked.
- **Relics banked:** 0 (the table's `relicsPerExpedition`; Relics come from surplus only).
- **Food grown:** the Village's rate (20 Villagers, 25 with Hardy Stock, 0.5 Food each a second)
  over the Generation's seconds: 5 seconds a Floor climbed and 165 seconds between Expeditions
  (3.10's model), final push included. About 22,000 to 35,000 Food.
- **Highest Floor:** the table's, capped at the Door's 99 by the formula.
- **Guardians slain:** the Guardian Floors from the start Floor up to the Highest Floor (all slain in
  this Campaign, as the plans' `easing` says): Generations 3, 5, 7, 8, 9, 10 and 11 one each, 12 three
  (Floors 80, 90 and the Summit), the rest none. Generation 1 meets none, as the brief says.
- **Baseline `B`:** 0. The reference plans launch from a start Floor opened inside the Campaign, so no
  inherited Start Floor is used (First Footing's Floor 5 and Narrow Stair, whose Party limit of 12 the
  plans exceed, pay no baseline).
- **Residue and New Ground:** carried from one Generation to the next, and `G_best` is the most
  Guardians any earlier Campaign slew.

| Gen | Highest Floor | Food | Materials | Credits | G |
|---:|---:|---:|---:|---:|---:|
| 1 | 6 | 21,850 | 415 | 166 | 0 |
| 2 | 9 | 26,438 | 800 | 320 | 0 |
| 3 | 12 | 26,438 | 1,960 | 784 | 1 |
| 4 | 16 | 26,875 | 1,950 | 780 | 0 |
| 5 | 21 | 27,313 | 3,515 | 1,406 | 1 |
| 6 | 27 | 27,750 | 4,760 | 1,904 | 0 |
| 7 | 34 | 28,188 | 9,035 | 3,614 | 1 |
| 8 | 42 | 28,625 | 10,990 | 4,396 | 1 |
| 9 | 52 | 29,500 | 15,820 | 6,328 | 1 |
| 10 | 64 | 30,375 | 24,110 | 9,644 | 1 |
| 11 | 78 | 31,250 | 35,520 | 14,208 | 1 |
| 12 | 100 | 34,750 | 71,760 | 28,704 | 3 |

## The fit

Marks per source on this Tally, and the Legacy they pay (residue and New Ground carried):

| Gen | Food | Mat. | Cred. | Exped. | Ascent | Guard. | Marks | Legacy | Table |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1.8 | 0.9 | 0.5 | 18.5 | 12.0 | 0 | 33.7 | **3** | 3 |
| 2 | 2.0 | 1.3 | 0.7 | 18.5 | 18.0 | 0 | 40.4 | **5** | 4 |
| 3 | 2.0 | 2.0 | 1.0 | 18.5 | 24.0 | 1.5 | 49.0 | **6** | 5 |
| 4 | 2.0 | 2.0 | 1.0 | 18.5 | 32.0 | 0 | 55.5 | **6** | 6 |
| 5 | 2.0 | 2.7 | 1.4 | 18.5 | 42.0 | 1.5 | 68.1 | **8** | 8 |
| 6 | 2.0 | 3.2 | 1.6 | 18.5 | 54.0 | 0 | 79.3 | **9** | 10 |
| 7 | 2.1 | 4.4 | 2.2 | 18.5 | 68.0 | 1.5 | 96.6 | **10** | 10 |
| 8 | 2.1 | 4.8 | 2.4 | 18.5 | 84.0 | 1.5 | 113.3 | 13 | 13 (was 10) |
| 9 | 2.1 | 5.8 | 2.9 | 18.5 | 104.0 | 1.5 | 134.8 | 15 | 15 (was 11) |
| 10 | 2.1 | 7.1 | 3.6 | 18.5 | 128.0 | 1.5 | 160.8 | 18 | 18 (was 12) |
| 11 | 2.2 | 8.7 | 4.3 | 18.5 | 156.0 | 1.5 | 191.2 | 21 | 21 (was 13) |
| 12 | 2.3 | 12.3 | 6.2 | 18.5 | 198.0 | 4.5 | 241.8 | 29 | 29 (was 14) |

Generations 1 to 7 are within one point of the original table (exact in 1, 4, 5 and 7; one off in 2,
3 and 6).

**Generation 1 and the Exodus gate.** Reaching only Floor 5 pays 31.5 Marks, Floor 6 33.7 and Floor 7
35.9: a forecast of 3 Legacy at each, the Exodus minimum (3.5, 3.7 and 4.0 times the divisor of 9,
so Floor 7 is 0.01 short of paying 4).

**Why these two numbers.** With the original weights the Toll could not pay a 3 for Generation 1 at
all (about 12 Marks against 27). The table's shape rises fast to Generation 6 (3 to 10) and then
flattens (10 to 14) while the Highest Floor triples. The Marks that rise with the Floors (Ascent,
and the square root of the loot) are near-linear in the Floor; a constant base has to carry the
first Generations and the climb the rest. So the Expeditions counter carries the base (a Campaign of
12 Expeditions earns 18.5 Marks before it climbs), and the Ascent weight sets the slope. Food is
left at its old weight on purpose: it is the only source an idle tab feeds, and 288,000 Food (an
abandoned eight hours) still pays under one Legacy. Materials and Credits are left at their old
weights; they matter little at these Tallies (under 10% of the Marks), which is as 3.4 meant.

Searched with a hill climb over the weights (Python, not kept): hit the first seven rows within one
point with the Roll still legal in order, keep Food, Materials, Credits, Guardians and the divisor
as they were, then look at the tail.

## What the Toll cannot do (a decision for the author)

The table's Legacy for Generations 8 to 12 (10, 11, 12, 13, 14) cannot be paid by this formula and
the table's Floors. The sources that grow with the Floor (Ascent, loot) are close to linear in it,
and the table's Floors grow 6 to 100 while its Legacy grows 3 to 14 and goes flat from Generation 6.
No weights fix that: the best fit that keeps every Generation within 2.5 Legacy of the table (a
minimax search over all six weights, the Roll left aside) pays 5, 7, 8 for Generations 1 to 3, so
Generation 1 is wrong. Keeping Generations 1 to 7 right makes the tail rise. This pass therefore
**changed rows 8 to 12 of the table** to what the fit pays (13, 15, 18, 21, 29) and left rows 1 to
7 as they were.

That is harmless to the Roll (the whole Roll is spent by Generation 9, so Legacy past it buys
nothing, only the Offering Stone and bragging rights) but it is a change to the design table, and
the alternative is a formula change: a concave Ascent (for example a square root of the Floors
climbed) would flatten the tail to the table's 10 to 14 and keep the rest. It was not done because
the brief prefers `par` and `marks`, and 3.4 calls Ascent "the one near-linear source, so the player
must climb". `PacingTollTest.theFirstSevenGenerationsAreTheLegacyOfTheDesignedPacing` pins rows 1 to
7 to the designed figures so the change stays confined to the tail.

## The Roll

Priced as it was (costs 1 to 8, 64 in all; 55 in the 15 Inheritances v1 offers; gates 0, 5, 12, 21).
The table's purchase order, bought as soon as the Legacy paid by the Toll allows it
(`PacingTollTest.theRollIsBoughtInTheTablesOrder...`):

| At the start of | Legacy held | Bought | Left |
|---:|---:|---|---:|
| 2 | 3 | Seed Grain, Hardy Stock | 0 |
| 3 | 5 | Whetstone Habit, Muster Roll | 2 |
| 4 | 8 | Heavier Packs, The Heir | 2 |
| 5 | 8 | Village Charter, The Dowry | 2 |
| 6 | 10 | **Blood of the Door** | 4 |
| 7 | 13 | Narrow Stair, Overreach, Quartermaster's Tally | 1 |
| 8 | 11 | Hard-Won Habits | 3 |
| 9 | 16 | The Open Breach, Offering Stone | 5 |

Blood of the Door in Generation 6, the whole v1 Roll (55) by Generation 9, the 64 reached on the
Legacy of Generation 9 (cumulative 74). Every Inheritance a reference plan holds is bought by its
Generation.

## What else moved

- `CampaignEndTransactionTest.aDefeatPaysThePityFloorWithThreeExpeditions`: three Expeditions now
  pay 22 x sqrt(3 / 17) = 9.2 Marks, one Legacy by themselves, so the pity floor has nothing to add
  at three Expeditions. The test was split: the Defeat pays 1 with no pity, and with the Expeditions
  weight put back to 3 it still pays the pity floor. The pity floor is now idle at its own
  threshold; it stays as the brief says (it would matter again if the minimum of Expeditions fell
  below 3).
- `TollCalculatorTest`: the first-Expedition golden (Marks 8.0 to 28.6, Legacy 1 to 4), the Door,
  baseline, Load and residue tests (Ascent weight), and the defaults.
- Not moved: every other test, `RollRulesTest`, `RollEffectsTest`, `RollCommandsTest` included.

## Caveats

- Everything rests on the table's economy model (12 Expeditions, no Relics, 5 seconds a Floor, 165
  between). The weights must be refitted after a real playtest, as 3.4 says; the Expeditions weight
  is the soft one. A real Generation plays more Expeditions than 12 (3.10: 15 to 17), which pays
  `22 x sqrt(n / 17)`: 21 Marks at 15, 22 at 17.
- The Relic counter and the Offering Stone are untouched; Relics are not in the model.
- The Expedition count counts all 12; the table's model clears fewer Floors in the early ones than
  the 3 a real Expedition must clear to qualify. A real Campaign of Generation 1 would have to
  clear 3 or more Floors in each.

## Rerun

From `backend/`:

```
# the check: every Generation within one point, Generation 1 can Exodus, the Roll's schedule
mvn test -Dtest=PacingTollTest

# print the Tally, the Marks per source and the Legacy of each Generation
mvn test -Dtest=PacingTollTest -Dpacing.toll=1

# the pacing table the Toll is read against, and the Toll's own golden tests
mvn test -Dtest='PacingTableTest,TollCalculatorTest'
```

To refit after a change to the curve or the table: edit `ascent-marks-per-floor` and
`counters.expeditions.marks` in `application.yml` and the defaults in `GameProperties.Prestige`
(`TollCalculatorTest.applicationYmlBindsToTheSameValuesAsTheDefaults` checks they agree), and run
the print above until each Generation is within one point of the table.
