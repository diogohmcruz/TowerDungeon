# Learning from *His Majesty the Worm*: loop, enemies, units

Design only. Nothing here is built. Written 2026-11-07 after a grilling session on `docs/his-majesty-the-worm.pdf` (Exalted Funeral, 2023). Book page numbers are printed pages (PDF page minus 12). Terms follow `CONTEXT.md`, which gained the entries marked "Planned" from this session. Decisions are the author's. Tags: **[Book]** taken from the text, **[Adapted]** the book's idea changed to fit, **[Proposal]** invented here, a first guess to be tuned. Numbers are knobs, not measurements.

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

A per-expedition gauge in four steps. It resets at Extraction, drains no Supplies, and is eased by a Camp or a lit lamp.

| Step | Name (draft) | Effect                                                        |
|------|--------------|---------------------------------------------------------------|
| I    | Uneasy       | Nothing visible; the clock band is a tell                     |
| II   | Dim          | Attrition +10%; encounter band widens by 1 slot               |
| III  | Gutter       | Attrition +25%; hazards cost more; encounter band +2          |
| IV   | Dark         | Attrition +50%; Sanity drains; the next hazard is a Doom draw |

Rises with depth beyond the Start Floor and by one notch per clock result. Deeper floors cost more, as the book advises (p. 333).

### 2.3 Hungry and Starving [Adapted, pp. 140-141]

Supplies empty: **Hungry** at once; Sanity falls steadily and Camp cannot heal that unit. After a set time **Starving**: health damage as today. The book's "second missed meal" becomes a timer [Proposal: 30 ticks Hungry before Starving]. A Camp restores Sanity before wounds, so Stressed is not added as a second condition [Adapted, p. 141].

### 2.4 Camp [Adapted, pp. 136-141]

A Halt option on a cleared floor. Costs Supplies (one meal per unit), then one floor draw: an encounter interrupts the Camp and the party is Surprised if a random unit fails a Sanity test [Book]. Restores Sanity, then heals, then recharges mana. Not allowed at Dread IV, or while the Garrison is alive. A Patrol (second draw, encounter only if both are encounters) is a possible later Camp action [Book, p. 137].

### 2.5 Quarters [Adapted, p. 154]

| Grade  | Cost                | Recovery | Sanity | Loadout       |
|--------|---------------------|----------|--------|---------------|
| Cold   | 0                   | Slow     | None   | None          |
| Common | Credits per reserve | Normal   | Partly | Basic restock |
| Full   | More per reserve    | Quick    | Full   | Full restock  |

The book's tiers cost 0, 25, 50 and 100 gold and gate healing and gear. One grade is chosen after every Extraction.

### 2.6 Works [Adapted, Build and Support pp. 156-159]

A Work has steps, each paid in Credits or Materials. The village can advance a small number of steps per return: 1 to start, rising with Village Tier [Proposal]. Existing buildings are recast.

| Work                                                | New or recast | Effect                                                          |
|-----------------------------------------------------|---------------|-----------------------------------------------------------------|
| Fields, Quarry, Scribe's Study, Spore Cellar        | Recast        | Their trades, as now                                            |
| Port                                                | Recast        | The Boat and Arrivals, as now                                   |
| Strongbox, Reliquary, Wall of Names, Stone of Count | Recast        | The Tier IV buildings, as now                                   |
| Infirmary                                           | New           | Faster Recovery; cures Hungry in reserves                       |
| Shrine                                              | New           | Restores Sanity in reserves; funerals                           |
| Watchtower                                          | New           | Shows the next floor's Theme and Hates                          |
| Armoury                                             | New           | More Loadout restock; counters to Hates                         |
| Training Yard                                       | New           | Buys Experience with Credits (the book's Train: 50 gold per XP) |

### 2.7 Tithe [Adapted, p. 149]

A small cut of carried Credits and Materials at Extraction [Proposal: start at 10%]. Relics exempt (the book exempts art and gems). None on a Summit victory. One config number. Fame is **parked**: it conflicts with the never-spent Tally and deeds cover its job.

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
| Dead Soldiers | Undead    | Minion     | Start  | Light                    | Zombie (p. 286): Last Grasp, a severed limb fights on                                                                    |
| Tower Beast   | Beast     | Brute      | Early  | Fire                     | Griffin (p. 264): Grab and drop; Fleet                                                                                   |
| Chimera       | Sorcerous | Strategist | Early  | Sound                    | Brain Spider (p. 256): Tactics, Web, Great Leap                                                                          |
| Chymus Insect | Beast     | Brute      | Mid    | Salt                     | Corrosive Attack (Brute list, p. 250)                                                                                    |
| Stone Golem   | Sorcerous | Brute      | Mid    | Sorcerers (pick counter) | Animate Statue (p. 254): 1/15, "Magically Null"; Notch = 2 Wounds                                                        |
| Necromancer   | Sorcerous | Strategist | Deep   | Light                    | Cautious Retreat; Sorcery; Wraith Hates bright light (p. 284)                                                            |
| Dead Dragon   | Undead    | Elite      | Deep   | Fire                     | Vampire (p. 282): Invulnerable except fire, silver, magic                                                                |
| Dragon        | Beast     | Lord       | Summit | Fire (close, explosive)  | Book's Dragon is an Elite 5/10 (p. 260); ours is a Lord with pools like the Sporehulk                                    |

Corrections made after the full read: Stone Golem moves to Sorcerous (the book's statue is); Dead Dragon follows the Vampire; Slime keeps Minion on purpose because it is the first, weakest enemy. Debut floors are numbers to set against `docs/research/curve-floors-6-to-100.md`. Salt against the chymus insect is canon in the manga; the book also hates Salt on Slime and Ungoat.

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

### 4.2 Arête [Book p. 28 and the kith chapter]

Three different deeds per Origin, each checked once, the first time it is done. All three give an arête talent that Experience cannot buy [Book]. The book's own deeds are cultural and one-off (human: marry, fulfil an oath, win a duel; orc: hoard a new skull, hoard a treasure, slay a monster twice your size; underfolk: craft something lasting, hoard a gem, find something forgotten; p. 53, 57, 61, 65). Deed styles a game can check [Adapted]: first kill of a new enemy type; slay an enemy with twice your max health; keep a vow declared at departure; kill a Man-theme Strategist or Elite alone; open a Find; bank a relic; kill with an item only; fund a lasting Work step. Draft deeds [Proposal]:

| Origin           | Deeds                                                                                         |
|------------------|-----------------------------------------------------------------------------------------------|
| Kingdom villager | Carry a wounded unit out; Extract at Dread III or higher; slay a Guardian's last pool         |
| Royal Guard      | Hold a floor as the last unit standing; draw a Garrison's first strike; survive a Wipe's wave |
| Excavators Guild | Open a Find; bank a relic; return with more Loot than carried out                             |

Others are written when their Origin opens. The Eastern Lands need research first.

### 4.3 Abilities [Book pp. 32-33; ADR 0010]

| Rule        | Value                                                    |
|-------------|----------------------------------------------------------|
| Pool        | About six per Unit Type                                  |
| Held        | Three at once per Old Hand                               |
| Start       | Origin talent and one chosen path ability, both mastered |
| In training | Costs 1 Experience per use                               |
| Mastered    | After 7 Experience spent; free                           |

Experience is earned at Extraction, more for deeper floors and Guardians [Proposal: one per floor cleared, five per Guardian]. A Training Yard sells it.

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

- Dread's four steps, the Hungry-to-Starving time, the draw bands.
- The Tithe percentage; Quarters costs; Work step counts, costs and steps per return.
- Experience per Extraction; each pool's abilities (first: Warrior, Archer, Porter).
- The three arête deeds of each Origin; the Eastern Lands need research on the latest chapters first.
- Where **Monarchy** opens: a late Village Tier, or after the Door.
- Debut floors and the Golem's Hate.
- Whether a Halt stops the Village clock (roguelite-layer decision 2).

## 9. Risks and checks

- Decision load: Camp, Orders, Finds and Works all ask for choices; the roguelite doc's cap of about 11% decision time applies.
- Enemy re-tuning (ADR 0008) touches Floor Difficulty, Easing and the budget formula together.
- Unit plumbing (ADR 0010) is the largest piece and last.
- Sanity must not become the only dial.
- Telemetry from day one: no Camp, draw result or Quarters grade chosen in over 70% of cases.

## 10. Sources

- The book: creature chapter (pp. 242-251), Meatgrinder and Crawl (pp. 86-95), disposition and combat (pp. 99-127), Camp Phase (pp. 136-141), City Phase (pp. 148-160), talents and arête (pp. 28-33).
- `docs/brainstorm/roguelite-layer.md`, `docs/research/tower-dungeon-since-vol6.md`, ADR 0006.
- The Penguin Random House volume 7 blurb (the Eastern Lands, the Excavators Guild, the Parliamentary Defenders, the Anti-Nausea Alliance).
