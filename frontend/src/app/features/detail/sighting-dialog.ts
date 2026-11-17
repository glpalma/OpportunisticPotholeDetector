import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { DashboardApi } from '../../core/dashboard-api';
import { DashboardSighting, Verdict } from '../../core/models';
import { confidenceLabel, imageUrl, reviewLabel } from '../../core/status';
import { BboxFrame } from '../../shared/bbox-frame';

export interface SightingDialogData {
  sighting: DashboardSighting;
}

@Component({
  selector: 'app-sighting-dialog',
  imports: [MatDialogModule, BboxFrame, DatePipe, DecimalPipe],
  templateUrl: './sighting-dialog.html',
})
export class SightingDialog {
  private readonly api = inject(DashboardApi);
  private readonly dialogRef = inject(MatDialogRef<SightingDialog, boolean>);
  protected readonly data = inject<SightingDialogData>(MAT_DIALOG_DATA);

  protected readonly busy = signal(false);
  protected readonly error = signal(false);
  protected readonly imageUrl = imageUrl;
  protected readonly confidenceLabel = confidenceLabel;
  protected readonly reviewLabel = reviewLabel;

  protected mark(verdict: Verdict | null): void {
    this.busy.set(true);
    this.api.review(this.data.sighting.sightingId, verdict).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.busy.set(false);
        this.error.set(true);
      },
    });
  }
}
