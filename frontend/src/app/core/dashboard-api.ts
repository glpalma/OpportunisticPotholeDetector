import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DashboardStats,
  FleetStatus,
  MapPoint,
  PotholeDetail,
  PotholePage,
  ReviewCandidate,
  Verdict,
} from './models';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  map(bounds: string, filters: HttpParams): Observable<MapPoint[]> {
    return this.http.get<MapPoint[]>('/app/api/potholes/map', {
      params: filters.set('bounds', bounds),
    });
  }

  list(filters: HttpParams, page: number, pageSize: number, sort: string, direction: string): Observable<PotholePage> {
    return this.http.get<PotholePage>('/app/api/potholes', {
      params: filters
        .set('page', page)
        .set('pageSize', pageSize)
        .set('sort', sort)
        .set('direction', direction),
    });
  }

  detail(id: string): Observable<PotholeDetail> {
    return this.http.get<PotholeDetail>(`/app/api/potholes/${id}`);
  }

  stats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/app/api/stats');
  }

  clients(): Observable<FleetStatus> {
    return this.http.get<FleetStatus>('/app/api/clients');
  }

  nextUnreviewed(limit: number, afterCapturedAt?: string, afterId?: string): Observable<ReviewCandidate[]> {
    let params = new HttpParams().set('limit', limit);
    if (afterCapturedAt && afterId) {
      params = params.set('afterCapturedAt', afterCapturedAt).set('afterId', afterId);
    }
    return this.http.get<ReviewCandidate[]>('/app/api/review-queue/next', { params });
  }

  review(id: string, verdict: Verdict | null): Observable<void> {
    return this.http.put<void>(`/app/api/sightings/${id}/review`, { verdict });
  }

  exportCsv(filters: HttpParams): Observable<Blob> {
    return this.http.get('/app/api/export/sightings.csv', {
      params: filters,
      responseType: 'blob',
    });
  }
}
