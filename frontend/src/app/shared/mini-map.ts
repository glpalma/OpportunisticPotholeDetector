import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import * as L from 'leaflet';

import { markerColor } from '../core/status';

@Component({
  selector: 'app-mini-map',
  template: '<div #canvas class="mini-map"></div>',
})
export class MiniMap implements AfterViewInit, OnDestroy {
  readonly lat = input.required<number>();
  readonly lon = input.required<number>();
  readonly status = input('open');
  readonly neglected = input(false);

  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');
  private map?: L.Map;
  private marker?: L.Marker;

  constructor() {
    effect(() => {
      const lat = this.lat();
      const lon = this.lon();
      const color = markerColor(this.status(), this.neglected());
      this.map?.setView([lat, lon], 16);
      this.marker?.setLatLng([lat, lon]);
      this.marker?.setIcon(dot(color));
    });
  }

  ngAfterViewInit(): void {
    const element = this.canvas()?.nativeElement;
    if (!element) {
      return;
    }
    this.map = L.map(element, { zoomControl: false, attributionControl: true }).setView(
      [this.lat(), this.lon()],
      16,
    );
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(this.map);
    this.marker = L.marker([this.lat(), this.lon()], {
      icon: dot(markerColor(this.status(), this.neglected())),
    }).addTo(this.map);
    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}

function dot(color: string): L.DivIcon {
  return L.divIcon({
    className: 'pothole-marker',
    html: `<span class="pothole-dot" style="background:${color}"></span>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}
