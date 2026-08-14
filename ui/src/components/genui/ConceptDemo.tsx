import {
  FC,
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import classNames from "classnames";
import {
  Pause,
  Play,
  RotateCcw,
  SkipBack,
  SkipForward,
} from "lucide-react";
import { useReducedMotion } from "motion/react";
import {
  defaultConceptDemo,
  estimateConceptNodeSize,
  layoutConceptFlow,
  measureConceptLabelChars,
  normalizeConceptEdges,
  normalizeConceptNodes,
  normalizeConceptSteps,
  resolveConceptScene,
  type ConceptEdge,
  type ConceptNode,
  type ConceptScene,
  type ConceptStep,
} from "./conceptDemoModel";
import { STAGE_CLASS, stageStyle } from "./genUiStage";

export type ConceptDemoProps = {
  title?: string;
  description?: string;
  scene?: string;
  steps?: unknown;
  nodes?: unknown;
  edges?: unknown;
  /** formula scene tokens */
  formulas?: unknown;
  left?: unknown;
  right?: unknown;
  leftTitle?: string;
  rightTitle?: string;
  height?: number;
  autoPlay?: boolean;
  loop?: boolean;
  stepDuration?: number;
};

function asStringList(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((x) => String(x)).filter(Boolean);
}

function isHi(highlight: string[], id: string): boolean {
  return highlight.includes(id);
}

type FlowPt = { x: number; y: number };

function flowEdgePath(
  a: { x: number; y: number; w: number; h: number },
  b: { x: number; y: number; w: number; h: number }
): { points: FlowPt[]; labelAt: FlowPt } {
  const sameRow = Math.abs(a.y - b.y) < 8;
  if (sameRow) {
    const leftToRight = b.x >= a.x;
    const x1 = a.x + (leftToRight ? a.w / 2 : -a.w / 2);
    const x2 = b.x + (leftToRight ? -b.w / 2 : b.w / 2);
    return {
      points: [
        {
          x: x1,
          y: a.y,
        },
        {
          x: x2,
          y: b.y,
        },
      ],
      labelAt: {
        x: (x1 + x2) / 2,
        y: a.y - 12,
      },
    };
  }
  const down = b.y >= a.y;
  const y1 = a.y + (down ? a.h / 2 : -a.h / 2);
  const y2 = b.y + (down ? -b.h / 2 : b.h / 2);
  const midY = (y1 + y2) / 2;
  return {
    points: [
      {
        x: a.x,
        y: y1,
      },
      {
        x: a.x,
        y: midY,
      },
      {
        x: b.x,
        y: midY,
      },
      {
        x: b.x,
        y: y2,
      },
    ],
    labelAt: {
      x: (a.x + b.x) / 2,
      y: midY - 10,
    },
  };
}

function pointAlongPath(points: FlowPt[], t: number): FlowPt {
  if (points.length < 2) {
    return points[0] || {
      x: 0,
      y: 0,
    };
  }
  const segs: number[] = [];
  let total = 0;
  for (let i = 0; i < points.length - 1; i += 1) {
    const len = Math.hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y);
    segs.push(len);
    total += len;
  }
  let remain = Math.max(0, Math.min(1, t)) * total;
  for (let i = 0; i < segs.length; i += 1) {
    if (remain <= segs[i] || i === segs.length - 1) {
      const u = segs[i] === 0 ? 0 : remain / segs[i];
      return {
        x: points[i].x + (points[i + 1].x - points[i].x) * u,
        y: points[i].y + (points[i + 1].y - points[i].y) * u,
      };
    }
    remain -= segs[i];
  }
  return points[points.length - 1];
}

const FlowScene: FC<{
  nodes: ConceptNode[];
  edges: ConceptEdge[];
  highlight: string[];
  progress: number;
}> = ({ nodes, edges, highlight, progress }) => {
  const hostRef = useRef<HTMLDivElement>(null);
  const [rowWidth, setRowWidth] = useState(720);
  useEffect(() => {
    const el = hostRef.current;
    if (!el) return;
    const apply = () => {
      const next = Math.floor(el.getBoundingClientRect().width);
      if (next > 0) setRowWidth(next);
    };
    apply();
    const ro = new ResizeObserver(apply);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  const layout = useMemo(
    () => layoutConceptFlow(nodes, { maxRowWidth: rowWidth }),
    [nodes, rowWidth]
  );
  const { width: w, height: h, boxById } = layout;

  return (
    <div ref={hostRef} className="w-full">
      <svg
        viewBox={`0 0 ${w} ${h}`}
        className="h-auto w-full"
        style={{
          aspectRatio: `${w} / ${h}`,
          minHeight: 180,
        }}
        preserveAspectRatio="xMidYMin meet"
        role="img"
        aria-label="流程示意"
      >
        <rect width={w} height={h} rx="12" fill="var(--chat-surface-soft)" />
        {edges.map((e) => {
          const a = boxById.get(e.from);
          const b = boxById.get(e.to);
          if (!a || !b) return null;
          const on = isHi(highlight, e.id) || (isHi(highlight, e.from) && isHi(highlight, e.to));
          const { points, labelAt } = flowEdgePath(a, b);
          const d = points
            .map((p, i) => `${i === 0 ? "M" : "L"} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
            .join(" ");
          const dot = on ? pointAlongPath(points, progress) : null;
          return (
            <g key={e.id}>
              <path
                d={d}
                fill="none"
                stroke={on ? "var(--chat-accent)" : "var(--chat-border-strong)"}
                strokeWidth={on ? 2.5 : 1.5}
                strokeDasharray={on ? undefined : "4 4"}
                opacity={on ? 1 : 0.55}
              />
              {dot ? (
                <circle r="4" fill="var(--chat-accent)" cx={dot.x} cy={dot.y}>
                  <title>{e.label || e.id}</title>
                </circle>
              ) : null}
              {e.label ? (
                <g>
                  <rect
                    x={labelAt.x - measureConceptLabelChars(e.label) / 2 - 4}
                    y={labelAt.y - 10}
                    width={measureConceptLabelChars(e.label) + 8}
                    height={14}
                    rx={4}
                    fill="var(--chat-surface-soft)"
                    opacity={0.92}
                  />
                  <text
                    x={labelAt.x}
                    y={labelAt.y}
                    textAnchor="middle"
                    dominantBaseline="middle"
                    fontSize="10"
                    fill="var(--chat-text-soft)"
                    style={{ fontWeight: 500 }}
                  >
                    {e.label}
                  </text>
                </g>
              ) : null}
            </g>
          );
        })}
        {nodes.map((n) => {
          const p = boxById.get(n.id);
          if (!p) return null;
          const on = isHi(highlight, n.id);
          const { lines } = estimateConceptNodeSize(n.label);
          const lineH = 15;
          const blockH = Math.max(1, lines.length) * lineH;
          const startY = -blockH / 2 + lineH / 2 + 0.5;
          return (
            <g key={n.id} transform={`translate(${p.x}, ${p.y})`}>
              <rect
                x={-p.w / 2}
                y={-p.h / 2}
                width={p.w}
                height={p.h}
                rx={12}
                fill={on ? "var(--chat-accent-soft)" : "var(--chat-surface)"}
                stroke={on ? "var(--chat-accent)" : "var(--chat-border)"}
                strokeWidth={on ? 2 : 1}
              />
              <text
                textAnchor="middle"
                fontSize="12"
                fill="var(--chat-text)"
                style={{ fontWeight: 500 }}
              >
                {lines.map((line, i) => (
                  <tspan key={`${n.id}-l${i}`} x={0} y={startY + i * lineH}>
                    {line}
                  </tspan>
                ))}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
};

const StackScene: FC<{ nodes: ConceptNode[]; highlight: string[] }> = ({
  nodes,
  highlight,
}) => (
  <div className="flex h-full flex-col justify-center gap-2 p-3">
    {nodes.map((n, i) => {
      const on = isHi(highlight, n.id);
      return (
        <div
          key={n.id}
          className={classNames(
            "rounded-lg border px-3 py-2.5 text-center text-[13px] font-medium transition-all duration-300",
            on
              ? "scale-[1.02] border-[var(--chat-accent)] bg-[var(--chat-accent-soft)] text-[var(--chat-accent)] shadow-sm"
              : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-soft)] opacity-70"
          )}
          style={{ transitionDelay: `${i * 40}ms` }}
        >
          {n.label}
          {n.sublabel ? (
            <div className="mt-0.5 text-[11px] font-normal opacity-80">{n.sublabel}</div>
          ) : null}
        </div>
      );
    })}
  </div>
);

const TreeScene: FC<{
  nodes: ConceptNode[];
  edges: ConceptEdge[];
  highlight: string[];
}> = ({ nodes, edges, highlight }) => {
  // simple layered layout by BFS depth
  const children = new Map<string, string[]>();
  const indeg = new Map<string, number>();
  nodes.forEach((n) => {
    children.set(n.id, []);
    indeg.set(n.id, 0);
  });
  edges.forEach((e) => {
    children.get(e.from)?.push(e.to);
    indeg.set(e.to, (indeg.get(e.to) || 0) + 1);
  });
  const roots = nodes.filter((n) => (indeg.get(n.id) || 0) === 0).map((n) => n.id);
  const depth = new Map<string, number>();
  const q = [...roots];
  roots.forEach((r) => depth.set(r, 0));
  while (q.length) {
    const id = q.shift()!;
    const d = depth.get(id) || 0;
    for (const c of children.get(id) || []) {
      if (!depth.has(c)) {
        depth.set(c, d + 1);
        q.push(c);
      }
    }
  }
  const layers = new Map<number, string[]>();
  nodes.forEach((n) => {
    const d = depth.get(n.id) ?? 0;
    if (!layers.has(d)) layers.set(d, []);
    layers.get(d)!.push(n.id);
  });
  const maxDepth = Math.max(0, ...layers.keys());
  const widest = Math.max(1, ...[...layers.values()].map((row) => row.length));
  const w = Math.max(360, 48 + widest * 88);
  const h = Math.max(180, 70 + maxDepth * 64);
  const pos = new Map<string, { x: number; y: number }>();
  for (let d = 0; d <= maxDepth; d += 1) {
    const row = layers.get(d) || [];
    row.forEach((id, i) => {
      const x = ((i + 1) / (row.length + 1)) * w;
      const y = 36 + d * 64;
      pos.set(id, {
        x,
        y,
      });
    });
  }
  const labelOf = new Map(nodes.map((n) => [n.id, n.label]));

  return (
    <svg
      viewBox={`0 0 ${w} ${h}`}
      className="h-auto w-full"
      style={{
        aspectRatio: `${w} / ${h}`,
        minHeight: 180,
      }}
      preserveAspectRatio="xMidYMin meet"
      role="img"
      aria-label="树结构"
    >
      <rect width={w} height={h} rx="12" fill="var(--chat-surface-soft)" />
      {edges.map((e) => {
        const a = pos.get(e.from);
        const b = pos.get(e.to);
        if (!a || !b) return null;
        const on = isHi(highlight, e.id) || isHi(highlight, e.from) || isHi(highlight, e.to);
        return (
          <line
            key={e.id}
            x1={a.x}
            y1={a.y + 14}
            x2={b.x}
            y2={b.y - 14}
            stroke={on ? "var(--chat-accent)" : "var(--chat-border-strong)"}
            strokeWidth={on ? 2 : 1.25}
            opacity={on ? 1 : 0.5}
          />
        );
      })}
      {nodes.map((n) => {
        const p = pos.get(n.id);
        if (!p) return null;
        const on = isHi(highlight, n.id);
        return (
          <g key={n.id} transform={`translate(${p.x}, ${p.y})`}>
            <circle
              r={16}
              fill={on ? "var(--chat-accent-soft)" : "var(--chat-surface)"}
              stroke={on ? "var(--chat-accent)" : "var(--chat-border)"}
              strokeWidth={on ? 2 : 1}
            />
            <text textAnchor="middle" y={4} fontSize="10" fill="var(--chat-text)" fontWeight={on ? 600 : 500}>
              {(labelOf.get(n.id) || n.id).slice(0, 4)}
            </text>
          </g>
        );
      })}
    </svg>
  );
};

const FormulaScene: FC<{
  formulas: string[];
  highlight: string[];
  badge?: string;
}> = ({ formulas, highlight, badge }) => (
  <div className="flex h-full flex-col items-center justify-center gap-4 p-4">
    <div className="flex flex-wrap items-center justify-center gap-2">
      {formulas.map((f, i) => {
        const id = `f${i}`;
        const on = isHi(highlight, id) || highlight.length === 0;
        return (
          <div
            key={id}
            className={classNames(
              "rounded-xl border px-4 py-3 font-mono text-[18px] transition-all duration-300 sm:text-[22px]",
              on
                ? "scale-105 border-[var(--chat-accent)] bg-[var(--chat-accent-soft)] text-[var(--chat-accent)] shadow-sm"
                : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-muted)] opacity-45"
            )}
          >
            {f}
          </div>
        );
      })}
    </div>
    {badge ? (
      <div className="rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[12px] text-[var(--chat-text-soft)]">
        {badge}
      </div>
    ) : null}
  </div>
);

const CompareScene: FC<{
  left: string[];
  right: string[];
  leftTitle: string;
  rightTitle: string;
  highlight: string[];
}> = ({ left, right, leftTitle, rightTitle, highlight }) => {
  const leftOn = isHi(highlight, "left") || highlight.length === 0;
  const rightOn = isHi(highlight, "right") || highlight.length === 0;
  return (
    <div className="grid h-full grid-cols-2 gap-3 p-3">
      <div
        className={classNames(
          "rounded-xl border p-3 transition-all duration-300",
          leftOn
            ? "border-[var(--chat-accent)] bg-[var(--chat-accent-soft)]/40"
            : "border-[var(--chat-border)] opacity-50"
        )}
      >
        <div className="mb-2 text-[12px] font-semibold text-[var(--chat-text)]">{leftTitle}</div>
        <ul className="space-y-1.5 text-[12px] text-[var(--chat-text-soft)]">
          {left.map((x) => (
            <li key={x}>· {x}</li>
          ))}
        </ul>
      </div>
      <div
        className={classNames(
          "rounded-xl border p-3 transition-all duration-300",
          rightOn
            ? "border-[var(--chat-accent)] bg-[var(--chat-accent-soft)]/40"
            : "border-[var(--chat-border)] opacity-50"
        )}
      >
        <div className="mb-2 text-[12px] font-semibold text-[var(--chat-text)]">{rightTitle}</div>
        <ul className="space-y-1.5 text-[12px] text-[var(--chat-text-soft)]">
          {right.map((x) => (
            <li key={x}>· {x}</li>
          ))}
        </ul>
      </div>
    </div>
  );
};

const SequenceScene: FC<{
  nodes: ConceptNode[];
  edges: ConceptEdge[];
  highlight: string[];
  progress: number;
}> = (props) => <FlowScene {...props} />;

const ConceptDemo: FC<ConceptDemoProps> = memo((props) => {
  const reduce = useReducedMotion();
  const scene = resolveConceptScene(props.scene);
  const defaults = useMemo(() => defaultConceptDemo(scene), [scene]);

  const nodes = useMemo(() => {
    const n = normalizeConceptNodes(props.nodes);
    return n.length ? n : defaults.nodes;
  }, [props.nodes, defaults.nodes]);

  const edges = useMemo(() => {
    const e = normalizeConceptEdges(props.edges);
    return e.length ? e : defaults.edges;
  }, [props.edges, defaults.edges]);

  const formulas = useMemo(() => {
    const f = asStringList(props.formulas);
    return f.length ? f : defaults.formulas || [];
  }, [props.formulas, defaults.formulas]);

  const left = useMemo(() => {
    const v = asStringList(props.left);
    return v.length ? v : defaults.left || [];
  }, [props.left, defaults.left]);

  const right = useMemo(() => {
    const v = asStringList(props.right);
    return v.length ? v : defaults.right || [];
  }, [props.right, defaults.right]);

  const steps = useMemo(() => {
    const s = normalizeConceptSteps(props.steps, Number(props.stepDuration) || 2200);
    return s.length ? s : defaults.steps;
  }, [props.steps, props.stepDuration, defaults.steps]);

  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(props.autoPlay !== false && !reduce);
  const [progress, setProgress] = useState(0);
  const rafRef = useRef<number | null>(null);
  const startRef = useRef<number>(0);
  const indexRef = useRef(0);
  indexRef.current = index;

  const step: ConceptStep | undefined = steps[index];
  const total = steps.length;
  const loop = props.loop !== false;

  const goTo = useCallback(
    (next: number) => {
      if (!total) return;
      const clamped = ((next % total) + total) % total;
      setIndex(clamped);
      setProgress(0);
      startRef.current = performance.now();
    },
    [total]
  );

  const restart = useCallback(() => {
    goTo(0);
    setPlaying(true);
  }, [goTo]);

  // autoplay timer
  useEffect(() => {
    if (!playing || !step || reduce) return;
    startRef.current = performance.now() - progress * step.duration;

    const tick = (now: number) => {
      const elapsed = now - startRef.current;
      const p = Math.min(1, elapsed / step.duration);
      setProgress(p);
      if (p >= 1) {
        const cur = indexRef.current;
        if (cur >= total - 1) {
          if (loop) {
            setIndex(0);
            setProgress(0);
            startRef.current = performance.now();
          } else {
            setPlaying(false);
            setProgress(1);
            return;
          }
        } else {
          setIndex(cur + 1);
          setProgress(0);
          startRef.current = performance.now();
        }
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
    };
    // progress intentionally omitted — restart clock on play/index/step change
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playing, step?.id, step?.duration, total, loop, reduce]);

  const highlight = step?.highlight || [];
  const title = props.title || "概念演示";
  const overall =
    total > 0 ? (index + Math.min(progress, 0.999)) / total : 0;

  const sceneView = (() => {
    switch (scene as ConceptScene) {
      case "stack":
        return <StackScene nodes={nodes} highlight={highlight} />;
      case "tree":
        return <TreeScene nodes={nodes} edges={edges} highlight={highlight} />;
      case "formula":
        return (
          <FormulaScene formulas={formulas} highlight={highlight} badge={step?.badge} />
        );
      case "compare":
        return (
          <CompareScene
            left={left}
            right={right}
            leftTitle={props.leftTitle || "方案 A"}
            rightTitle={props.rightTitle || "方案 B"}
            highlight={highlight}
          />
        );
      case "sequence":
        return (
          <SequenceScene
            nodes={nodes}
            edges={edges}
            highlight={highlight}
            progress={progress}
          />
        );
      case "flow":
      default:
        return (
          <FlowScene
            nodes={nodes}
            edges={edges}
            highlight={highlight}
            progress={progress}
          />
        );
    }
  })();

  if (!total) {
    return (
      <div className="rounded-xl border border-dashed border-[var(--chat-border)] px-3 py-6 text-center text-[12px] text-[var(--chat-text-soft)]">
        暂无演示步骤
      </div>
    );
  }

  return (
    <div className="genui-concept-demo overflow-hidden rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] shadow-[var(--shadow-xs)]">
      <div className="border-b border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/60 px-3 py-2.5 sm:px-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="text-[14px] font-semibold tracking-tight text-[var(--chat-text)]">
              {title}
            </div>
            {props.description ? (
              <div className="mt-0.5 text-[12px] text-[var(--chat-text-soft)]">
                {props.description}
              </div>
            ) : null}
          </div>
          <span className="shrink-0 rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] tabular-nums text-[var(--chat-text-soft)]">
            {index + 1}/{total}
          </span>
        </div>
      </div>

      <div>
        <div
          className={classNames(
            "border-b border-[var(--chat-border)]/40",
            scene === "flow" || scene === "sequence" || scene === "tree"
              ? "w-full min-h-[200px] overflow-x-auto"
              : STAGE_CLASS
          )}
          style={stageStyle(props.height)}
        >
          {sceneView}
        </div>

        <div className="flex flex-col justify-between gap-3 p-3 sm:p-4">
          <div>
            <div className="text-[11px] font-medium uppercase tracking-wide text-[var(--chat-accent)]">
              步骤 {index + 1}
            </div>
            <div className="mt-1 text-[15px] font-semibold text-[var(--chat-text)]">
              {step?.title}
            </div>
            {step?.caption ? (
              <p className="mt-2 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
                {step.caption}
              </p>
            ) : null}
            {step?.badge ? (
              <div className="mt-2 inline-flex rounded-md bg-[var(--chat-accent-soft)] px-2 py-0.5 font-mono text-[12px] text-[var(--chat-accent)]">
                {step.badge}
              </div>
            ) : null}
          </div>

          <div className="space-y-2">
            {/* overall scrub */}
            <input
              type="range"
              min={0}
              max={Math.max(total - 1, 0)}
              step={1}
              value={index}
              aria-label="演示进度"
              className="h-1.5 w-full cursor-pointer accent-[var(--chat-accent)]"
              onChange={(e) => {
                setPlaying(false);
                goTo(Number(e.target.value));
              }}
            />
            <div className="h-1 overflow-hidden rounded-full bg-[var(--chat-surface-muted)]">
              <div
                className="h-full rounded-full bg-[var(--chat-accent)] transition-[width] duration-100"
                style={{ width: `${overall * 100}%` }}
              />
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
                title="上一步"
                onClick={() => {
                  setPlaying(false);
                  goTo(index - 1);
                }}
              >
                <SkipBack className="size-3.5" />
              </button>
              <button
                type="button"
                className="inline-flex h-8 flex-1 items-center justify-center gap-1.5 rounded-lg border border-[var(--chat-accent)] bg-[var(--chat-accent)] text-[13px] font-medium text-white hover:opacity-90"
                onClick={() => setPlaying((p) => !p)}
              >
                {playing ? (
                  <>
                    <Pause className="size-3.5" /> 暂停
                  </>
                ) : (
                  <>
                    <Play className="size-3.5" /> 播放
                  </>
                )}
              </button>
              <button
                type="button"
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
                title="下一步"
                onClick={() => {
                  setPlaying(false);
                  goTo(index + 1);
                }}
              >
                <SkipForward className="size-3.5" />
              </button>
              <button
                type="button"
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
                title="重播"
                onClick={restart}
              >
                <RotateCcw className="size-3.5" />
              </button>
            </div>

            <div className="flex flex-wrap gap-1">
              {steps.map((s, i) => (
                <button
                  key={s.id}
                  type="button"
                  title={s.title}
                  onClick={() => {
                    setPlaying(false);
                    goTo(i);
                  }}
                  className={classNames(
                    "h-1.5 flex-1 min-w-[12px] rounded-full transition-colors",
                    i === index
                      ? "bg-[var(--chat-accent)]"
                      : i < index
                        ? "bg-[var(--chat-accent-muted)]"
                        : "bg-[var(--chat-surface-muted)]"
                  )}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});

ConceptDemo.displayName = "ConceptDemo";

export default ConceptDemo;
