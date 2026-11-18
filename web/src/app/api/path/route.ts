import { assertIngestAuth } from "@/lib/auth";
import { clearPath } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function DELETE(request: Request) {
  const unauthorized = await assertIngestAuth(request);
  if (unauthorized) return unauthorized;
  const { planId, beaconKey } = (await request.json()) as {
    planId?: string;
    beaconKey?: string;
  };
  if (!planId || !beaconKey) {
    return Response.json({ error: "planId and beaconKey required" }, { status: 400 });
  }
  await clearPath(planId, beaconKey);
  return Response.json({ ok: true });
}
