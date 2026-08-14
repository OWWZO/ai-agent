import { evalLabExpr, interpolateTemplate, normalizeParams } from "./parametricMath";

const SKIP_KEYS = new Set([
  "bind",
  "name",
  "action",
  "params",
  "state",
  "outputs",
  "options",
  "steps",
  "children",
]);

const TOKEN = /^\{\{\s*([\s\S]+?)\s*\}\}$/;
const DOLLAR = /^\$([a-zA-Z_][a-zA-Z0-9_]*)$/;

export function evalBoundToken(
  expr: string,
  scope: Record<string, number>
): number | string {
  const n = evalLabExpr(expr, scope);
  if (Number.isFinite(n)) return n;
  return interpolateTemplate(expr, scope, {});
}

export function resolveBoundValue(
  value: unknown,
  scope: Record<string, number>
): unknown {
  if (typeof value === "string") {
    const trimmed = value.trim();
    const m = trimmed.match(TOKEN);
    if (m) return evalBoundToken(m[1], scope);
    const d = trimmed.match(DOLLAR);
    if (d) {
      const n = scope[d[1]];
      return Number.isFinite(n) ? n : value;
    }
    if (/\{\{|\$[a-zA-Z_]/.test(value)) {
      return interpolateTemplate(value, scope, {});
    }
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((item) => resolveBoundValue(item, scope));
  }
  if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    const next: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(obj)) {
      if (SKIP_KEYS.has(k)) {
        next[k] = v;
      } else if (k === "values" || k === "expr") {
        next[k] = resolveBoundValue(v, scope);
      } else {
        next[k] = resolveBoundValue(v, scope);
      }
    }
    return next;
  }
  return value;
}

export function resolveBoundProps(
  props: Record<string, unknown> | undefined,
  scope: Record<string, number>
): Record<string, any> {
  if (!props) return {};
  const next: Record<string, any> = {};
  for (const [k, v] of Object.entries(props)) {
    next[k] = SKIP_KEYS.has(k) ? v : resolveBoundValue(v, scope);
  }
  return next;
}

export function derivedScope(
  params: ReturnType<typeof normalizeParams>,
  values: Record<string, number>,
  outputs: unknown
): Record<string, number> {
  const scope: Record<string, number> = {};
  for (const p of params) {
    const v = values[p.id];
    scope[p.id] = Number.isFinite(v) ? v : (p.value ?? p.min ?? 0);
  }
  if (Array.isArray(outputs)) {
    for (const item of outputs) {
      if (!item || typeof item !== "object") continue;
      const o = item as Record<string, unknown>;
      const id = String(o.id || "").trim();
      const expr = String(o.expr || o.formula || "");
      if (!id || !expr) continue;
      const n = evalLabExpr(expr, scope);
      if (Number.isFinite(n)) scope[id] = n;
    }
  }
  return scope;
}
