import { seedDemo } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST() {
  const state = await seedDemo();
  return Response.json(state);
}
