create table if not exists sightings (
  id bigserial primary key,
  plan_id text not null references floor_plans(id) on delete cascade,
  receiver_id text not null,
  beacon_key text not null,
  label text not null,
  uuid text not null,
  major integer not null,
  minor integer not null,
  distance_meters double precision not null,
  rssi integer,
  recorded_at timestamptz not null default now()
);

create index if not exists sightings_plan_beacon_time_idx
  on sightings(plan_id, beacon_key, recorded_at desc);

create index if not exists sightings_receiver_time_idx
  on sightings(receiver_id, recorded_at desc);
