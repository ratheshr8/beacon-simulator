import { assertIngestAuth } from "@/lib/auth";
import { createFloorPlan, deleteFloorPlan, listFloorPlans, updateFloorPlan } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  return Response.json({ floorPlans: await listFloorPlans() });
}

export async function POST(request: Request) {
  const unauthorized = await assertIngestAuth(request);
  if (unauthorized) return unauthorized;

  const body = (await request.json()) as {
    name?: string;
    widthMeters?: number;
    imageDataUrl?: string | null;
    id?: string;
    action?: "update" | "delete" | "create";
  };

  if (body.action === "delete" && body.id) {
    await deleteFloorPlan(body.id);
    return Response.json({ ok: true });
  }

  if (body.action === "update" && body.id) {
    const plan = await updateFloorPlan(body.id, {
      name: body.name,
      widthMeters: body.widthMeters,
      imageDataUrl: body.imageDataUrl,
    });
    if (!plan) return Response.json({ error: "Not found" }, { status: 404 });
    return Response.json({ plan });
  }

  const plan = await createFloorPlan({
    name: body.name ?? "New floor",
    widthMeters: body.widthMeters ?? 20,
    imageDataUrl: body.imageDataUrl ?? null,
  });
  return Response.json({ plan });
}
