import { sql } from "./db";
import { getIngestToken, isSampleMode } from "./auth";
import { publish } from "./events";
import {
  beaconKey,
  type BeaconScan,
  type DashboardState,
  type FloorPlan,
  type PlacedBeacon,
  type Receiver,
  type TrackPoint,
} from "./types";

function asIso(value: unknown): string {
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "string") return value;
  return new Date().toISOString();
}

function num(value: unknown, fallback = 0): number {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : fallback;
}

export async function listFloorPlans(): Promise<FloorPlan[]> {
  const rows = await sql<{
    id: string;
    name: string;
    width_meters: number;
    image_data_url: string | null;
    created_at: unknown;
    updated_at: unknown;
  }>("select * from floor_plans order by updated_at desc");
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    widthMeters: num(r.width_meters, 20),
    imageDataUrl: r.image_data_url,
    createdAt: asIso(r.created_at),
    updatedAt: asIso(r.updated_at),
  }));
}

export async function ensureDefaultPlan(): Promise<FloorPlan> {
  const existing = await listFloorPlans();
  if (existing[0]) return existing[0];
  const id = crypto.randomUUID();
  await sql(
    `insert into floor_plans(id, name, width_meters) values ($1, $2, $3)`,
    [id, "Main Floor", 24],
  );
  const plans = await listFloorPlans();
  return plans[0]!;
}

export async function createFloorPlan(input: {
  name: string;
  widthMeters: number;
  imageDataUrl?: string | null;
}): Promise<FloorPlan> {
  const id = crypto.randomUUID();
  await sql(
    `insert into floor_plans(id, name, width_meters, image_data_url)
     values ($1, $2, $3, $4)`,
    [id, input.name.trim() || "Floor plan", input.widthMeters || 20, input.imageDataUrl ?? null],
  );
  const plans = await listFloorPlans();
  const plan = plans.find((p) => p.id === id)!;
  await broadcast();
  return plan;
}

export async function updateFloorPlan(
  id: string,
  patch: { name?: string; widthMeters?: number; imageDataUrl?: string | null },
): Promise<FloorPlan | null> {
  const current = (await listFloorPlans()).find((p) => p.id === id);
  if (!current) return null;
  await sql(
    `update floor_plans
     set name = $2,
         width_meters = $3,
         image_data_url = $4,
         updated_at = now()
     where id = $1`,
    [
      id,
      patch.name ?? current.name,
      patch.widthMeters ?? current.widthMeters,
      patch.imageDataUrl === undefined ? current.imageDataUrl : patch.imageDataUrl,
    ],
  );
  await broadcast();
  return (await listFloorPlans()).find((p) => p.id === id) ?? null;
}

export async function deleteFloorPlan(id: string): Promise<void> {
  await sql(`delete from floor_plans where id = $1`, [id]);
  await broadcast();
}

async function listBeacons(planId: string | null): Promise<PlacedBeacon[]> {
  const rows = planId
    ? await sql<{
        id: string;
        plan_id: string;
        label: string;
        uuid: string;
        major: number;
        minor: number;
        x: number;
        y: number;
        tracked: boolean;
        updated_at: unknown;
      }>(`select * from placed_beacons where plan_id = $1 order by label`, [planId])
    : await sql<{
        id: string;
        plan_id: string;
        label: string;
        uuid: string;
        major: number;
        minor: number;
        x: number;
        y: number;
        tracked: boolean;
        updated_at: unknown;
      }>(`select * from placed_beacons order by label`);
  return rows.map((r) => ({
    id: r.id,
    planId: r.plan_id,
    label: r.label,
    uuid: r.uuid,
    major: Number(r.major),
    minor: Number(r.minor),
    x: num(r.x),
    y: num(r.y),
    tracked: Boolean(r.tracked),
    updatedAt: asIso(r.updated_at),
  }));
}

async function listReceivers(planId: string | null): Promise<Receiver[]> {
  const rows = planId
    ? await sql<{
        id: string;
        name: string;
        plan_id: string | null;
        pin_x: number | null;
        pin_y: number | null;
        last_seen_at: unknown;
      }>(
        `select * from receivers
         where plan_id = $1 or plan_id is null
         order by last_seen_at desc`,
        [planId],
      )
    : await sql<{
        id: string;
        name: string;
        plan_id: string | null;
        pin_x: number | null;
        pin_y: number | null;
        last_seen_at: unknown;
      }>(`select * from receivers order by last_seen_at desc`);
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    planId: r.plan_id,
    pinX: r.pin_x == null ? null : num(r.pin_x),
    pinY: r.pin_y == null ? null : num(r.pin_y),
    lastSeenAt: asIso(r.last_seen_at),
  }));
}

async function listPaths(planId: string): Promise<Record<string, TrackPoint[]>> {
  const rows = await sql<{
    id: number;
    plan_id: string;
    beacon_key: string;
    label: string;
    x: number;
    y: number;
    distance_meters: number | null;
    rssi: number | null;
    confidence: number | null;
    recorded_at: unknown;
  }>(
    `select * from track_points
     where plan_id = $1
       and recorded_at > now() - interval '6 hours'
     order by recorded_at asc
     limit 4000`,
    [planId],
  );
  const paths: Record<string, TrackPoint[]> = {};
  for (const r of rows) {
    const point: TrackPoint = {
      id: Number(r.id),
      planId: r.plan_id,
      beaconKey: r.beacon_key,
      label: r.label,
      x: num(r.x),
      y: num(r.y),
      distanceMeters: r.distance_meters == null ? null : num(r.distance_meters),
      rssi: r.rssi == null ? null : Number(r.rssi),
      confidence: r.confidence == null ? null : num(r.confidence),
      recordedAt: asIso(r.recorded_at),
    };
    (paths[point.beaconKey] ??= []).push(point);
  }
  return paths;
}

async function latestScans(): Promise<BeaconScan[]> {
  const rows = await sql<{ payload: unknown }>(
    `select payload from scan_snapshots order by received_at desc limit 1`,
  );
  const payload = rows[0]?.payload as { beacons?: BeaconScan[] } | undefined;
  if (!payload?.beacons || !Array.isArray(payload.beacons)) return [];
  return payload.beacons;
}

export async function getDashboardState(selectedPlanId?: string | null): Promise<DashboardState> {
  const plans = await listFloorPlans();
  const planId = selectedPlanId && plans.some((p) => p.id === selectedPlanId)
    ? selectedPlanId
    : plans[0]?.id ?? null;
  const token = await getIngestToken();
  return {
    floorPlans: plans,
    selectedPlanId: planId,
    beacons: planId ? await listBeacons(planId) : [],
    receivers: await listReceivers(planId),
    paths: planId ? await listPaths(planId) : {},
    latestScans: await latestScans(),
    ingestHint: token,
    sampleMode: isSampleMode(),
    updatedAt: new Date().toISOString(),
  };
}

async function broadcast(selectedPlanId?: string | null) {
  const state = await getDashboardState(selectedPlanId);
  publish({ type: "state", state });
}

export async function clearPath(planId: string, beaconKeyValue: string) {
  await sql(`delete from track_points where plan_id = $1 and beacon_key = $2`, [
    planId,
    beaconKeyValue,
  ]);
  await broadcast(planId);
}

type IngestBody = {
  source?: string;
  event?: string;
  timestampMs?: number;
  receiverId?: string;
  receiverName?: string;
  planId?: string;
  planName?: string;
  planWidthMeters?: number;
  receiverPin?: { x?: number; y?: number } | null;
  beacons?: Array<Record<string, unknown>>;
};

function clamp01(n: number): number {
  return Math.min(0.98, Math.max(0.02, n));
}

export async function ingestPayload(body: IngestBody): Promise<{ ok: true; planId: string | null }> {
  const event = body.event ?? "beacon_scan";
  const receiverId = body.receiverId?.trim() || "anonymous";
  const receiverName = body.receiverName?.trim() || "Receiver";

  if (event === "beacon_scan") {
    await sql(
      `insert into scan_snapshots(source, receiver_id, payload) values ($1, $2, $3::jsonb)`,
      [
        body.source ?? "beacon-simulator",
        receiverId,
        JSON.stringify({
          timestampMs: body.timestampMs ?? Date.now(),
          beacons: body.beacons ?? [],
        }),
      ],
    );
    await sql(
      `insert into receivers(id, name, last_seen_at)
       values ($1, $2, now())
       on conflict (id) do update set name = excluded.name, last_seen_at = now()`,
      [receiverId, receiverName],
    );
    await broadcast();
    return { ok: true, planId: null };
  }

  // beacon_location (or legacy aliases)
  let planId = body.planId?.trim() || null;
  if (planId) {
    const found = await sql<{ id: string }>(`select id from floor_plans where id = $1`, [planId]);
    if (!found[0]) {
      await sql(
        `insert into floor_plans(id, name, width_meters) values ($1, $2, $3)`,
        [planId, body.planName?.trim() || "Synced floor", num(body.planWidthMeters, 20)],
      );
    } else if (body.planName || body.planWidthMeters) {
      await sql(
        `update floor_plans
         set name = coalesce(nullif($2, ''), name),
             width_meters = coalesce($3, width_meters),
             updated_at = now()
         where id = $1`,
        [planId, body.planName ?? "", body.planWidthMeters ?? null],
      );
    }
  } else {
    planId = (await ensureDefaultPlan()).id;
  }

  const pinX = body.receiverPin?.x;
  const pinY = body.receiverPin?.y;
  await sql(
    `insert into receivers(id, name, plan_id, pin_x, pin_y, last_seen_at)
     values ($1, $2, $3, $4, $5, now())
     on conflict (id) do update set
       name = excluded.name,
       plan_id = excluded.plan_id,
       pin_x = coalesce(excluded.pin_x, receivers.pin_x),
       pin_y = coalesce(excluded.pin_y, receivers.pin_y),
       last_seen_at = now()`,
    [
      receiverId,
      receiverName,
      planId,
      typeof pinX === "number" ? clamp01(pinX) : null,
      typeof pinY === "number" ? clamp01(pinY) : null,
    ],
  );

  for (const raw of body.beacons ?? []) {
    const uuid = String(raw.uuid ?? "").toUpperCase();
    const major = Number(raw.major);
    const minor = Number(raw.minor);
    if (!uuid || !Number.isFinite(major) || !Number.isFinite(minor)) continue;
    const x = clamp01(num(raw.x, 0.5));
    const y = clamp01(num(raw.y, 0.5));
    const label = String(raw.label ?? `Beacon ${major}/${minor}`);
    const tracked = Boolean(raw.tracked ?? true);
    const key = beaconKey(uuid, major, minor);

    const existing = await sql<{ id: string }>(
      `select id from placed_beacons
       where plan_id = $1 and uuid = $2 and major = $3 and minor = $4
       limit 1`,
      [planId, uuid, major, minor],
    );
    const id = existing[0]?.id ?? crypto.randomUUID();
    if (existing[0]) {
      await sql(
        `update placed_beacons
         set label = $2, x = $3, y = $4, tracked = $5, updated_at = now()
         where id = $1`,
        [id, label, x, y, tracked],
      );
    } else {
      await sql(
        `insert into placed_beacons(id, plan_id, label, uuid, major, minor, x, y, tracked)
         values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
        [id, planId, label, uuid, major, minor, x, y, tracked],
      );
    }

    if (tracked) {
      const last = await sql<{ x: number; y: number }>(
        `select x, y from track_points
         where plan_id = $1 and beacon_key = $2
         order by recorded_at desc limit 1`,
        [planId, key],
      );
      const lastX = last[0] ? num(last[0].x) : null;
      const lastY = last[0] ? num(last[0].y) : null;
      const moved =
        lastX == null ||
        lastY == null ||
        Math.hypot(x - lastX, y - lastY) >= 0.008;
      if (moved) {
        await sql(
          `insert into track_points(
             plan_id, beacon_key, label, x, y, distance_meters, rssi, confidence
           ) values ($1,$2,$3,$4,$5,$6,$7,$8)`,
          [
            planId,
            key,
            label,
            x,
            y,
            raw.distanceMeters == null ? null : num(raw.distanceMeters),
            raw.rssi == null ? null : Number(raw.rssi),
            raw.confidence == null ? null : num(raw.confidence),
          ],
        );
      }
    }
  }

  await sql(
    `insert into scan_snapshots(source, receiver_id, payload) values ($1, $2, $3::jsonb)`,
    [
      body.source ?? "beacon-simulator",
      receiverId,
      JSON.stringify({ event, timestampMs: body.timestampMs ?? Date.now(), beacons: body.beacons ?? [] }),
    ],
  );

  await broadcast(planId);
  return { ok: true, planId };
}

export async function seedDemo(): Promise<DashboardState> {
  const plans = await listFloorPlans();
  let plan = plans[0];
  if (!plan) {
    plan = await createFloorPlan({ name: "Sample atrium", widthMeters: 30 });
  }

  await sql(`delete from placed_beacons where plan_id = $1`, [plan.id]);
  await sql(`delete from track_points where plan_id = $1`, [plan.id]);
  await sql(`delete from receivers where plan_id = $1`, [plan.id]);

  const uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0";
  const anchors = [
    { label: "Anchor NE", major: 1, minor: 10, x: 0.82, y: 0.18, tracked: false },
    { label: "Anchor SW", major: 1, minor: 11, x: 0.18, y: 0.78, tracked: false },
    { label: "Transmitter 1", major: 1, minor: 1, x: 0.35, y: 0.42, tracked: true },
  ];
  for (const a of anchors) {
    await sql(
      `insert into placed_beacons(id, plan_id, label, uuid, major, minor, x, y, tracked)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [crypto.randomUUID(), plan.id, a.label, uuid, a.major, a.minor, a.x, a.y, a.tracked],
    );
  }

  await sql(
    `insert into receivers(id, name, plan_id, pin_x, pin_y, last_seen_at)
     values ($1,$2,$3,$4,$5,now()), ($6,$7,$3,$8,$9,now())
     on conflict (id) do update set
       name = excluded.name, plan_id = excluded.plan_id,
       pin_x = excluded.pin_x, pin_y = excluded.pin_y, last_seen_at = now()`,
    ["recv-a", "Phone A", plan.id, 0.22, 0.28, "recv-b", "Phone B", 0.74, 0.66],
  );

  const path: Array<[number, number]> = [
    [0.28, 0.55],
    [0.32, 0.5],
    [0.36, 0.46],
    [0.42, 0.44],
    [0.48, 0.42],
    [0.54, 0.4],
    [0.58, 0.38],
    [0.62, 0.42],
    [0.66, 0.48],
    [0.6, 0.54],
    [0.52, 0.58],
    [0.44, 0.56],
    [0.38, 0.5],
    [0.35, 0.42],
  ];
  const key = beaconKey(uuid, 1, 1);
  for (let i = 0; i < path.length; i++) {
    const [x, y] = path[i]!;
    const recordedAt = new Date(Date.now() - (path.length - i) * 8_000).toISOString();
    await sql(
      `insert into track_points(plan_id, beacon_key, label, x, y, distance_meters, rssi, confidence, recorded_at)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [plan.id, key, "Transmitter 1", x, y, 2.4 - i * 0.05, -58 - i, 0.7, recordedAt],
    );
  }

  await sql(
    `insert into scan_snapshots(source, receiver_id, payload) values ($1,$2,$3::jsonb)`,
    [
      "demo",
      "recv-a",
      JSON.stringify({
        timestampMs: Date.now(),
        beacons: [
          {
            uuid,
            major: 1,
            minor: 1,
            rssi: -61,
            txPower: -59,
            distanceMeters: 1.4,
            proximity: "Near",
            deviceAddress: "AA:BB:CC:DD:EE:FF",
            lastSeenEpochMs: Date.now(),
          },
        ],
      }),
    ],
  );

  const state = await getDashboardState(plan.id);
  publish({ type: "state", state });
  return state;
}
