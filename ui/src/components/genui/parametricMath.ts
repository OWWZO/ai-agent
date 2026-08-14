/**
 * Safe math for ParametricLab outputs.
 * Only numbers, param ids, + - * / ^ ( ), and a fixed function set.
 */

const FN_NAMES = new Set([
  "sqrt",
  "abs",
  "sin",
  "cos",
  "tan",
  "asin",
  "acos",
  "atan",
  "min",
  "max",
  "pow",
  "log",
  "ln",
  "exp",
  "floor",
  "ceil",
  "round",
  "hypot",
]);

const CONST_NAMES = new Set(["pi", "e", "tau"]);

export type LabParam = {
  id: string;
  label?: string;
  value?: number;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
};

export type LabOutput = {
  id?: string;
  label?: string;
  expr: string;
  format?: string;
  unit?: string;
};

const IDENT = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

export function clamp(n: number, min: number, max: number): number {
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, n));
}

export function normalizeParams(raw: unknown): LabParam[] {
  if (!Array.isArray(raw)) return [];
  const out: LabParam[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const o = item as Record<string, unknown>;
    const id = String(o.id || o.name || "").trim();
    if (!id || !IDENT.test(id) || FN_NAMES.has(id) || CONST_NAMES.has(id)) continue;
    const min = Number.isFinite(Number(o.min)) ? Number(o.min) : 0;
    const max = Number.isFinite(Number(o.max)) ? Number(o.max) : Math.max(min + 1, 10);
    const step = Number.isFinite(Number(o.step)) && Number(o.step) > 0 ? Number(o.step) : 0.1;
    const value = Number.isFinite(Number(o.value))
      ? clamp(Number(o.value), min, max)
      : clamp((min + max) / 2, min, max);
    out.push({
      id,
      label: o.label != null ? String(o.label) : id,
      value,
      min,
      max,
      step,
      unit: o.unit != null ? String(o.unit) : undefined,
    });
  }
  return out;
}

export function normalizeOutputs(raw: unknown): LabOutput[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((item) => item && typeof item === "object")
    .map((item) => {
      const o = item as Record<string, unknown>;
      return {
        id: o.id != null ? String(o.id) : undefined,
        label: o.label != null ? String(o.label) : undefined,
        expr: String(o.expr || o.formula || o.value || ""),
        format: o.format != null ? String(o.format) : undefined,
        unit: o.unit != null ? String(o.unit) : undefined,
      };
    })
    .filter((o) => o.expr.trim().length > 0);
}

/** Rewrite ^ to ** and allow unicode √ as sqrt */
function preprocess(expr: string): string {
  return expr
    .replace(/√\s*\(/g, "sqrt(")
    .replace(/√\s*([a-zA-Z_][a-zA-Z0-9_]*)/g, "sqrt($1)")
    .replace(/√\s*(\d+(?:\.\d+)?)/g, "sqrt($1)")
    .replace(/\^/g, "**")
    .replace(/π/g, "pi");
}

function tokenize(src: string): string[] | null {
  const tokens: string[] = [];
  let i = 0;
  while (i < src.length) {
    const ch = src[i];
    if (/\s/.test(ch)) {
      i += 1;
      continue;
    }
    if ("+-*/(),".includes(ch)) {
      tokens.push(ch);
      i += 1;
      continue;
    }
    if (ch === "*" && src[i + 1] === "*") {
      tokens.push("**");
      i += 2;
      continue;
    }
    if (/\d/.test(ch) || (ch === "." && /\d/.test(src[i + 1] || ""))) {
      let j = i + 1;
      while (j < src.length && /[\d.]/.test(src[j])) j += 1;
      const num = src.slice(i, j);
      if (!/^\d+(\.\d+)?$/.test(num) && !/^\.\d+$/.test(num)) return null;
      tokens.push(num);
      i = j;
      continue;
    }
    if (/[a-zA-Z_]/.test(ch)) {
      let j = i + 1;
      while (j < src.length && /[a-zA-Z0-9_]/.test(src[j])) j += 1;
      tokens.push(src.slice(i, j));
      i = j;
      continue;
    }
    return null;
  }
  return tokens;
}

function validateTokens(
  tokens: string[],
  scopeKeys: Set<string>
): boolean {
  for (const t of tokens) {
    if (t === "**" || "+-*/(),".includes(t)) continue;
    if (/^\d+(\.\d+)?$/.test(t) || /^\.\d+$/.test(t)) continue;
    if (FN_NAMES.has(t) || CONST_NAMES.has(t) || scopeKeys.has(t)) continue;
    return false;
  }
  return true;
}

const MATH_FNS: Record<string, (...args: number[]) => number> = {
  sqrt: (x) => Math.sqrt(x),
  abs: (x) => Math.abs(x),
  sin: (x) => Math.sin(x),
  cos: (x) => Math.cos(x),
  tan: (x) => Math.tan(x),
  asin: (x) => Math.asin(x),
  acos: (x) => Math.acos(x),
  atan: (x) => Math.atan(x),
  min: (...xs) => Math.min(...xs),
  max: (...xs) => Math.max(...xs),
  pow: (a, b) => Math.pow(a, b),
  log: (x) => Math.log10(x),
  ln: (x) => Math.log(x),
  exp: (x) => Math.exp(x),
  floor: (x) => Math.floor(x),
  ceil: (x) => Math.ceil(x),
  round: (x) => Math.round(x),
  hypot: (...xs) => Math.hypot(...xs),
};

/**
 * Evaluate a math expression against a numeric scope.
 * Returns NaN on invalid / unsafe expressions.
 */
export function evalLabExpr(
  expr: string,
  scope: Record<string, number>
): number {
  if (!expr || typeof expr !== "string") return NaN;
  const prepared = preprocess(expr.trim());
  if (!prepared) return NaN;
  const tokens = tokenize(prepared);
  if (!tokens || !tokens.length) return NaN;
  const keys = new Set(Object.keys(scope));
  if (!validateTokens(tokens, keys)) return NaN;

  // Rebuild a JS expression using only validated tokens.
  let js = "";
  for (let i = 0; i < tokens.length; i += 1) {
    const t = tokens[i];
    if (t === "**") {
      js += "**";
      continue;
    }
    if (FN_NAMES.has(t)) {
      js += `__fn.${t}`;
      continue;
    }
    if (CONST_NAMES.has(t)) {
      js += `__c.${t}`;
      continue;
    }
    if (keys.has(t)) {
      js += `__s[${JSON.stringify(t)}]`;
      continue;
    }
    js += t;
  }

  try {
    // eslint-disable-next-line no-new-func
    const fn = new Function(
      "__s",
      "__fn",
      "__c",
      `"use strict"; return (${js});`
    );
    const result = fn(scope, MATH_FNS, {
      pi: Math.PI,
      e: Math.E,
      tau: Math.PI * 2,
    });
    return typeof result === "number" && Number.isFinite(result) ? result : NaN;
  } catch {
    return NaN;
  }
}

export function formatLabNumber(
  value: number,
  format?: string,
  digits = 3
): string {
  if (!Number.isFinite(value)) return "—";
  if (format === "int") return String(Math.round(value));
  if (format === "percent") return `${(value * 100).toFixed(1)}%`;
  if (format && /^fixed:(\d+)$/.test(format)) {
    const d = Number(RegExp.$1);
    return value.toFixed(Math.min(8, Math.max(0, d)));
  }
  if (Math.abs(value) >= 1000 || (Math.abs(value) > 0 && Math.abs(value) < 0.001)) {
    return value.toExponential(2);
  }
  const rounded = Number(value.toFixed(digits));
  return String(rounded);
}

export function interpolateTemplate(
  template: string,
  scope: Record<string, number>,
  outputs: Record<string, number>
): string {
  if (!template) return "";
  const all = { ...scope, ...outputs };
  return template.replace(/\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}|\$([a-zA-Z_][a-zA-Z0-9_]*)/g, (_, a, b) => {
    const key = a || b;
    const v = all[key];
    return Number.isFinite(v) ? String(v) : "0";
  });
}
