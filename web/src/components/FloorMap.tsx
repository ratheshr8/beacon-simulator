"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { DashboardState, PlacedBeacon, TrackPoint } from "@/lib/types";

const PATH_COLORS = ["#f4b942", "#3ecf8e", "#5bb8ff", "#ff7a59", "#c084fc"];

type Props = {
  state: DashboardState;
  selectedBeaconKey: string | null;
  onSelectBeacon: (key: string) => void;
};

function keyOf(b: PlacedBeacon) {
  return `${b.uuid.toUpperCase()}|${b.major}|${b.minor}`;
}

export function FloorMap({ state, selectedBeaconKey, onSelectBeacon }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 800, h: 520 });
  const plan = state.floorPlans.find((p) => p.id === state.selectedPlanId) ?? null;

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => {
      const { width } = entry.contentRect;
      setSize({ w: Math.max(320, width), h: Math.max(280, Math.round(width * 0.62)) });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const pathEntries = useMemo(() => Object.entries(state.paths), [state.paths]);

  return (
    <div ref={wrapRef} className="map-shell">
      <svg
        viewBox={`0 0 ${size.w} ${size.h}`}
        width="100%"
        height={size.h}
        role="img"
        aria-label="Floor plan tracking map"
        className="map-svg"
      >
        <defs>
          <pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse">
            <path d="M 32 0 L 0 0 0 32" fill="none" stroke="rgba(232,224,208,0.08)" strokeWidth="1" />
          </pattern>
          <linearGradient id="trailFade" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#f4b942" stopOpacity="0.15" />
            <stop offset="100%" stopColor="#f4b942" stopOpacity="0.95" />
          </linearGradient>
          <filter id="softGlow" x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="2.5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <rect width={size.w} height={size.h} fill="#12160f" />
        {plan?.imageDataUrl ? (
          <image
            href={plan.imageDataUrl}
            x={0}
            y={0}
            width={size.w}
            height={size.h}
            preserveAspectRatio="xMidYMid slice"
            opacity={0.88}
          />
        ) : (
          <>
            <rect width={size.w} height={size.h} fill="url(#grid)" />
            <rect
              x={size.w * 0.08}
              y={size.h * 0.1}
              width={size.w * 0.84}
              height={size.h * 0.78}
              fill="none"
              stroke="rgba(232,224,208,0.18)"
              strokeWidth={2}
              strokeDasharray="8 10"
            />
          </>
        )}

        {pathEntries.map(([key, points], idx) => (
          <PathTrail
            key={key}
            points={points}
            width={size.w}
            height={size.h}
            color={PATH_COLORS[idx % PATH_COLORS.length]!}
            active={selectedBeaconKey === key}
          />
        ))}

        {state.receivers
          .filter((r) => r.pinX != null && r.pinY != null)
          .map((r) => {
            const cx = (r.pinX as number) * size.w;
            const cy = (r.pinY as number) * size.h;
            return (
              <g key={r.id} className="receiver-pin">
                <circle cx={cx} cy={cy} r={10} fill="none" stroke="#5bb8ff" strokeWidth={2} opacity={0.9}>
                  <animate attributeName="r" values="8;14;8" dur="2.4s" repeatCount="indefinite" />
                  <animate attributeName="opacity" values="0.9;0.25;0.9" dur="2.4s" repeatCount="indefinite" />
                </circle>
                <circle cx={cx} cy={cy} r={5} fill="#5bb8ff" />
                <text x={cx + 10} y={cy - 10} className="map-label">
                  {r.name}
                </text>
              </g>
            );
          })}

        {state.beacons.map((b) => {
          const key = keyOf(b);
          const cx = b.x * size.w;
          const cy = b.y * size.h;
          const selected = selectedBeaconKey === key;
          return (
            <g
              key={b.id}
              className={b.tracked ? "beacon tracked" : "beacon fixed"}
              style={{ cursor: "pointer" }}
              onClick={() => onSelectBeacon(key)}
            >
              {b.tracked ? (
                <>
                  <circle cx={cx} cy={cy} r={selected ? 18 : 14} fill="#f4b942" opacity={0.18} filter="url(#softGlow)">
                    <animate attributeName="r" values="12;20;12" dur="1.8s" repeatCount="indefinite" />
                  </circle>
                  <path
                    d={`M ${cx} ${cy - 9} L ${cx + 8} ${cy + 6} L ${cx - 8} ${cy + 6} Z`}
                    fill="#f4b942"
                    stroke="#1a1408"
                    strokeWidth={1.5}
                  />
                </>
              ) : (
                <>
                  <rect
                    x={cx - 7}
                    y={cy - 7}
                    width={14}
                    height={14}
                    rx={2}
                    fill="#3ecf8e"
                    stroke="#0d1a14"
                    strokeWidth={1.5}
                  />
                </>
              )}
              <text x={cx + 12} y={cy + 4} className="map-label">
                {b.label}
              </text>
            </g>
          );
        })}

        {!state.beacons.length && (
          <text
            x={size.w / 2}
            y={size.h / 2}
            textAnchor="middle"
            className="map-empty"
          >
            Waiting for beacon locations…
          </text>
        )}
      </svg>
      <div className="map-legend">
        <span><i className="swatch tracked" /> Live transmitter</span>
        <span><i className="swatch fixed" /> Fixed beacon</span>
        <span><i className="swatch receiver" /> Receiver pin</span>
        <span><i className="swatch path" /> Movement path</span>
      </div>
    </div>
  );
}

function PathTrail({
  points,
  width,
  height,
  color,
  active,
}: {
  points: TrackPoint[];
  width: number;
  height: number;
  color: string;
  active: boolean;
}) {
  if (points.length < 2) return null;
  const d = points
    .map((p, i) => `${i === 0 ? "M" : "L"} ${p.x * width} ${p.y * height}`)
    .join(" ");
  const last = points[points.length - 1]!;
  return (
    <g opacity={active ? 1 : 0.72}>
      <path
        d={d}
        fill="none"
        stroke={color}
        strokeWidth={active ? 3.5 : 2.25}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="path-line"
      />
      {points.map((p, i) =>
        i % Math.max(1, Math.floor(points.length / 12)) === 0 ? (
          <circle
            key={p.id}
            cx={p.x * width}
            cy={p.y * height}
            r={2.2}
            fill={color}
            opacity={0.55}
          />
        ) : null,
      )}
      <circle cx={last.x * width} cy={last.y * height} r={4} fill={color} />
    </g>
  );
}
