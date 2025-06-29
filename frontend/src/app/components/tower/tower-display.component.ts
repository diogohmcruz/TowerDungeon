import { Component, input } from '@angular/core';
import { Tower } from '../../interfaces/game-state';
import { JsonPipe } from '@angular/common';

@Component({
  selector: 'app-tower',
  imports: [JsonPipe],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.scss',
})
export class TowerDisplay {
  tower = input<Tower | undefined>();
  arrayCreator = Array;

  randomWindowCount(): boolean {
    return Math.random() > 0.5;
  }
}
