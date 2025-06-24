export interface GameState {
  mana: number;
  manaPerSecond: number;
  upgrades: string[];
  prestigePoints: number;
  unlockedSpells?: string[];
  dungeonLevel?: number;
  enemiesDefeated?: number;
  lastUpdateTimestamp?: number;
}
