export const CAMPINAS = { lat: -22.9056, lng: -47.0608, zoom: 13 };

export function statusLabel(status: string, neglected: boolean): string {
  if (status === 'open' && neglected) {
    return 'Negligenciado';
  }
  if (status === 'open') {
    return 'Aberto';
  }
  if (status === 'fixed') {
    return 'Sem registro recente';
  }
  if (status === 'resunk') {
    return 'Reaberto';
  }
  return status;
}

export function statusExplanation(status: string, neglected: boolean): string {
  if (status === 'open' && neglected) {
    return 'Continua aberto e o primeiro avistamento já passou da janela de negligência.';
  }
  if (status === 'open') {
    return 'O último avistamento ainda está dentro da janela de silêncio.';
  }
  if (status === 'fixed') {
    return 'Sem avistamentos dentro da janela de silêncio. Isso não prova que o buraco foi consertado.';
  }
  if (status === 'resunk') {
    return 'Já tinha ficado sem registro recente e um novo avistamento chegou.';
  }
  return '';
}

export function markerColor(status: string, neglected: boolean): string {
  if (status === 'open' && neglected) {
    return '#d70466';
  }
  if (status === 'open') {
    return '#ff385c';
  }
  if (status === 'fixed') {
    return '#717171';
  }
  if (status === 'resunk') {
    return '#222222';
  }
  return '#222222';
}

export function confidenceLabel(value: number): string {
  return `${Math.round(value * 100)}%`;
}

export function reviewLabel(review: string | null): string {
  if (review === 'pothole') {
    return 'É buraco';
  }
  if (review === 'false_positive') {
    return 'Falso positivo';
  }
  return 'Sem revisão';
}

export function thumbnailUrl(sightingId: string | null): string | null {
  return sightingId ? `/app/api/sightings/${sightingId}/thumbnail` : null;
}

export function imageUrl(sightingId: string): string {
  return `/app/api/sightings/${sightingId}/image`;
}

export function precisionLabel(value: number | null): string {
  if (value == null) {
    return '—';
  }
  return `${Math.round(value * 100)}%`;
}
