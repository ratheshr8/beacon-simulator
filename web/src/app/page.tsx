import { Dashboard } from "@/components/Dashboard";
import { ensureDefaultPlan, getDashboardState } from "@/lib/store";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  await ensureDefaultPlan();
  const initial = await getDashboardState();
  return <Dashboard initial={initial} />;
}
