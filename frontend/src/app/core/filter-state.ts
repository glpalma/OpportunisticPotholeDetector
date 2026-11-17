import { HttpParams } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';

import { Situation } from './models';

@Injectable({ providedIn: 'root' })
export class FilterState {
  readonly situation = signal<Situation>('all');
  readonly from = signal('');
  readonly to = signal('');
  readonly minConfidence = signal('');
  readonly unreviewed = signal(false);
  readonly includeFalsePositives = signal(false);

  readonly params = computed(() => filterParams(this.snapshot()));

  snapshot(): FilterSnapshot {
    return {
      situation: this.situation(),
      from: this.from(),
      to: this.to(),
      minConfidence: this.minConfidence(),
      unreviewed: this.unreviewed(),
      includeFalsePositives: this.includeFalsePositives(),
    };
  }
}

export interface FilterSnapshot {
  situation: Situation;
  from: string;
  to: string;
  minConfidence: string;
  unreviewed: boolean;
  includeFalsePositives: boolean;
}

export function filterParams(state: FilterSnapshot): HttpParams {
  let params = new HttpParams();
  if (state.situation === 'open') {
    params = params.set('status', 'open').set('neglected', 'false');
  } else if (state.situation === 'neglected') {
    params = params.set('status', 'open').set('neglected', 'true');
  } else if (state.situation === 'fixed' || state.situation === 'resunk') {
    params = params.set('status', state.situation);
  }
  const from = saoPauloToUtc(state.from);
  const to = saoPauloToUtc(state.to);
  if (from) {
    params = params.set('from', from);
  }
  if (to) {
    params = params.set('to', to);
  }
  if (state.minConfidence.trim() !== '') {
    const value = Number(state.minConfidence);
    if (!Number.isNaN(value)) {
      params = params.set('minConfidence', String(value));
    }
  }
  if (state.unreviewed) {
    params = params.set('unreviewed', 'true');
  }
  if (state.includeFalsePositives) {
    params = params.set('includeFalsePositives', 'true');
  }
  return params;
}

function saoPauloToUtc(value: string): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(`${value}:00-03:00`);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed.toISOString();
}
