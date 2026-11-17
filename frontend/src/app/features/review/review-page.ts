import { DecimalPipe } from '@angular/common';
import { Component, HostListener, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DashboardApi } from '../../core/dashboard-api';
import { ReviewCandidate, Verdict } from '../../core/models';
import { RefreshBus } from '../../core/refresh';
import { confidenceLabel, imageUrl } from '../../core/status';
import { BboxFrame } from '../../shared/bbox-frame';
import { MiniMap } from '../../shared/mini-map';

interface UndoReview {
  kind: 'review';
  id: string;
  previous: Verdict | null;
}

interface UndoSkip {
  kind: 'skip';
  item: ReviewCandidate;
}

type Undo = UndoReview | UndoSkip;

@Component({
  selector: 'app-review-page',
  imports: [BboxFrame, MiniMap, RouterLink, DecimalPipe],
  templateUrl: './review-page.html',
})
export class ReviewPage {
  private readonly api = inject(DashboardApi);
  private readonly refreshBus = inject(RefreshBus);

  protected readonly queue = signal<ReviewCandidate[]>([]);
  protected readonly cursor = signal<ReviewCandidate | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal(false);
  protected readonly empty = signal(false);
  private readonly undoStack: Undo[] = [];

  protected readonly imageUrl = imageUrl;
  protected readonly confidenceLabel = confidenceLabel;

  constructor() {
    effect(() => {
      this.refreshBus.tick();
      untracked(() => {
        this.queue.set([]);
        this.cursor.set(null);
        this.undoStack.length = 0;
        this.fill();
      });
    });
  }

  @HostListener('window:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === 'b') {
      this.mark('pothole');
    } else if (key === 'f') {
      this.mark('false_positive');
    } else if (key === 'p') {
      this.skip();
    } else if (key === 'z') {
      this.undo();
    }
  }

  protected current(): ReviewCandidate | undefined {
    return this.queue()[0];
  }

  protected others(item: ReviewCandidate): number {
    return Math.max(0, item.potholeSightingCount - 1);
  }

  protected mark(verdict: Verdict): void {
    const item = this.current();
    if (!item || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.review(item.sightingId, verdict).subscribe({
      next: () => {
        this.undoStack.push({ kind: 'review', id: item.sightingId, previous: null });
        this.busy.set(false);
        this.advance();
      },
      error: () => {
        this.busy.set(false);
        this.error.set(true);
      },
    });
  }

  protected skip(): void {
    const item = this.current();
    if (!item || this.busy()) {
      return;
    }
    this.undoStack.push({ kind: 'skip', item });
    this.cursor.set(item);
    this.advance();
  }

  protected undo(): void {
    const action = this.undoStack.pop();
    if (!action || this.busy()) {
      return;
    }
    if (action.kind === 'skip') {
      this.queue.update((items) => [action.item, ...items]);
      this.empty.set(false);
      this.preload();
      return;
    }
    this.busy.set(true);
    this.api.review(action.id, action.previous).subscribe({
      next: () => {
        this.busy.set(false);
        this.queue.set([]);
        this.cursor.set(null);
        this.fill();
      },
      error: () => {
        this.busy.set(false);
        this.error.set(true);
      },
    });
  }

  private advance(): void {
    this.queue.update((items) => items.slice(1));
    this.error.set(false);
    if (this.queue().length < 2) {
      this.fill();
    } else {
      this.preload();
    }
  }

  private fill(): void {
    const after = this.cursor();
    this.api.nextUnreviewed(2, after?.capturedAt, after?.sightingId).subscribe({
      next: (items) => {
        const known = new Set(this.queue().map((item) => item.sightingId));
        const fresh = items.filter((item) => !known.has(item.sightingId));
        this.queue.update((current) => [...current, ...fresh]);
        const last = fresh.at(-1) ?? this.queue().at(-1);
        if (last) {
          this.cursor.set(last);
        }
        this.empty.set(this.queue().length === 0);
        this.preload();
      },
      error: () => this.error.set(true),
    });
  }

  private preload(): void {
    const next = this.queue()[1];
    if (!next?.imageAvailable) {
      return;
    }
    const image = new Image();
    image.src = imageUrl(next.sightingId);
  }
}
