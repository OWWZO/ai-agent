import { FC, memo, useMemo, useState } from "react";
import classNames from "classnames";
import {
  clamp,
  evalLabExpr,
  formatLabNumber,
  interpolateTemplate,
  normalizeOutputs,
  normalizeParams,
  type LabOutput,
  type LabParam,
} from "./parametricMath";
import { STAGE_CLASS, stageStyle } from "./genUiStage";

export type ParametricLabProps = {
  title?: string;
  description?: string;
  /** right_triangle | circle | rectangle | linear | quadratic | unit_circle | custom_svg */
  scene?: string;
  params?: unknown;
  outputs?: unknown;
  /** SVG markup with {{paramId}} or $paramId placeholders (scene=custom_svg) */
  svg?: string;
  height?: number | string;
  showFormulas?: boolean;
  formulaNote?: string;
  accent?: string;
  /** Alias bootstrap for PythagorasLab */
  preset?: string;
};

type SceneKind =
  | "right_triangle"
  | "circle"
  | "rectangle"
  | "linear"
  | "quadratic"
  | "unit_circle"
  | "number_line"
  | "coordinate"
  | "custom_svg"
  | "none";

function resolveScene(raw?: string, preset?: string): SceneKind {
  const s = String(raw || preset || "right_triangle")
    .trim()
    .toLowerCase()
    .replace(/-/g, "_");
  if (s === "pythagoras" || s === "pythagorean" || s === "triangle") {
    return "right_triangle";
  }
  if (
    s === "right_triangle" ||
    s === "circle" ||
    s === "rectangle" ||
    s === "linear" ||
    s === "quadratic" ||
    s === "unit_circle" ||
    s === "number_line" ||
    s === "coordinate" ||
    s === "custom_svg" ||
    s === "none"
  ) {
    return s;
  }
  return "right_triangle";
}

function defaultParams(scene: SceneKind): LabParam[] {
  switch (scene) {
    case "right_triangle":
      return [
        { id: "a", label: "直角边 a", value: 3, min: 0.5, max: 12, step: 0.1 },
        { id: "b", label: "直角边 b", value: 4, min: 0.5, max: 12, step: 0.1 },
      ];
    case "circle":
      return [
        { id: "r", label: "半径 r", value: 3, min: 0.5, max: 10, step: 0.1 },
      ];
    case "rectangle":
      return [
        { id: "w", label: "宽 w", value: 5, min: 0.5, max: 12, step: 0.1 },
        { id: "h", label: "高 h", value: 3, min: 0.5, max: 12, step: 0.1 },
      ];
    case "linear":
      return [
        { id: "m", label: "斜率 m", value: 1, min: -3, max: 3, step: 0.1 },
        { id: "b", label: "截距 b", value: 0, min: -5, max: 5, step: 0.1 },
      ];
    case "quadratic":
      return [
        { id: "a", label: "a", value: 0.5, min: -2, max: 2, step: 0.05 },
        { id: "b", label: "b", value: 0, min: -3, max: 3, step: 0.1 },
        { id: "c", label: "c", value: 0, min: -3, max: 3, step: 0.1 },
      ];
    case "unit_circle":
      return [
        {
          id: "theta",
          label: "角度 θ (°)",
          value: 45,
          min: 0,
          max: 360,
          step: 1,
          unit: "°",
        },
      ];
    case "number_line":
      return [
        { id: "x", label: "点 x", value: 2, min: -8, max: 8, step: 0.1 },
      ];
    case "coordinate":
      return [
        { id: "x", label: "x", value: 2, min: -5, max: 5, step: 0.1 },
        { id: "y", label: "y", value: 1, min: -5, max: 5, step: 0.1 },
      ];
    default:
      return [];
  }
}

function defaultOutputs(scene: SceneKind): LabOutput[] {
  switch (scene) {
    case "right_triangle":
      return [
        { id: "c", label: "斜边 c", expr: "sqrt(a*a + b*b)", format: "fixed:3" },
        { id: "area", label: "面积", expr: "a*b/2", format: "fixed:2" },
      ];
    case "circle":
      return [
        { id: "area", label: "面积 πr²", expr: "pi*r*r", format: "fixed:2" },
        { id: "circ", label: "周长 2πr", expr: "2*pi*r", format: "fixed:2" },
      ];
    case "rectangle":
      return [
        { id: "area", label: "面积", expr: "w*h", format: "fixed:2" },
        { id: "peri", label: "周长", expr: "2*(w+h)", format: "fixed:2" },
      ];
    case "linear":
      return [{ id: "eq", label: "y = mx + b", expr: "m", format: "fixed:2" }];
    case "quadratic":
      return [
        {
          id: "vertex_x",
          label: "顶点 x",
          expr: "a == 0 ? 0 : -b/(2*a)",
          format: "fixed:2",
        },
      ];
    case "number_line":
      return [{ id: "absx", label: "|x|", expr: "abs(x)", format: "fixed:2" }];
    case "coordinate":
      return [
        { id: "r", label: "到原点距离", expr: "hypot(x,y)", format: "fixed:2" },
      ];
    case "unit_circle":
      return [
        {
          id: "rad",
          label: "弧度",
          expr: "theta * pi / 180",
          format: "fixed:3",
        },
        {
          id: "cosv",
          label: "cos θ",
          expr: "cos(theta * pi / 180)",
          format: "fixed:3",
        },
        {
          id: "sinv",
          label: "sin θ",
          expr: "sin(theta * pi / 180)",
          format: "fixed:3",
        },
      ];
    default:
      return [];
  }
}

function defaultFormula(scene: SceneKind): string {
  switch (scene) {
    case "right_triangle":
      return "a² + b² = c²";
    case "circle":
      return "S = πr²，C = 2πr";
    case "rectangle":
      return "S = w·h，P = 2(w+h)";
    case "linear":
      return "y = mx + b";
    case "quadratic":
      return "y = ax² + bx + c";
    case "unit_circle":
      return "x = cos θ，y = sin θ";
    case "number_line":
      return "数轴上的点";
    case "coordinate":
      return "P(x, y)";
    default:
      return "";
  }
}

// ternary ?: not in our tokenizer — simplify unit_circle / quadratic defaults without ?:
// Fix defaultOutputs for quadratic - remove ternary
function defaultOutputsSafe(scene: SceneKind): LabOutput[] {
  if (scene === "quadratic") {
    return [
      { id: "disc", label: "判别式 Δ", expr: "b*b - 4*a*c", format: "fixed:2" },
    ];
  }
  return defaultOutputs(scene).filter((o) => !o.expr.includes("?"));
}

function SceneSvg({
  scene,
  scope,
  outputs,
  customSvg,
  height,
  accent,
}: {
  scene: SceneKind;
  scope: Record<string, number>;
  outputs: Record<string, number>;
  customSvg?: string;
  height: number;
  accent: string;
}) {
  const w = 480;
  const h = 300;
  const pad = 32;

  if (scene === "custom_svg" && customSvg) {
    const markup = interpolateTemplate(customSvg, scope, outputs)
      .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, "")
      .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "");
    return (
      <div
        className="genui-lab-scene overflow-hidden rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)] p-2"
        style={{ minHeight: h }}
        // Model SVG: numbers only via {{id}}; scripts/handlers stripped.
        dangerouslySetInnerHTML={{ __html: markup }}
      />
    );
  }

  if (scene === "right_triangle") {
    const a = scope.a ?? 3;
    const b = scope.b ?? 4;
    const maxLeg = Math.max(a, b, 0.1);
    const boxW = w - pad * 2;
    const boxH = h - pad * 2;
    const scale = Math.min(boxW / maxLeg, boxH / maxLeg);
    const tw = b * scale;
    const th = a * scale;
    const ax = (w - tw) / 2;
    const ay = (h + th) / 2;
    const bx = ax + tw;
    const by = ay;
    const cx = ax;
    const cy = ay - th;
    const cLen = outputs.c ?? Math.hypot(a, b);
    const mark = Math.min(14, Math.min(tw, th) * 0.18);
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="直角三角形">
        <polygon
          points={`${ax},${ay} ${bx},${by} ${cx},${cy}`}
          fill={`${accent}18`}
          stroke={accent}
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        <rect x={ax} y={ay - mark} width={mark} height={mark} fill="none" stroke={accent} strokeWidth="1.5" />
        <text x={(ax + bx) / 2} y={ay + 20} textAnchor="middle" fontSize="13" fill="var(--chat-text-soft)">
          b = {formatLabNumber(b, "fixed:1")}
        </text>
        <text
          x={ax - 16}
          y={(ay + cy) / 2}
          textAnchor="middle"
          fontSize="13"
          fill="var(--chat-text-soft)"
          transform={`rotate(-90 ${ax - 16} ${(ay + cy) / 2})`}
        >
          a = {formatLabNumber(a, "fixed:1")}
        </text>
        <text x={(bx + cx) / 2 + 10} y={(by + cy) / 2 - 6} fontSize="13" fontWeight="600" fill="var(--chat-text)">
          c = {formatLabNumber(cLen, "fixed:2")}
        </text>
      </svg>
    );
  }

  if (scene === "circle") {
    const r = scope.r ?? 3;
    const maxR = 10;
    const radius = Math.min(w, h) * 0.32 * (r / maxR) * (10 / 3.5);
    const cx = w / 2;
    const cy = h / 2;
    const rr = Math.max(12, Math.min(Math.min(w, h) * 0.38, radius));
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="圆">
        <circle cx={cx} cy={cy} r={rr} fill={`${accent}22`} stroke={accent} strokeWidth="2.5" />
        <line x1={cx} y1={cy} x2={cx + rr} y2={cy} stroke={accent} strokeWidth="1.5" strokeDasharray="4 3" />
        <circle cx={cx} cy={cy} r="3" fill={accent} />
        <text x={cx + rr / 2} y={cy - 8} textAnchor="middle" fontSize="12" fill="var(--chat-text)">
          r = {formatLabNumber(r, "fixed:1")}
        </text>
      </svg>
    );
  }

  if (scene === "rectangle") {
    const ww = scope.w ?? 5;
    const hh = scope.h ?? 3;
    const maxSide = Math.max(ww, hh, 0.1);
    const scale = Math.min((w - pad * 2) / maxSide, (h - pad * 2) / maxSide);
    const rw = ww * scale;
    const rh = hh * scale;
    const x = (w - rw) / 2;
    const y = (h - rh) / 2;
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="矩形">
        <rect x={x} y={y} width={rw} height={rh} fill={`${accent}22`} stroke={accent} strokeWidth="2.5" rx="4" />
        <text x={w / 2} y={y + rh + 18} textAnchor="middle" fontSize="12" fill="var(--chat-text-soft)">
          w = {formatLabNumber(ww, "fixed:1")}
        </text>
        <text x={x - 12} y={y + rh / 2} textAnchor="middle" fontSize="12" fill="var(--chat-text-soft)" transform={`rotate(-90 ${x - 12} ${y + rh / 2})`}>
          h = {formatLabNumber(hh, "fixed:1")}
        </text>
      </svg>
    );
  }

  if (scene === "linear" || scene === "quadratic") {
    const xMin = -5;
    const xMax = 5;
    const yMin = -5;
    const yMax = 5;
    const mapX = (x: number) => pad + ((x - xMin) / (xMax - xMin)) * (w - pad * 2);
    const mapY = (y: number) => h - pad - ((y - yMin) / (yMax - yMin)) * (h - pad * 2);
    const pts: string[] = [];
    for (let i = 0; i <= 80; i += 1) {
      const x = xMin + ((xMax - xMin) * i) / 80;
      let y = 0;
      if (scene === "linear") {
        y = (scope.m ?? 1) * x + (scope.b ?? 0);
      } else {
        y = (scope.a ?? 0.5) * x * x + (scope.b ?? 0) * x + (scope.c ?? 0);
      }
      if (!Number.isFinite(y)) continue;
      const cy = clamp(y, yMin - 1, yMax + 1);
      pts.push(`${mapX(x)},${mapY(cy)}`);
    }
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="函数图像">
        <line x1={pad} y1={mapY(0)} x2={w - pad} y2={mapY(0)} stroke="var(--chat-border)" strokeWidth="1" />
        <line x1={mapX(0)} y1={pad} x2={mapX(0)} y2={h - pad} stroke="var(--chat-border)" strokeWidth="1" />
        <polyline fill="none" stroke={accent} strokeWidth="2.5" strokeLinejoin="round" points={pts.join(" ")} />
      </svg>
    );
  }

  if (scene === "unit_circle") {
    const theta = ((scope.theta ?? 45) * Math.PI) / 180;
    const cx = w / 2;
    const cy = h / 2;
    const rr = Math.min(w, h) * 0.32;
    const px = cx + rr * Math.cos(theta);
    const py = cy - rr * Math.sin(theta);
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="单位圆">
        <circle cx={cx} cy={cy} r={rr} fill="none" stroke="var(--chat-border-strong)" strokeWidth="1.5" />
        <line x1={cx - rr - 8} y1={cy} x2={cx + rr + 8} y2={cy} stroke="var(--chat-border)" />
        <line x1={cx} y1={cy - rr - 8} x2={cx} y2={cy + rr + 8} stroke="var(--chat-border)" />
        <line x1={cx} y1={cy} x2={px} y2={py} stroke={accent} strokeWidth="2" />
        <line x1={px} y1={cy} x2={px} y2={py} stroke={accent} strokeWidth="1.5" strokeDasharray="3 3" />
        <line x1={cx} y1={cy} x2={px} y2={cy} stroke={accent} strokeWidth="1.5" strokeDasharray="3 3" />
        <circle cx={px} cy={py} r="5" fill={accent} />
        <text x={px + 8} y={py - 8} fontSize="11" fill="var(--chat-text)">
          ({formatLabNumber(Math.cos(theta), "fixed:2")}, {formatLabNumber(Math.sin(theta), "fixed:2")})
        </text>
      </svg>
    );
  }

  if (scene === "number_line") {
    const x = scope.x ?? 0;
    const lo = -8;
    const hi = 8;
    const map = (v: number) => pad + ((v - lo) / (hi - lo)) * (w - pad * 2);
    const y = h / 2;
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="数轴">
        <line x1={pad} y1={y} x2={w - pad} y2={y} stroke="var(--chat-text)" strokeWidth="1.8" />
        {[-8, -4, 0, 4, 8].map((t) => (
          <g key={t}>
            <line x1={map(t)} y1={y - 7} x2={map(t)} y2={y + 7} stroke="var(--chat-text-soft)" />
            <text x={map(t)} y={y + 24} textAnchor="middle" fontSize="12" fill="var(--chat-text-soft)">
              {t}
            </text>
          </g>
        ))}
        <circle cx={map(x)} cy={y} r="7" fill={accent} />
        <text x={map(x)} y={y - 16} textAnchor="middle" fontSize="13" fontWeight="600" fill="var(--chat-text)">
          x = {formatLabNumber(x, "fixed:1")}
        </text>
      </svg>
    );
  }

  if (scene === "coordinate") {
    const px = scope.x ?? 0;
    const py = scope.y ?? 0;
    const lo = -5;
    const hi = 5;
    const mx = (v: number) => pad + ((v - lo) / (hi - lo)) * (w - pad * 2);
    const my = (v: number) => h - pad - ((v - lo) / (hi - lo)) * (h - pad * 2);
    return (
      <svg viewBox={`0 0 ${w} ${h}`} className="h-full w-full" role="img" aria-label="坐标">
        <line x1={pad} y1={my(0)} x2={w - pad} y2={my(0)} stroke="var(--chat-border-strong)" />
        <line x1={mx(0)} y1={pad} x2={mx(0)} y2={h - pad} stroke="var(--chat-border-strong)" />
        <line x1={mx(0)} y1={my(0)} x2={mx(px)} y2={my(py)} stroke={accent} strokeWidth="2" />
        <circle cx={mx(px)} cy={my(py)} r="6" fill={accent} />
        <text x={mx(px) + 10} y={my(py) - 10} fontSize="13" fill="var(--chat-text)">
          ({formatLabNumber(px, "fixed:1")}, {formatLabNumber(py, "fixed:1")})
        </text>
      </svg>
    );
  }

  return (
    <div
      className="flex items-center justify-center rounded-xl border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-[12px] text-[var(--chat-text-soft)]"
      style={{ minHeight: h }}
    >
      调整参数查看计算结果
    </div>
  );
}

const ParametricLab: FC<ParametricLabProps> = memo((props) => {
  const scene = resolveScene(props.scene, props.preset);
  const initialParams = useMemo(() => {
    const fromModel = normalizeParams(props.params);
    return fromModel.length ? fromModel : defaultParams(scene);
  }, [props.params, scene]);

  const outputDefs = useMemo(() => {
    const fromModel = normalizeOutputs(props.outputs);
    return fromModel.length ? fromModel : defaultOutputsSafe(scene);
  }, [props.outputs, scene]);

  const [values, setValues] = useState<Record<string, number>>(() => {
    const init: Record<string, number> = {};
    initialParams.forEach((p) => {
      init[p.id] = p.value ?? p.min ?? 0;
    });
    return init;
  });

  // When model tree remounts with new defaults, React key on parent remounts this component.
  const scope = useMemo(() => {
    const s: Record<string, number> = {};
    initialParams.forEach((p) => {
      const min = p.min ?? 0;
      const max = p.max ?? 10;
      const v = values[p.id];
      s[p.id] = clamp(Number.isFinite(v) ? v : (p.value ?? min), min, max);
    });
    return s;
  }, [initialParams, values]);

  const computed = useMemo(() => {
    const out: Record<string, number> = {};
    const display: Array<{ label: string; text: string; expr: string }> = [];
    for (const o of outputDefs) {
      const n = evalLabExpr(o.expr, scope);
      if (o.id) out[o.id] = n;
      display.push({
        label: o.label || o.id || o.expr,
        text: `${formatLabNumber(n, o.format)}${o.unit ? ` ${o.unit}` : ""}`,
        expr: o.expr,
      });
    }
    return { out, display };
  }, [outputDefs, scope]);

  const accent = props.accent || "var(--chat-accent)";
  const showFormulas = props.showFormulas !== false;
  const formula = props.formulaNote || defaultFormula(scene);
  const title =
    props.title ||
    (scene === "right_triangle"
      ? "勾股定理实验室"
      : scene === "circle"
        ? "圆的面积与周长"
        : scene === "unit_circle"
          ? "单位圆与三角函数"
          : "参数实验室");

  return (
    <div className="genui-parametric-lab overflow-hidden rounded-xl bg-[var(--chat-surface-soft)]">
      <div className="flex items-center justify-between gap-3 px-4 pt-3 pb-1">
        <div className="min-w-0">
          <div className="truncate text-[15px] font-semibold tracking-tight text-[var(--chat-text)]">
            {title}
          </div>
          {props.description ? (
            <div className="mt-0.5 truncate text-[12px] text-[var(--chat-text-soft)]">
              {props.description}
            </div>
          ) : null}
        </div>
        {showFormulas && formula ? (
          <div className="shrink-0 rounded-md bg-[var(--chat-accent-soft)] px-2.5 py-1 font-mono text-[13px] text-[var(--chat-accent)]">
            {formula}
          </div>
        ) : null}
      </div>

      <div
        className={classNames(
          "mx-3 mt-2 overflow-hidden rounded-xl bg-[var(--chat-surface)]",
          STAGE_CLASS,
          scene !== "custom_svg" && "ring-1 ring-[var(--chat-border)]/50"
        )}
        style={stageStyle(props.height)}
      >
        <SceneSvg
          scene={scene}
          scope={scope}
          outputs={computed.out}
          customSvg={props.svg}
          height={300}
          accent={accent.startsWith("var(") ? "#3b82f6" : accent}
        />
      </div>

      <div className="space-y-3 px-4 py-3">
        <div className={classNames(
          "grid gap-x-6 gap-y-2",
          initialParams.length > 1 ? "sm:grid-cols-2" : "grid-cols-1"
        )}>
          {initialParams.map((p) => {
            const min = p.min ?? 0;
            const max = p.max ?? 10;
            const step = p.step ?? 0.1;
            const val = scope[p.id] ?? min;
            return (
              <div key={p.id} className="min-w-0">
                <div className="mb-1 flex items-baseline justify-between gap-2 text-[12px]">
                  <label className="font-medium text-[var(--chat-text)]" htmlFor={`lab-${p.id}`}>
                    {p.label || p.id}
                  </label>
                  <span className="tabular-nums text-[var(--chat-text)]">
                    {formatLabNumber(val, "fixed:2")}
                    {p.unit ? (
                      <span className="ml-0.5 text-[var(--chat-text-soft)]">{p.unit}</span>
                    ) : null}
                  </span>
                </div>
                <input
                  id={`lab-${p.id}`}
                  type="range"
                  min={min}
                  max={max}
                  step={step}
                  value={val}
                  className="h-1.5 w-full cursor-pointer accent-[var(--chat-accent)]"
                  onChange={(e) => {
                    const next = parseFloat(e.target.value);
                    setValues((prev) => ({
                      ...prev,
                      [p.id]: clamp(next, min, max),
                    }));
                  }}
                />
              </div>
            );
          })}
        </div>

        {computed.display.length ? (
          <div className="flex flex-wrap gap-2">
            {computed.display.map((d) => (
              <div
                key={d.label + d.expr}
                className="min-w-[7.5rem] flex-1 rounded-lg bg-[var(--chat-surface)] px-3 py-2 ring-1 ring-[var(--chat-border)]/40"
              >
                <div className="text-[11px] text-[var(--chat-text-soft)]">{d.label}</div>
                <div className="mt-0.5 text-[18px] font-semibold tabular-nums tracking-tight text-[var(--chat-text)]">
                  {d.text}
                </div>
                {showFormulas ? (
                  <div className="mt-0.5 truncate font-mono text-[10px] text-[var(--chat-text-muted)]">
                    {d.expr}
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
});

ParametricLab.displayName = "ParametricLab";

export default ParametricLab;
