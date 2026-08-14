export type ConceptStep = {
  id: string;
  title: string;
  caption: string;
  /** ms, default 2200 */
  duration: number;
  /** indices or ids of nodes/edges to emphasize */
  highlight: string[];
  /** optional formula / key phrase badge */
  badge?: string;
  /** scene-specific freeform payload */
  data?: Record<string, unknown>;
};

export type ConceptNode = {
  id: string;
  label: string;
  sublabel?: string;
};

export type ConceptEdge = {
  id: string;
  from: string;
  to: string;
  label?: string;
};

export type ConceptScene =
  | "flow"
  | "stack"
  | "tree"
  | "formula"
  | "compare"
  | "sequence";

export function resolveConceptScene(raw?: unknown): ConceptScene {
  const s = String(raw || "flow")
    .trim()
    .toLowerCase()
    .replace(/-/g, "_");
  if (s === "formula_transform" || s === "formula") return "formula";
  if (
    s === "flow" ||
    s === "stack" ||
    s === "tree" ||
    s === "compare" ||
    s === "sequence"
  ) {
    return s;
  }
  return "flow";
}

function asString(v: unknown, fallback = ""): string {
  if (v == null) return fallback;
  return String(v);
}

function asDuration(v: unknown, fallback: number): number {
  const n = Number(v);
  if (!Number.isFinite(n) || n < 400) return fallback;
  return Math.min(20000, Math.round(n));
}

export function normalizeConceptSteps(raw: unknown, defaultMs = 2200): ConceptStep[] {
  if (!Array.isArray(raw) || !raw.length) return [];
  return raw
    .map((item, i) => {
      if (typeof item === "string") {
        return {
          id: `step-${i}`,
          title: item,
          caption: "",
          duration: defaultMs,
          highlight: [],
        } satisfies ConceptStep;
      }
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      const highlightRaw = o.highlight ?? o.highlights ?? o.focus;
      const highlight = Array.isArray(highlightRaw)
        ? highlightRaw.map((h) => String(h))
        : typeof highlightRaw === "string" && highlightRaw
          ? highlightRaw.split(/[,\s]+/).filter(Boolean)
          : [];
      const data =
        o.data && typeof o.data === "object" && !Array.isArray(o.data)
          ? (o.data as Record<string, unknown>)
          : undefined;
      return {
        id: asString(o.id, `step-${i}`),
        title: asString(o.title || o.name || o.label, `步骤 ${i + 1}`),
        caption: asString(o.caption || o.description || o.text || o.body),
        duration: asDuration(o.duration ?? o.ms ?? o.delay, defaultMs),
        highlight,
        badge: o.badge != null ? asString(o.badge) : o.formula != null ? asString(o.formula) : undefined,
        data,
      } satisfies ConceptStep;
    })
    .filter((s): s is ConceptStep => Boolean(s));
}

export function normalizeConceptNodes(raw: unknown): ConceptNode[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item, i) => {
      if (typeof item === "string") {
        return { id: `n${i}`, label: item };
      }
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      const id = asString(o.id || o.key, `n${i}`);
      const label = asString(o.label || o.title || o.name || o.value, id);
      return {
        id,
        label,
        sublabel: o.sublabel != null ? asString(o.sublabel) : o.desc != null ? asString(o.desc) : undefined,
      };
    })
    .filter((n): n is ConceptNode => Boolean(n));
}

export function normalizeConceptEdges(raw: unknown): ConceptEdge[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item, i) => {
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      const from = asString(o.from || o.source || o.src);
      const to = asString(o.to || o.target || o.dst);
      if (!from || !to) return null;
      return {
        id: asString(o.id, `e${i}`),
        from,
        to,
        label: o.label != null ? asString(o.label) : undefined,
      };
    })
    .filter((e): e is ConceptEdge => Boolean(e));
}

export type FlowNodeBox = {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
};

export type FlowLayout = {
  width: number;
  height: number;
  boxes: FlowNodeBox[];
  boxById: Map<string, FlowNodeBox>;
};

const FLOW_PAD_X = 36;
const FLOW_PAD_Y = 44;
const FLOW_H_GAP = 56;
const FLOW_V_GAP = 88;
const FLOW_NODE_MIN_H = 44;
const FLOW_NODE_MAX_W = 188;
const FLOW_NODE_PAD_X = 22;
const FLOW_LINE_H = 15;
const FLOW_MAX_LINES = 3;
const FLOW_MAX_ROW_WIDTH = 720;

/** Approximate glyph advance for SVG 12px text (no faux-bold). */
export function measureConceptLabelChars(label: string): number {
  let w = 0;
  for (const ch of Array.from(label || "")) {
    if (/[\u3400-\u9fff]/.test(ch)) {
      w += 12.5;
    } else if (/[·•|/／、，。]/.test(ch)) {
      w += 5;
    } else if (/\s/.test(ch)) {
      w += 4;
    } else {
      w += 7.4;
    }
  }
  return w;
}

/**
 * Soft-wrap a node label so long CJK/Latin mix stays inside the pill.
 * Prefer breaks after space / slash / middot; otherwise hard-break by width.
 */
export function wrapConceptLabel(
  label: string,
  maxInnerWidth = FLOW_NODE_MAX_W - FLOW_NODE_PAD_X
): string[] {
  const text = (label || "").trim();
  if (!text) return [""];

  const tokens = text.split(/(\s+|\/|·|•|｜)/).filter((t) => t.length > 0);
  const lines: string[] = [];
  let current = "";
  let currentW = 0;

  const pushLine = () => {
    if (!current) return;
    lines.push(current);
    current = "";
    currentW = 0;
  };

  const appendChunk = (chunk: string) => {
    const cw = measureConceptLabelChars(chunk);
    if (current && currentW + cw > maxInnerWidth) {
      pushLine();
    }
    if (!current && cw > maxInnerWidth) {
      // Hard-break oversized single token by character.
      let piece = "";
      let pieceW = 0;
      for (const ch of Array.from(chunk)) {
        const chW = measureConceptLabelChars(ch);
        if (piece && pieceW + chW > maxInnerWidth) {
          lines.push(piece);
          piece = ch;
          pieceW = chW;
        } else {
          piece += ch;
          pieceW += chW;
        }
      }
      current = piece;
      currentW = pieceW;
      return;
    }
    current += chunk;
    currentW += cw;
  };

  for (const token of tokens) {
    appendChunk(token);
  }
  pushLine();

  if (lines.length <= FLOW_MAX_LINES) {
    return lines.length ? lines : [""];
  }
  const kept = lines.slice(0, FLOW_MAX_LINES);
  const last = kept[FLOW_MAX_LINES - 1];
  kept[FLOW_MAX_LINES - 1] =
    last.length > 1 ? `${Array.from(last).slice(0, -1).join("")}…` : "…";
  return kept;
}

export function estimateConceptNodeSize(label: string): {
  w: number;
  h: number;
  lines: string[];
} {
  const maxInner = FLOW_NODE_MAX_W - FLOW_NODE_PAD_X;
  const lines = wrapConceptLabel(label, maxInner);
  const maxLineW = Math.max(
    ...lines.map((line) => measureConceptLabelChars(line)),
    40
  );
  const w = Math.min(
    FLOW_NODE_MAX_W,
    Math.max(88, Math.round(maxLineW + FLOW_NODE_PAD_X))
  );
  const h = Math.max(
    FLOW_NODE_MIN_H,
    Math.round(lines.length * FLOW_LINE_H + 18)
  );
  return { w, h, lines };
}

export function estimateConceptLabelWidth(label: string): number {
  return estimateConceptNodeSize(label).w;
}

/** Wrap a long process left-to-right so nodes keep a readable size. */
export function layoutConceptFlow(
  nodes: ConceptNode[],
  opts?: { maxRowWidth?: number }
): FlowLayout {
  const maxRowWidth = Math.max(280, opts?.maxRowWidth ?? FLOW_MAX_ROW_WIDTH);
  const sizes = new Map(
    nodes.map((n) => [n.id, estimateConceptNodeSize(n.label)] as const)
  );
  const rows: ConceptNode[][] = [];
  let row: ConceptNode[] = [];
  let rowWidth = 0;

  for (const n of nodes) {
    const w = sizes.get(n.id)?.w ?? 88;
    const next = row.length === 0 ? w : rowWidth + FLOW_H_GAP + w;
    if (row.length > 0 && next > maxRowWidth - FLOW_PAD_X * 2) {
      rows.push(row);
      row = [n];
      rowWidth = w;
    } else {
      row.push(n);
      rowWidth = next;
    }
  }
  if (row.length) rows.push(row);

  const boxes: FlowNodeBox[] = [];
  let maxX = FLOW_PAD_X;
  let cursorY = FLOW_PAD_Y;
  rows.forEach((items) => {
    let x = FLOW_PAD_X;
    const rowH = Math.max(
      FLOW_NODE_MIN_H,
      ...items.map((n) => sizes.get(n.id)?.h ?? FLOW_NODE_MIN_H)
    );
    for (const n of items) {
      const size = sizes.get(n.id) || { w: 88, h: FLOW_NODE_MIN_H };
      boxes.push({
        id: n.id,
        x: x + size.w / 2,
        y: cursorY + rowH / 2,
        w: size.w,
        h: size.h,
      });
      x += size.w + FLOW_H_GAP;
      maxX = Math.max(maxX, x - FLOW_H_GAP);
    }
    cursorY += rowH + FLOW_V_GAP;
  });

  const width = Math.max(320, maxX + FLOW_PAD_X);
  const height = Math.max(160, cursorY - FLOW_V_GAP + FLOW_PAD_Y);
  return {
    width,
    height,
    boxes,
    boxById: new Map(boxes.map((b) => [b.id, b])),
  };
}

/** Built-in demos when model only sets scene + title */
export function defaultConceptDemo(scene: ConceptScene): {
  nodes: ConceptNode[];
  edges: ConceptEdge[];
  steps: ConceptStep[];
  formulas?: string[];
  left?: string[];
  right?: string[];
} {
  switch (scene) {
    case "stack":
      return {
        nodes: [
          { id: "l1", label: "应用层" },
          { id: "l2", label: "传输层" },
          { id: "l3", label: "网络层" },
          { id: "l4", label: "链路层" },
        ],
        edges: [],
        steps: [
          {
            id: "s0",
            title: "分层思想",
            caption: "复杂系统拆成上下叠放的层次，每层只关心相邻接口。",
            duration: 2400,
            highlight: [],
          },
          {
            id: "s1",
            title: "自顶向下",
            caption: "请求从应用层进入，逐层向下封装。",
            duration: 2600,
            highlight: ["l1", "l2"],
          },
          {
            id: "s2",
            title: "继续下沉",
            caption: "网络层负责寻址，链路层负责邻接传输。",
            duration: 2600,
            highlight: ["l3", "l4"],
          },
          {
            id: "s3",
            title: "完整栈",
            caption: "四层协同，上层不需要知道底层实现细节。",
            duration: 2400,
            highlight: ["l1", "l2", "l3", "l4"],
          },
        ],
      };
    case "tree":
      return {
        nodes: [
          { id: "root", label: "根" },
          { id: "a", label: "左子树" },
          { id: "b", label: "右子树" },
          { id: "a1", label: "叶" },
          { id: "a2", label: "叶" },
          { id: "b1", label: "叶" },
        ],
        edges: [
          { id: "e1", from: "root", to: "a" },
          { id: "e2", from: "root", to: "b" },
          { id: "e3", from: "a", to: "a1" },
          { id: "e4", from: "a", to: "a2" },
          { id: "e5", from: "b", to: "b1" },
        ],
        steps: [
          {
            id: "s0",
            title: "树结构",
            caption: "从唯一根节点出发，每个节点可以有若干子节点。",
            duration: 2200,
            highlight: ["root"],
          },
          {
            id: "s1",
            title: "展开子树",
            caption: "根的左右孩子构成两棵子树。",
            duration: 2400,
            highlight: ["root", "a", "b", "e1", "e2"],
          },
          {
            id: "s2",
            title: "到达叶子",
            caption: "没有孩子的节点称为叶子。",
            duration: 2400,
            highlight: ["a1", "a2", "b1"],
          },
        ],
      };
    case "formula":
      return {
        nodes: [],
        edges: [],
        formulas: ["a² + b²", "→", "c²"],
        steps: [
          {
            id: "s0",
            title: "直角边",
            caption: "先关注两条直角边 a、b 的平方和。",
            duration: 2200,
            highlight: ["f0"],
            badge: "a² + b²",
          },
          {
            id: "s1",
            title: "变换",
            caption: "勾股关系把平方和映射为斜边的平方。",
            duration: 2000,
            highlight: ["f1"],
            badge: "→",
          },
          {
            id: "s2",
            title: "斜边",
            caption: "得到 c²，开方即斜边长度。",
            duration: 2400,
            highlight: ["f2"],
            badge: "c²",
          },
          {
            id: "s3",
            title: "完整公式",
            caption: "a² + b² = c²，三边关系一目了然。",
            duration: 2600,
            highlight: ["f0", "f1", "f2"],
            badge: "a² + b² = c²",
          },
        ],
      };
    case "compare":
      return {
        nodes: [],
        edges: [],
        left: ["串行处理", "阻塞等待", "吞吐低"],
        right: ["并行流水", "重叠计算", "吞吐高"],
        steps: [
          {
            id: "s0",
            title: "两种策略",
            caption: "对比串行与并行在吞吐上的差异。",
            duration: 2200,
            highlight: [],
          },
          {
            id: "s1",
            title: "串行侧",
            caption: "任务排队依次完成，简单但容易空等。",
            duration: 2400,
            highlight: ["left"],
          },
          {
            id: "s2",
            title: "并行侧",
            caption: "阶段重叠执行，整体延迟下降、吞吐上升。",
            duration: 2400,
            highlight: ["right"],
          },
          {
            id: "s3",
            title: "结论",
            caption: "在可分解任务上，流水/并行通常更优。",
            duration: 2400,
            highlight: ["left", "right"],
          },
        ],
      };
    case "sequence":
      return {
        nodes: [
          { id: "t1", label: "输入" },
          { id: "t2", label: "处理" },
          { id: "t3", label: "校验" },
          { id: "t4", label: "输出" },
        ],
        edges: [
          { id: "e1", from: "t1", to: "t2" },
          { id: "e2", from: "t2", to: "t3" },
          { id: "e3", from: "t3", to: "t4" },
        ],
        steps: [
          {
            id: "s0",
            title: "流程概览",
            caption: "按时间顺序经历四个阶段。",
            duration: 2000,
            highlight: [],
          },
          {
            id: "s1",
            title: "接收输入",
            caption: "系统读取原始数据。",
            duration: 2000,
            highlight: ["t1"],
          },
          {
            id: "s2",
            title: "核心处理",
            caption: "执行变换或计算。",
            duration: 2000,
            highlight: ["t2", "e1"],
          },
          {
            id: "s3",
            title: "校验",
            caption: "检查结果是否满足约束。",
            duration: 2000,
            highlight: ["t3", "e2"],
          },
          {
            id: "s4",
            title: "输出",
            caption: "交付最终结果。",
            duration: 2200,
            highlight: ["t4", "e3"],
          },
        ],
      };
    case "flow":
    default:
      return {
        nodes: [
          { id: "n1", label: "请求" },
          { id: "n2", label: "网关" },
          { id: "n3", label: "服务" },
          { id: "n4", label: "存储" },
          { id: "n5", label: "响应" },
        ],
        edges: [
          { id: "e1", from: "n1", to: "n2", label: "HTTPS" },
          { id: "e2", from: "n2", to: "n3", label: "RPC" },
          { id: "e3", from: "n3", to: "n4", label: "读写" },
          { id: "e4", from: "n3", to: "n5", label: "结果" },
        ],
        steps: [
          {
            id: "s0",
            title: "请求进入",
            caption: "客户端发起请求，首先到达网关。",
            duration: 2200,
            highlight: ["n1", "e1", "n2"],
          },
          {
            id: "s1",
            title: "路由到服务",
            caption: "网关鉴权后转发到业务服务。",
            duration: 2200,
            highlight: ["n2", "e2", "n3"],
          },
          {
            id: "s2",
            title: "访问存储",
            caption: "服务读写数据库或缓存。",
            duration: 2200,
            highlight: ["n3", "e3", "n4"],
          },
          {
            id: "s3",
            title: "返回响应",
            caption: "组装结果并返回客户端。",
            duration: 2400,
            highlight: ["n3", "e4", "n5"],
          },
        ],
      };
  }
}
