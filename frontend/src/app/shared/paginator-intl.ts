import { MatPaginatorIntl } from '@angular/material/paginator';

export function ptPaginator(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Por página';
  intl.nextPageLabel = 'Próxima';
  intl.previousPageLabel = 'Anterior';
  intl.firstPageLabel = 'Primeira';
  intl.lastPageLabel = 'Última';
  intl.getRangeLabel = (page, pageSize, length) => {
    if (length === 0 || pageSize === 0) {
      return `0 de ${length}`;
    }
    const start = page * pageSize + 1;
    const end = Math.min((page + 1) * pageSize, length);
    return `${start}–${end} de ${length}`;
  };
  return intl;
}
