import {Component, input} from '@angular/core';
import {Tower} from '../../interfaces/game-state';

@Component({
  selector: 'app-tower',
  imports: [],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.css'
})
export class TowerDisplay {
  tower = input<Tower | undefined>();
}
