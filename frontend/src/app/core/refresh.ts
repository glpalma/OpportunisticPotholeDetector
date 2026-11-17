import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class RefreshBus {
  readonly tick = signal(0);

  refresh(): void {
    this.tick.update((value) => value + 1);
  }
}
