import { FC, memo } from "react";
import { STAGE_CLASS, stageStyle } from "./genUiStage";

type Point = { x: number; label?: string };

function pointsFrom(raw: unknown, value?: unknown): Point[] {
  const out: Point[] = [];
  if (value != null && Number.isFinite(Number(value))) {
    out.push({ x: Number(value), label: String(value) });
  }
  if (Array.isArray(raw)) {
    for (const item of raw) {
      if (typeof item === "number") out.push({ x: item, label: String(item) });
      else if (item && typeof item === "object") {
        const o = item as Record<string, unknown>;
        const x = Number(o.x ?? o.value);
        if (Number.isFinite(x)) out.push({ x, label: o.label != null ? String(o.label) : String(x) });
      }
    }
  }
  return out;
}

export const NumberLine: FC<{
  min?: number;
  max?: number;
  value?: number;
  points?: unknown;
  title?: string;
  height?: number;
}> = memo(({ min = -5, max = 5, value, points, title, height }) => {
  const lo = Number(min);
  const hi = Number(max) > lo ? Number(max) : lo + 10;
  const pts = pointsFrom(points, value);
  const w = 480;
  const h = 120;
  const pad = 36;
  const y = 64;
  const map = (x: number) => pad + ((x - lo) / (hi - lo)) * (w - pad * 2);
  const ticks: number[] = [];
  const step = hi - lo <= 12 ? 1 : Math.ceil((hi - lo) / 10);
  for (let t = Math.ceil(lo); t <= hi; t += step) ticks.push(t);

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)]">
      {title ? (
        <div className="px-3 pt-2 text-[13px] font-medium text-[var(--chat-text)]">{title}</div>
      ) : null}
      <div className={STAGE_CLASS} style={{ ...stageStyle(height), minHeight: 140, aspectRatio: "16 / 5" }}>
        <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="数轴">
          <line x1={pad} y1={y} x2={w - pad} y2={y} stroke="var(--chat-text)" strokeWidth="1.6" />
          <polygon points={`${w - pad + 8},${y} ${w - pad - 2},${y - 5} ${w - pad - 2},${y + 5}`} fill="var(--chat-text)" />
          {ticks.map((t) => (
            <g key={t}>
              <line x1={map(t)} y1={y - 6} x2={map(t)} y2={y + 6} stroke="var(--chat-text-soft)" />
              <text x={map(t)} y={y + 20} textAnchor="middle" fontSize="11" fill="var(--chat-text-soft)">
                {t}
              </text>
            </g>
          ))}
          {pts.map((p, i) => (
            <g key={`${p.x}-${i}`}>
              <circle cx={map(p.x)} cy={y} r="6" fill="var(--chat-accent)" />
              <text x={map(p.x)} y={y - 14} textAnchor="middle" fontSize="12" fontWeight="600" fill="var(--chat-text)">
                {p.label}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
});

NumberLine.displayName = "NumberLine";
