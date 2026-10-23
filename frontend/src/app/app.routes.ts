import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';

export const routes: Routes = isDevMode()
  ? [
      {
        // Dev tooling: the Village Elevation without a backend.
        path: 'village-fixture',
        loadComponent: () =>
          import('./components/village-scene/village-fixture.page').then(
            (m) => m.VillageFixturePage,
          ),
      },
    ]
  : [];
