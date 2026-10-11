/**
 * 「已工作 4 分 24 秒」的时长写法。
 *
 * 移植自 ZCode `packages/ui/src/lib/workDuration.ts`，规则逐条对齐：
 * 秒向最近取整、至少 1 秒；天 / 时 / 分 / 秒里只写非零的，**最多两段**
 * （`1 小时 3 分`，不再写秒）；中文单位与数字之间留一个空格。
 *
 * 与原实现的差异：本仓库没有 i18n 框架（见 `plans/026`），
 * 单位词直接内联成中文常量，不做 `formatMessage`。
 */

interface WorkDurationPart {
  value: number;
  /** 单位词（`分` / `秒`）。 */
  unit: string;
}

/** 中文单位与数字之间留空格以保持可读（对齐 ZCode `workDurationUnitSeparator`）。 */
const UNIT_SEPARATOR = " ";

const UNITS = {
  day: "天",
  hour: "小时",
  minute: "分",
  second: "秒",
} as const;

/**
 * 拆成 `[{ value, unit }]`，给要把数字与单位分开排的地方用。
 *
 * 例：`264000 → [{4,"分"},{24,"秒"}]`、`3600000 → [{1,"小时"}]`。
 */
export function workDurationParts(durationMs: number): WorkDurationPart[] {
  const totalSeconds = Math.max(1, Math.round(durationMs / 1000));
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  const parts: WorkDurationPart[] = [];
  if (days > 0) parts.push({ value: days, unit: UNITS.day });
  if (hours > 0) parts.push({ value: hours, unit: UNITS.hour });
  if (minutes > 0) parts.push({ value: minutes, unit: UNITS.minute });
  if (seconds > 0 || parts.length === 0) {
    parts.push({ value: seconds, unit: UNITS.second });
  }
  return parts.slice(0, 2);
}

/** 拼成一行文案：`4 分 24 秒` / `1 小时 3 分` / `42 秒`。 */
export function formatWorkDurationParts(parts: readonly WorkDurationPart[]): string {
  return parts.map((part) => `${part.value}${UNIT_SEPARATOR}${part.unit}`).join(" ");
}

/**
 * 直接格式化毫秒为「4 分 24 秒」。
 *
 * 非法输入（`undefined` / `NaN` / 负数）返回 `undefined`，由调用方决定是否展示，
 * 避免渲染出 `NaN 秒`。
 */
export function formatWorkDuration(durationMs?: number): string | undefined {
  if (typeof durationMs !== "number" || !Number.isFinite(durationMs) || durationMs < 0) {
    return undefined;
  }
  return formatWorkDurationParts(workDurationParts(durationMs));
}

/**
 * 把后端给的时间戳（毫秒字符串 `"1714041600000"` 或 ISO 串）解析成毫秒数。
 *
 * 运行中状态条要用它算「已工作 X」；两种格式在历史数据里都出现过
 * （见 `agentProcessModel.test.ts` 的 `startedAt: "1714041600000"` 与
 * `view.test.tsx` 的 `startedAt: "2026-07-06T10:00:00"`）。
 */
export function parseTimestampMs(value?: string | number | null): number | undefined {
  if (value == null) return undefined;
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : undefined;
  }
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const numeric = Number(trimmed);
  if (Number.isFinite(numeric)) return numeric;
  const parsed = Date.parse(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
