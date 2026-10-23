import { Component, computed, input, isDevMode } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Tower } from '../../interfaces/tower';
import { Member } from '../../interfaces/member';
import { ResourceWallet, Squad, Reinforcement } from '../../interfaces/game-state';
import { EnemyCard } from '../enemy-card/enemy-card';
import { Army } from '../army/army';
import { ExpeditionLoot } from '../expedition-loot/expedition-loot';
import { Supplies } from '../supplies/supplies';
import { TowerScene } from '../tower-scene/tower-scene.component';
import { VillageScene } from '../village-scene/village-scene.component';
import { readFixtureParams } from '../../three/ink/ink-fixture';

@Component({
  selector: 'app-tower',
  imports: [EnemyCard, Army, ExpeditionLoot, Supplies, LowerCasePipe, TowerScene, VillageScene],
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
  /** Finish Floors of Races the player has run and discovered; only those wear the mark. */
  pillarMarks = input<number[]>([]);

  /** Dev only: `?inkFixture=<floors>` shows the Tower scene without an Expedition (capture tooling). */
  private readonly fixtureOn =
    isDevMode() && readFixtureParams(location.search) !== null;
  sceneShown = computed(() => this.fixtureOn || this.active());

  towerFloors = computed(() => this.tower()?.floors ?? {});
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
  /** The Floor shown wears the mark of a Race's Finish Floor, but only when listed by the server. */
  currentFloorMarked = computed(() => this.pillarMarks().includes(this.currentFloor()));
  enemyCount = computed(() => this.currentTowerFloorEnemies()?.length ?? 0);
}
