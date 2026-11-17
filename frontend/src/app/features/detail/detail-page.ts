import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { DashboardApi } from '../../core/dashboard-api';
import { DashboardSighting, PotholeDetail } from '../../core/models';
import { RefreshBus } from '../../core/refresh';
import { confidenceLabel, reviewLabel, statusExplanation, statusLabel, thumbnailUrl } from '../../core/status';
import { MiniMap } from '../../shared/mini-map';
import { SightingDialog } from './sighting-dialog';

@Component({
  selector: 'app-detail-page',
  imports: [MiniMap, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './detail-page.html',
})
export class DetailPage {
  private readonly api = inject(DashboardApi);
  private readonly route = inject(ActivatedRoute);
  private readonly refreshBus = inject(RefreshBus);
  private readonly dialog = inject(MatDialog);

  private readonly id = toSignal(this.route.paramMap.pipe(map((params) => params.get('id'))));

  protected readonly pothole = signal<PotholeDetail | null>(null);
  protected readonly missing = signal(false);
  protected readonly error = signal(false);
  protected readonly statusLabel = statusLabel;
  protected readonly statusExplanation = statusExplanation;
  protected readonly confidenceLabel = confidenceLabel;
  protected readonly reviewLabel = reviewLabel;
  protected readonly thumbnailUrl = thumbnailUrl;

  constructor() {
    effect(() => {
      const id = this.id();
      this.refreshBus.tick();
      if (!id) {
        return;
      }
      untracked(() => this.load(id));
    });
  }

  protected open(sighting: DashboardSighting): void {
    this.dialog
      .open(SightingDialog, { data: { sighting }, width: 'min(960px, 96vw)' })
      .afterClosed()
      .subscribe((changed) => {
        const id = this.id();
        if (changed && id) {
          this.load(id);
        }
      });
  }

  protected missingThumb(event: Event): void {
    (event.target as HTMLImageElement).replaceWith(note());
  }

  private load(id: string): void {
    this.api.detail(id).subscribe({
      next: (pothole) => {
        this.error.set(false);
        this.missing.set(false);
        this.pothole.set(pothole);
      },
      error: (err: { status?: number }) => {
        this.pothole.set(null);
        this.missing.set(err.status === 404);
        this.error.set(err.status !== 404);
      },
    });
  }
}

function note(): HTMLElement {
  const element = document.createElement('span');
  element.className = 'placeholder';
  element.textContent = 'Imagem removida';
  return element;
}
