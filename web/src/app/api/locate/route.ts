import { assertIngestAuth } from "@/lib/auth";
import {
  getDashboardState,
  locateBeaconsFromSightings,
  recordManualSighting,
} from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const unauthorized = await assertIngestAuth(request);
  if (unauthorized) return unauthorized;

  const body = (await request.json()) as {
    action?: "recompute" | "sighting";
    planId?: string;
    receiverId?: string;
    uuid?: string;
    major?: number;
    minor?: number;
    label?: string;
    distanceMeters?: number;
    rssi?: number | null;
  };

  if (!body.planId) {
    return Response.json({ error: "planId required" }, { status: 400 });
  }

  if (body.action === "sighting") {
    if (
      !body.receiverId ||
      !body.uuid ||
      body.major == null ||
      body.minor == null ||
      body.distanceMeters == null
    ) {
      return Response.json(
        { error: "receiverId, uuid, major, minor, distanceMeters required" },
        { status: 400 },
      );
    }
    const state = await recordManualSighting({
      planId: body.planId,
      receiverId: body.receiverId,
      uuid: body.uuid,
      major: Number(body.major),
      minor: Number(body.minor),
      label: body.label,
      distanceMeters: Number(body.distanceMeters),
      rssi: body.rssi ?? null,
    });
    return Response.json(state);
  }

  const fixes = await locateBeaconsFromSightings(body.planId);
  const state = await getDashboardState(body.planId);
  return Response.json({ fixes, state });
}
