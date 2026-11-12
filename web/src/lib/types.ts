export type Point = { x: number; y: number };

export type FloorPlan = {
  id: string;
  name: string;
  widthMeters: number;
  imageDataUrl: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PlacedBeacon = {
  id: string;
  planId: string;
  label: string;
  uuid: string;
  major: number;
  minor: number;
  x: number;
  y: number;
  tracked: boolean;
  updatedAt: string;
};

export type Receiver = {
  id: string;
  name: string;
  planId: string | null;
  pinX: number | null;
  pinY: number | null;
  lastSeenAt: string;
};

export type TrackPoint = {
  id: number;
  planId: string;
  beaconKey: string;
  label: string;
  x: number;
  y: number;
  distanceMeters: number | null;
  rssi: number | null;
  confidence: number | null;
  recordedAt: string;
};

export type BeaconScan = {
  uuid: string;
  major: number;
  minor: number;
  rssi: number;
  txPower: number;
  distanceMeters: number | null;
  proximity: string | null;
  deviceAddress: string | null;
  lastSeenEpochMs: number | null;
};

export type DashboardState = {
  floorPlans: FloorPlan[];
  selectedPlanId: string | null;
  beacons: PlacedBeacon[];
  receivers: Receiver[];
  paths: Record<string, TrackPoint[]>;
  latestScans: BeaconScan[];
  ingestHint: string;
  sampleMode: boolean;
  updatedAt: string;
};

export function beaconKey(uuid: string, major: number, minor: number): string {
  return `${uuid.toUpperCase()}|${major}|${minor}`;
}
