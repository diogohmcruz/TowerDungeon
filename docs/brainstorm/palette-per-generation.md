# Palette per Generation (grim): design, verified

Design only. No repository file was written or edited. Terms follow `CONTEXT.md` and `docs/brainstorm/roadmap.md` (Generation, Succession, Tower Memory, Easing, Works, the Door, Mutation). Tags: [TW §n] is `docs/research/tower-wizard.md`; [Arch §n] is `architecture-and-visual-identity.md`; [PB §n] is `village-resources.md`; [P&P §n] is `prestige-and-persistence.md`; [Invented] is original to this game. Every colour number below was recomputed from the hex values with Python (section 12), not estimated. Nothing was rendered on screen. All hex values are starting values to be confirmed in the ink spike.

> **Decided by the author (2026-10-10):** the palette is the Tower's state, set by Generation, never a reward (recorded in `docs/adr/0004-the-palette-is-the-towers-state-not-a-reward.md`), and the darker, indigo-tinted night ground is accepted, so Generation 1's slot 0 becomes `#080923` (today's `#0e1424` stays as `LEGACY_INK` for the pinned round-trip test). Whether the dithered indigo shadow still reads as grim bleak stone is judged on screen in the ink spike (issue #15); the lighter CIE76-only alternative in open decision 1 is the fallback.

## 0. Constraints that shaped the ramps

The ramps satisfy these checks:

1. **Protanopia (the CVD gate).** Guardian crimson `#7a2230` simulates to `#393730`, an olive-grey at L* 22.9. A neutral slot cannot clear the gate against it: a neutral at L* 13 is 7.8 dE00 from simulated crimson, and at L* 19 only 5.0. The only lever left is chroma on the one axis protanopes keep (blue to violet). So **slots 0 and 1 carry OKLCH chroma 0.053 to 0.067**, hue 271 to 294. This is the biggest tone cost in the document; see open decisions 1 to 3 for the cheaper CIE76 alternative.
2. **Slot 1 against slot 0** is 1.322 to 1.340:1, with slot 1 at L* 14.6 to 16.2.
3. **Crimson as a fill on night slot 0** is 1.955 to 2.032:1 because slot 0 has Y at most 0.0037. Today's `#0e1424` gives only 1.834:1 (the 1.9:1 in [PB §8.4] is measured on `--panel`, not on `--ink`). **Generation 1 slot 0 is therefore `#080923`, not `#0e1424`** (dE00 4.6, L* 6.5 to 3.3).
4. Smaller checks (hue rule, Salt gap, day steps, lamp under tritan, adjacent Generation, crimson text on Day Pitch) are met in the ramps below.

## 1. The ruling: palette is the Tower's state, not a reward

[Arch §3.1, §7.3, §8.4 item 2] rejected a per-Generation ramp because [PB §R26 and §8] forbid "a palette swap as a reward". The two are reconciled by one rule:

> The ramp is a function of Tower Memory. It is never earned, bought, unlocked, previewed, chosen, or reversible. It changes only when `BEGIN_GENERATION` starts a new Campaign. It does not get nicer.

- `rampIndex = TowerMemory = min(Generation - 1, 4)` ([P&P §3.3]). Generations 5 and later share the last ramp, exactly as Tower Memory stops hardening at m = 4.
- Nothing in the Roll, Legacy, Loot or Settings touches it. The only control that remains is the existing Night/Day toggle, which is orthogonal.
- It is not a reward because it is not better. Measured: slot 0 only gets darker (L* 3.3, 3.1, 2.5, 2.0, 1.5); slot 1 is never lighter than Generation 1's by more than 0.2 L* (16.0, 16.2, 15.9, 15.1, 14.6); night line art is never brighter than Generation 1's by more than 1.9 L* (77.0, 78.9, 72.0, 78.7, 66.7); the last ramp is the dimmest.
- It earns its place in v1: [P&P §1, §3.12] admit v1 is the "samey" loop. Before Mutations (v2) exist, the ramp is the one thing that makes Generation 3 look like the Tower's third memory of you.
- Easing stays what [PB §R26] says: a thinning of the dark inside a ramp, never a recolour.
- Technique credit: Tower Wizard swaps palette per tower ascension, which is climbing a tower level, not Prestige [TW §3b, §5b]. Binding ours to Generation is our choice. We take the mechanism (a small ramp per state, one uniform update) and drop its brightness: its screenshots are pink, gold, green; ours are near-neutral lines over indigo-black shadow.

## 2. The five ramps

Slot meaning is **ink weight, not luminance**: slot 0 is nothing (fog, clear colour, scene background); slot 1 is shadow fill; slot 2 is detail and hatch; slot 3 is the line art ("white" at night, ink by day). In the Day theme slot 3 equals the Night ramp's slot 0 (the same black ink), which also fixes the lamp plate (section 3.5).

### Night (slot 0 equals fog, clear colour and scene background)

| Gen | Ramp | Slot 0 | Slot 1 | Slot 2 | Slot 3 | L* (0/1/2/3) | OKLCH C (0/1/2/3) | Meaning [Invented] |
|---|---|---|---|---|---|---|---|---|
| 1 | Cold Stone | `#080923` | `#232449` | `#667082` | `#b5bfcf` | 3.3 / 16.0 / 47.0 / 77.0 | 0.053 / 0.067 / 0.031 / 0.025 | The Tower as found: blue-black stone, unmarked and indifferent. Slot 0 is the void, a shade darker than today's `#0e1424`. Memory 0. |
| 2 | Ash | `#0c0722` | `#292348` | `#83838d` | `#c2c3c9` | 3.1 / 16.2 / 55.1 / 78.9 | 0.055 / 0.066 / 0.015 / 0.008 | What the last Campaign left on the stairs: the dead burned, the ash taken into every seam. Line and mid-tone lose their blue and lift to dust; only the shadow stays cold. Memory 1. |
| 3 | Bruise | `#0d0420` | `#292247` | `#6f6476` | `#b8adba` | 2.5 / 15.9 / 43.9 / 72.0 | 0.059 / 0.066 / 0.031 / 0.022 | Necrotic spread: the stone discolours the way flesh does under a blow, violet under the skin of the Tower. Mid-tone heavier, lines duller. Memory 2. |
| 4 | Bleach | `#03051f` | `#1e2346` | `#90959a` | `#c1c3c5` | 2.0 / 15.1 / 61.4 / 78.7 | 0.057 / 0.065 / 0.009 / 0.004 | The pigment is gone from everything but the dark. Chalk and old bone, the building drying out like something that was alive. Mid-tone at its highest, line and mid-tone near colourless, the shadow still ink. Memory 3. |
| 5+ | Pitch | `#05021d` | `#212144` | `#6c7682` | `#9ca3ad` | 1.5 / 14.6 / 49.2 / 66.7 | 0.061 / 0.064 / 0.022 / 0.017 | Blackened stone: soot and old fire. Darkest ground, dimmest lines. The Tower no longer returns light, and something below it is awake. Memory 4 (the cap). |

### Day / overcast (slot 0 is the ground; slot 3 is ink)

| Gen | Slot 0 | Slot 1 | Slot 2 | Slot 3 (= Night slot 0) | L* (0/1/2/3) | Reading |
|---|---|---|---|---|---|---|
| 1 | `#b9bcc0` | `#9499a0` | `#686e78` | `#080923` | 76.1 / 63.0 / 46.2 / 3.3 | Cold overcast. The low end of the proposed ash range in [PB §8.4]. |
| 2 | `#c0c1c6` | `#9fa0a6` | `#818086` | `#0c0722` | 78.1 / 66.0 / 53.8 / 3.1 | Dusty overcast. |
| 3 | `#b4aeb6` | `#8f8892` | `#6c6376` | `#0d0420` | 71.8 / 57.6 / 43.4 / 2.5 | Lilac-grey, heavy sky, a bruise held in the cloud. |
| 4 | `#c9c9c4` | `#a6a9ad` | `#858a8d` | `#03051f` | 80.8 / 69.1 / 57.2 / 2.0 | White-out. The high end of the proposed ash range. |
| 5+ | `#a8b0b8` | `#83898f` | `#687079` | `#05021d` | 71.4 / 56.8 / 46.9 / 1.5 | Smoke. Chosen so crimson stays at least 4.5:1 as `--ember-text` on this ground. Outside the proposed range (open decision 4). |

Generations 1 to 4 use the proposed ash range `#b9bcc0` to `#c9c9c4` for slot 0, so replacing sky-blue `#9fc0e8` and adding the family are one change. Sky-blue stays switchable by token [Arch §8.4 item 1].

### Role colours (not part of any ramp, never change per Generation)

| Role | Hex | OKLCH C | Rule |
|---|---|---|---|
| Guardian crimson | `#7a2230` (`--ember`) | 0.121 | Fill and line only, never scene text. Dim form `#4a1620` for a slain-this-Campaign seal. |
| Lamp resting / lit | `#8a5a1e` / `#c98a3a` | 0.097 / 0.122 | Human labour only, only on a plate (3.5). |
| Quarry mass | `#6a5326` | 0.069 | Art only, always a heap shape. |
| Salt | bone `#e7dcc4` (L* 88.0) | 0.034 | Art only, shape plus stipple. Same as the selection outline and numerals. |
| Spores | `#4a5a52` | 0.024 | Art only, shape plus a 1 px slot-3 rim. |

These are shader constants (`uRoleCrimson` and so on), never ramp slots. At most 4 ramp colours plus 3 role colours appear in a scene, the Tower Wizard "3 to 5 colours on near-black" discipline [TW §5b].

### Construction rules (each is a unit test, section 10)

1. **Cold hues only, 245 to 322 degrees OKLCH hue** wherever chroma exceeds 0.0075. Measured: shadows 271 to 294; the coldest and the most violet mid or line slots are 248 and 321. No ramp enters crimson (15.6), amber (68), Quarry (82), bone (86) or Spores (164). This is why there is no "rust" or "verdigris" ramp.
2. **Chroma budget, two tiers.** Slots 0 and 1 (and Day slot 3, which is Night slot 0): OKLCH C at most 0.070, measured maximum 0.067. Slots 2 and 3 and the Day grounds: at most 0.036, measured maximum 0.031. All chroma beyond 0.0075 sits at L* 17 or below or in slots 2 and 3, so it reads as depth of black, not as colour. For scale, crimson is 0.121 (1.8 times the shadow budget).
3. **Slot 0 is darker than `#0e1424` and never lighter with Generation**: Y 0.0037, 0.0035, 0.0028, 0.0023, 0.0016 (`#0e1424` is 0.0072). This gives crimson at least 1.9:1 as a fill and the resting lamp 3.32 to 3.45:1 on the plate.
4. **Crimson CVD gate.** Every slot of every ramp in both themes is at least 20 CIEDE2000 from crimson under normal, protan, deutan and tritan vision (measured minimum 20.8, protan). As a backstop, no slot is within 6 L* of crimson's lightness under any vision (measured minimum 6.1).
5. **Slot 3 at most 78.9 L*, so Salt (88.0) is always at least 9.2 L* brighter** and the selection outline is the brightest thing on screen.
6. **Line art against ground at least 7.5:1** (slot 3 to slot 0): night 10.54, 11.17, 9.21, 11.36, 7.99; day 10.26, 10.93, 9.16, 12.09, 9.26.
7. **Faint lines still read**: slot 1 against slot 0 at least 1.3:1 (night 1.322 to 1.340; day 1.420 to 1.611).
8. **Day grounds are light enough for crimson text**: crimson against Day slot 0 at least 4.5:1 (measured 4.56 on Pitch, which has the lowest ground at L* 71.4).
9. **Hardening is in the shadow and the line, not in a brighter shine.** Slot 0 gets darker and slot 3 dimmer toward Pitch; Bleach lifts only the mid-tone, and its line stays below Salt.

## 3. Verified numbers

Method in section 12. Vision means normal, protanopia, deuteranopia, tritanopia (Machado 2009, severity 1.0, applied in linear RGB). dE is CIEDE2000 unless it says CIE76.

### 3.0 Gate summary (the thresholds set for this verification)

| Gate | Threshold | Worst measured | Result |
|---|---|---|---|
| Slot 3 vs slot 0, night | at least 7:1 | 7.99 (Pitch) | pass |
| Slot 3 vs slot 0, day | at least 4.5:1 | 9.16 (Bruise) | pass |
| Slot 1 vs slot 0, both themes | at least 1.3:1 | 1.322 (Pitch, night) | pass |
| Crimson vs slot 0 as a fill, night | at least 1.9:1 | 1.955 (Cold Stone) | pass |
| Crimson vs slot 0 as a fill, day | at least 1.9:1 | 4.56 (Pitch) | pass |
| Crimson vs nearest ramp colour, CVD, dE00 | at least 20 | 20.8 protan; 24.4 normal; 23.3 deutan; 26.1 tritan | pass |
| Same, CIE76 | at least 20 | 25.1 protan | pass |
| Slot order monotonic in luminance | night rising, day falling | holds under all four visions | pass |
| Adjacent Generations differ in lightness | slot 2 at least 5 L* and at least 7.5 dE00 under every vision | 7.6 L*, 7.6 dE00 (Day 1 to 2) | pass |

### 3.1 Per ramp and theme (slot 3 vs 0, slot 1 vs 0, crimson vs 0, then minimum dE00 from crimson to any slot of the ramp per vision, then minimum CIE76 over all visions)

| Ramp | S3/S0 | S1/S0 | Crimson/S0 | Normal | Protan | Deutan | Tritan | CIE76 min |
|---|---|---|---|---|---|---|---|---|
| Night 1 | 10.54 | 1.323 | 1.95 | 27.4 | 20.9 | 27.0 | 34.3 | 28.2 |
| Night 2 | 11.17 | 1.336 | 1.96 | 26.0 | 20.8 | 28.6 | 31.8 | 28.1 |
| Night 3 | 9.21 | 1.340 | 1.99 | 24.4 | 20.9 | 23.4 | 26.1 | 25.1 |
| Night 4 | 11.36 | 1.324 | 2.01 | 28.3 | 20.8 | 32.2 | 33.3 | 28.0 |
| Night 5+ | 7.99 | 1.322 | 2.03 | 27.3 | 20.8 | 26.0 | 32.8 | 28.0 |
| Day 1 | 10.26 | 1.504 | 5.25 | 30.3 | 20.9 | 23.3 | 29.4 | 26.0 |
| Day 2 | 10.93 | 1.451 | 5.57 | 29.3 | 20.9 | 26.3 | 32.6 | 28.3 |
| Day 3 | 9.16 | 1.584 | 4.61 | 24.7 | 20.9 | 23.9 | 26.7 | 25.2 |
| Day 4 | 12.09 | 1.420 | 6.02 | 30.9 | 20.8 | 28.7 | 33.3 | 28.1 |
| Day 5+ | 9.26 | 1.611 | 4.56 | 30.1 | 20.8 | 23.4 | 30.2 | 26.4 |

Simulated crimson: normal `#7a2230` (L* 28.1), protan `#393730` (22.9), deutan `#4f492e` (31.0), tritan `#860d28` (28.1). Protanopia is the binding vision for every ramp; the nearest slot is slot 0 or 1 at night and slot 3 (or 2 on Bruise) by day. Night ember-text `#d0556a` on slot 0 is 4.83 to 5.02:1 (at least 4.5).

### 3.2 Each ramp is usable as a ramp (minimum over all four visions, slot 0 to 1 / 1 to 2 / 2 to 3)

| Ramp | Night | Day |
|---|---|---|
| 1 | 8.7 / 24.4 / 25.5 | 10.2 / 15.9 / 31.3 |
| 2 | 8.6 / 32.3 / 19.1 | 9.2 / 10.6 / 38.6 |
| 3 | 9.2 / 22.6 / 25.3 | 11.7 / 14.1 / 29.1 |
| 4 | 9.1 / 40.3 / 13.3 | 9.2 / 10.0 / 42.3 |
| 5+ | 9.3 / 27.4 / 15.7 | 12.2 / 9.8 / 33.0 |

The night slot 0 to 1 step is still the smallest (dE 8.6 to 9.3, was 5.6 to 7.1) because it is a shadow fill against the void; structure is carried by slots 2 and 3. Every other step is at least 13.3 (night) or 9.2 (day). All 40 step pairs (5 ramps, 2 themes, 4 slots) also keep slot order monotonic in the CVD-simulated luminance.

### 3.3 Crimson, other measures

- **Lightness gap** (minimum over the ramp's slots, per vision, night): Cold Stone 12.1 / 6.1 / 15.4 / 11.7 (normal, protan, deutan, tritan); Ash 11.9 / 6.2 / 15.0 / 11.5; Bruise 12.3 / 6.6 / 13.2 / 11.8; Bleach 13.0 / 6.9 / 16.4 / 12.6; Pitch 13.5 / 7.6 / 16.8 / 13.1. Day minimum 12.5.
- **50% dither mix** (averaged in linear light) of night slots 1 and 2 is 22.1 to 28.1 dE from crimson (normal) and 16.7 to 19.5 under protan. Night slots 0 and 1 mix: 20.8 or more under every vision. **Not gated, stated for honesty:** the Day mix of slots 2 and 3 is only 13.1 to 17.2 dE from crimson under protan, so by day a dithered dark mid-tone can sit near crimson for a protanope; shape and numeral carry the Guardian there (section 5).
- **Dim crimson `#4a1620`** (slain-this-Campaign seal) against night slot 1 is 16.4 to 17.0 under protan and at least 19.7 normal. Against crimson itself it is 10.4 normal and 7.5 protan, so the struck numeral still carries that state.
- Crimson against Day ground (as `--ember-text` in the light variant): 5.25, 5.57, 4.61, 6.02, 4.56:1.

### 3.4 Lamp amber

| Gen | Resting on plate (night slot 0) | Lit on plate | Min dE of resting lamp to any slot (any vision, both themes) | Same for lit lamp |
|---|---|---|---|---|
| 1 | 3.32 | 6.69 | 26.0 | 23.1 |
| 2 | 3.33 | 6.72 | 25.2 | 22.6 |
| 3 | 3.38 | 6.80 | 18.6 | 21.6 |
| 4 | 3.41 | 6.87 | 27.6 | 21.6 |
| 5+ | 3.45 | 6.95 | 28.0 | 25.8 |

Rule: resting at least 3:1 and lit at least 6:1 on the plate (met); at least 25 dE normal (measured 27.3 or more) and at least 18 under any vision (measured 18.6, resting lamp vs Bruise under tritan). Resting lamp on slot 1 is only 2.49 to 2.61:1 and on slots 2 and 3 is 1.06 to 3.35:1, so **a lamp is only ever drawn on a plate**: a slot 0 patch with a one-pixel slot 1 frame. Windows are cut to the void. In Day the plate is Day slot 3 (identical to night slot 0). Quarry `#6a5326` is at least 25.3 dE from every slot (normal) and 13.9 under tritan; it is 5.6 to 9.2 from the resting lamp (pre-existing), so Quarry is always a heap shape, never a point of light.

### 3.5 Resource tints

| Resource | Contrast / distance | Treatment |
|---|---|---|
| Spores `#4a5a52` | On night slot 0 2.68 to 2.78:1 (below 3:1); on day ground 3.33 to 4.39:1. Against its own 1 px slot-3 rim 2.87 to 4.15:1 by night. Rim against ground 7.99 to 11.36:1 by night. dE to the nearest slot at least 14.4 normal and 9.6 under any vision; the nearest slot is slot 2 on all ten ramps. | Always a shape (the Spore sprite and the Cellar slit) with a 1 px slot-3 rim. Not lightened: [PB §8.4] says about `#4a5a52`, and a lighter value closes the gap to slot 2. |
| Salt, bone `#e7dcc4` | Night: 11.0, 9.2, 16.1, 9.4, 21.3 L* brighter than slot 3; dE to slot 3 at least 11.9 under every vision. Contrast to night slot 0 14.37 to 14.94:1. Day: only 1.22 to 1.61:1 on the ground. | A stippled crust shape, never a flat fill. In Day it always carries a 1 px slot-3 outline (outline to ground 9.16 to 12.09:1). |
| Bone vs Spores | dE 43.3 to 48.1 | Not an issue. |

### 3.6 Adjacent Generations differ in lightness, not only hue

| Transition | Night slot 2 dL* / min-vision dE | Day slot 2 dL* / min-vision dE |
|---|---|---|
| 1 to 2 | +8.0 / 8.5 | +7.6 / 7.6 |
| 2 to 3 | -11.1 / 11.1 | -10.5 / 11.1 |
| 3 to 4 | +17.5 / 17.2 | +13.8 / 14.5 |
| 4 to 5+ | -12.2 / 11.8 | -10.3 / 10.4 |

Slot 0 also steps darker every Generation (night Y 0.0037 to 0.0016), but by 1.4 to 2.9 dE only; the visible change is the mid-tone and line. **Non-adjacent pairs that stay confusable** (slots 2 and 3 within 6 dE under some vision): night 1 and 3 (4.8), 2 and 4 (5.9), 3 and 5+ (4.6); day 1 and 3 (3.0), 1 and 5+ (1.7), 2 and 4 (3.1), 3 and 5+ (3.6). A player sees one ramp per Campaign, so this only matters across screenshots and memory; section 5 gives the cues that do not use colour.

### 3.7 Backgrounds and the page

- Night slot 0 dE from today's `#0e1424`: 4.6, 5.9, 6.7, 4.0, 5.0 (Generation 1 is 4.6). Fog colour, clear colour, `scene.background` and CSS `--ink` all read slot 0, so far geometry dissolves into the exact void in every Generation. `--ink` is 7.5 to 9.6 dE from `--panel` `#0b0e17`.
- Day: sky-blue `#9fc0e8` (C 0.067) is gone. Day slot 0 is `#b9bcc0` up to `#c9c9c4`, then `#a8b0b8` for Pitch.
- Text on night slot 0: `--text` `#aab3c7` 9.30 to 9.66:1, `--muted` `#7b869c` 5.34 to 5.55:1, bone 14.37 to 14.94:1.

## 4. What does not change per Generation

| Token ([PB §8.4]) | Changes? | Why |
|---|---|---|
| `--text` `#aab3c7`, `--muted` `#7b869c`, `--bone` `#e7dcc4`, `--ember-text` `#d0556a` (night) and `#7a2230` (day), `--ember`, `--ember-dim` | **No** | Measured on `--panel` `#0b0e17`: 9.16, 5.26, 14.17, 4.76. Nothing here moves. |
| `--panel`, `--void` `#07090f`, `--cold` `#8fb7ff`, interactive borders | **No** | `--cold` is DOM-only; the sprite rim is baked as slot 3 [Arch §3.1], so it follows the ramp. |
| `--ink` | **Yes (scene backdrop only)** | Set from slot 0. Not a text surface; text on it would still pass (section 3.7). |
| `--rule` (1 px decorative hairline) | **Yes** | Same lightness as the original `#2e3546` (L* 22.2, 1.573:1 on the panel): Generation 1 `#323445` (1.572), 2 `#353346` (1.573), 3 `#363345` (1.575), 4 `#313446` (1.570), 5+ `#323445` (1.572); dE 2.3 to 5.3 from the original. Decoration only. Day `--rule` is not yet specified; apply the same rule when the light variant is verified. |
| Selection outline | Night: no (bone). Day: slot 3 | Always against slot 0 with a 1 px gap; bone to night slot 0 is 14.4 to 14.9:1. In Day bone fails (1.22 to 1.61:1), so the Day outline is the heavier slot-3 ink. |
| `fogDensity` (0.05 night, 0.028 day) | No | Starving still adds +0.01 [PB §8.4]. |

## 5. Second cues: the Generation is never read from colour

Across Generations the line and mid-tone are near-neutral and the shadow is a similar indigo, so colour is the weakest cue (3.6 lists the confusable pairs). Three cues that do not use colour:

1. **A numeral.** The Succession screen and the HUD lintel print `GENERATION 003` in the numbered-lintel style [Arch §5.3 item 6]; the numeral keeps counting past 5. It is the only cue that tells 6 from 5.
2. **Generation strokes on the Base slab.** One vertical hairline stroke per Generation in slot 3, a gate of five then a diagonal (Generations 1 to 5; from 6 the strokes stay at one gate and the numeral carries the count). They appear on Succession, drawn in with the Tower's lines. Not "notches" (Risen) and not "Tally".
3. **Dither character.** One kernel and bias per Generation in the shared `ink` shader [Arch §3.3]: 1 `bayer4` bias 0 (today's look), 2 `hatch4` 0, 3 `bayer8` 0, 4 `bayer2` +0.08, 5+ `bayer4` -0.12 (the dark gains weight). **[To verify in the ink spike; nothing rendered.]** The `prefers-contrast` path flattens dither and removes cue 3, which is why 1 and 2 are the primary cues.

Shape rules that keep colour from carrying meaning:
- **Guardian.** A Guardian Floor carries the seal ring, the numeral and the DOM text GUARDIAN; crimson is the third cue. In-scene labels are slot 3 or bone, never crimson. The slain-this-Campaign state is the ring with a struck numeral, because dim crimson is only 7.5 dE from crimson under protan.
- **Lamps** are small plates with a window frame; **Salt** is a stippled crust; **Spores** have a rim and a slit silhouette; icons are single-tone shapes.

## 6. Transition rule

1. **When.** The ramp is a pure function of the **current Campaign's** Generation. The backend sends that Generation (a small integer, never the seed or `towerSecret`), constant for the whole Campaign, incrementing only at `BEGIN_GENERATION`. "Never mid-Campaign" is structural. Defeat, Victory and Exodus leave the old Tower and ramp in place during phase `OFFER`; the Succession screen is drawn on the old Tower.
2. **How.** On `BEGIN_GENERATION` the four ramp uniforms ease from old to new over 2 s, linearly in linear light, in the same frame the Tower's lines are redrawn from the Base upward [Arch §7.3] and the new stroke is scored. The clear colour and fog ease with slot 0. With `prefers-reduced-motion` it is a cut. No sound, banner, flash, particles or camera move.
3. **Announcement.** One Ledger line: `Generation 003 begins.` No congratulation. The colour is the reveal.
4. **Never animated otherwise.** On load, restore, import or theme toggle the ramp applies immediately.
5. **Easing and the ramp reset together** [P&P §3.3]: Works fall back to Raw in the same moment, one visible turn of the page.

## 7. Interactions

- **Easing and Works** [Arch §4.5]. Works remap indices inside a ramp (Raw 0 steps, Chalked 1 to 2, Shored 3 to 5, Held 6 or more: Held drops the slot-1 fill and keeps slot-3 edges, "the white read"). They never swap ramps. The Held read is at least 7.99:1 at night (Pitch) and 9.16:1 by day, and dimmest in Pitch: the relief gets bleaker with Generation, by design.
- **Mutation signs** (v2 [Arch §7.3]). Drawn only from ramp slots (stipple density, hatch, a glyph), so they hold on every Generation (slot 3 to slot 1 is at least 46 dE at night and 42 by day, any vision). They never use role colours, except Unburied's second seal (a smaller crimson ring, a Guardian state). Salt-pale streaks use slot 3, not bone. Spore Blight marks use slot 2 stipple, not the Spores tint.
- **The Door** (Floor 99). Sealed: slot-3 hairline double frame, slot-0 void, slot-2 hatch bars. Open (Blood of the Door): bars gone, void with a dither fade. Never crimson (not a Guardian), never amber (not labour). Absence of crimson at Floor 99, then crimson at the Floor 100 Gate, is itself a state signal.
- **Reseed** and the Floor 100 Gate are unaffected; the ramp acts on indices, not Architectures.
- **Village states** [PB §8.4]. Low Pantry desaturates sprites by 15%, Starving raises fog by +0.01, Wipe aftermath is "ink and crimson only". All operate on role colours, uniforms and slot 0, so none is invalidated.
- **Tower Wizard's technique** [TW §5b]: few colours per state (4 ramp plus 3 role), dither as depth, flat bordered panels, a thick outline on the selected thing, tiny silhouettes. The new use is a per-state ramp; we drop its hue range and keep the tone bleak.

## 8. Data shape: `palette.ts`

Planned location `three/ink/palette.ts` per [Arch §3.3]; today the scene lives under `frontend/src/app/components/tower-scene/`.

```ts
export type Theme = 'night' | 'day';
/** [0] = fog = clear colour = background; [3] = line art. Index = ink weight, not luminance. */
export type Ramp = readonly [string, string, string, string];
/** 5 means "Generation 5 and later". Equals Tower Memory + 1. */
export type RampGeneration = 1 | 2 | 3 | 4 | 5;
export type Kernel = 'bayer4' | 'hatch4' | 'bayer8' | 'bayer2';
export interface GenerationCue { readonly kernel: Kernel; readonly bias: number; readonly strokes: RampGeneration; }

export const RAMPS: Readonly<Record<RampGeneration, Readonly<Record<Theme, Ramp>>>> = {
  1: { night: ['#080923', '#232449', '#667082', '#b5bfcf'], day: ['#b9bcc0', '#9499a0', '#686e78', '#080923'] },
  2: { night: ['#0c0722', '#292348', '#83838d', '#c2c3c9'], day: ['#c0c1c6', '#9fa0a6', '#818086', '#0c0722'] },
  3: { night: ['#0d0420', '#292247', '#6f6476', '#b8adba'], day: ['#b4aeb6', '#8f8892', '#6c6376', '#0d0420'] },
  4: { night: ['#03051f', '#1e2346', '#90959a', '#c1c3c5'], day: ['#c9c9c4', '#a6a9ad', '#858a8d', '#03051f'] },
  5: { night: ['#05021d', '#212144', '#6c7682', '#9ca3ad'], day: ['#a8b0b8', '#83898f', '#687079', '#05021d'] },
};
export const CUES: Readonly<Record<RampGeneration, GenerationCue>> = {
  1: { kernel: 'bayer4', bias: 0, strokes: 1 }, 2: { kernel: 'hatch4', bias: 0, strokes: 2 },
  3: { kernel: 'bayer8', bias: 0, strokes: 3 }, 4: { kernel: 'bayer2', bias: 0.08, strokes: 4 },
  5: { kernel: 'bayer4', bias: -0.12, strokes: 5 },
};
/** Never per Generation. Shader constants, not ramp slots. */
export const ROLES = {
  crimson: '#7a2230', crimsonDim: '#4a1620', lampRest: '#8a5a1e', lampLit: '#c98a3a',
  quarry: '#6a5326', salt: '#e7dcc4', spores: '#4a5a52',
} as const;
/** The pre-ramp scene background, kept only for the colour-space round-trip test. */
export const LEGACY_INK = '#0e1424';

export function rampGeneration(generation: number): RampGeneration {
  if (Number.isNaN(generation)) return 1;
  const g = Math.trunc(generation);          // 1.9 -> 1; Infinity stays Infinity and clamps below
  return (g < 1 ? 1 : g > 5 ? 5 : g) as RampGeneration;
}
export const rampFor = (generation: number, theme: Theme): Ramp => RAMPS[rampGeneration(generation)][theme];
export const lampPlate = (generation: number): string => rampFor(generation, 'night')[0];
/** Mirrors the backend's m = min(Generation - 1, 4). */
export const towerMemory = (generation: number): number => rampGeneration(generation) - 1;
```

**Clamp.** Generation 6 and above (including 1000, `+Infinity`) resolve to 5. Generation 0, negative, `-Infinity` and `NaN` resolve to 1. Fractions truncate. `undefined` from a pre-v1 server resolves to 1 through `state.generation ?? 1`. The numeral is the raw Generation; `strokes` clamps at 5.

**Pipeline.** The shader's `uRamp[4]` (linear, from `ColorManagement`) is fed from `rampFor(generation, theme)`; `scene.background`, `scene.fog` and the clear colour read slot 0. Roles are separate uniforms. A small effect sets `data-gen` on `<html>` and writes `--ink` and `--rule`; the existing `readTheme()` and `MutationObserver` [Arch §3.2] already watch the root. Persistence stores nothing: the ramp is derived. Backend: one new field `generation` on `GameState`.

## 9. Reconciling the atlas index meaning

[Arch §3.3] says an atlas texel stores "0 clear, 1 line, 2 detail, 3 fill or hatch". This design needs slot 3 to be the line (so the same index works by day). Re-label the indices as tone ranks before any cell is painted: **0 clear, 1 fill or hatch, 2 detail, 3 line**; or keep the labelling and add `ATLAS_ROLE_TO_SLOT = { clear: 0, line: 3, detail: 2, fill: 1 }`.

## 10. Tests to write

Colour-metric helper (test tree only): WCAG 2.x contrast, CIEDE2000, Machado 2009 matrices, OKLCH conversion. Verify the helpers first against the Sharma CIEDE2000 reference pairs and WCAG black-on-white 21:1, so a bug in the helper cannot make a threshold pass.

| # | Test | Assertion |
|---|---|---|
| 1 | Shape | 5 x 2 x 4 slots, all `^#[0-9a-f]{6}$`; keys exactly 1 to 5; `RAMPS[1].night[0] === '#080923'`; `ROLES.crimson === '#7a2230'`. |
| 2 | Colour-space round trip | `LEGACY_INK` `#0e1424`, `#7a2230` and every ramp slot survive hex to `THREE.Color` (linear) to output within 1/255. |
| 3 | Clamp table | `rampGeneration` of 1, 2, 3, 4, 5, 6, 99, 1000, `Infinity` is 1, 2, 3, 4, 5, 5, 5, 5, 5; of 0, -3, `-Infinity`, `NaN`, 1.9 is 1; `rampFor(undefined as any, t)` is the Generation 1 ramp. |
| 4 | Fog and clear equal slot 0 | `scene.background`, `scene.fog.color` and uniform slot 0 are equal, per Generation and theme. |
| 5 | Lamp plate | `RAMPS[g].day[3] === RAMPS[g].night[0]`. |
| 6 | Roles are not ramp colours | No `ROLES` value appears in any ramp. |
| 7 | Chroma, hue, luminance | Slots 0, 1 and Day slot 3: OKLCH C at most 0.070; slots 2, 3 and Day grounds: at most 0.036; wherever C exceeds 0.0075, hue in [245, 322]; night slot 0 Y at most 0.0040 and non-increasing with g; night slot 3 L* at most 79; bone at least 9 L* above it. |
| 8 | Line, shadow and steps | Slot 3 to slot 0 at least 7.5:1 (both themes); slot 1 to slot 0 at least 1.3:1 (both themes); adjacent-slot dE00, minimum over visions: night 0 to 1 at least 8, other night steps at least 13, day steps at least 9; luminance strictly rising (night) and falling (day) in all four visions. |
| 9 | Crimson | For every slot, both themes: dE00 at least 20 under normal, protan, deutan and tritan; crimson against night slot 0 at least 1.9:1 and against Day slot 0 at least 4.5:1; no slot within 6 L* of crimson's lightness under any vision; the 50% mix of night slots 1 and 2 at least 14 dE away under protan; dim crimson to night slot 1 at least 15 dE under every vision. |
| 10 | Lamp | Resting at least 3.0:1 and lit at least 6.0:1 on `lampPlate(g)`; dE to every slot at least 25 normal and at least 18 under any vision. |
| 11 | Tints | Spores to every slot at least 14 normal and 9 under any vision; Salt at least 9 L* above night slot 3 and at least 11.7 dE from it under every vision; Day Salt outline slot 3 at least 7.5:1 against slot 0. |
| 12 | Text tokens | The token snapshot (`--text`, `--muted`, `--bone`, `--ember-text`, `--panel`, `--void`) is identical across Generations; `--text` on slot 0 at least 9.0:1, `--muted` at least 5.0:1, bone at least 14:1; `--rule` against `--panel` stays within 1.56 to 1.59. |
| 13 | Adjacent Generation | Slot 2 between g and g+1: at least 5 L* apart and at least 7.5 dE00 under every vision, both themes. |
| 14 | Cues unique | `(kernel, bias)` unique per Generation; `strokes` equals the clamped Generation; numeral for 6 differs from 5. |
| 15 | Transition | Ramp unchanged across state pushes while `PLAYING`; changes only on the `OFFER` to `PLAYING` edge with a new Generation; 2000 ms, linear in linear light; restore and import apply with no ease; `prefers-reduced-motion` cuts. |
| 16 | Works and signs | Works remaps and sign cells reference only indices 0 to 3; Held always leaves slots 0 and 3 present. |
| 17 | Backend | `GameState` JSON exposes `generation` and not `towerSecret` or the seed; stable across N ticks; `BEGIN_GENERATION` is the only increment; `min(g - 1, 4)` matches `towerMemory` on a shared table 1 to 8. |
| 18 | Optional goldens | One screenshot of a fixed Tower per Generation and theme at `PIXEL_SCALE` 2, within a tolerance. |

## 11. Amendments this design needs in the existing documents (not made)

- `roadmap.md` conflict 3: replace with the ruling in section 1 and point at this design.
- `architecture-and-visual-identity.md`: §3.1 "Palette driver" row and §3.3 `palette.ts` bullet (RAMPS keyed by Generation, then theme); §7.3 last "No ramp family per Generation" bullet (reverse); §8.4 decision 2; §3.3 atlas index meaning (section 9); §3.3 "Slot 0 is `#0e1424`" (now `#080923`, with `#0e1424` kept as the legacy token).
- `village-resources.md` §R26 and §8: keep for Easing and states; add "ramp per Generation is the Tower's state, not a reward". §8.4 "crimson is 1.9:1" stays true for `--panel`; the scene ground is now at least 1.95:1.
- `prestige-and-persistence.md`: R12 already lists "palette per Generation"; add `generation` to the pushed state (not stored).
- `CONTEXT.md`: no new term. "Generation strokes" is an art word only.

## 12. Method and what is not verified

- Python (`python3 -I`, standard library only): sRGB to linear, WCAG 2.x relative luminance and contrast, CIE Lab (D65) with CIEDE2000 and CIE76, OKLab and OKLCH (Ottosson), and Machado et al. 2009 dichromacy simulation (severity 1.0) in linear sRGB, clipped to gamut.
- The helpers were checked first: 14 Sharma CIEDE2000 reference pairs to 0.001, WCAG black on white 21:1, `#777777` on white 4.478:1, Lab and OKLCH of pure red and white, OKLCH hex round trips.
- Each ramp was solved by search (coordinate descent on OKLCH with exhaustive sRGB grids for the dark slots), then **re-measured from the final hex values** by a second script that restates only the task thresholds (all pass), and by a 454-check script that also covers the design rules in section 2.
- Dichromacy simulation is the worst case; anomalous trichromacy (the common form) is milder. Screens differ.
- Not verified: how the ramps read dithered on the real strip at `PIXEL_SCALE` 2; the dither kernels in section 5; whether slot 1 at L* 14.6 to 16.2 reads on cheap panels; whether the indigo shadows still read as grim in context; whether Pitch's 66.7 L* line reads too dim in the Village shot. All of it is for the ink spike [Arch §8.1 step 2].

## 13. Sequencing, and the roguelite note

Nothing here blocks anything: it is one array and one uniform. The ramp can ship at the end of the ink spike with Generation fixed at 1 (the Day theme alone is already a gain over sky-blue), and the rest turns on when `generation` is on the wire (Prestige v1, [roadmap Phase 4]).

**Future roguelite layer (author's wish, not designed here).** The ramp is deliberately one function of Generation and nothing else. A later roguelite layer (the v2 Mutations, Omens and Pact, or run modifiers) must vary the Tower through Architecture, Mutation signs drawn from ramp slots, and rules, never through the palette: any unlock that recolours the Tower would turn the ramp back into a palette swap as a reward. If variety per Campaign is wanted on top, the safe knobs are the dither kernel and bias (section 5, cue 3) and ink density by Floor, both of which stay inside the four slots and the verified gates.
