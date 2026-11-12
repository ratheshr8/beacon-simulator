"use client";

import { useEffect, useMemo, useState, useTransition } from "react";
import type { DashboardState } from "@/lib/types";
import { FloorMap } from "./FloorMap";

type Props = {
  initial: DashboardState;
};

function beaconIdentityKey(uuid: string, major: number, minor: number) {
  return `${uuid.toUpperCase()}|${major}|${minor}`;
}

function firstTrackedKey(beacons: DashboardState["beacons"]): string | null {
  const tracked = beacons.find((b) => b.tracked) ?? beacons[0];
  return tracked
    ? beaconIdentityKey(tracked.uuid, tracked.major, tracked.minor)
    : null;
}

export function Dashboard({ initial }: Props) {
  const [state, setState] = useState(initial);
  const [selectedBeaconKey, setSelectedBeaconKey] = useState<string | null>(
    () => firstTrackedKey(initial.beacons),
  );
  const [planName, setPlanName] = useState("");
  const [widthMeters, setWidthMeters] = useState("24");
  const [status, setStatus] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const token = state.ingestHint;

  useEffect(() => {
    const planId = state.selectedPlanId ? `?planId=${encodeURIComponent(state.selectedPlanId)}` : "";
    const es = new EventSource(`/api/stream${planId}`);
    es.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as { type?: string; state?: DashboardState };
        if (payload.type === "state" && payload.state) {
          setState(payload.state);
          setSelectedBeaconKey((current) => current ?? firstTrackedKey(payload.state!.beacons));
        }
      } catch {
        // ignore malformed frames
      }
    };
    return () => es.close();
  }, [state.selectedPlanId]);

  const selectedPath = useMemo(
    () => (selectedBeaconKey ? state.paths[selectedBeaconKey] ?? [] : []),
    [selectedBeaconKey, state.paths],
  );
  const selectedBeacon = state.beacons.find(
    (b) => beaconIdentityKey(b.uuid, b.major, b.minor) === selectedBeaconKey,
  );
  const totalPathPoints = useMemo(
    () => Object.values(state.paths).reduce((sum, pts) => sum + pts.length, 0),
    [state.paths],
  );
  const mappedReceivers = useMemo(
    () => state.receivers.filter((r) => r.pinX != null && r.pinY != null),
    [state.receivers],
  );

  const ingestUrl =
    typeof window !== "undefined"
      ? `${window.location.origin}/api/ingest?token=${encodeURIComponent(token)}`
      : `/api/ingest?token=${token}`;

  async function authed(url: string, init?: RequestInit) {
    return fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        "X-Ingest-Token": token,
        ...(init?.headers ?? {}),
      },
    });
  }

  function createPlan() {
    startTransition(async () => {
      const res = await authed("/api/floor-plans", {
        method: "POST",
        body: JSON.stringify({
          name: planName || "New floor",
          widthMeters: Number(widthMeters) || 20,
        }),
      });
      if (!res.ok) {
        setStatus("Could not create floor plan");
        return;
      }
      const data = (await res.json()) as { plan: { id: string } };
      setPlanName("");
      setStatus("Floor plan created");
      const next = await fetch(`/api/state?planId=${data.plan.id}`).then((r) => r.json());
      setState(next);
    });
  }

  function onImageSelected(file: File | null) {
    if (!file || !state.selectedPlanId) return;
    const reader = new FileReader();
    reader.onload = () => {
      startTransition(async () => {
        await authed("/api/floor-plans", {
          method: "POST",
          body: JSON.stringify({
            action: "update",
            id: state.selectedPlanId,
            imageDataUrl: String(reader.result),
          }),
        });
        setStatus("Floor image updated");
      });
    };
    reader.readAsDataURL(file);
  }

  function loadDemo() {
    startTransition(async () => {
      const next = await fetch("/api/demo", { method: "POST" }).then((r) => r.json());
      setState(next);
      setStatus("Sample tracking loaded");
    });
  }

  function clearSelectedPath() {
    if (!state.selectedPlanId || !selectedBeaconKey) return;
    startTransition(async () => {
      await authed("/api/path", {
        method: "DELETE",
        body: JSON.stringify({
          planId: state.selectedPlanId,
          beaconKey: selectedBeaconKey,
        }),
      });
      setStatus("Path cleared");
    });
  }

  const pathDistance = useMemo(() => {
    if (selectedPath.length < 2 || !state.selectedPlanId) return 0;
    const plan = state.floorPlans.find((p) => p.id === state.selectedPlanId);
    const width = plan?.widthMeters ?? 20;
    let sum = 0;
    for (let i = 1; i < selectedPath.length; i++) {
      const a = selectedPath[i - 1]!;
      const b = selectedPath[i]!;
      sum += Math.hypot((b.x - a.x) * width, (b.y - a.y) * width);
    }
    return sum;
  }, [selectedPath, state.floorPlans, state.selectedPlanId]);

  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <p className="brand">Floorcast</p>
          <h1>Live beacon floor tracking</h1>
        </div>
        <div className="top-actions">
          <button type="button" className="ghost" onClick={loadDemo} disabled={pending}>
            Load sample path
          </button>
          {state.sampleMode && <span className="chip">Sample data mode</span>}
        </div>
      </header>

      <section className="hero-strip">
        <p>
          Point each Android receiver&apos;s webhook at this ingest URL to plot live positions and
          movement trails on your floor plan.
        </p>
        <code className="ingest">{ingestUrl}</code>
      </section>

      <div className="layout">
        <main className="stage">
          <div className="stage-toolbar">
            <label>
              Floor plan
              <select
                value={state.selectedPlanId ?? ""}
                onChange={(e) => {
                  const id = e.target.value;
                  startTransition(async () => {
                    const next = await fetch(`/api/state?planId=${encodeURIComponent(id)}`).then(
                      (r) => r.json(),
                    );
                    setState(next);
                  });
                }}
              >
                {state.floorPlans.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.widthMeters}m)
                  </option>
                ))}
              </select>
            </label>
            <label className="file">
              Upload floor image
              <input
                type="file"
                accept="image/*"
                onChange={(e) => onImageSelected(e.target.files?.[0] ?? null)}
              />
            </label>
            <div className="stats">
              <div>
                <strong>{state.beacons.length}</strong>
                <span>beacons</span>
              </div>
              <div>
                <strong>{mappedReceivers.length}</strong>
                <span>receivers</span>
              </div>
              <div>
                <strong>{totalPathPoints}</strong>
                <span>path points</span>
              </div>
            </div>
          </div>

          <FloorMap
            state={state}
            selectedBeaconKey={selectedBeaconKey}
            onSelectBeacon={setSelectedBeaconKey}
          />
        </main>

        <aside className="side">
          <section>
            <h2>Selected track</h2>
            {selectedBeacon ? (
              <div className="card-plain">
                <p className="title">{selectedBeacon.label}</p>
                <p className="mono">
                  {selectedBeacon.uuid.slice(0, 8)}… · {selectedBeacon.major}/{selectedBeacon.minor}
                </p>
                <p>
                  Position{" "}
                  <span className="mono">
                    {selectedBeacon.x.toFixed(3)}, {selectedBeacon.y.toFixed(3)}
                  </span>
                </p>
                <p>
                  Path length <strong>{pathDistance.toFixed(1)} m</strong>
                </p>
                <button type="button" className="ghost" onClick={clearSelectedPath} disabled={pending}>
                  Clear path
                </button>
              </div>
            ) : (
              <p className="muted">No tracked beacon yet.</p>
            )}
          </section>

          <section>
            <h2>Beacons</h2>
            <ul className="list">
              {state.beacons.map((b) => {
                const key = beaconIdentityKey(b.uuid, b.major, b.minor);
                const points = state.paths[key]?.length ?? 0;
                return (
                  <li key={b.id}>
                    <button
                      type="button"
                      className={selectedBeaconKey === key ? "row active" : "row"}
                      onClick={() => setSelectedBeaconKey(key)}
                    >
                      <span>
                        <strong>{b.label}</strong>
                        <em>{b.tracked ? "live" : "fixed"}</em>
                      </span>
                      <span className="mono">{points} pts</span>
                    </button>
                  </li>
                );
              })}
              {!state.beacons.length && <li className="muted">None on this plan</li>}
            </ul>
          </section>

          <section>
            <h2>Latest scans</h2>
            <ul className="list scans">
              {state.latestScans.map((s) => (
                <li key={`${s.uuid}-${s.major}-${s.minor}-${s.deviceAddress}`}>
                  <strong>
                    {s.major}/{s.minor}
                  </strong>
                  <span>{s.distanceMeters?.toFixed(2) ?? "—"} m</span>
                  <span>{s.rssi} dBm</span>
                  <span>{s.proximity ?? "—"}</span>
                </li>
              ))}
              {!state.latestScans.length && <li className="muted">No scan webhook yet</li>}
            </ul>
          </section>

          <section>
            <h2>New floor plan</h2>
            <div className="form">
              <input
                value={planName}
                onChange={(e) => setPlanName(e.target.value)}
                placeholder="Name"
              />
              <input
                value={widthMeters}
                onChange={(e) => setWidthMeters(e.target.value)}
                placeholder="Width (m)"
                inputMode="decimal"
              />
              <button type="button" onClick={createPlan} disabled={pending}>
                Create
              </button>
            </div>
          </section>

          <section>
            <h2>Android setup</h2>
            <ol className="steps">
              <li>Install Beacon Simulator v1.5+ on receiver phones.</li>
              <li>Receiver → expand filters → paste the ingest URL above.</li>
              <li>Enable Forward to URL while scanning.</li>
              <li>Floor plan → pin each phone → enable live-move + peer share.</li>
              <li>Location posts appear here with movement trails.</li>
            </ol>
          </section>

          {status && <p className="status">{status}</p>}
        </aside>
      </div>
    </div>
  );
}
