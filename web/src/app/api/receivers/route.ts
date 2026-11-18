import { assertIngestAuth } from "@/lib/auth";
import { upsertReceiverPin } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const unauthorized = await assertIngestAuth(request);
  if (unauthorized) return unauthorized;

  const body = (await request.json()) as {
    id?: string;
    name?: string;
    planId?: string;
    pinX?: number;
    pinY?: number;
  };

  if (!body.planId || body.pinX == null || body.pinY == null) {
    return Response.json(
      { error: "planId, pinX, and pinY are required" },
      { status: 400 },
    );
  }

  const receiver = await upsertReceiverPin({
    id: body.id,
    name: body.name ?? "Receiver",
    planId: body.planId,
    pinX: Number(body.pinX),
    pinY: Number(body.pinY),
  });

  return Response.json({ receiver });
}
