import { Component, inject } from '@angular/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { FilterState } from '../core/filter-state';
import { Situation } from '../core/models';

@Component({
  selector: 'app-filters',
  imports: [MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  templateUrl: './filters-panel.html',
})
export class FiltersPanel {
  protected readonly filters = inject(FilterState);

  protected setSituation(value: Situation): void {
    this.filters.situation.set(value);
  }

  protected setFrom(event: Event): void {
    this.filters.from.set(inputValue(event));
  }

  protected setTo(event: Event): void {
    this.filters.to.set(inputValue(event));
  }

  protected setConfidence(event: Event): void {
    this.filters.minConfidence.set(inputValue(event));
  }
}

function inputValue(event: Event): string {
  return (event.target as HTMLInputElement).value;
}
