import { Component, computed, input } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Tower } from '../../interfaces/tower';
import { Member } from '../../interfaces/member';
import { ResourceWallet, Squad } from '../../interfaces/game-state';
import { EnemyCard } from '../enemy-card/enemy-card';
import { Army } from '../army/army';
import { ExpeditionLoot } from '../expedition-loot/expedition-loot';
import { Supplies } from '../supplies/supplies';

/**
 * The Expedition HUD (architecture doc 5.5): the Floor, the carried loot, Supplies, the
 * Party and the enemies, sticky at the top of the menu column. Absent outside an Expedition.
 */
@Component({
  selector: 'app-expedition-hud',
  imports: [EnemyCard, Army, ExpeditionLoot, Supplies, LowerCasePipe],
  templateUrl: './expedition-hud.html',
  styleUrl: './expedition-hud.scss',
})
export class ExpeditionHud {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  supplies = input<number>(0);
  maxSupplies = input<number>(0);
  carriedLoot = input<ResourceWallet | undefined>();
  carriedCredits = input<number>(0);
  active = input<boolean>(false);
  /** Finish Floors of Races the player has run and discovered; only those wear the mark. */
  pillarMarks = input<number[]>([]);

  towerFloors = computed(() => this.tower()?.floors ?? {});
  currentFloor = computed(() => this.tower()?.currentFloor ?? 0);
  currentTowerFloor = computed(() => this.towerFloors()?.[this.currentFloor() ?? 0]);
  currentTowerFloorEnemies = computed(() => this.currentTowerFloor()?.enemies);
  groupedEnemyEntries = computed<[string, Member[]][]>(() => {
    const groups: Record<string, Member[]> = {};
    for (const enemy of this.currentTowerFloorEnemies() ?? []) {
      const key = enemy.stats?.type ?? enemy.name;
      (groups[key] ??= []).push(enemy);
    }
    return Object.entries(groups);
  });
  currentFloorIsBoss = computed(() => this.currentTowerFloor()?.boss === true);
  /** The Floor shown wears the mark of a Race's Finish Floor, but only when listed by the server. */
  currentFloorMarked = computed(() => this.pillarMarks().includes(this.currentFloor()));
  enemyCount = computed(() => this.currentTowerFloorEnemies()?.length ?? 0);
}
