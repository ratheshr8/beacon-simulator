import { assertIngestAuth } from "@/lib/auth";
import { ingestPayload } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const unauthorized = await assertIngestAuth(request);
  if (unauthorized) return unauthorized;

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  if (!body || typeof body !== "object") {
    return Response.json({ error: "Expected JSON object" }, { status: 400 });
  }

  try {
    const result = await ingestPayload(body as Parameters<typeof ingestPayload>[0]);
    return Response.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Ingest failed";
    return Response.json({ error: message }, { status: 500 });
  }
}

export async function GET() {
  return Response.json({
    ok: true,
    endpoint: "/api/ingest",
    acceptedEvents: ["beacon_scan", "beacon_location"],
    auth: "X-Ingest-Token header, Authorization: Bearer, or ?token=",
  });
}
