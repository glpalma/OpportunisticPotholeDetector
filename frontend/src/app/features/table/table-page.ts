import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { DashboardApi } from '../../core/dashboard-api';
import { FilterState } from '../../core/filter-state';
import { PotholeSummary } from '../../core/models';
import { RefreshBus } from '../../core/refresh';
import { confidenceLabel, statusLabel, thumbnailUrl } from '../../core/status';
import { FiltersPanel } from '../../shared/filters-panel';
import { ptPaginator } from '../../shared/paginator-intl';

@Component({
  selector: 'app-table-page',
  imports: [FiltersPanel, MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule, RouterLink, DatePipe],
  providers: [{ provide: MatPaginatorIntl, useFactory: ptPaginator }],
  templateUrl: './table-page.html',
})
export class TablePage {
  private readonly api = inject(DashboardApi);
  private readonly filters = inject(FilterState);
  private readonly refreshBus = inject(RefreshBus);

  protected readonly columns = ['thumb', 'firstSeenAt', 'lastSeenAt', 'sightingCount', 'maxConfidence', 'status', 'reviewedCount'];
  protected readonly rows = signal<PotholeSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = signal(50);
  protected readonly sort = signal('lastSeenAt');
  protected readonly direction = signal<'asc' | 'desc'>('desc');
  protected readonly error = signal(false);
  protected readonly statusLabel = statusLabel;
  protected readonly confidenceLabel = confidenceLabel;
  protected readonly thumbnailUrl = thumbnailUrl;

  constructor() {
    effect(() => {
      this.filters.params();
      untracked(() => {
        this.page.set(0);
        this.load();
      });
    });
    effect(() => {
      if (this.refreshBus.tick() === 0) {
        return;
      }
      untracked(() => this.load());
    });
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected onSort(sort: Sort): void {
    if (!sort.active || sort.direction === '') {
      this.sort.set('lastSeenAt');
      this.direction.set('desc');
    } else {
      this.sort.set(sort.active);
      this.direction.set(sort.direction);
    }
    this.page.set(0);
    this.load();
  }

  protected exportCsv(): void {
    this.api.exportCsv(this.filters.params()).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'sightings.csv';
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  protected missingThumb(event: Event): void {
    (event.target as HTMLImageElement).replaceWith(placeholder());
  }

  private load(): void {
    this.api
      .list(this.filters.params(), this.page() + 1, this.pageSize(), this.sort(), this.direction())
      .subscribe({
        next: (page) => {
          this.error.set(false);
          this.rows.set(page.items);
          this.total.set(page.total);
        },
        error: () => this.error.set(true),
      });
  }
}

function placeholder(): HTMLElement {
  const note = document.createElement('span');
  note.className = 'placeholder';
  note.textContent = 'Imagem removida';
  return note;
}
