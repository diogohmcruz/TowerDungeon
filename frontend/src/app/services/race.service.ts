import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CampaignKeyService } from './campaign-key.service';
import { RaceTableView, RaceTerms } from '../interfaces/race';

/** Reads of the races the server holds. The Campaign Key is the bearer. */
@Injectable({ providedIn: 'root' })
export class RaceService {
  private readonly http = inject(HttpClient);
  private readonly keys = inject(CampaignKeyService);

  private headers(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.keys.key()}` });
  }

  /** The races on offer, each with this Key's attempts and the reason entry would be refused. */
  races(): Promise<RaceTerms[]> {
    return firstValueFrom(this.http.get<RaceTerms[]>('/api/races', { headers: this.headers() }));
  }

  /** The finished table; the server refuses it (403) until this Key has closed an attempt. */
  table(raceId: string): Promise<RaceTableView> {
    return firstValueFrom(
      this.http.get<RaceTableView>(`/api/races/${encodeURIComponent(raceId)}/table`, {
        headers: this.headers(),
      }),
    );
  }
}
