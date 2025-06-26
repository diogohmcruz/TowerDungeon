import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, shareReplay } from 'rxjs';
import { Unit, UnitStats } from '../interfaces/unit.enum';

@Injectable({
  providedIn: 'root',
})
export class UnitStatsService {
  private http = inject(HttpClient);

  getUnitStats(): Observable<Map<Unit, UnitStats>> {
    return this.http
      .get<Map<Unit, UnitStats>>('http://localhost:8080/api/unit-stats/')
      .pipe(
        map((stats) => {
          const unitStatsMap = new Map<Unit, UnitStats>();
          Object.entries(stats).map(([key, value]) => {
            return unitStatsMap.set(key as Unit, value);
          });
          return unitStatsMap;
        }),
        shareReplay(1),
      );
  }
}
