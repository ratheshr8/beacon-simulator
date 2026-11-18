# Floorcast

Next.js web app that receives Beacon Simulator webhooks and plots **live floor tracking** with movement paths.

## Run locally

```bash
cp .env.example .env.local   # optional; see comments in the file
npm install
npm run dev
```

Open the app, click **Load sample path** to preview, or paste the shown ingest URL into Android Receiver → Forward URL.

Env vars are documented in [`.env.example`](./.env.example) (`INGEST_TOKEN`, optional `DATABASE_URL`).

## Ingest API

`POST /api/ingest` (auth: `X-Ingest-Token`, `Authorization: Bearer`, or `?token=`)

Accepted events:

- `beacon_scan` — same shape as the Android scan webhook
- `beacon_location` — floor coordinates (`x`/`y` 0..1), receiver pin, plan metadata

Live UI updates over `GET /api/stream` (SSE).

## Features

- **Pin receivers first**, then locate each beacon from per-receiver distances
- Multilateration on the server from pinned receivers + sightings
- Range rings in Locate mode
- Floor plan create / select / image upload
- Live beacon markers (fixed + tracked transmitter)
- Movement path / route details (length, segments, per-receiver distances)
- Latest RSSI/distance snapshot
- Optional Postgres via `cursor-app.json` + `DATABASE_URL` (local uses PGlite)
