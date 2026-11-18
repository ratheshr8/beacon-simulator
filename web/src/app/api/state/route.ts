import { getDashboardState, ensureDefaultPlan } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  await ensureDefaultPlan();
  const planId = new URL(request.url).searchParams.get("planId");
  const state = await getDashboardState(planId);
  return Response.json(state);
}
