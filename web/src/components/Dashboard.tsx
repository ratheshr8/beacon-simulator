"use client";

import { useEffect, useMemo, useState, useTransition } from "react";
import type { DashboardState, MapTool } from "@/lib/types";
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
  const [selectedReceiverId, setSelectedReceiverId] = useState<string | null>(
    () => initial.receivers[0]?.id ?? null,
  );
  const [tool, setTool] = useState<MapTool>("pin");
  const [planName, setPlanName] = useState("");
  const [widthMeters, setWidthMeters] = useState("24");
  const [receiverNameDraft, setReceiverNameDraft] = useState("Receiver 1");
  const [distanceDraft, setDistanceDraft] = useState("5");
  const [beaconMajor, setBeaconMajor] = useState("1");
  const [beaconMinor, setBeaconMinor] = useState("1");
  const [beaconLabel, setBeaconLabel] = useState("Transmitter 1");
  const [status, setStatus] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const token = state.ingestHint;

  useEffect(() => {
    const planId = state.selectedPlanId
      ? `?planId=${encodeURIComponent(state.selectedPlanId)}`
      : "";
    const es = new EventSource(`/api/stream${planId}`);
    es.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as {
          type?: string;
          state?: DashboardState;
        };
        if (payload.type === "state" && payload.state) {
          setState(payload.state);
          setSelectedBeaconKey(
            (current) => current ?? firstTrackedKey(payload.state!.beacons),
          );
          setSelectedReceiverId(
            (current) => current ?? payload.state!.receivers[0]?.id ?? null,
          );
        }
      } catch {
        // ignore malformed frames
      }
    };
    return () => es.close();
  }, [state.selectedPlanId]);

  const selectedPath = useMemo(
    () => (selectedBeaconKey ? (state.paths[selectedBeaconKey] ?? []) : []),
    [selectedBeaconKey, state.paths],
  );
  const selectedBeacon = state.beacons.find(
    (b) =>
      beaconIdentityKey(b.uuid, b.major, b.minor) === selectedBeaconKey,
  );
  const selectedFix = state.locateFixes.find(
    (f) => f.beaconKey === selectedBeaconKey,
  );
  const totalPathPoints = useMemo(
    () => Object.values(state.paths).reduce((sum, pts) => sum + pts.length, 0),
    [state.paths],
  );
  const mappedReceivers = useMemo(
    () => state.receivers.filter((r) => r.pinX != null && r.pinY != null),
    [state.receivers],
  );
  const selectedReceiver =
    state.receivers.find((r) => r.id === selectedReceiverId) ?? null;

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
      setStatus("Floor plan created — pin receivers next");
      setTool("pin");
      const next = await fetch(`/api/state?planId=${data.plan.id}`).then((r) =>
        r.json(),
      );
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
      const next = await fetch("/api/demo", { method: "POST" }).then((r) =>
        r.json(),
      );
      setState(next);
      setSelectedReceiverId(next.receivers?.[0]?.id ?? null);
      setSelectedBeaconKey(firstTrackedKey(next.beacons ?? []));
      setTool("locate");
      setStatus("Sample tracking loaded — receivers pinned, beacon located");
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

  function onMapClick(x: number, y: number) {
    if (tool !== "pin" || !state.selectedPlanId) return;
    startTransition(async () => {
      const res = await authed("/api/receivers", {
        method: "POST",
        body: JSON.stringify({
          id: selectedReceiverId ?? undefined,
          name: selectedReceiver?.name ?? receiverNameDraft,
          planId: state.selectedPlanId,
          pinX: x,
          pinY: y,
        }),
      });
      if (!res.ok) {
        setStatus("Could not pin receiver");
        return;
      }
      const data = (await res.json()) as { receiver: { id: string } };
      setSelectedReceiverId(data.receiver.id);
      const next = await fetch(
        `/api/state?planId=${encodeURIComponent(state.selectedPlanId!)}`,
      ).then((r) => r.json());
      setState(next);
      setStatus(
        `Pinned ${selectedReceiver?.name ?? receiverNameDraft} — pin more, then switch to Locate`,
      );
    });
  }

  function addReceiver() {
    if (!state.selectedPlanId) return;
    startTransition(async () => {
      const res = await authed("/api/receivers", {
        method: "POST",
        body: JSON.stringify({
          name: receiverNameDraft || `Receiver ${state.receivers.length + 1}`,
          planId: state.selectedPlanId,
          pinX: 0.5,
          pinY: 0.5,
        }),
      });
      const data = (await res.json()) as { receiver: { id: string } };
      setSelectedReceiverId(data.receiver.id);
      setTool("pin");
      const next = await fetch(
        `/api/state?planId=${encodeURIComponent(state.selectedPlanId!)}`,
      ).then((r) => r.json());
      setState(next);
      setStatus("Receiver added — click the map to place its pin");
    });
  }

  function submitDistanceFromReceiver() {
    if (!state.selectedPlanId || !selectedReceiverId) {
      setStatus("Select a pinned receiver first");
      return;
    }
    const meters = Number(distanceDraft);
    if (!Number.isFinite(meters) || meters <= 0) {
      setStatus("Enter a positive distance in meters");
      return;
    }
    startTransition(async () => {
      const res = await authed("/api/locate", {
        method: "POST",
        body: JSON.stringify({
          action: "sighting",
          planId: state.selectedPlanId,
          receiverId: selectedReceiverId,
          uuid: "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
          major: Number(beaconMajor) || 1,
          minor: Number(beaconMinor) || 1,
          label: beaconLabel || "Transmitter",
          distanceMeters: meters,
        }),
      });
      if (!res.ok) {
        setStatus("Could not record distance");
        return;
      }
      const next = (await res.json()) as DashboardState;
      setState(next);
      setSelectedBeaconKey(
        beaconIdentityKey(
          "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
          Number(beaconMajor) || 1,
          Number(beaconMinor) || 1,
        ),
      );
      setTool("locate");
      setStatus(
        mappedReceivers.length < 2
          ? "Distance saved — pin another receiver and enter its distance for a better fix"
          : "Beacon located from receiver distances",
      );
    });
  }

  function recomputeLocate() {
    if (!state.selectedPlanId) return;
    startTransition(async () => {
      const res = await authed("/api/locate", {
        method: "POST",
        body: JSON.stringify({
          action: "recompute",
          planId: state.selectedPlanId,
        }),
      });
      const data = (await res.json()) as { state: DashboardState };
      setState(data.state);
      setTool("locate");
      setStatus("Recomputed beacon positions from receiver distances");
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

  const routeSegments = useMemo(() => {
    if (selectedPath.length < 2 || !state.selectedPlanId) return [];
    const plan = state.floorPlans.find((p) => p.id === state.selectedPlanId);
    const width = plan?.widthMeters ?? 20;
    const segs: Array<{ from: string; to: string; meters: number; at: string }> =
      [];
    for (let i = 1; i < selectedPath.length; i++) {
      const a = selectedPath[i - 1]!;
      const b = selectedPath[i]!;
      segs.push({
        from: `${a.x.toFixed(2)},${a.y.toFixed(2)}`,
        to: `${b.x.toFixed(2)},${b.y.toFixed(2)}`,
        meters: Math.hypot((b.x - a.x) * width, (b.y - a.y) * width),
        at: new Date(b.recordedAt).toLocaleTimeString(),
      });
    }
    return segs.slice(-8).reverse();
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
          Workflow: <strong>1) Pin each receiver</strong> on the floor →{" "}
          <strong>2) Locate</strong> the beacon from each receiver&apos;s distance →
          watch the route update live.
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
                    const next = await fetch(
                      `/api/state?planId=${encodeURIComponent(id)}`,
                    ).then((r) => r.json());
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
            <div className="tool-toggle" role="group" aria-label="Map tool">
              {(
                [
                  ["pin", "1. Pin receivers"],
                  ["locate", "2. Locate beacons"],
                  ["view", "View"],
                ] as const
              ).map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  className={tool === id ? "tool active" : "tool"}
                  onClick={() => setTool(id)}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="stats">
              <div>
                <strong>{state.beacons.length}</strong>
                <span>beacons</span>
              </div>
              <div>
                <strong>{mappedReceivers.length}</strong>
                <span>pinned</span>
              </div>
              <div>
                <strong>{totalPathPoints}</strong>
                <span>path pts</span>
              </div>
            </div>
          </div>

          <FloorMap
            state={state}
            selectedBeaconKey={selectedBeaconKey}
            selectedReceiverId={selectedReceiverId}
            tool={tool}
            onSelectBeacon={setSelectedBeaconKey}
            onSelectReceiver={setSelectedReceiverId}
            onMapClick={onMapClick}
          />
        </main>

        <aside className="side">
          <section>
            <h2>1. Pin receivers</h2>
            <div className="form">
              <input
                value={receiverNameDraft}
                onChange={(e) => setReceiverNameDraft(e.target.value)}
                placeholder="Receiver name"
              />
              <button type="button" onClick={addReceiver} disabled={pending}>
                Add receiver
              </button>
            </div>
            <ul className="list">
              {state.receivers.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    className={
                      selectedReceiverId === r.id ? "row active" : "row"
                    }
                    onClick={() => {
                      setSelectedReceiverId(r.id);
                      setTool("pin");
                    }}
                  >
                    <span>
                      <strong>{r.name}</strong>
                      <em>
                        {r.pinX != null
                          ? `${r.pinX.toFixed(2)}, ${r.pinY!.toFixed(2)}`
                          : "not pinned"}
                      </em>
                    </span>
                  </button>
                </li>
              ))}
              {!state.receivers.length && (
                <li className="muted">Add a receiver, then click the map</li>
              )}
            </ul>
          </section>

          <section>
            <h2>2. Locate from receiver</h2>
            <p className="muted small">
              Enter the measured distance from the selected pinned receiver to the
              beacon. With 2+ receivers, Floorcast multilaterates the position.
            </p>
            <div className="form">
              <input
                value={beaconLabel}
                onChange={(e) => setBeaconLabel(e.target.value)}
                placeholder="Beacon label"
              />
              <div className="row-inputs">
                <input
                  value={beaconMajor}
                  onChange={(e) => setBeaconMajor(e.target.value)}
                  placeholder="Major"
                  inputMode="numeric"
                />
                <input
                  value={beaconMinor}
                  onChange={(e) => setBeaconMinor(e.target.value)}
                  placeholder="Minor"
                  inputMode="numeric"
                />
              </div>
              <input
                value={distanceDraft}
                onChange={(e) => setDistanceDraft(e.target.value)}
                placeholder="Distance (m)"
                inputMode="decimal"
              />
              <button
                type="button"
                onClick={submitDistanceFromReceiver}
                disabled={pending || !selectedReceiverId}
              >
                Save distance from{" "}
                {selectedReceiver?.name ?? "receiver"}
              </button>
              <button
                type="button"
                className="ghost"
                onClick={recomputeLocate}
                disabled={pending}
              >
                Recompute locations
              </button>
            </div>
            {selectedFix && (
              <p className="status">
                Fix uses {selectedFix.usedReceiverCount} receiver
                {selectedFix.usedReceiverCount === 1 ? "" : "s"} · confidence{" "}
                {(selectedFix.confidence * 100).toFixed(0)}%
              </p>
            )}
          </section>

          <section>
            <h2>Route details</h2>
            {selectedBeacon ? (
              <div className="card-plain">
                <p className="title">{selectedBeacon.label}</p>
                <p className="mono">
                  {selectedBeacon.uuid.slice(0, 8)}… · {selectedBeacon.major}/
                  {selectedBeacon.minor}
                </p>
                <p>
                  Position{" "}
                  <span className="mono">
                    {selectedBeacon.x.toFixed(3)}, {selectedBeacon.y.toFixed(3)}
                  </span>
                </p>
                <p>
                  Path length <strong>{pathDistance.toFixed(1)} m</strong> ·{" "}
                  {selectedPath.length} points
                </p>
                {selectedPath.length > 0 && (
                  <p className="muted small">
                    {new Date(selectedPath[0]!.recordedAt).toLocaleTimeString()} →{" "}
                    {new Date(
                      selectedPath[selectedPath.length - 1]!.recordedAt,
                    ).toLocaleTimeString()}
                  </p>
                )}
                <h3 className="subhead">From each receiver</h3>
                <ul className="list compact">
                  {(selectedFix?.sightings ??
                    state.sightings.filter(
                      (s) => s.beaconKey === selectedBeaconKey,
                    )).map((s) => (
                    <li key={`${s.receiverId}-${s.beaconKey}`}>
                      <span>
                        <strong>{s.receiverName}</strong>
                        <em className="mono">{s.distanceMeters.toFixed(2)} m</em>
                      </span>
                      <span className="mono muted">
                        {s.rssi != null ? `${s.rssi} dBm` : "—"}
                      </span>
                    </li>
                  ))}
                  {!selectedFix?.sightings?.length &&
                    !state.sightings.some(
                      (s) => s.beaconKey === selectedBeaconKey,
                    ) && (
                      <li className="muted">No receiver distances yet</li>
                    )}
                </ul>
                <h3 className="subhead">Recent segments</h3>
                <ul className="list compact">
                  {routeSegments.map((seg, i) => (
                    <li key={`${seg.at}-${i}`}>
                      <span className="mono">
                        {seg.from} → {seg.to}
                      </span>
                      <span>
                        {seg.meters.toFixed(1)} m · {seg.at}
                      </span>
                    </li>
                  ))}
                  {!routeSegments.length && (
                    <li className="muted">Path builds as the beacon moves</li>
                  )}
                </ul>
                <button
                  type="button"
                  className="ghost"
                  onClick={clearSelectedPath}
                  disabled={pending}
                >
                  Clear path
                </button>
              </div>
            ) : (
              <p className="muted">
                Locate a beacon to see route length, receiver distances, and
                segments.
              </p>
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
                      onClick={() => {
                        setSelectedBeaconKey(key);
                        setTool("locate");
                      }}
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
              {!state.beacons.length && (
                <li className="muted">None located yet</li>
              )}
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
              <li>Install Beacon Simulator v1.5.1+ on each receiver phone.</li>
              <li>Paste the ingest URL → enable Forward while scanning.</li>
              <li>
                On Floorcast (or the phone): <strong>pin each receiver</strong>{" "}
                first.
              </li>
              <li>
                Each phone reports distance; Floorcast locates the beacon from
                those pins.
              </li>
              <li>Route details update as the transmitter moves.</li>
            </ol>
          </section>

          {status && <p className="status">{status}</p>}
        </aside>
      </div>
    </div>
  );
}
