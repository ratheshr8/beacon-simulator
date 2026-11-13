import { sql } from "./db";

const META_KEY = "ingest_token";
const LOCAL_DEFAULT = "dev-ingest-token";

export async function getIngestToken(): Promise<string> {
  const fromEnv = process.env.INGEST_TOKEN?.trim();
  if (fromEnv) return fromEnv;

  const existing = await sql<{ value: string }>(
    "select value from app_meta where key = $1",
    [META_KEY],
  );
  if (existing[0]?.value) return existing[0].value;

  await sql(
    "insert into app_meta(key, value) values ($1, $2) on conflict (key) do nothing",
    [META_KEY, LOCAL_DEFAULT],
  );
  return LOCAL_DEFAULT;
}

export async function assertIngestAuth(request: Request): Promise<Response | null> {
  const expected = await getIngestToken();
  const header =
    request.headers.get("x-ingest-token") ??
    request.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ??
    "";
  const queryToken = new URL(request.url).searchParams.get("token") ?? "";
  const provided = header.trim() || queryToken.trim();
  if (provided !== expected) {
    return Response.json(
      { error: "Unauthorized — send X-Ingest-Token or ?token=" },
      { status: 401 },
    );
  }
  return null;
}

export function isSampleMode(): boolean {
  return !process.env.DATABASE_URL?.trim();
}
