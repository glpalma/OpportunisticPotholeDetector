import { formatDate } from '@angular/common';
import { Component, LOCALE_ID, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

import { DashboardApi } from './core/dashboard-api';
import { FleetStatus } from './core/models';
import { RefreshBus } from './core/refresh';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly api = inject(DashboardApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LOCALE_ID);
  protected readonly refreshBus = inject(RefreshBus);
  protected readonly fleet = signal<FleetStatus | null>(null);

  private readonly navigated = toSignal(
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)),
  );

  constructor() {
    effect(() => {
      this.navigated();
      this.refreshBus.tick();
      untracked(() => this.loadFleet());
    });
  }

  protected busLabel(): string {
    const fleet = this.fleet();
    if (!fleet || fleet.clients.length === 0) {
      return 'Nenhum ônibus sincronizado';
    }
    const client = fleet.clients[0];
    const when = fleet.lastReceivedAt
      ? formatDate(fleet.lastReceivedAt, 'dd/MM/yyyy HH:mm', this.locale, 'America/Sao_Paulo')
      : 'sem envio';
    const queue = client.queueSize ?? '—';
    const extra = fleet.clients.length > 1 ? ` · ${fleet.clients.length} ônibus` : '';
    return `${client.clientId} · último envio ${when} · ${fleet.sightingsInLastSync} neste envio · fila ${queue}${extra}`;
  }

  private loadFleet(): void {
    this.api.clients().subscribe({
      next: (fleet) => this.fleet.set(fleet),
      error: () => this.fleet.set(null),
    });
  }
}
