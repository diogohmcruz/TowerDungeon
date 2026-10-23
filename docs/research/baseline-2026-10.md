# Baseline balance report for the scaling pass (#8), October 2026

The first measured baseline of the game as it is when the telemetry pipeline of #65 lands:
no Package B, no unit-depth epic (#41), no Relic change. Every one of those moves the numbers, so
this report must be **rerun** after each (see "Rerunning"). Rows from before and after a balance
change carry a different `config_hash` and are never merged: the report groups by
`(engine, config_hash)`.

## What was measured

The game measured is `main` at the commit that adds the Year and its Quarters. An earlier run on the
game just before the Year gave `config_hash` `4586011f096d16a5` and a greedy Wipe rate of 18.7%: the
Year moved the numbers and the hash told the two apart, which is what the hash is for.

- Engine `td-1` on the manual tick driver, `config_hash` `be9c6c6af14b3a62` (the hash of the game
  configuration the Sandbox ran with).
- Three scripted policies, each on 1000 Sandbox Campaigns, seed `1` (Campaign `i` of a batch uses a
  seed derived from the batch seed and `i`, so the same seed and policy give identical rows):
  - `greedy`: buys the cheapest affordable Archers and Warriors, climbs until Supplies fall to 30%,
    then Extracts.
  - `cautious`: Extracts as soon as a Guardian Floor is cleared, and for no other reason.
  - `random`: picks among what is legal at each step and Extracts at random.
- Each Campaign stops after 150 Expeditions (`MAX_EXPEDITIONS`) or earlier on a stop condition.
- Ticks are tagged Village-only or Expedition. Floor rows hold only Expedition ticks. Time at home
  appears only in the Expedition and Campaign rows (`village_ticks_before`, `village_ticks`), so idle
  farming never contaminates a Floor number.
- `Units needed` is **computed** by `td_balance_report` as `sqrt(HP / (1.25 x d))` from the rows
  (`HP` the Garrison's total health on the Floor, `d` the measured damage per Unit per tick). The
  formula quoted in #8 was not re-derived; the report just applies it to measured `d`.

## Rerunning

From `backend/`, with no server on the profile (it plays, prints and exits):

```
mvn spring-boot:run -Dspring-boot.run.profiles=playtest \
  -Dspring-boot.run.arguments="--playtest.campaigns=1000 --playtest.seed=1 --playtest.report=report.md"
```

Optional settings: `playtest.policies` (default `greedy,cautious,random`), `playtest.max-expeditions`
(150), `playtest.workers` (4), `playtest.min-samples` (30, below which a Floor is flagged `thin`).
Rows land under `data/telemetry/{batch_id}.jsonl` (git-ignored). A model reads the same table with
the `td_balance_report` MCP tool, and `td_get_telemetry` reads the rows. Wall time for this baseline
on a laptop: about 50 s, 70 s and 45 s for the three policies.

## Reading the numbers

- The tower the bots reach is shallow: no policy gets past Floor 5 (a Surge Floor), and Floor 1 and
  Floor 2 decide almost every Expedition. Floors 40 to 100 cannot be sized from this baseline;
  what it does give is the per-Floor shape of Floors 1 to 5 and a validated pipeline.
- `greedy` Wipes 22.8% of its Expeditions and reaches Floor 2 on average (mean 2.33). Its Floor 1 attrition (about
  41 Supplies) dwarfs combat damage (about 8), so Supplies, not fighting, set how far a Party goes.
- `cautious` Wipes in 100% of Expeditions: no policy ever reaches a Guardian Floor (none appears in the table), so it never Extracts until
  Supplies are gone, and Floor 2 is where starvation (about 41 per Floor) kills it. This policy shows
  that Extract-after-Guardian is no rule at all while the first Guardian is out of reach.
- `random` reaches deepest (mean 4.06, max 5) by accident, with a smaller Party (about 3.5 Units) that
  drains Supplies more slowly; Floor 4 is where it hits Garrisons of about 48 HP with a needed
  size of about 5.9 Units.
- Across Floors 1 to 4 the Units needed is about 5 to 6.5 for the measured `d`, which is close to the
  Party the economy affords at the start (about 7.6): the early curve is tight, not slack.
- Village farming runs at about 10 food per Village tick for every policy (the column is constant:
  the early game has one farm rate).

## Full tables (seed 1, 1000 Campaigns each)

## Batch tb-d3ef00f2cbd94919

### engine td-1, config_hash be9c6c6af14b3a62

| Policy | Campaigns | Expeditions | Highest Floor mean | median | p90 | max | Wipe rate | Ticks per Expedition | Village ticks per Expedition tick | Food grown per Village tick | Stops |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| greedy | 1000 | 150000 | 2.33 | 2 | 3 | 4 | 22.8% | 8.48 | 0.51 | 10.06 | MAX_EXPEDITIONS=1000 |

Per Floor (379738 Floor rows). `thin` = fewer than 30 samples. Units needed = sqrt(HP / (1.25 x d)), computed here, not re-derived.

| Floor | Kind | Samples | Thin | Cleared | Wiped here | Party at start | Ticks | Garrison HP | d (dmg/Unit/tick) | Units needed | Supplies drained | Taken combat | Taken attrition | Taken starvation | Units lost |
|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | Approach | 150000 |  | 100.0% | 0.0% | 7.90 | 1.00 | 0.00 | 0.000 | n/a | 11.35 | 0.00 | 8.19 | 0.00 | 0.25 |
| 1 | - | 150000 |  | 52.9% | 17.6% | 7.65 | 6.63 | 22.78 | 0.481 | 6.15 | 55.07 | 7.61 | 40.77 | 0.00 | 2.98 |
| 2 | - | 79396 |  | 0.4% | 9.8% | 5.91 | 1.57 | 34.77 | 0.735 | 6.15 | 9.71 | 2.93 | 7.25 | 0.00 | 0.97 |
| 3 | - | 338 |  | 1.2% | 26.6% | 1.19 | 5.16 | 29.89 | 1.801 | 3.64 | 7.32 | 11.26 | 5.42 | 0.00 | 0.38 |
| 4 | - | 4 | thin | 0.0% | 75.0% | 1.00 | 2.50 | 36.00 | 2.002 | 3.79 | 2.49 | 5.24 | 2.33 | 0.00 | 0.75 |

## Batch tb-5a4245bed60b44a1

### engine td-1, config_hash be9c6c6af14b3a62

| Policy | Campaigns | Expeditions | Highest Floor mean | median | p90 | max | Wipe rate | Ticks per Expedition | Village ticks per Expedition tick | Food grown per Village tick | Stops |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| cautious | 1000 | 150000 | 3.02 | 3 | 3 | 4 | 100.0% | 12.26 | 0.04 | 10.01 | MAX_EXPEDITIONS=1000 |

Per Floor (447875 Floor rows). `thin` = fewer than 30 samples. Units needed = sqrt(HP / (1.25 x d)), computed here, not re-derived.

| Floor | Kind | Samples | Thin | Cleared | Wiped here | Party at start | Ticks | Garrison HP | d (dmg/Unit/tick) | Units needed | Supplies drained | Taken combat | Taken attrition | Taken starvation | Units lost |
|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | Approach | 150000 |  | 100.0% | 0.0% | 7.83 | 1.00 | 0.00 | 0.000 | n/a | 11.61 | 0.00 | 8.02 | 0.00 | 0.00 |
| 1 | - | 150000 |  | 97.3% | 2.7% | 7.83 | 5.73 | 17.89 | 0.434 | 5.74 | 60.79 | 5.37 | 43.05 | 0.33 | 0.92 |
| 2 | - | 145901 |  | 1.3% | 98.7% | 7.10 | 5.62 | 37.83 | 0.720 | 6.48 | 29.53 | 9.60 | 29.19 | 40.62 | 7.07 |
| 3 | - | 1947 |  | 1.4% | 98.6% | 2.58 | 5.72 | 37.13 | 1.103 | 5.19 | 4.18 | 10.69 | 7.98 | 27.02 | 2.56 |
| 4 | - | 27 | thin | 0.0% | 100.0% | 1.00 | 3.37 | 39.70 | 2.076 | 3.91 | 2.48 | 6.89 | 3.13 | 8.44 | 1.00 |

## Batch tb-92da68a1d70a471e

### engine td-1, config_hash be9c6c6af14b3a62

| Policy | Campaigns | Expeditions | Highest Floor mean | median | p90 | max | Wipe rate | Ticks per Expedition | Village ticks per Expedition tick | Food grown per Village tick | Stops |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| random | 1000 | 150000 | 4.06 | 4 | 5 | 5 | 65.4% | 10.02 | 0.68 | 10.48 | MAX_EXPEDITIONS=1000 |

Per Floor (357045 Floor rows). `thin` = fewer than 30 samples. Units needed = sqrt(HP / (1.25 x d)), computed here, not re-derived.

| Floor | Kind | Samples | Thin | Cleared | Wiped here | Party at start | Ticks | Garrison HP | d (dmg/Unit/tick) | Units needed | Supplies drained | Taken combat | Taken attrition | Taken starvation | Units lost |
|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | Approach | 144906 |  | 99.8% | 0.2% | 3.53 | 1.00 | 0.00 | 0.000 | n/a | 5.19 | 0.00 | 3.65 | 0.01 | 0.02 |
| 1 | - | 144682 |  | 32.5% | 45.4% | 3.51 | 6.39 | 26.18 | 0.714 | 5.42 | 27.12 | 10.75 | 19.87 | 0.09 | 1.29 |
| 2 | - | 46999 |  | 36.9% | 40.5% | 4.85 | 6.64 | 30.76 | 0.751 | 5.72 | 35.37 | 11.78 | 26.84 | 2.69 | 2.28 |
| 3 | - | 17322 |  | 17.4% | 61.7% | 4.62 | 6.16 | 40.87 | 0.943 | 5.89 | 26.87 | 13.68 | 22.31 | 10.92 | 3.21 |
| 4 | - | 3006 |  | 4.3% | 79.2% | 4.05 | 4.79 | 47.88 | 1.112 | 5.87 | 16.62 | 12.03 | 15.50 | 14.69 | 3.44 |
| 5 | Surge | 130 |  | 0.0% | 87.7% | 3.65 | 3.28 | 71.61 | 1.096 | 7.23 | 8.51 | 11.32 | 9.23 | 15.94 | 3.33 |

