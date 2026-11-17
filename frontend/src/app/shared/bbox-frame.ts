import { Component, computed, input, signal } from '@angular/core';

@Component({
  selector: 'app-bbox-frame',
  templateUrl: './bbox-frame.html',
})
export class BboxFrame {
  readonly src = input<string | null>(null);
  readonly bbox = input<number[] | null>(null);
  readonly alt = input('Quadro do avistamento');

  protected readonly failed = signal(false);
  protected readonly natural = signal<{ w: number; h: number } | null>(null);

  protected readonly box = computed(() => {
    const bbox = this.bbox();
    const size = this.natural();
    if (!bbox || bbox.length < 4 || !size || size.w === 0 || size.h === 0) {
      return null;
    }
    return {
      left: `${(bbox[0] / size.w) * 100}%`,
      top: `${(bbox[1] / size.h) * 100}%`,
      width: `${(bbox[2] / size.w) * 100}%`,
      height: `${(bbox[3] / size.h) * 100}%`,
    };
  });

  protected onLoad(event: Event): void {
    const image = event.target as HTMLImageElement;
    this.failed.set(false);
    this.natural.set({ w: image.naturalWidth, h: image.naturalHeight });
  }

  protected onError(): void {
    this.failed.set(true);
  }
}
