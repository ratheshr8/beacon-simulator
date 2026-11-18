import { sql } from "./db";
import { getIngestToken, isSampleMode } from "./auth";
import { publish } from "./events";
import { estimate, metersToNormalized } from "./trilateration";
import {
  beaconKey,
  type BeaconScan,
  type DashboardState,
  type FloorPlan,
  type LocateFix,
  type PlacedBeacon,
  type Receiver,
  type ReceiverSighting,
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

function clamp01(n: number): number {
  return Math.min(0.98, Math.max(0.02, n));
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

/** Latest sighting per receiver for a plan (optionally one beacon). */
export async function listLatestSightings(
  planId: string,
  beaconKeyFilter?: string | null,
): Promise<ReceiverSighting[]> {
  const rows = await sql<{
    receiver_id: string;
    receiver_name: string;
    pin_x: number;
    pin_y: number;
    beacon_key: string;
    label: string;
    uuid: string;
    major: number;
    minor: number;
    distance_meters: number;
    rssi: number | null;
    recorded_at: unknown;
  }>(
    `select
       s.receiver_id,
       coalesce(r.name, s.receiver_id) as receiver_name,
       r.pin_x,
       r.pin_y,
       s.beacon_key,
       s.label,
       s.uuid,
       s.major,
       s.minor,
       s.distance_meters,
       s.rssi,
       s.recorded_at
     from sightings s
     join receivers r on r.id = s.receiver_id
     join (
       select receiver_id, beacon_key, max(recorded_at) as max_at
       from sightings
       where plan_id = $1
         and ($2::text is null or beacon_key = $2)
         and recorded_at > now() - interval '30 minutes'
       group by receiver_id, beacon_key
     ) latest
       on latest.receiver_id = s.receiver_id
      and latest.beacon_key = s.beacon_key
      and latest.max_at = s.recorded_at
     where s.plan_id = $1
       and r.pin_x is not null
       and r.pin_y is not null`,
    [planId, beaconKeyFilter ?? null],
  );

  return rows.map((r) => ({
    receiverId: r.receiver_id,
    receiverName: r.receiver_name,
    pinX: num(r.pin_x),
    pinY: num(r.pin_y),
    beaconKey: r.beacon_key,
    label: r.label,
    uuid: r.uuid,
    major: Number(r.major),
    minor: Number(r.minor),
    distanceMeters: num(r.distance_meters),
    rssi: r.rssi == null ? null : Number(r.rssi),
    recordedAt: asIso(r.recorded_at),
  }));
}

function buildLocateFixes(
  sightings: ReceiverSighting[],
  widthMeters: number,
): LocateFix[] {
  const byBeacon = new Map<string, ReceiverSighting[]>();
  for (const s of sightings) {
    const group = byBeacon.get(s.beaconKey);
    if (group) group.push(s);
    else byBeacon.set(s.beaconKey, [s]);
  }
  const fixes: LocateFix[] = [];
  for (const [key, group] of byBeacon) {
    if (group.length < 1) continue;
    const anchors = group.map((s) => ({
      x: s.pinX,
      y: s.pinY,
      radius: metersToNormalized(s.distanceMeters, widthMeters),
    }));
    let point = estimate(anchors);
    if (!point && group.length === 1) {
      // Single receiver: place on a default ray southeast of the pin.
      const s = group[0]!;
      const r = metersToNormalized(s.distanceMeters, widthMeters);
      point = {
        x: clamp01(s.pinX + r * 0.7),
        y: clamp01(s.pinY + r * 0.7),
      };
    }
    if (!point) continue;
    fixes.push({
      beaconKey: key,
      label: group[0]!.label,
      x: point.x,
      y: point.y,
      confidence: Math.min(1, group.length / 3),
      usedReceiverCount: group.length,
      sightings: group,
    });
  }
  return fixes;
}

async function upsertLocatedBeacon(
  planId: string,
  fix: LocateFix,
  tracked = true,
): Promise<void> {
  const sample = fix.sightings[0]!;
  const existing = await sql<{ id: string; x: number; y: number }>(
    `select id, x, y from placed_beacons
     where plan_id = $1 and uuid = $2 and major = $3 and minor = $4
     limit 1`,
    [planId, sample.uuid, sample.major, sample.minor],
  );
  const id = existing[0]?.id ?? crypto.randomUUID();
  if (existing[0]) {
    await sql(
      `update placed_beacons
       set label = $2, x = $3, y = $4, tracked = $5, updated_at = now()
       where id = $1`,
      [id, fix.label, fix.x, fix.y, tracked],
    );
  } else {
    await sql(
      `insert into placed_beacons(id, plan_id, label, uuid, major, minor, x, y, tracked)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [
        id,
        planId,
        fix.label,
        sample.uuid,
        sample.major,
        sample.minor,
        fix.x,
        fix.y,
        tracked,
      ],
    );
  }

  const last = await sql<{ x: number; y: number }>(
    `select x, y from track_points
     where plan_id = $1 and beacon_key = $2
     order by recorded_at desc limit 1`,
    [planId, fix.beaconKey],
  );
  const lastX = last[0] ? num(last[0].x) : null;
  const lastY = last[0] ? num(last[0].y) : null;
  const moved =
    lastX == null ||
    lastY == null ||
    Math.hypot(fix.x - lastX, fix.y - lastY) >= 0.008;
  if (moved) {
    const avgDistance =
      fix.sightings.reduce((s, g) => s + g.distanceMeters, 0) / fix.sightings.length;
    await sql(
      `insert into track_points(
         plan_id, beacon_key, label, x, y, distance_meters, rssi, confidence
       ) values ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [
        planId,
        fix.beaconKey,
        fix.label,
        fix.x,
        fix.y,
        avgDistance,
        sample.rssi,
        fix.confidence,
      ],
    );
  }
}

/** Recompute beacon positions from pinned receivers + latest distances. */
export async function locateBeaconsFromSightings(planId: string): Promise<LocateFix[]> {
  const plans = await listFloorPlans();
  const plan = plans.find((p) => p.id === planId);
  if (!plan) return [];
  const sightings = await listLatestSightings(planId);
  const fixes = buildLocateFixes(sightings, plan.widthMeters);
  for (const fix of fixes) {
    if (fix.usedReceiverCount >= 1) {
      await upsertLocatedBeacon(planId, fix, true);
    }
  }
  return fixes;
}

export async function getDashboardState(selectedPlanId?: string | null): Promise<DashboardState> {
  const plans = await listFloorPlans();
  const planId =
    selectedPlanId && plans.some((p) => p.id === selectedPlanId)
      ? selectedPlanId
      : (plans[0]?.id ?? null);
  const token = await getIngestToken();
  const sightings = planId ? await listLatestSightings(planId) : [];
  const width = plans.find((p) => p.id === planId)?.widthMeters ?? 20;
  return {
    floorPlans: plans,
    selectedPlanId: planId,
    beacons: planId ? await listBeacons(planId) : [],
    receivers: await listReceivers(planId),
    paths: planId ? await listPaths(planId) : {},
    latestScans: await latestScans(),
    sightings,
    locateFixes: buildLocateFixes(sightings, width),
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

export async function upsertReceiverPin(input: {
  id?: string;
  name: string;
  planId: string;
  pinX: number;
  pinY: number;
}): Promise<Receiver> {
  const id = input.id?.trim() || crypto.randomUUID();
  await sql(
    `insert into receivers(id, name, plan_id, pin_x, pin_y, last_seen_at)
     values ($1, $2, $3, $4, $5, now())
     on conflict (id) do update set
       name = excluded.name,
       plan_id = excluded.plan_id,
       pin_x = excluded.pin_x,
       pin_y = excluded.pin_y,
       last_seen_at = now()`,
    [id, input.name.trim() || "Receiver", input.planId, clamp01(input.pinX), clamp01(input.pinY)],
  );
  await locateBeaconsFromSightings(input.planId);
  await broadcast(input.planId);
  const receivers = await listReceivers(input.planId);
  return receivers.find((r) => r.id === id)!;
}

export async function recordManualSighting(input: {
  planId: string;
  receiverId: string;
  uuid: string;
  major: number;
  minor: number;
  label?: string;
  distanceMeters: number;
  rssi?: number | null;
}): Promise<DashboardState> {
  const uuid = input.uuid.toUpperCase();
  const key = beaconKey(uuid, input.major, input.minor);
  const label = input.label?.trim() || `Beacon ${input.major}/${input.minor}`;
  await sql(
    `insert into sightings(
       plan_id, receiver_id, beacon_key, label, uuid, major, minor, distance_meters, rssi
     ) values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [
      input.planId,
      input.receiverId,
      key,
      label,
      uuid,
      input.major,
      input.minor,
      input.distanceMeters,
      input.rssi ?? null,
    ],
  );
  await locateBeaconsFromSightings(input.planId);
  await broadcast(input.planId);
  return getDashboardState(input.planId);
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

async function ensurePlanFromBody(body: IngestBody): Promise<string> {
  const planId = body.planId?.trim() || null;
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
    return planId;
  }
  return (await ensureDefaultPlan()).id;
}

async function upsertReceiverFromIngest(
  receiverId: string,
  receiverName: string,
  planId: string | null,
  pinX: number | null,
  pinY: number | null,
) {
  await sql(
    `insert into receivers(id, name, plan_id, pin_x, pin_y, last_seen_at)
     values ($1, $2, $3, $4, $5, now())
     on conflict (id) do update set
       name = excluded.name,
       plan_id = coalesce(excluded.plan_id, receivers.plan_id),
       pin_x = coalesce(excluded.pin_x, receivers.pin_x),
       pin_y = coalesce(excluded.pin_y, receivers.pin_y),
       last_seen_at = now()`,
    [receiverId, receiverName, planId, pinX, pinY],
  );
}

async function insertSightingsFromBeacons(
  planId: string,
  receiverId: string,
  beacons: Array<Record<string, unknown>>,
) {
  for (const raw of beacons) {
    const uuid = String(raw.uuid ?? "").toUpperCase();
    const major = Number(raw.major);
    const minor = Number(raw.minor);
    const distanceMeters =
      raw.distanceMeters == null ? null : num(raw.distanceMeters);
    if (!uuid || !Number.isFinite(major) || !Number.isFinite(minor)) continue;
    if (distanceMeters == null || distanceMeters <= 0) continue;
    const label = String(raw.label ?? `Beacon ${major}/${minor}`);
    await sql(
      `insert into sightings(
         plan_id, receiver_id, beacon_key, label, uuid, major, minor, distance_meters, rssi
       ) values ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [
        planId,
        receiverId,
        beaconKey(uuid, major, minor),
        label,
        uuid,
        major,
        minor,
        distanceMeters,
        raw.rssi == null ? null : Number(raw.rssi),
      ],
    );
  }
}

export async function ingestPayload(body: IngestBody): Promise<{ ok: true; planId: string | null }> {
  const event = body.event ?? "beacon_scan";
  const receiverId = body.receiverId?.trim() || "anonymous";
  const receiverName = body.receiverName?.trim() || "Receiver";
  const pinX = typeof body.receiverPin?.x === "number" ? clamp01(body.receiverPin.x) : null;
  const pinY = typeof body.receiverPin?.y === "number" ? clamp01(body.receiverPin.y) : null;

  if (event === "beacon_scan") {
    const planId = body.planId?.trim()
      ? await ensurePlanFromBody(body)
      : (await listReceivers(null)).find((r) => r.id === receiverId)?.planId ??
        (await ensureDefaultPlan()).id;

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
    await upsertReceiverFromIngest(receiverId, receiverName, planId, pinX, pinY);
    await insertSightingsFromBeacons(planId, receiverId, body.beacons ?? []);
    await locateBeaconsFromSightings(planId);
    await broadcast(planId);
    return { ok: true, planId };
  }

  // beacon_location
  const planId = await ensurePlanFromBody(body);
  await upsertReceiverFromIngest(receiverId, receiverName, planId, pinX, pinY);
  await insertSightingsFromBeacons(planId, receiverId, body.beacons ?? []);

  for (const raw of body.beacons ?? []) {
    const uuid = String(raw.uuid ?? "").toUpperCase();
    const major = Number(raw.major);
    const minor = Number(raw.minor);
    if (!uuid || !Number.isFinite(major) || !Number.isFinite(minor)) continue;
    // Prefer server multilateration when we already have 2+ pinned receivers.
    // Still accept explicit x/y as a seed/fallback placement.
    if (raw.x == null || raw.y == null) continue;
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

  // Prefer multilateration from pinned receivers whenever enough sightings exist.
  await locateBeaconsFromSightings(planId);

  await sql(
    `insert into scan_snapshots(source, receiver_id, payload) values ($1, $2, $3::jsonb)`,
    [
      body.source ?? "beacon-simulator",
      receiverId,
      JSON.stringify({
        event,
        timestampMs: body.timestampMs ?? Date.now(),
        beacons: body.beacons ?? [],
      }),
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
  await sql(`delete from sightings where plan_id = $1`, [plan.id]);
  await sql(`delete from receivers where plan_id = $1`, [plan.id]);

  const uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0";

  await sql(
    `insert into receivers(id, name, plan_id, pin_x, pin_y, last_seen_at)
     values ($1,$2,$3,$4,$5,now()), ($6,$7,$3,$8,$9,now())
     on conflict (id) do update set
       name = excluded.name, plan_id = excluded.plan_id,
       pin_x = excluded.pin_x, pin_y = excluded.pin_y, last_seen_at = now()`,
    ["recv-a", "Phone A", plan.id, 0.22, 0.28, "recv-b", "Phone B", 0.74, 0.66],
  );

  // Distances from each pinned receiver to the moving transmitter (~center-left).
  await sql(
    `insert into sightings(plan_id, receiver_id, beacon_key, label, uuid, major, minor, distance_meters, rssi)
     values
       ($1,'recv-a',$2,'Transmitter 1',$3,1,1,6.2,-61),
       ($1,'recv-b',$2,'Transmitter 1',$3,1,1,9.4,-68)`,
    [plan.id, beaconKey(uuid, 1, 1), uuid],
  );

  await locateBeaconsFromSightings(plan.id);

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
    `insert into placed_beacons(id, plan_id, label, uuid, major, minor, x, y, tracked)
     values ($1,$2,'Anchor NE',$3,1,10,0.82,0.18,false),
            ($4,$2,'Anchor SW',$3,1,11,0.18,0.78,false)
     on conflict (id) do nothing`,
    [crypto.randomUUID(), plan.id, uuid, crypto.randomUUID()],
  );

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
            distanceMeters: 6.2,
            proximity: "Far",
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
