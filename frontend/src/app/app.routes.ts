import { Routes } from '@angular/router';

import { DetailPage } from './features/detail/detail-page';
import { MapPage } from './features/map/map-page';
import { ReviewPage } from './features/review/review-page';
import { TablePage } from './features/table/table-page';

export const routes: Routes = [
  { path: '', component: MapPage },
  { path: 'tabela', component: TablePage },
  { path: 'buracos/:id', component: DetailPage },
  { path: 'revisao', component: ReviewPage },
  { path: '**', redirectTo: '' },
];
