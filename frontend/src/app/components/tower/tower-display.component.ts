import { Component, computed, input } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Tower } from '../../interfaces/tower';
import { Member } from '../../interfaces/member';
import { ResourceWallet, Squad, Reinforcement } from '../../interfaces/game-state';
import { EnemyCard } from '../enemy-card/enemy-card';
import { Army } from '../army/army';
import { ExpeditionLoot } from '../expedition-loot/expedition-loot';
import { Supplies } from '../supplies/supplies';
import { TowerScene } from '../tower-scene/tower-scene.component';

@Component({
  selector: 'app-tower',
  imports: [EnemyCard, Army, ExpeditionLoot, Supplies, LowerCasePipe, TowerScene],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.scss',
})
export class TowerDisplay {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  supplies = input<number>(0);
  maxSupplies = input<number>(0);
  carriedLoot = input<ResourceWallet | undefined>();
  carriedCredits = input<number>(0);
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);

  towerFloors = computed(() => this.tower()?.floors ?? {});
  maxFloor = computed(() => this.tower()?.maxFloor ?? 0);
  currentFloor = computed(() => this.tower()?.currentFloor ?? 0);
  currentTowerFloor = computed(
    () => this.towerFloors()?.[this.currentFloor() ?? 0],
  );
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
  enemyCount = computed(() => this.currentTowerFloorEnemies()?.length ?? 0);
  depthPercent = computed(() => {
    const max = this.maxFloor();
    return max > 0 ? Math.min(100, (this.currentFloor() / max) * 100) : 0;
  });
}
