export type Anchor = {
  x: number;
  y: number;
  /** Distance in the same coordinate space as x/y (normalized plan units). */
  radius: number;
};

export type Point = { x: number; y: number };

function distance(x1: number, y1: number, x2: number, y2: number): number {
  return Math.hypot(x1 - x2, y1 - y2);
}

function estimateTwo(a: Anchor, b: Anchor): Point {
  const d = distance(a.x, a.y, b.x, b.y);
  if (d < 1e-4) return { x: a.x, y: a.y };
  const r0 = a.radius;
  const r1 = b.radius;
  let along = (r0 ** 2 - r1 ** 2 + d ** 2) / (2 * d);
  along = Math.min(d, Math.max(0, along));
  return {
    x: Math.min(1, Math.max(0, a.x + (along * (b.x - a.x)) / d)),
    y: Math.min(1, Math.max(0, a.y + (along * (b.y - a.y)) / d)),
  };
}

/** Damped least-squares multilateration; falls back to two-circle line estimate. */
export function estimate(anchors: Anchor[]): Point | null {
  if (anchors.length < 2) return null;
  if (anchors.length === 2) return estimateTwo(anchors[0]!, anchors[1]!);

  let x = anchors.reduce((s, a) => s + a.x, 0) / anchors.length;
  let y = anchors.reduce((s, a) => s + a.y, 0) / anchors.length;

  for (let i = 0; i < 12; i++) {
    let dx = 0;
    let dy = 0;
    for (const a of anchors) {
      const dist = Math.max(1e-4, distance(x, y, a.x, a.y));
      const err = dist - a.radius;
      dx += ((a.x - x) / dist) * err;
      dy += ((a.y - y) / dist) * err;
    }
    x += dx / anchors.length;
    y += dy / anchors.length;
  }

  return {
    x: Math.min(1, Math.max(0, x)),
    y: Math.min(1, Math.max(0, y)),
  };
}

export function metersToNormalized(meters: number, planWidthMeters: number): number {
  if (planWidthMeters <= 0) return 0.05;
  return Math.min(1.5, Math.max(0.01, meters / planWidthMeters));
}
