import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GameComponent } from './components/game-component/game-component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, GameComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected title = 'Tower Dungeon';
}
