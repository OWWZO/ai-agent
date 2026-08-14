import { FC, memo } from "react";
import { STAGE_CLASS, stageStyle } from "./genUiStage";
import { evalLabExpr } from "./parametricMath";
import { useGenUiBind } from "./GenUiBind";

type Pt = { x: number; y: number; label?: string };

function readPoints(raw: unknown): Pt[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      if (Array.isArray(item) && item.length >= 2) {
        return { x: Number(item[0]), y: Number(item[1]) };
      }
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      const x = Number(o.x);
      const y = Number(o.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
      return { x, y, label: o.label != null ? String(o.label) : undefined };
    })
    .filter((p): p is Pt => Boolean(p));
}

export const CoordinateGrid: FC<{
  xmin?: number;
  xmax?: number;
  ymin?: number;
  ymax?: number;
  points?: unknown;
  vectors?: unknown;
  /** y = expr in x, e.g. "2*x+1" or "{{m}}*x+{{b}}" already resolved */
  fn?: string;
  title?: string;
  height?: number;
}> = memo(({ xmin = -5, xmax = 5, ymin = -5, ymax = 5, points, vectors, fn, title, height }) => {
  const bind = useGenUiBind();
  const x0 = Number(xmin);
  const x1 = Number(xmax) > x0 ? Number(xmax) : x0 + 10;
  const y0 = Number(ymin);
  const y1 = Number(ymax) > y0 ? Number(ymax) : y0 + 10;
  const pts = readPoints(points);
  const vecs = readPoints(vectors);
  const w = 400;
  const h = 400;
  const pad = 28;
  const mx = (x: number) => pad + ((x - x0) / (x1 - x0)) * (w - pad * 2);
  const my = (y: number) => h - pad - ((y - y0) / (y1 - y0)) * (h - pad * 2);

  const curve: string[] = [];
  if (fn) {
    const scope = { ...(bind?.values || {}), x: 0 };
    for (let i = 0; i <= 80; i += 1) {
      const x = x0 + ((x1 - x0) * i) / 80;
      const y = evalLabExpr(fn, { ...scope, x });
      if (!Number.isFinite(y)) continue;
      curve.push(`${mx(x)},${my(y)}`);
    }
  }

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)]">
      {title ? (
        <div className="px-3 pt-2 text-[13px] font-medium text-[var(--chat-text)]">{title}</div>
      ) : null}
      <div className={STAGE_CLASS} style={stageStyle(height)}>
        <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="坐标系">
          <rect width={w} height={h} fill="var(--chat-surface-soft)" />
          {Array.from({ length: 11 }, (_, i) => {
            const x = x0 + ((x1 - x0) * i) / 10;
            const y = y0 + ((y1 - y0) * i) / 10;
            return (
              <g key={i}>
                <line x1={mx(x)} y1={pad} x2={mx(x)} y2={h - pad} stroke="var(--chat-border)" strokeWidth="1" />
                <line x1={pad} y1={my(y)} x2={w - pad} y2={my(y)} stroke="var(--chat-border)" strokeWidth="1" />
              </g>
            );
          })}
          <line x1={pad} y1={my(0)} x2={w - pad} y2={my(0)} stroke="var(--chat-text-muted)" strokeWidth="1.4" />
          <line x1={mx(0)} y1={pad} x2={mx(0)} y2={h - pad} stroke="var(--chat-text-muted)" strokeWidth="1.4" />
          {curve.length ? (
            <polyline fill="none" stroke="var(--chat-accent)" strokeWidth="2.2" points={curve.join(" ")} />
          ) : null}
          {vecs.map((v, i) => (
            <line
              key={`v${i}`}
              x1={mx(0)}
              y1={my(0)}
              x2={mx(v.x)}
              y2={my(v.y)}
              stroke="var(--chat-accent)"
              strokeWidth="2"
              markerEnd="url(#arrow)"
            />
          ))}
          {pts.map((p, i) => (
            <g key={`${p.x},${p.y},${i}`}>
              <circle cx={mx(p.x)} cy={my(p.y)} r="5" fill="var(--chat-accent)" />
              {p.label ? (
                <text x={mx(p.x) + 8} y={my(p.y) - 8} fontSize="11" fill="var(--chat-text)">
                  {p.label}
                </text>
              ) : null}
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
});

CoordinateGrid.displayName = "CoordinateGrid";
