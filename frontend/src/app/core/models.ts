export interface MapPoint {
  id: string;
  lat: number;
  lon: number;
  status: string;
  neglected: boolean;
}

export interface PotholeSummary {
  id: string;
  lat: number;
  lon: number;
  status: string;
  neglected: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
  sightingCount: number;
  maxConfidence: number;
  reviewedCount: number;
  falsePositive: boolean;
  thumbnailSightingId: string | null;
}

export interface PotholePage {
  page: number;
  pageSize: number;
  total: number;
  items: PotholeSummary[];
}

export interface DashboardSighting {
  sightingId: string;
  clientId: string;
  capturedAt: string;
  receivedAt: string;
  lat: number;
  lon: number;
  heading: number;
  speed: number;
  horizontalAccuracyM: number;
  confidence: number;
  bbox: number[];
  review: string | null;
  reviewedAt: string | null;
  imageAvailable: boolean;
}

export interface PotholeDetail {
  id: string;
  lat: number;
  lon: number;
  status: string;
  neglected: boolean;
  firstSeenAt: string;
  lastSeenAt: string;
  sightingCount: number;
  maxConfidence: number;
  reviewedCount: number;
  falsePositive: boolean;
  sightings: DashboardSighting[];
}

export interface ReviewCandidate {
  sightingId: string;
  clientId: string;
  capturedAt: string;
  receivedAt: string;
  lat: number;
  lon: number;
  speed: number;
  horizontalAccuracyM: number;
  confidence: number;
  bbox: number[];
  imageAvailable: boolean;
  potholeId: string;
  potholeLat: number;
  potholeLon: number;
  potholeStatus: string;
  potholeNeglected: boolean;
  potholeSightingCount: number;
}

export interface DashboardStats {
  open: number;
  neglected: number;
  fixed: number;
  resunk: number;
  sightingsReviewed: number;
  sightingsPending: number;
  detectorPrecision: number | null;
}

export interface ClientStatus {
  clientId: string;
  lastSeenAt: string;
  queueSize: number | null;
}

export interface FleetStatus {
  lastReceivedAt: string | null;
  sightingsInLastSync: number;
  clients: ClientStatus[];
}

export type Verdict = 'pothole' | 'false_positive';

export type Situation = 'all' | 'open' | 'neglected' | 'fixed' | 'resunk';
