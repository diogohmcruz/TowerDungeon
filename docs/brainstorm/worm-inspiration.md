# Learning from *His Majesty the Worm*: loop, enemies, units

Design only. Nothing here is built. Written 2026-11-07 after a grilling session on `docs/his-majesty-the-worm.pdf` (Exalted Funeral, 2023). Book page numbers are printed pages (PDF page minus 12). Terms follow `CONTEXT.md`, which gained the entries marked "Planned" from this session. Decisions are the author's. Tags: **[Book]** taken from the text, **[Adapted]** the book's idea changed to fit, **[Proposal]** invented here, a first guess to be tuned. Numbers are knobs, not measurements.

Every number is a **proposal** derived from the game's real config and code (payout 2f Credits and 5f Materials per floor f, times 3 on a Guardian floor; Materials sell at 2 Credits) and still needs the duel harness and telemetry. The manga's latest chapters are in `docs/research/tower-dungeon-since-vol6.md` (section 13).

## 1. Rule of borrowing

Mechanics and structure only. The tarot resolution, the book's names and its setting stay out; everything is re-skinned in the manga's grim register. Priority: **loop first**, then customisation, then detail. `docs/brainstorm/roguelite-layer.md` stays the loop baseline (the Halt, Orders, Finds, Lessons); the book adds content and depth, and that doc's section 10 decisions are still open.

## 2. The loop at three scales

| Scale      | Today                           | After                                                                           |
|------------|---------------------------------|---------------------------------------------------------------------------------|
| Floor      | Fixed Garrison, then loot       | Fixed Garrison plus one seeded draw on entry                                    |
| Expedition | Supplies drain, wipe or extract | Supplies (food) and Dread (fear) apart; Camp at a Halt; Morale ends some fights |
| Village    | Pantry Recovery, buy upgrades   | Quarters after Extraction; Works advanced in steps; Tithe on carrying           |
| Campaign   | Roll, Tally, Toll               | Old Hands, Origins, funerals pass experience on                                 |

### 2.1 The floor draw [Adapted, Meatgrinder p. 91]

One draw each time a party enters a floor, seeded by Generation, floor and `expeditionsCompleted`, so a reload replays it. Bands of the book's 21 slots, kept in proportion:

| Band      | Share | Result                                                        |
|-----------|-------|---------------------------------------------------------------|
| Clock     | 5/21  | Raises Dread one notch; never marks off, so it repeats [Book] |
| Curiosity | 5/21  | A hint or a Tell for the floor ahead [Book]                   |
| Hazard    | 5/21  | A cost in Supplies, Sanity or time [Book]                     |
| Encounter | 5/21  | A wanderer joins the fixed Garrison [Book]                    |
| Rumour    | 1/21  | Points toward a Guardian's Hate or the next Debut [Book]      |

- A repeat of a non-clock result is "a watch of respite": nothing happens [Book, p. 91].
- Only encounters in that band can use the Hate and Morale rules for wanderers.
- Carrying more than the party can use ("moving slowly") gives a second draw that can only add Dread [Adapted, p. 91].
- Entries are small scenarios with a clue before the fight ("broadcast the dangers", p. 340) [Book].

### 2.2 Dread [Proposal; ADR 0009]

A per-expedition gauge in four steps. It resets at Extraction, drains no Supplies, and is eased by a Camp or a lit lamp. **Light is folded in:** a lit lamp slows the clock band, Lamp Oil is a Loadout item, and at the last step a Doom draw replaces the next hazard (a unit lost and later found as a rescue, captured, sent home stripped, half the gear lost, or unharmed) [Adapted, pp. 94-95].

Points: a clock result adds 1 (5/21 per floor entered, about 0.24) and 1 per 60 ticks; a Camp removes 3.

| Step | Name (draft) | Points | About floor | About tick | Effect                                                        |
|------|--------------|--------|-------------|------------|---------------------------------------------------------------|
| I    | Uneasy       | 0      | start       | 0          | Nothing visible; the clock band is a tell                     |
| II   | Dim          | 3      | 13          | 60-80      | Attrition +5%; encounter band widens by 1 slot                |
| III  | Gutter       | 6      | 25          | 100-150    | Attrition +15%; hazards cost more; encounter band +2          |
| IV   | Dark         | 10     | 42          | 150-250    | Attrition +30%; Sanity drains; the next hazard is a Doom draw |

The first draft's +10, +25 and +50% was too hard: attrition is 1 hp a tick and a Warrior has 20, so +50% over a 30-tick Guardian fight costs 15 hp. Deeper floors cost more, as the book advises (p. 333). The duel curve was calibrated with no Dread; re-run `PacingSweep` with Dread on.

### 2.3 Hungry and Starving [Adapted, pp. 140-141]

Supplies empty: **Hungry** at once; Sanity falls 2 a tick and Camp cannot heal that unit. After **20 ticks** (a Generation 1 climb is about 34 ticks) it is **Starving**: 5 hp a tick, so a Warrior lasts four. The book's "second missed meal" becomes the timer. A Camp restores Sanity before wounds, so Stressed is not added as a second condition [Adapted, p. 141].

### 2.4 Camp [Adapted, pp. 136-141]

A Halt option on a cleared floor. Costs one meal per unit, set at 3 ticks of that floor's drain (77 Supplies at floor 6, 48 at floor 100), then one floor draw: an encounter (24% of Camps) interrupts it and the party is Surprised if a random unit fails a Sanity test [Book]. Restores Sanity +30, then half the missing health, then mana, and removes 3 Dread. Not allowed while the Garrison is alive. At Dread IV it costs double meals, so deep runs are not trapped. A Patrol (second draw, encounter only if both are encounters) is a possible later Camp action [Book, p. 137].

### 2.5 Quarters [Adapted, p. 154]

Price per reserve that needs care (about 60% of the party); H is Highest Floor. One grade is chosen after every Extraction.

| Grade  | Credits per reserve | Recovery             | Sanity   | Loadout restock               |
|--------|---------------------|----------------------|----------|-------------------------------|
| Cold   | 0                   | 2.5 hp/tick          | none     | none                          |
| Common | 0.1 x H             | 5 hp/tick (today's)  | +40      | to 50%                        |
| Full   | 0.3 x H             | 8 hp/tick            | to 100   | to 100%, from the Vault       |

Share of the return's Credits: Common 17% at floor 5, 11% at 10, 5% at 40, 4% at 70; Full 50%, 32%, 15%, 12%. Full is out of reach at floor 5 on purpose and cheap deep; a superlinear price (0.3 x H^1.3) is an option. Eight hp/tick, not ten, because ten exceeds the 10 Food/tick the starting Fields make. The book's tiers cost 0, 25, 50 and 100 gold and gate healing and gear.

### 2.6 Works [Adapted, Build and Support pp. 156-159]

A Work has steps, each paid in Materials (a few in Credits or Relics). Book rungs 25/50/100/1000 scaled to 50/100/200/2000 Materials. Steps per return: Village Tier I 1, II 2, III 3, IV 4. Existing buildings are recast.

| Work (opens at) | Steps | Step cost | Total |
|---|---|---|---|
| Fields, Quarry (Tier I) | 4 each | 50, 50, 100, 100 M | 300 M |
| Scribe's Study (II; Contracts open at step 3) | 5 | 50, 100, 100, 200, 200 M | 650 M |
| Infirmary (II) | 4 | 100, 100, 200, 200 M | 600 M |
| Shrine (II) | 3 | 100, 200, 200 M | 500 M |
| Spore Cellar (III) | 5 | 100, 100, 200, 200, 200 M | 800 M |
| Watchtower, Armoury (III) | 4 each | 100, 200, 200, 400 M | 900 M |
| Training Yard (III) | 3 | 100, 200, 200 M | 500 M |
| Port (12 Expeditions) | 6 | 100 M + 50 C each | 600 M + 300 C |
| Strongbox (IV) | 3 | 200 M, 200 M, 1,000 C | 400 M + 1,000 C |
| Reliquary (IV) | 3 | 200 M, 200 M, 10 Relics | 400 M + 10 R |
| Wall of Names (IV) | 2 | 200 M each | 400 M |
| Stone of Count (IV) | 3 | 200, 200, 400 M | 800 M |

53 steps and about 8,050 M + 1,300 C + 10 R, which is 47% of the Materials par per Campaign (17,000). A Campaign of 16 returns gives about 35 to 40 steps. Generations 1 and 2 are bound by Materials; from Generation 5 the step cap binds. Tiers III and IV open from Tally thresholds that early Campaigns never reach, so those Works do not exist for them.

Effects of the new Works: **Infirmary** faster Recovery and cures Hungry in reserves; **Shrine** restores Sanity in reserves and hosts funerals; **Watchtower** shows the next floor's Theme and Hates; **Armoury** more Loadout restock and counters to Hates; **Training Yard** buys Experience with Credits (the book's Train, 50 gold per XP).

### 2.7 Tithe [Adapted, p. 149]

10% of carried Credits and Materials at Extraction; Relics exempt (the book exempts art and gems); none on a Summit victory; the first 25 Credits and 50 Materials exempt. It takes 3 Credits at floor 5, 54 at floor 20 and 609 at floor 70, and is taken after the Tally and Toll count the loot, so it never cuts Legacy. One config number. Fame is **parked**: it conflicts with the never-spent Tally and deeds cover its job.

### 2.8 Money in the game today [Code]

| Return at floor | Credits | Materials | Credit-equivalent | Party |
|---|---|---|---|---|
| 5 | 30 | 75 | 180 | 17 |
| 10 | 150 | 375 | 900 | 26 |
| 20 | 540 | 1,350 | 3,240 | 29 |
| 40 | 2,040 | 5,100 | 12,240 | 43 |
| 70 | 6,090 | 15,225 | 36,540 | 58 |

Income grows about 200 times from floor 5 to 70 while the party grows three times, so prices scale with Highest Floor (H) instead of staying flat.

### 2.9 Village Events and Contracts [Adapted, City Events pp. 150-153]

One Village Event per return, plus a Portent on 1 in 21 returns (about 0.8 a Campaign). The effect is capped at plus or minus 5% of the return's credit-equivalent. The Contract board shows 2 at Scribe's Study step 3 and 3 at step 5, one taken per Expedition, a reward of about 0.9 x H(H+1) credit-equivalent (15% of that return), mostly Materials.

The book draws from 21 slots: I-V curiosities, VI-X happenings, XI-XV rumours, XVI-XX travel, XXI portents. Entries below are adapted; comic ones were rewritten.

| # | Band | Village Event |
|---|---|---|
| I | Curiosity | **Foul Water**: one unit starts the next climb with Sanity -10; the Pantry loses a meal |
| II | Curiosity | **Starving Hound**: pay 1 Supplies and a hound scouts, showing the next floor's Hates; it leaves if Supplies run out |
| III | Curiosity | **The Gibbet**: all units Sanity -5; the Wall of Names gains a line |
| IV | Curiosity | **The Pillar Preacher**: names your best unit's worst deed; its Phobia is revealed, Sanity -5 |
| V | Curiosity | **The Fevered Gleaner**: a one-stage Affliction hits a random unit unless the Infirmary is built |
| VI | Happening | **Famine**: Supplies cost x2 this return |
| VII | Happening | **Saint's Night**: all Works close except the Shrine; units recover Sanity |
| VIII | Happening | **Alms Day**: Quarters cost half this return |
| IX | Happening | **The Fast**: the Shrine is free; no other Work step |
| X | Happening | **The Steward Is Dead**: the Tithe changes by 50% either way for three returns |
| XI | Rumour | **The Barrow-Jars**: a named floor holds a Relic cache, guarded by that Reach's garrison |
| XII | Rumour | **The Bargain on the Stair**: a Pact offer worth one Relic with an intangible price |
| XIII | Rumour | **The Crawling Thing**: a petrifying Brute is on named floors; Golem counters apply |
| XIV | Rumour | **The Hidden Hearth**: one floor has a free Camp with no draw, for one climb |
| XV | Rumour | **The First Grave**: a tomb holds a Relic that cuts Sanity loss from Necromancers or Wraiths |
| XVI | Travel | **The Thief**: a rear unit's best item is stolen; buy it back or lose it |
| XVII | Travel | **Bravos at the Gate**: pick a unit to duel (a Test of Fate) or refuse and lose Sanity |
| XVIII | Travel | **The Recruiter**: a one-time Arrival offers its service, with a hidden Phobia |
| XIX | Travel | **The Bequest**: a free Work step and a Credit drain |
| XX | Travel | **Fire in the Quarters**: a Sanity test per unit; failure loses two Loadout items and starts the next climb Hungry |
| XXI | Portent | draw from the table below |

| d14 | Portent | Lasting effect |
|---|---|---|
| 1 | **Lost Years** | A Generation passes: Old Hands retire and Tower Memory ages a step |
| 2 | **Blood-Red Star** | The next Expedition starts Hungry; Quarters give no Sanity |
| 3 | **Green Mists** | No Work advances this return; Dread starts one step higher |
| 4 | **The Old Quarter Returns** | One free random Work |
| 5 | **Blood Rain** | Lose 25% of the Pantry |
| 6 | **Borrowed Dreams** | Two units swap Phobias; the Shrine cures it |
| 7 | **Slime Catastrophe** | One random Work is destroyed; Salt stops a repeat |
| 8 | **Mushroom Forest** | One random Work is unusable this return |
| 9 | **The Thing in the Water** | The Port is unusable; all units Sanity -10 |
| 10 | **The Silent Streets** | The next floor's Hates are hidden |
| 11 | **The Statues Speak** | Relics sell at half price this return; the Shrine grants Sanity |
| 12 | **The Honey Cult** | Dread starts a point higher; a hint about the next Lord |
| 13 | **The Child of Silence** | A Garrison hunts the Village unless you pay to hide |
| 14 | **The Dead Walk** | No Works until the marked floor is cleared; Risen chance rises |

### 2.10 Reagents [Adapted, Alchemy pp. 229-239]

Book rules: a fresh kill gives one reagent (several from huge creatures); it rots after a watch unless bottled or brewed; only monstrous creatures yield, never wraiths, statues or sentient humanoids; a potion helps only the living, bombs and oils affect anything; effects last about a watch; a creature's substances are unknown until first brewed (pp. 231-234). Our brewing happens in the Spore Cellar after Extraction.

| # | From | Substance | Game effect | Extends |
|---|---|---|---|---|
| 1 | Slime | **Slime Oil** | Dissolves a barrier or hits a Brute hard; lasts a floor | Salt |
| 2 | Chymus Insect | **Corrosive Bomb** | Strips one armour piece or a Brute's defence for a fight | Salt |
| 3 | Dragon-Spore line | **Mushroom Potion** | Heals, cures a Hungry-stage Affliction, stops Death's Door | Dragon Spores |

Later: Dead Soldier Grave-Rot Oil (a weapon strikes Risen as blessed), Tower Beast Fat Oil (a lamp lasts a floor longer), Chimera Bomb (roots a Strategist so it cannot withdraw), Stone Golem Statue Oil (breaks "Magically Null" for a fight), Mimic Potion (reveals one hidden feature on the next floor), Ungoat Oil and Bomb (strips a caster's casts; the bomb inflicts a Maleficence). Wraith, Necromancer and Dead Dragon yield none: potions do not work on the dead, and wraiths have no guts.

## 3. Enemies

### 3.1 Theme x Threat [Book pp. 242-251; ADR 0008]

Themes: Beast, Undead, Elemental, Sorcerous, Man. Threats:

| Threat     | Behaviour                                                  | Morale                                                        |
|------------|------------------------------------------------------------|---------------------------------------------------------------|
| Minion     | Weak, no tactics [Book]                                    | Flees below half a Garrison; pays part of its loot [Proposal] |
| Brute      | Tough: hits must beat its defence, can be outwitted [Book] | Never breaks                                                  |
| Strategist | Weak, tactical, disengages [Book]                          | Withdraws, returns on a later floor                           |
| Elite      | Invulnerable, retributive or threshold [Book]              | Never breaks                                                  |
| Lord       | Several pools, each with its own abilities [Book]          | Never breaks                                                  |

### 3.2 First placement of the nine types [Proposal, checked against the bestiary]

| Type          | Theme     | Threat     | Debut  | Hate                     | Borrow from the book                                                                                                     |
|---------------|-----------|------------|--------|--------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Slime         | Elemental | Minion     | Start  | Salt, Fire               | Zombie-weak here; the book's Slime is an Elite (Engulf, Regenerate, Acidic: Notches weapons; Salt does 2 Wounds, p. 278) |
| Dead Soldiers | Undead    | Minion     | 7      | Light                    | Zombie (p. 286): Last Grasp, a severed limb fights on                                                                    |
| Tower Beast   | Beast     | Brute      | 13     | Fire                     | Griffin (p. 264): Grab and drop; Fleet                                                                                   |
| Chimera       | Sorcerous | Strategist | 24     | Sound                    | Brain Spider (p. 256): Tactics, Web, Great Leap                                                                          |
| Chymus Insect | Beast     | Brute      | 36     | Salt                     | Corrosive Attack (Brute list, p. 250)                                                                                    |
| Stone Golem   | Sorcerous | Brute      | 44     | Sorcerers (pick counter) | Animate Statue (p. 254): 1/15, "Magically Null"; Notch = 2 Wounds                                                        |
| Necromancer   | Sorcerous | Strategist | 57     | Light                    | Cautious Retreat; Sorcery; Wraith Hates bright light (p. 284)                                                            |
| Dead Dragon   | Undead    | Elite      | 67     | Fire                     | Vampire (p. 282): Invulnerable except fire, silver, magic                                                                |
| Dragon        | Beast     | Lord       | 80     | Fire (close, explosive)  | Book's Dragon is an Elite 5/10 (p. 260); ours is a Lord with pools like the Sporehulk                                    |

Corrections made after the full read: Stone Golem moves to Sorcerous (the book's statue is); Dead Dragon follows the Vampire; Slime keeps Minion on purpose because it is the first, weakest enemy. Debut floors, clue floors and a Reach-by-Reach pacing sheet are in 3.7. Salt against the chymus insect is canon in the manga; the book also hates Salt on Slime and Ungoat.

### 3.3 Mob Rule [Adapted, p. 122]

A Garrison of one type: bonus at 2, 4 and 8 or more. The book gives favour, then Piercing, then Critical. Here [Proposal]: +10%, +25%, +50% damage. Surge Floors get their bite from this.

### 3.4 Hates and counters [Adapted, p. 244]

One Hate and one counter per type, packed in the Loadout; the counter beats that type's trait (as Salt dissolves mucus). Hates are on the enemy; Phobia stays on the unit.

### 3.5 Lords [Book pp. 251, 269-271, 279-280; builds on ADR 0006]

A Lord has several body-part pools. Each carries its own abilities and loses them when the pool falls. Some Lords die only when a core pool is empty; others need every pool down. Two worked designs:

| Lord        | Pools                                      | Rule                                                                                                       |
|-------------|--------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Sporehulk   | Legs 4/0, two Arms 5/0, Torso 5/10 (Tough) | Legs down: it crumples and only crawls. Immune until a rune on its back is hit. Kill when the Torso falls. |
| Yellow King | Phylactery 1/0, Crown 1/4, Body 5/9        | The phylactery is the true kill: the body regrows otherwise. Disarming the Crown removes his spells.       |

Our Dragon can follow the Sporehulk: Head, Wings, Body, with Breath on the Head and flight on the Wings.

### 3.6 Rules for new enemies [Book p. 243]

Take a classic monster, pick a Theme and a Threat, exaggerate one aspect, add one or two abilities for it.

### 3.7 Debut Floors and pacing [Proposal, issue #156]

Today the tiers are Slimes to floor 12, Dead Soldiers from 13, Tower Beasts from 23, Chimeras from 34, Chymus Insects from 50, and each Guardian's type appears before its garrison does. Debuts move earlier so a Guardian is a payoff, and each has a clue on the floor before (the floor draw's curiosity band, rigged to fire on that floor).

| Type (Theme/Threat) | Debut | Clue | Hate -> counter |
|---|---|---|---|
| Slime (Elemental/Minion) | 1 | none | Salt, Fire -> Salt |
| Dead Soldiers (Undead/Minion) | 7 | 6 | Light -> Lamp Oil |
| Tower Beast (Beast/Brute) | 13 | 12 | Fire -> Fire flask |
| Ungoat (Sorcerous/Minion) | 16 | 15 | Salt -> Salt |
| Chimera (Sorcerous/Strategist) | 24 | 23 | Sound -> Horn |
| Mimic (Sorcerous/Brute) | 28 | 27 | mixed weapon types (no item) |
| Chymus Insect (Beast/Brute) | 36 | 35 | Salt -> Salt |
| Stone Golem (Sorcerous/Brute) | 44 | 43 | Sorcerers -> Rune Chalk |
| Wraith (Undead/Strategist) | 54 | 53 | Light -> Lamp Oil |
| Necromancer (Sorcerous/Strategist) | 57 | 56 | Light -> Lamp Oil |
| Dead Dragon (Undead/Elite) | 67 | 66 | Fire -> Fire flask |
| Dragon (Beast/Lord) | 80 | rumours 75-79 | Fire (close, explosive) |

The Stone Golem's Hate is **Sorcerers**, answered by Rune Chalk (an item).

| Reach | New | Guardian | Beat |
|---|---|---|---|
| 1 (1-10) | Slime, Dead Soldiers | Dead Soldiers Lord | Learn to fight, then the first horde |
| 2 (11-20) | Tower Beast, Ungoat | Beast | First Brute, first theft |
| 3 (21-30) | Chimera, Mimic | Chimera | Enemies that withdraw, a shifting defence |
| 4 (31-40) | Chymus Insect | Insect | Units die one per tick |
| 5 (41-50) | Stone Golem | Golem | A wall you must read |
| 6 (51-60) | Wraith, Necromancer | Necromancer | Fear of the dark, answered by Light |
| 7 (61-70) | Dead Dragon lair | Dead Dragon | A rehearsal for the Dragon |
| 8 (71-80) | Mixed garrisons | Dragon, first | Awe |
| 9 (81-90) | Wings pool twist | Dragon, second | Grim endurance |
| 10 (91-100) | Door at 99 | Dragon at the summit | Payoff |

- **Floor Difficulty** becomes the count of debuted types; Easing subtracts from it but never takes a type below its debut. A type thins one tier per slain Guardian above it.
- **Rule-breakers** (Mimic, Ungoat, Wraith, Golem) are cut to 0 or 1 per garrison by Easing, never removed, so their counters stay rehearsed. Mimics are capped at 1 or 2 a garrison.
- **Ramp:** on its Debut Floor a type fills at most 25% of the budget, rising to full over about five floors. No debut on a Surge Floor (every fifth) except floor 45's risk, which needs a look.
- **Fade:** Slimes at 40, Dead Soldiers at 50, Beasts at 60; Mimic, Ungoat and Wraith never fade.
- **Risk:** the budget curve was sized with Insects from floor 50; floor 36 needs a re-bisection of the curve.

## 4. Units

### 4.1 Origin x Unit Type [Adapted; kith x path pp. 49-84]

Every unit has an Origin (rolled at recruitment) and a Unit Type. Origin gives an origin talent, mastered from the start, and three arête deeds.

| Origin                  | Opens               | Leans toward         |
|-------------------------|---------------------|----------------------|
| Kingdom villager        | Start               | Warrior, Porter      |
| Royal Guard             | Start               | Warrior, Captain     |
| Excavators Guild        | Early milestone     | Rogue, Porter        |
| Parliamentary Defenders | Village Tier II     | Tank, Healer         |
| Barbitos                | The Mage companion  | Mage                 |
| Drakmetamorph           | With that Unit Type | Draco-Metamorph only |
| Eastern Lands           | The Port            | Open                 |
| Monarchy                | Open (see 7)        | Rare and late        |

Further Origin candidates from the same groups, not yet built: an **Anti-Nausea Alliance healer** (Iscale's people; talent: cure a poison or ailment for a herb; deeds: cure a poisoned ally, use a Dragon Spore or Panacea, revive an ally once) and a **Necromancer-race outcast** (Aridellia's kind; talent: raise dead enemies for a tick; deeds: raise three corpses, be spared by a hostile guard, survive an undead fight without attacking). Both are proposals on single-source facts (`docs/research/tower-dungeon-since-vol6.md`, section 13.2).

### 4.2 Arête [Book p. 28 and the kith chapter]

Three different deeds per Origin, each checked once, the first time it is done. All three give an arête talent that Experience cannot buy [Book]. The book's own deeds are cultural and one-off (human: marry, fulfil an oath, win a duel; orc: hoard a new skull, hoard a treasure, slay a monster twice your size; underfolk: craft something lasting, hoard a gem, find something forgotten; pp. 53, 57, 61, 65). Deed styles a game can check [Adapted]: first kill of a new enemy type; slay an enemy with twice your max health; keep a vow declared at departure; kill a Man-theme Strategist or Elite alone; open a Find; bank a relic; kill with an item only; fund a lasting Work step. Every Origin's deeds are in 4.6.

### 4.3 Abilities [Book pp. 32-33; ADR 0010]

| Rule        | Value                                                    |
|-------------|----------------------------------------------------------|
| Pool        | About six per Unit Type                                  |
| Held        | Three at once per Old Hand                               |
| Start       | Origin talent and one chosen path ability, both mastered |
| In training | Costs 1 Experience per use                               |
| Mastered    | After 7 Experience spent; free                           |

**Experience** [Proposal]:

| Floor reached | Experience per Extraction |
|---|---|
| 5 | 5 |
| 10 | 15 |
| 20 | 25 |
| 40 | 42 |
| 70 | 63 |

One per floor for floors 1-10, one per two floors for 11-30, one per five beyond; plus 5 per Guardian. A Wipe pays half the floor part and no Guardian bonus. A pool is shared per Old Hand (the book is silent). The Training Yard sells it at 10 + 0.5 x H Credits (12.5 at floor 5, 45 at floor 70), capped at 3, 6 and 10 per return by its steps. Mastering an ability (7) costs 88 Credits at floor 5 (290% of the return) and 315 at floor 70 (5%). Twelve Old Hands x three abilities x 7 = 252 needed, which a Campaign earns by about Generation 6 or 7, after which the Yard has nothing to sell.

Ability seeds from the book's path and kin talents (pp. 69-84), adapted to ticks [Adapted]:

| Type            | Seeds                                                                                                                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Warrior         | Reaver (first strike on a new target closes the gap for a bonus hit); Two-handed Focus (every third swing is free); Berserkergang (wounded: more damage, no healing until the fight ends) |
| Archer          | Doom Eye (every Nth shot cannot miss); Monster Hunter (bonus against one chosen Theme); Ambusher (opening volley does double)                                                             |
| Porter          | Up My Sleeve (once per Expedition, produces a ration); Hale and Hearty (extra Supplies at Camp restores Sanity); Labor Unending (becomes Hungry to Camp twice)                            |
| Mage            | Gramarye (ranged bolt); Counter-spell (cancels a Sorcerous cast); a Realm picks the spell set                                                                                             |
| Healer          | Chirurgeon (full Camp heal for a unit that is not Hungry); Alchemy (Materials into potions); War Stories (Camp restores Sanity)                                                           |
| Captain         | Counsel (an ally gets an extra action); High Chant (Camp buff)                                                                                                                            |
| Tank            | Aegis (negates one hit, then the shield is Notched); Heavy Metal Machine (heavy armour adds to defence once a fight); Iron Beards (resists stun)                                          |
| Rogue           | Ambusher; Fight Dirty (attacks apply Exhaust, Notch or Silence); Quick! (light armour acts first)                                                                                         |
| Necromancer     | Magic of the Wastes (fear, decay); Jarl (a capped horde of minions, one dies per Wound); Counter-spell                                                                                    |
| Draco-Metamorph | Berserkergang; Poison Blood (attackers take a penalty when it is wounded); Colossal (breaks a barrier)                                                                                    |

Origin talents come from the kin talents the same way (Proud and Ancient for the Royal Guard, Labor Unending for Excavators, Berserkergang for the Drakmetamorph). Talents that need a human to answer (Foretell, Read the Past, Loremaster) do not translate; only a Watchtower or Rumour hint can stand in.

### 4.4 Old Hands and funerals [ADR 0010; Book pp. 38, 158]

- Become an Old Hand by near-death survival (a Lesson in the roguelite doc) or three Expeditions survived. Cap twelve.
- At the Shrine, Credits give the next Old Hand a one-deed head start on each ability the dead one held [Adapted, p. 38: 10 XP of a retiree buys an heir a talent]. An unpaid death passes nothing.
- Mentoring (a master teaches another path's ability) is parked.
- "Weirdness" is unruled in the book (scars, mutations, phobias, p. 33); here Maimed, Phobia and Items already do it.

### 4.5 Ability pools: Warrior, Archer, Porter [Adapted, paths pp. 67-84]

Each ability has a base clause (what it does in training, 1 Experience per use) and a mastered clause (added once it is free). They read state the combat code already has (strike counter per fight, `getAccuracy`, `hasPart`, gear, the Panacea save); (P) marks planned systems.

**Warrior**

| Ability | Book | Base | Mastered adds |
|---|---|---|---|
| Closing Stride | Reaver p.71 | Tick 1 of a fight, one extra strike on the same target at x1.0, once a fight | +10 accuracy |
| Two-Handed Rhythm | Two-handed Focus p.71 | Every 3rd own strike adds a second at x0.5; needs both arms | the second is x1.0 |
| Blood Heat | Berserkergang p.64 | At 50% health or below: x1.5 damage, no healing until the floor clears, ignores Sanity's accuracy penalty | also ignores the Phobia penalty |
| Shieldwall | Aegis p.70 | A worn arm item negates one hit, once a fight, then it is Notched until Camp (P) | Notched only if the hit was 25% of max health or more |
| Plated Hide | Heavy Metal Machine p.70 | Torso armour halves the first hit each fight | also halves the first hit from a Brute |
| War Tale | War Stories p.71 | At Camp, the others gain 8 Sanity, once (P) | listeners also lose one Dread point (P) |

**Archer**

| Ability | Book | Base | Mastered adds |
|---|---|---|---|
| Dead Eye | Doom Eye p.70 | Every 5th shot cannot miss | that shot aims at the head |
| Quarry-Sworn | Monster Hunter p.70 | Name a Theme at departure: x1.25 against it | takes x0.8 from that Theme |
| Opening Volley | Ambusher p.74 | Tick 1 shot deals x2, once a fight | an ambush does not give the garrison its first strike (P) |
| First Light | Quick! p.74 | With no torso armour, shoots before the garrison replies on tick 1 | every tick against Strategists |
| Hamstring | Fight Dirty p.74 | Once a fight, a leg shot cuts the target's damage by 25% | against a caster, also cancels one cast |
| Spotter's Call | Counsel p.78 | Once a fight, one ally strikes twice this tick | the extra strike has +10 accuracy |

**Porter**

| Ability | Book | Base | Mastered adds |
|---|---|---|---|
| Hidden Ration | Up My Sleeve p.74 | Once an Expedition, when Supplies fall under 25%, produce 20 | twice an Expedition |
| Hale and Hearty | Hale and Hearty p.59 | At Camp, pay 5 extra Supplies: the party gains 10 Sanity (P) | also heals each unit 10% |
| Second Watch | Labor Unending p.58 | At a Halt, becomes Hungry to run Camp's restore twice (P) | Hungry clears at the next Halt if Supplies remain |
| Field Dressing | Chirurgeon p.78 | Once a fight, pay 2 Supplies to heal the most wounded ally 3 | free; once an Expedition reattaches a lost part at 25% |
| Shield the Pack | Aegis p.70 | Once an Expedition, a lethal hit on an ally lands on the Porter | lands at half damage |
| Rendering | Alchemy p.78 | A fresh kill with the Porter present yields one extra reagent from a Guardian or Elite (P) | any kill |

### 4.6 Origin talents and arête deeds [Adapted, kin talents pp. 49-65]

Arête deeds are checked once each. Books: Byname p.53, Iron Beards p.61, Underfoot p.61, Colossal p.61. Eastern, Anti-Nausea and Monarchy rows come from `docs/research/tower-dungeon-since-vol6.md` (section 13) and are **single-source**; talents and deeds are proposals, not manga.

| Origin | Origin talent | Deed 1 | Deed 2 | Deed 3 | Arête talent |
|---|---|---|---|---|---|
| Kingdom villager | **Hard Hands**: the Hungry-to-Starving timer doubles | Extract with a wounded or maimed ally alive | Extract at Dread III or higher | Land the killing blow on a Guardian | **The Lucky**: the Chronicle gives it a byname; party Sanity loss from deaths -25% while it lives |
| Royal Guard | **Cry the Motto**: once a fight, the party's next strikes x1.25 | Be the last unit standing and clear the floor | Draw a garrison's first strike on 5 floors and survive them | Arrive in a Wave, merge, and later extract | **Unbowed**: once an Expedition a lethal hit leaves it at 1 health |
| Excavators Guild | **Dig In**: at a Halt, becomes Hungry to draw twice from the floor table and keep the better (P) | Open a Find | Bank a relic this unit found | Extract with more Loot than the Voyage carried out | **Quiet Feet**: once an Expedition, the next garrison loses its first strike |
| Parliamentary Defenders | **Fortress Drill**: the first hit each fight is halved | Clear a Surge Floor with no unit lost | Extract with every unit that set out, from floor 5 | Hold the front when a Guardian falls (P) | **Charter Chant**: at Camp, the next floor's party deals +10% (P) |
| Barbitos | **Kindled Hands**: a Magic kill restores 1 mana | Kill an enemy that Hates fire | Win a floor with mana at 0 | Slay an enemy with twice this unit's max health | **Pyre**: once an Expedition the garrison burns for 3 ticks |
| Drakmetamorph | **Blood Heat** (as the Warrior ability) | Slay a Guardian | Win a floor from under 10% health | Break a barrier | **Colossal**: once an Expedition, smash a barrier or door |
| Eastern Lands (princess type) | **Purifier's Stroke**: once a floor, a guaranteed kill on a sealed or boss-flagged enemy | Defeat a boss-flagged enemy in one hit | Clear a sealed door or sealed enemy | Finish a floor with no ally struck by undead | open: pick when the Port opens |
| Eastern Lands (retainer type) | **Sworn Shadow**: takes a hit meant for a bonded unit | Absorb damage for an ally 5 times | Stay beside the bonded unit for a full floor | Survive a fight under a quarter health | open |
| Monarchy | **Rally**: nearby units gain a bonus toward a shared goal; opens royal-blood doors | Open a royal-blood door | Gather 3 allies on a floor | Break a seal | open; gate still open (late Village Tier or after the Door) |

### 4.7 What the backend must expose

Unit ids are stable, but none of these exist today:

| Counter or fact | Used by |
|---|---|
| Per-unit kill credit (enemy type, Guardian flag, max-health ratio) | Guardian and Barbitos deeds, Rendering |
| Per-unit strike counter within a fight, and the fight tick | Two-Handed Rhythm, Dead Eye, Closing Stride, Opening Volley |
| Per-fight and per-Expedition use flags per ability | every "once a" rule |
| First-strike target of each garrison round | Royal Guard deed 2, Quiet Feet |
| Last-unit-standing flag at floor clear | Royal Guard deed 1 |
| At Extraction: Dread step, wounded or maimed survivors, full-party flag, Loot against the Voyage | Villager, Parliament and Excavator deeds |
| Item or relic provenance (finder's unit id) | Excavators deed 2 |
| Wave membership and merge event | Royal Guard deed 3 |
| New event types: UNIT_KILL, FLOOR_CLEARED, FIND_OPENED, RELIC_BANKED | all deeds |

Open points: Shieldwall and Shield the Pack both adapt Aegis (different effects); check that an item can be disabled until Camp.

## 5. What was dropped, and why

| Book mechanic                                         | Why not                                              |
|-------------------------------------------------------|------------------------------------------------------|
| Tarot cards for tests, initiative and dooms           | The game ticks once a second with auto-combat.       |
| Bonds (roleplay-charged healing, p. 26)               | Table-social; no system-generated equivalent wanted. |
| Carousing and the Hangover table                      | Comic; against the serious, dread tone.              |
| Syllable-priced Build and Commission (p. 156)         | Needs a GM to arbitrate.                             |
| Slot inventory with Notch durability (pp. 37, 96)     | Items already exist; not asked for.                  |
| Full Disposition wheel (pp. 99-101)                   | Partly kept as Morale; the rest is dialogue-driven.  |
| Initiative-as-defence, Defense/Health pools, Piercing | Duplicate Attack Types, Weaknesses and body parts.   |
| Fame (p. 149)                                         | Parked.                                              |

## 6. How it meets what exists

| Existing                                   | Meets                                                                  |
|--------------------------------------------|------------------------------------------------------------------------|
| Supplies, Salt, Porters, Carrying Capacity | Unchanged; Dread is separate                                           |
| Sanity, Phobia                             | Carry Hungry, Camp and Dread; Sanity before wounds at Camp             |
| Attrition, the Year's Quarters             | Dread raises Attrition; Winter stacks; check the product               |
| Floor Difficulty, Easing, Surge Floors     | Debut Floors replace the ladder; Mob Rule uses Garrison size           |
| Guardians, body parts (ADR 0006)           | Become Lords with per-pool abilities                                   |
| Lessons, Old Hands (roguelite doc)         | Same cap and pool as abilities                                         |
| Port, Boat, Arrivals                       | Port is a Work; the Eastern Lands arrive through it                    |
| Chronicle, Roll, Legacy                    | Funerals pass experience on; origins and abilities are not on the Roll |

## 7. Build order

1. Foundations: the Halt framework (stop predicate, the seeded tarot Deck with its draw index, stable unit ids).
2. The floor draw with its clock band, and Dread with light and the Doom draw.
3. Hungry, Camp, Death's Door, Afflictions and Risen from any death.
4. Threat, Morale, Mob Rule and wanderer Stances on the existing nine types.
5. Theme, Debut Floors and Hates with Loadout counters; the first new types (Mimic, Ungoat, Wraith).
6. Quarters, Works, the Tithe, Village Events, Contracts and Reagents.
7. Old Hands and Lessons, with named selection.
8. Origins, abilities, Experience and the Training Yard.

Customisation is last: it needs the most plumbing and the loop stands without it.

## 7a. Decisions from the full read (author, 2026-11-07)

| Item | Decision |
|---|---|
| Tarot | **Yes.** A seeded tarot Deck is the one random source for floor draws, Halts and event tables; a Test of Fate with a push for a few named moments; an optional doom deck per Reach. Fights stay auto-resolved. ADR 0011. |
| Light | Folded into Dread. A lit lamp slows the clock band; Lamp Oil is a Loadout item. At the last Dread step a Doom draw replaces the next hazard (a unit lost and later found as a rescue, captured, sent home stripped, half the gear lost, or unharmed). |
| The dead | **Common, not rare.** Any unit that dies on the tower can rise as a Risen (Dead Soldier) on that floor; the chance grows with Dread and Tower Memory. An Old Hand rises named and tougher and its gear becomes the floor's bundle. The Wipe-only cap of the roadmap (rows 4 and 8) and the roguelite doc's D1 no longer applies; the Cache (25% of lost Loot) stays on Wipe floors. |
| Death's Door | A unit at head or torso zero lingers a few ticks; a Healer, Spore Dose or Panacea can save it. |
| Afflictions | Three or four at first, staged, advancing when a Camp ends; hidden cure charges burned at Camp. |
| Stances | All seven (anger, distaste, sadness, joy, surprise, trust, fear), each mild, basic or intense, as data: anger fights recklessly, distaste fights, sadness moves first and takes chances, joy is cocky and slow to fight, surprise disengages early, trust parleys or allies, fear flees. Start comes from Theme and Threat; Likes and Hates move it one step; Morale reads fear. |
| Village Events | One per Extraction from a small table, with a rare Portent slot; a Contract board at the Scribe's Study. |
| Reagents | A fresh kill yields one reagent; the Spore Cellar brews it into a potion, bomb or oil; first three substances extend Salt, Spores and Hate counters. |
| First new enemy types | Mimic (Sorcerous Brute), Ungoat (Sorcerous Minion), Wraith (Undead Strategist), built with the matrix at step 5. The rest of the trait pool waits. |
| Meta content | Reach seed card, Maleficence tables, pact prices, phylactery and "the Worm grows closer" feed the existing Architecture, Mutation, Pact and Door designs; no new systems. |
| Unit extras | Motifs and Marching Order in; roles at Camp, familiars and pack slots parked. |

## 7b. Found on the full read

A second, whole-book read (every chapter and appendix) found the following. Items not named in 7a stay **candidates** until the author rules. Tags as above.

### Tarot [Book pp. 7-13, 112-123]

The book uses two decks: the 21 major arcana for the GM (without the Fool) and 56 minor arcana plus the Fool for players. A test of fate is one minor card plus an attribute (1-4); 14 or more succeeds. A great success needs the tested suit on the first card. A failure can be pushed with one more card; a failed push is a great failure. Favor is +3, disfavor -3. With a push, success is 65% at attribute 1 and 80% at attribute 4 (computed from the deck). Major cards I-XIV trigger lesser dooms and XV-XXI greater ones, drawn without replacement until the Fool reshuffles.

| Use | What                                                                                                                                | Verdict                                                         |
|-----|-------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| A   | A seeded tarot deck as the one RNG for floor draws, Halts, Orders and event tables; major arcana set severity, card art foreshadows | Fits. Save the seed and a draw index                            |
| B   | A doom deck per Reach drawn without replacement; the Fool reshuffles                                                                | Optional pacing device                                          |
| C   | A test of fate (with push) for a few named moments                                                                                  | Fits: one resolution per event, not per tick                    |
| D   | Per-tick card resolution                                                                                                            | Does not fit: hidden hands and bluffing vanish with auto-fights |

**Status:** proposed A and C, with B optional. Fights stay auto-resolved.

### Expedition

- **Light as a second clock** (pp. 94-95). Candle 2, torch 3, lantern 4 flickers; the clock band flickers them; oil resets them. Out of light is the Dark Dread step and a Doom draw.
- **Doom table when light is gone** (p. 95). Eaten about half the time; otherwise captured, ransomed, lost (becomes an encounter that can be rescued), retired, sent home stripped, half the gear lost, or unharmed.
- **Afflictions as clocks** (pp. 97-98). A stage advances only when a Camp ends, unless cured. Hidden charges cure the worst stage first; some have good early stages (drugs).
- **Death's Door and Injured** (pp. 31, 124-126). Injured: the next Wound goes to Death's Door. Death's Door is a one-watch rescue window; unhealed, the unit dies and rises as undead, which could return as an enemy on that floor.
- **Wounded abilities** (p. 31). Up to two abilities disabled until healed at Camp.
- **Camp menu** (pp. 137-139): Patrol (two draws), Scout Ahead (a hint), Update Maps (two known rooms per draw), Hunt (needs Salt to preserve), Fellowship, Repair one Notch. Needs two of bedroll, tent, fire or the party wakes Hungry.
- **Wanderers get stances** (pp. 99-102). Seven emotions; negative means a fight, fear means flee, positive means parley, trade or an ally. Gives the encounter band non-combat results.
- **Travel event as a choice** (p. 340). "Go X, suffer Y; or go A, suffer B; or turn back", or a tax on one random unit.
- **Noise and care** (p. 91). A loud order draws for encounters only; moving carefully draws a second time, but only a clock result counts.
- **Durability and falling** (p. 96). Notches 1/2/3 on gear, repaired in the village; a fall deals armour-ignoring damage per ten feet.
- **Marching order** (p. 43). Front is hit first and meets hazards first; rear handles ambushes. Weight sets who triggers a pressure plate.
- **Reasons per floor** (p. 335): explore, flee, talk, fight, breathe easy, experiment, surprised, return. A lint list for floor templates; "breathe easy" floors host Camps.
- **Restock as consequence** (pp. 160, 173): delete what was seen, add what the party provoked.

### Village

- **Village events at Extraction** (pp. 150-153). 21 slots: curiosity, happening, rumour, travel, and a Signs and Portents slot with lasting effects. Examples: Famine (Supplies cost double), Coronation (Quarters half price), Mushroom Forest (one Work unusable), Blood-Red Star (next Expedition starts Hungry), Slime Catastrophe (a building lost).
- **Contract board** (pp. 154, 160). Four to six jobs; pay Credits and Experience; some need a live capture (ties to Morale: captured, not killed).
- **Reagents from kills** (pp. 229-239). A fresh kill gives one reagent; the Spore Cellar brews it into a potion, bomb or oil. Extends Salt and Spores into a family and into Hate counters. Which substance a creature yields stays hidden until first brewed (a bestiary discovery).
- **Support price rungs** (p. 159): 25, 50, 100 and 1000 for small, humble, significant and dramatic steps; two to eight steps.
- **Relic valuation** (pp. 149, 172): each relic sells at a fixed price; a Port buyer pays more.
- **Banking** (p. 156): a stored Vault amount earns 2% per return.

### Enemies

- **Traits worth lifting** (pp. 254-286): Mimic (Harden: immune to the last weapon type); Bloodybones (cannot be killed, only avoided); Ungoat (raids the Loadout; Salt kills it); Face Rat (steals belt items, spreads an affliction); Wraith (intangible until lit, so light is a counter); Questing Beast (a decoy that draws others); Imp (regrows from violence, which punishes Mob Rule); Ogre (erases a bonus).
- **Iron as anti-magic** (p. 201): an iron-clad Garrison cannot be targeted by casts but takes area effects.
- **Twist table** (p. 187): a stalled fight draws a twist (reinforcement, fire, flood).
- **Elite notes** (p. 251): Invulnerable (only one source), Retributive (wounds whoever is engaged), Threshold (no damage until two Wounds in one hit).
- **Spare, do not kill** (p. 125): a finishing blow can spare, which supports captives.

### Units

- **Motifs** (pp. 24-25). Three descriptor-and-profession tags ("Penitent grave robber"), each gives an edge on a matching floor and a hint. Fits the rolled Phobia and Affinity.
- **Roles** (p. 43) set what a unit does at Camp (cartographer, cook, tinker, trapsmith).
- **Familiars** (p. 34): act only on command, need feed, leave when starving.
- **Pack slots** (p. 37): four belt (quick), 21 pack (slow); gold takes slots.
- **Fame as decaying deeds** (pp. 46, 149): up to five deeds, the oldest drops each return; five levels from unknown to famous in the City. Kept apart from the Tally.

### Tower and meta

- **Reach seed card** (pp. 344-364). Each Reach gets a card: purpose versus usurper, sights, sounds, smell, lighting, structures, common monsters, a Lord. Lighting feeds Dread. Maps to Architecture.
- **Maleficence-style Mutations** (pp. 202-203): a 1-10 table of temporary effects that last until the next Camp or Extraction, court results permanent or regional.
- **Hellmarkt-style Pacts** (p. 315): the price is intangible and "too reasonable" (a memory, a fear, an eye colour), paid in Sanity or an Old Hand's memory.
- **Phylactery** (pp. 269-271): the Reach Lord regrows each Generation unless a separate object elsewhere in the Tower is destroyed. Ties to Tower Memory.
- **The Worm grows closer** (p. 315): a Generation counter as an Omen; lore lives in traps and clues, never cutscenes.
- **Room features and levers** (pp. 335, 374-388): Hearthfire, Mirror (one rewind), Waterwheel (lamps cut Dread), Elevator (a shortcut kept across Generations).

## 8. Open knobs

- Every number above is a first proposal (Dread, Hungry timer, Quarters, Tithe, Works, Experience, Events, debut floors). They need the duel harness and telemetry.
- The Tithe percentage; Quarters costs; Work step counts, costs and steps per return.
- Experience per Extraction; each pool's abilities (first: Warrior, Archer, Porter).
- Arête deeds are drafted for every Origin in 4.6; the Eastern Lands, Anti-Nausea and Monarchy rows are single-source (`docs/research/tower-dungeon-since-vol6.md` (section 13)).
- Where **Monarchy** opens: a late Village Tier, or after the Door.
- Debut floors are proposed in 3.7 (Golem Hate: Sorcerers, counter Rune Chalk); Insects at 36 need the budget curve re-bisected.
- Whether a Halt stops the Village clock (roguelite-layer decision 2).

## 9. Risks and checks

- Decision load: Camp, Orders, Finds and Works all ask for choices; the roguelite doc's cap of about 11% decision time applies.
- Enemy re-tuning (ADR 0008) touches Floor Difficulty, Easing and the budget formula together.
- Unit plumbing (ADR 0010) is the largest piece and last.
- Sanity must not become the only dial.
- Telemetry from day one: no Camp, draw result or Quarters grade chosen in over 70% of cases. First three to watch:
  1. Wipe rate and Supplies by Dread step at Extraction.
  2. Unspent Materials and Credits at each return, and Work steps taken against the cap.
  3. The Quarters grade chosen and its cost as a share of the return's Credits.

## 10. Sources

- The book: creature chapter (pp. 242-251), Meatgrinder and Crawl (pp. 86-95), disposition and combat (pp. 99-127), Camp Phase (pp. 136-141), City Phase (pp. 148-160), talents and arête (pp. 28-33).
- `docs/brainstorm/roguelite-layer.md`, `docs/research/tower-dungeon-since-vol6.md`, ADR 0006.
- The Penguin Random House volume 7 blurb (the Eastern Lands, the Excavators Guild, the Parliamentary Defenders, the Anti-Nausea Alliance).
