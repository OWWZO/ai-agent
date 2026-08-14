import { FC, memo } from "react";
import { STAGE_CLASS, stageStyle } from "./genUiStage";
import { formatLabNumber } from "./parametricMath";

type Mark = { x: number; y?: number; label?: string };

function num(v: unknown, fallback: number): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function parseMarks(raw: unknown, axis: "x" | "xy"): Mark[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      if (typeof item === "number") return axis === "x" ? { x: item } : { x: item, y: 0 };
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      return {
        x: num(o.x ?? o.value ?? o.t, NaN),
        y: num(o.y, 0),
        label: o.label != null ? String(o.label) : undefined,
      };
    })
    .filter((m): m is Mark => Boolean(m && Number.isFinite(m.x)));
}

export const NumberLine: FC<{
  min?: number;
  max?: number;
  value?: number;
  points?: unknown;
  title?: string;
  height?: number;
}> = memo(({ min = -5, max = 5, value, points, title, height }) => {
  const lo = Math.min(num(min, -5), num(max, 5) - 0.1);
  const hi = Math.max(num(max, 5), lo + 0.1);
  const marks = parseMarks(points, "x");
  if (Number.isFinite(Number(value))) marks.push({ x: Number(value), label: formatLabNumber(Number(value), "fixed:1") });
  const w = 480;
  const h = 120;
  const pad = 36;
  const map = (x: number) => pad + ((x - lo) / (hi - lo)) * (w - pad * 2);
  const ticks: number[] = [];
  const span = hi - lo;
  const step = span <= 10 ? 1 : span <= 20 ? 2 : 5;
  for (let t = Math.ceil(lo); t <= hi; t += step) ticks.push(t);

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)]">
      {title ? (
        <div className="px-3 pt-2 text-[13px] font-semibold text-[var(--chat-text)]">{title}</div>
      ) : null}
      <div className={height ? undefined : "w-full"} style={stageStyle(height) || { minHeight: 120 }}>
        <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="数轴">
          <line x1={pad} y1={h / 2} x2={w - pad} y2={h / 2} stroke="var(--chat-text)" strokeWidth="2" />
          <polygon points={`${w - pad + 8},${h / 2} ${w - pad - 4},${h / 2 - 5} ${w - pad - 4},${h / 2 + 5}`} fill="var(--chat-text)" />
          {ticks.map((t) => (
            <g key={t}>
              <line x1={map(t)} y1={h / 2 - 6} x2={map(t)} y2={h / 2 + 6} stroke="var(--chat-text-soft)" />
              <text x={map(t)} y={h / 2 + 22} textAnchor="middle" fontSize="11" fill="var(--chat-text-soft)">
                {t}
              </text>
            </g>
          ))}
          {marks.map((m, i) => (
            <g key={`${m.x}-${i}`}>
              <circle cx={map(m.x)} cy={h / 2} r="6" fill="var(--chat-accent)" />
              <text x={map(m.x)} y={h / 2 - 14} textAnchor="middle" fontSize="11" fill="var(--chat-text)">
                {m.label || formatLabNumber(m.x, "fixed:1")}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
});

NumberLine.displayName = "NumberLine";

export const CoordinateGrid: FC<{
  xmin?: number;
  xmax?: number;
  ymin?: number;
  ymax?: number;
  points?: unknown;
  vectors?: unknown;
  title?: string;
  height?: number;
}> = memo(({ xmin = -5, xmax = 5, ymin = -5, ymax = 5, points, vectors, title, height }) => {
  const x0 = num(xmin, -5);
  const x1 = num(xmax, 5);
  const y0 = num(ymin, -5);
  const y1 = num(ymax, 5);
  const pts = parseMarks(points, "xy");
  const vecs = parseMarks(vectors, "xy");
  const w = 360;
  const h = 360;
  const pad = 28;
  const mx = (x: number) => pad + ((x - x0) / (x1 - x0)) * (w - pad * 2);
  const my = (y: number) => h - pad - ((y - y0) / (y1 - y0)) * (h - pad * 2);
  const ox = mx(0);
  const oy = my(0);

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)]">
      {title ? (
        <div className="px-3 pt-2 text-[13px] font-semibold text-[var(--chat-text)]">{title}</div>
      ) : null}
      <div className={STAGE_CLASS} style={stageStyle(height)}>
        <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="坐标平面">
          <rect width={w} height={h} fill="var(--chat-surface-soft)" />
          {Array.from({ length: Math.floor(x1 - x0) + 1 }, (_, i) => x0 + i).map((t) => (
            <line key={`vx${t}`} x1={mx(t)} y1={pad} x2={mx(t)} y2={h - pad} stroke="var(--chat-border)" strokeWidth="1" />
          ))}
          {Array.from({ length: Math.floor(y1 - y0) + 1 }, (_, i) => y0 + i).map((t) => (
            <line key={`hy${t}`} x1={pad} y1={my(t)} x2={w - pad} y2={my(t)} stroke="var(--chat-border)" strokeWidth="1" />
          ))}
          <line x1={pad} y1={oy} x2={w - pad} y2={oy} stroke="var(--chat-text-soft)" strokeWidth="1.5" />
          <line x1={ox} y1={pad} x2={ox} y2={h - pad} stroke="var(--chat-text-soft)" strokeWidth="1.5" />
          {vecs.map((v, i) => (
            <line
              key={`vec-${i}`}
              x1={ox}
              y1={oy}
              x2={mx(v.x)}
              y2={my(v.y ?? 0)}
              stroke="var(--chat-accent)"
              strokeWidth="2.5"
              markerEnd="url(#arrow)"
            />
          ))}
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
              <path d="M0,0 L0,6 L8,3 z" fill="var(--chat-accent)" />
            </marker>
          </defs>
          {pts.map((p, i) => (
            <g key={`p-${i}`}>
              <circle cx={mx(p.x)} cy={my(p.y ?? 0)} r="5" fill="var(--chat-accent)" />
              <text x={mx(p.x) + 8} y={my(p.y ?? 0) - 8} fontSize="11" fill="var(--chat-text)">
                {p.label || `(${formatLabNumber(p.x, "fixed:1")}, ${formatLabNumber(p.y ?? 0, "fixed:1")})`}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
});

CoordinateGrid.displayName = "CoordinateGrid";
