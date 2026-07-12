import { Component, computed, input } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Tower } from '../../interfaces/tower';
import { Member } from '../../interfaces/member';
import { ResourceWallet, Squad, Reinforcement } from '../../interfaces/game-state';
import { EnemyCard } from '../enemy-card/enemy-card';
import { Army } from '../army/army';
import { ExpeditionLoot } from '../expedition-loot/expedition-loot';
import { Supplies } from '../supplies/supplies';

@Component({
  selector: 'app-tower',
  imports: [EnemyCard, Army, ExpeditionLoot, Supplies, LowerCasePipe],
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

  /** A distinct, stable-per-index highlight color for each climbing wave (golden-angle hues). */
  waveColor(index: number): string {
    return `hsl(${(index * 137.5) % 360}, 72%, 55%)`;
  }

  private waveSize(wave: Reinforcement): number {
    return Object.values(wave.units ?? {}).reduce(
      (total, members) => total + (members?.length ?? 0),
      0,
    );
  }

  /** Groups climbing waves by the floor they are currently on, so each floor can show its markers. */
  reinforcementMarkers = computed(() => {
    const floorKeys = Object.keys(this.towerFloors()).map((k) => +k);
    const minFloor = floorKeys.length ? Math.min(...floorKeys) : 0;
    const maxFloor = floorKeys.length ? Math.max(...floorKeys) : 0;
    const byFloor: Record<
      number,
      { index: number; size: number; color: string }[]
    > = {};
    this.reinforcements().forEach((wave, index) => {
      const raw = Math.floor(wave.currentFloor ?? 0);
      const floor = Math.min(maxFloor, Math.max(minFloor, raw));
      (byFloor[floor] ??= []).push({
        index,
        size: this.waveSize(wave),
        color: this.waveColor(index),
      });
    });
    return byFloor;
  });

  markersForFloor(floor: number) {
    return this.reinforcementMarkers()[floor] ?? [];
  }
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
