# Tower Dungeon

A real-time tower-climbing management game inspired by Tsutomu Nihei's manga _Tower Dungeon_: the player runs a village that recruits and feeds units, then sends them on expeditions up a hostile tower, floor by floor, to reach its summit. Each expedition is a gamble between pushing higher and extracting before the party is wiped and its loot lost.

## Language

### Campaign and outcomes

**Campaign**:
The whole game for one player, spanning many expeditions, from the first recruit until victory or defeat.
_Avoid_: Game, save, session

**Victory**:
The campaign outcome reached when a party conquers the summit.
_Avoid_: Win, tower conquered, game won

**Defeat**:
The campaign outcome reached when the village has fallen: no units remain anywhere, no villagers are left, and there is nothing left to sell or spend to raise another party.
_Avoid_: Game over, village has fallen, loss

**Exodus**:
The campaign outcome the player chooses from the village, with no expedition out, when the village leaves the tower rather than wait for defeat. Planned.
_Avoid_: Quit, abandon, give up

**Prestige**:
The passage from a campaign that has ended (by victory, defeat, or exodus) to the next generation, in exchange for legacy. Intended but not yet built.
_Avoid_: Reset, new game

**Generation**:
One campaign in a lineage: the first campaign is generation 1, and each prestige begins the next. Planned.
_Avoid_: Run, cycle, loop, new game plus

**Toll**:
What a campaign's tally is worth in legacy when it ends. Planned.
_Avoid_: Prestige bar, score, XP

**Legacy**:
The currency a campaign leaves behind when it ends, spent on the roll. Planned.
_Avoid_: Prestige points, soul, essence

**Roll**:
The permanent, finite set of inheritances that legacy buys, kept across generations. Planned.
_Avoid_: Skill tree, blessings, perk tree

**Inheritance**:
A single permanent benefit on the roll. Planned.
_Avoid_: Blessing, perk, talent

**Succession**:
The moment between two generations when the toll is settled, the roll is spent, and the next generation's choices are made. Planned.
_Avoid_: Prestige screen, new game screen

**Chronicle**:
The permanent record of every ended campaign: its generation, outcome, toll, and the names of the fallen. Planned.
_Avoid_: History, save log

### The tower

**Tower**:
The single huge, hostile structure the party climbs; it keeps every floor discovered on previous expeditions for the whole campaign.
_Avoid_: Dungeon, Dragon Tower (manga name only)

**Floor**:
One level of the tower, holding a garrison of enemies or a guardian that must be defeated before the party can climb to the next.
_Avoid_: Level, stage, landing, hall, depth

**Base**:
The foot of the tower, below the first floor, where every climb starts unless a shortcut is used.
_Avoid_: Entrance, foundation, floor 0

**Summit**:
The top floor of the tower, always held by a guardian; clearing it wins the campaign. What truly waits there is undecided until the manga is finished; for now it is a Dragon.
_Avoid_: Top, max floor, final floor

**Highest Floor**:
The highest floor any party has reached during the campaign; it drives floor-based milestones.
_Avoid_: Deepest floor, max depth, record

**Floor Difficulty**:
How many enemy types are allowed to appear on a floor; it rises with height and drops as guardians above are slain.
_Avoid_: Tier (on its own, which means village tier), threat level

**Architecture**:
A floor's structural form (colonnade, pillar maze, stair shaft, and so on): how the floor looks and, once enabled, the small rules of the fight there. Planned.
_Avoid_: Floor type, biome, theme, room

**Reach**:
Ten consecutive floors closed by a guardian floor. Planned.
_Avoid_: Zone, region, chapter, band

**Door**:
The sealed way above the floor beneath the summit; until the roll holds the inheritance that opens it, the summit cannot be reached. Planned.
_Avoid_: Gate, barrier, final lock

**Tower Memory**:
The hardening of the tower with each generation: garrisons and guardians return stronger after earlier campaigns. Planned.
_Avoid_: Scaling, difficulty creep, new game plus

**Mutation**:
A lasting alteration to the tower for one generation, hidden until met. Planned.
_Avoid_: Modifier, affix, curse

**Omen**:
A foretelling of a mutation offered at succession; its nature stays unknown until the mutation has been met once. Planned.
_Avoid_: Preview, hint

**Pact**:
An omen the player accepts at succession, adding its mutation to the next generation in exchange for a larger toll. Planned.
_Avoid_: Contract, wager

**Garrison**:
The group of ordinary enemies occupying a non-guardian floor.
_Avoid_: Horde, defenders, spawn, wave (reserved for reinforcement waves)

**Surge Floor**:
A floor that recurs at a fixed interval and holds a noticeably larger garrison than its neighbours.
_Avoid_: Elite floor, spike

**Guardian**:
A single, much stronger enemy that holds a guardian floor instead of a garrison, and pays out multiplied loot when slain.
_Avoid_: Boss

**Guardian Floor**:
A floor held by a guardian, recurring at a fixed interval and always including the summit.
_Avoid_: Boss floor, boss level

**Easing**:
The lasting thinning of every floor below a slain guardian: smaller garrisons and lower floor difficulty there for the rest of the campaign.
_Avoid_: Boss-clear bonus, softening

**Shortcut**:
A permanent foothold that lets a new expedition start partway up the tower instead of at the base, opened once the campaign has slain enough enemies or completed enough expeditions.
_Avoid_: Foothold, waypoint, checkpoint, start point, lift, camp

**Start Floor**:
The floor an expedition begins on: the base, or the floor of an open shortcut.
_Avoid_: Spawn floor, entry floor

### Expeditions

**Expedition**:
One trip by a party up the tower, from setting out until extraction, wipe, or conquering the summit.
_Avoid_: Run, invasion, delve, sortie, raid, climb

**Party**:
The units currently on the tower during an expedition.
_Avoid_: Army, squad, units on tower, delvers, expedition party

**Extraction**:
The player choosing to end an expedition: the surviving party comes home, leftover supplies go back to the pantry, and carried loot is banked.
_Avoid_: Retreat, return, extract party

**Wipe**:
An expedition ending because every unit in the party died, from combat, attrition, or starvation; all carried loot is lost.
_Avoid_: Party wiped, run lost, death

**Expedition Outcome**:
How an expedition ended: Extraction, Wipe, or Summit Conquered.
_Avoid_: Run outcome, result

**Loadout**:
What a party takes when it sets out: its supplies and the consumables chosen in the village beforehand. Planned.
_Avoid_: Kit, pack list, inventory

**Ledger**:
The village's plain record of what happened: each expedition summary is one entry, and losses are written down as plainly as gains. Planned.
_Avoid_: Log, journal, history

**Expedition Summary**:
The report shown in the village after an expedition ends: start floor, highest floor reached, floors cleared, enemies slain, loot gained or lost, units lost, and survivors.
_Avoid_: Run summary, post-run summary, post-run report, debrief

**Attrition**:
The steady loss of health every unit in the party suffers just by being on the tower, regardless of combat.
_Avoid_: Passing time, decay, tower damage

**Reinforcement Wave**:
A group of reserve units sent up during an expedition, carrying its own supplies and climbing from the base until it reaches the party's floor and merges into it.
_Avoid_: Reinforcements, follow-up wave, relief, backup

**Merge**:
A reinforcement wave reaching the party's current floor and joining it, together with any supplies it still carries.
_Avoid_: Join, catch up, arrive

**Recall**:
Reinforcement waves still climbing when the expedition ends turning back to the village with their survivors and leftover supplies.
_Avoid_: Return home, cancel

### Units and combat

**Unit**:
A single player-controlled fighter with a name, health, and a unit type.
_Avoid_: Soldier, character, member, troop, recruit, delver

**Unit Type**:
The kind of unit (Warrior, Archer, Porter, Mage, Healer, Captain, Tank, Rogue, Necromancer, Draco-Metamorph), defining its health, damage, attack type, weaknesses, and recruit cost.
_Avoid_: Class, unit stats, archetype, role

**Roster**:
The unit types the player has unlocked and can recruit; it starts with Warrior, Archer, and Porter and grows through upgrades and milestones.
_Avoid_: Hire list, unlocked units

**Recruit**:
Buying a new unit of an unlocked type with credits; the new unit joins the reserves.
_Avoid_: Buy, hire, raise, enlist

**Reserves**:
The units waiting in the village rather than on the tower; they eat food, recover health, and recharge mana there.
_Avoid_: Home party, home units, idle units, standby units, army, units on base

**Porter**:
A weak unit type whose purpose is to raise its party's or wave's carrying capacity; when it dies, the extra supplies it carried are lost.
_Avoid_: Carrier, mule

**Companion**:
A named manga character who joins the campaign through a milestone and then makes that unit type recruitable: Captain Eliquo (Captain) and Sargan the fire mage (Mage). Milestones that only buff the party do not bring in a companion.
_Avoid_: Story unit, hero

**Enemy**:
A single tower-dwelling foe, either part of a floor's garrison or a guardian.
_Avoid_: Monster, mob, creature, defender

**Enemy Type**:
The kind of enemy, drawn from the manga's bestiary and ordered from weakest to strongest: Slime, Dead Soldiers, Tower Beast, Chimera, Chymus Insect, Stone Golem, Necromancer, Dead Dragon, Dragon.
_Avoid_: Enemy stats, archetype, species

**Necromancer**:
A rare figure in the world of Tower Dungeon: the manga's main villain, but also a unit type the player can recruit and an enemy type found in the tower. The three uses are deliberate.

**Attack Type**:
The kind of harm a unit or enemy deals: Melee, Ranged, Magic, or Heal.
_Avoid_: Damage type, element

**Weakness**:
A per-attack-type multiplier on how much a unit or enemy is affected by incoming hits; a negative value means that attack type restores health instead.
_Avoid_: Resistance, vulnerability, modifier

**Heal**:
The attack type that restores health to party members instead of harming them.
_Avoid_: Mend, cure, support

### Magic

**Caster**:
A unit whose attack type is Magic or Heal, and who therefore needs mana to act.
_Avoid_: Mage (a single unit type), spellcaster, magic user

**Mana**:
A caster's personal reserve of magic, spent on each cast and refilled by recharge.
_Avoid_: Magic points, MP, energy

**Cast**:
One mana-paying action by a caster, either an attack on an enemy or a heal on an ally; a caster makes at most one cast per tick, and one without enough mana does nothing. A Healer heals a wounded ally if there was one when the tick began (so two Healers can both spend a cast on the same last wounded ally); otherwise it strikes an enemy that a Heal hit would harm, and if there is none it does not cast.
_Avoid_: Spell, ability

**Recharge**:
Mana flowing back into a caster: quickly while in the reserves, and only as a slow trickle while on the tower.
_Avoid_: Regen, regeneration, refill

### Village and economy

**Village**:
The player's home settlement, where villagers grow food, reserves wait and recover, and loot is banked.
_Avoid_: Base, home, town, camp

**Villager**:
A non-combatant who lives in the village and grows food; bought with credits, and lost to starvation.
_Avoid_: Peasant, population, worker

**Food**:
The single shared resource that keeps everyone alive: grown by villagers, eaten by reserves, and carried up the tower as supplies.
_Avoid_: Rations, provisions

**Pantry**:
The village's food stock, which expeditions and reinforcement waves draw from and return leftovers to.
_Avoid_: Food store, granary, village food

**Upkeep**:
The food reserves eat while waiting in the village.
_Avoid_: Consumption, food cost

**Supplies**:
The food a party or reinforcement wave is carrying on the tower, eaten as it climbs.
_Avoid_: Rations, provisions, carried food

**Carrying Capacity**:
The most supplies a party or reinforcement wave can carry, raised by porters, the Supply Lines upgrade, and milestones.
_Avoid_: Max supplies, supply cap, carry

**Starvation**:
Running out of food: on the tower, units take damage each moment their supplies are empty; in the village, a villager and a random reserve unit die when the pantry is empty.
_Avoid_: Hunger, famine

**Recovery**:
Wounded reserves regaining health over time in the village, paid for with pantry food.
_Avoid_: Home healing, food healing, resting

**Credits**:
The village's currency, used to recruit units and buy villagers; found on cleared floors and earned by selling food, materials, and relics.
_Avoid_: Credit, coin, gold, money

**Materials**:
The common loot dropped on every cleared floor, used to pay for most upgrades or sold for credits.
_Avoid_: Resources, scrap, ore

**Relics**:
The rare loot found only on high floors, used to pay for the most powerful unlock upgrades or sold for credits.
_Avoid_: Artifacts, treasure

**Loot**:
The credits, materials, and relics a party gathers while clearing floors; it is carried until extraction banks it and lost on a wipe.
_Avoid_: Haul, expedition loot, carried loot, rewards, drops

**Vault**:
The village's permanent store of banked credits, materials, and relics, which only extraction can add loot to.
_Avoid_: Treasury, wallet, resources, bank, stockpile

**Trade**:
The work a villager does in the village: tilling the fields (which grows food), cutting stone in the quarry, or copying in the scribe's study; later salting and tending spores. Every villager works one trade. Planned.
_Avoid_: Role, job, worker class

**Stockroom**:
The village's store of goods its own villagers make (salt, dragon spores, knowledge). Unlike the vault, trades fill it and extraction does not. Planned.
_Avoid_: Storehouse, warehouse, inventory

**Quarry Heap**:
Materials the quarry has cut but not yet delivered; extraction delivers them to the vault, and a wipe delivers none. Planned.
_Avoid_: Stockpile, quarry stock

**Knowledge**:
What scribes produce in the scribe's study, kept in the stockroom and spent on lasting improvements to the village's trades. Planned.
_Avoid_: Lore, research, XP

**Salt**:
A village-made good that slows supplies drain when carried as treated supplies, and that dissolves a chymus insect's mucus as a counter packed for the fight. Planned.
_Avoid_: Preservative, rock salt

**Dragon Spores**:
A village-grown healing ingredient, made into remedies that restore a unit's health at once and into the panacea. Planned.
_Avoid_: Herbs, mushrooms, potions

### Progression

**Upgrade**:
A permanent improvement bought with materials or relics from the vault: either an unlock upgrade or a repeatable upgrade.
_Avoid_: Perk, buff, tech

**Unlock Upgrade**:
A one-time upgrade that adds a new unit type to the roster (Healer, Tank, Rogue, Necromancer, Draco-Metamorph); flavour titles such as Field Medic, Ironclad and Shadow are subtitles, never the unit's name.
_Avoid_: Recruit upgrade, character unlock

**Repeatable Upgrade**:
An upgrade that can be bought again and again, each level adding to its bonus and costing more than the last (Supply Lines, Sharpened Arms).
_Avoid_: Levelled upgrade, scaling upgrade

**Milestone**:
A free, one-time reward granted automatically when the campaign reaches a highest floor or a number of completed expeditions; it either brings in a companion or permanently strengthens the party.
_Avoid_: Achievement, campaign milestone, story event

**Tally**:
The never-spent counters of a campaign's deeds (food grown, loot banked, highest floor, guardians slain); they raise the village tier and fill the toll. Planned.
_Avoid_: Lifetime ledger, stats, score

**Village Tier**:
The village's progression level, from I to IV, raised by its tally; it opens trades and buildings. Always written in full, because tier on its own is avoided. Planned.
_Avoid_: Tier (on its own), standing, settlement tier, level
