create table if not exists floor_plans (
  id text primary key,
  name text not null,
  width_meters double precision not null default 20,
  image_data_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists placed_beacons (
  id text primary key,
  plan_id text not null references floor_plans(id) on delete cascade,
  label text not null,
  uuid text not null,
  major integer not null,
  minor integer not null,
  x double precision not null,
  y double precision not null,
  tracked boolean not null default false,
  updated_at timestamptz not null default now()
);

create index if not exists placed_beacons_plan_idx on placed_beacons(plan_id);
create index if not exists placed_beacons_identity_idx on placed_beacons(uuid, major, minor);

create table if not exists receivers (
  id text primary key,
  name text not null,
  plan_id text references floor_plans(id) on delete set null,
  pin_x double precision,
  pin_y double precision,
  last_seen_at timestamptz not null default now()
);

create table if not exists track_points (
  id bigserial primary key,
  plan_id text not null references floor_plans(id) on delete cascade,
  beacon_key text not null,
  label text not null,
  x double precision not null,
  y double precision not null,
  distance_meters double precision,
  rssi integer,
  confidence double precision,
  recorded_at timestamptz not null default now()
);

create index if not exists track_points_beacon_time_idx
  on track_points(plan_id, beacon_key, recorded_at desc);

create table if not exists scan_snapshots (
  id bigserial primary key,
  source text not null default 'beacon-simulator',
  receiver_id text,
  payload jsonb not null,
  received_at timestamptz not null default now()
);

create table if not exists app_meta (
  key text primary key,
  value text not null
);
