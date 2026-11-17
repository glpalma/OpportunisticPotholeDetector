import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import * as L from 'leaflet';
import 'leaflet.markercluster';

import { DashboardApi } from '../../core/dashboard-api';
import { FilterState } from '../../core/filter-state';
import { DashboardStats, MapPoint } from '../../core/models';
import { RefreshBus } from '../../core/refresh';
import { CAMPINAS, markerColor, precisionLabel, statusLabel } from '../../core/status';
import { FiltersPanel } from '../../shared/filters-panel';

function runtimeLeaflet(): typeof L {
  const published = (globalThis as { L?: typeof L }).L;
  return published?.map ? published : L;
}

@Component({
  selector: 'app-map-page',
  imports: [FiltersPanel],
  templateUrl: './map-page.html',
})
export class MapPage implements AfterViewInit, OnDestroy {
  private readonly api = inject(DashboardApi);
  private readonly filters = inject(FilterState);
  private readonly refreshBus = inject(RefreshBus);
  private readonly router = inject(Router);

  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');
  private readonly Leaflet = runtimeLeaflet();
  private map?: L.Map;
  private cluster?: L.FeatureGroup;
  private request = 0;
  private searchedBeyondView = false;

  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly error = signal(false);
  protected readonly precision = precisionLabel;

  constructor() {
    effect(() => {
      this.filters.params();
      this.refreshBus.tick();
      untracked(() => this.reload());
    });
  }

  ngAfterViewInit(): void {
    const element = this.canvas()?.nativeElement;
    if (!element) {
      return;
    }
    this.map = this.Leaflet.map(element).setView([CAMPINAS.lat, CAMPINAS.lng], CAMPINAS.zoom);
    this.Leaflet.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; colaboradores do OpenStreetMap',
      maxZoom: 19,
    }).addTo(this.map);
    this.cluster = this.Leaflet.markerClusterGroup({ showCoverageOnHover: false, maxClusterRadius: 48 });
    this.map.addLayer(this.cluster);
    this.map.on('moveend', () => this.reload());
    setTimeout(() => this.map?.invalidateSize(), 0);
    this.reload();
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private reload(): void {
    if (!this.map || !this.cluster) {
      return;
    }
    const bounds = this.map.getBounds();
    const box = `${bounds.getWest()},${bounds.getSouth()},${bounds.getEast()},${bounds.getNorth()}`;
    const request = ++this.request;
    this.api.map(box, this.filters.params()).subscribe({
      next: (points) => {
        if (request !== this.request) {
          return;
        }
        this.error.set(false);
        this.draw(points);
        if (points.length === 0) {
          this.showPotholesOutsideTheView();
        }
      },
      error: () => {
        if (request === this.request) {
          this.error.set(true);
        }
      },
    });
    this.api.stats().subscribe({
      next: (stats) => {
        if (request === this.request) {
          this.stats.set(stats);
        }
      },
      error: () => {
        if (request === this.request) {
          this.stats.set(null);
        }
      },
    });
  }

  private showPotholesOutsideTheView(): void {
    if (this.searchedBeyondView || !this.map) {
      return;
    }
    this.searchedBeyondView = true;
    this.api.map('-74,-34,-28,6', this.filters.params()).subscribe({
      next: (points) => {
        if (!this.map || points.length === 0) {
          return;
        }
        if (points.length === 1) {
          this.map.setView([points[0].lat, points[0].lon], 16);
          return;
        }
        const bounds = this.Leaflet.latLngBounds(points.map((point) => [point.lat, point.lon] as [number, number]));
        this.map.fitBounds(bounds, { padding: [48, 48], maxZoom: 16 });
      },
    });
  }

  private draw(points: MapPoint[]): void {
    this.cluster?.clearLayers();
    for (const point of points) {
      const marker = this.Leaflet.marker([point.lat, point.lon], {
        icon: this.Leaflet.divIcon({
          className: 'pothole-marker',
          html: `<span class="pothole-dot" style="background:${markerColor(point.status, point.neglected)}"></span>`,
          iconSize: [22, 22],
          iconAnchor: [11, 11],
        }),
      });
      marker.bindPopup(this.popup(point));
      this.cluster?.addLayer(marker);
    }
  }

  private popup(point: MapPoint): HTMLElement {
    const container = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = statusLabel(point.status, point.neglected);
    const link = document.createElement('a');
    link.href = `/buracos/${point.id}`;
    link.textContent = 'Abrir buraco';
    link.addEventListener('click', (event) => {
      event.preventDefault();
      void this.router.navigate(['/buracos', point.id]);
    });
    container.append(title, link);
    return container;
  }
}
