import { Component, computed, input } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Tower } from '../../interfaces/tower';
import { Member } from '../../interfaces/member';
import { EnemyCard } from '../enemy-card/enemy-card';

@Component({
  selector: 'app-tower',
  imports: [EnemyCard, LowerCasePipe],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.scss',
})
export class TowerDisplay {
  tower = input<Tower | undefined>();
  towerFloors = computed(() => this.tower()?.floors ?? {});
  towerFloorsEntries = computed(() =>
    Object.entries(this.tower()?.floors ?? {}),
  );
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
  arrayCreator = Array;
}
