import { Component, computed, input } from '@angular/core';
import { Tower } from '../../interfaces/tower';
import { EnemyCard } from '../enemy-card/enemy-card';

@Component({
  selector: 'app-tower',
  imports: [EnemyCard],
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
  currentFloorIsBoss = computed(() => this.currentTowerFloor()?.boss === true);
  enemyCount = computed(() => this.currentTowerFloorEnemies()?.length ?? 0);
  arrayCreator = Array;
}
