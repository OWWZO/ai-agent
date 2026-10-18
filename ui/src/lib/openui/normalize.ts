/**
 * 程序体本地纠偏：模型写歪的 DSL，在浏览器里就地掰正。
 *
 * <p>与 `segments.ts` 里的 `normalizeOpenUiFences` / `normalizeOpenUiMarkers` 是同一类东西——
 * 提示词已经写明了正确写法，模型还是会自创形态。差别是那两个修的是 marker 外壳，
 * 这个修的是程序体内部。</p>
 *
 * <p>动机是成本：纠偏命中就不需要打 Autofix（按次计费、不支持流式，且每次都要带上
 * 53KB 的组件库 config 块）。像 `@//set/(` 这种错误，一次字符串替换就够了。</p>
 *
 * <p>三条规则，都只做**往合法枚举里收**的替换，不做语义改写：</p>
 * <ol>
 *   <li>action 写成了路径形态 `@//set/(` → `@Set(`；</li>
 *   <li>内置函数写成了路径形态 `@//clamp/(` → `@Clamp(`（模型常把 `@Clamp` / `@Count`
 *       和 action 混成同一种路径记法——函数比 action 更常见，实战里两者会同时出现）；</li>
 *   <li>`Text` 用了只有 `Title` 才支持的字号 → 钳到 `Text` 的取值上界 `lg`。</li>
 * </ol>
 *
 * <p>两者都是有损的（第 3 条会改小字号），所以调用方必须用「原文不可渲染 + 纠偏后可渲染」
 * 才采用的策略：纠不动就回退原文继续走 Autofix，绝不会把本就能渲染的程序改坏。</p>
 */

/** 已知 action 步骤的规范名（小写 → 规范写法）。与 `prompt-resident.txt` 的 Action 段一致。 */
const ACTION_STEPS: Record<string, string> = {
  set: "Set",
  reset: "Reset",
  toassistant: "ToAssistant",
  openurl: "OpenUrl",
  copytoclipboard: "CopyToClipboard",
};

/** 已知内置函数的规范名（小写 → 规范写法）。与 `prompt-resident.txt` 的函数段一致。 */
const BUILTIN_FUNCTIONS: Record<string, string> = {
  append: "Append",
  avg: "Avg",
  clamp: "Clamp",
  count: "Count",
  default: "Default",
  each: "Each",
  filter: "Filter",
  format: "Format",
  formatdate: "FormatDate",
  includes: "Includes",
  join: "Join",
  percent: "Percent",
  plural: "Plural",
  range: "Range",
  remove: "Remove",
  round: "Round",
  sum: "Sum",
  today: "Today",
  toggle: "Toggle",
};

/**
 * `@//set/(` / `@//clamp/(` —— 模型把 action 或内置函数记成了路径语法。
 * 结尾的斜杠是误加的，名字与括号之间可能夹空白。
 * 只匹配紧跟 `(` 的调用形态，避免碰到恰好以 `@//` 开头的字符串字面量。
 */
const MALFORMED_ACTION = /@\/\/\s*([A-Za-z]+)\s*\/\s*\(/g;

/**
 * `Text(..., "3xl", ...)` —— `Text` 的 size 只接受 xs/sm/md/lg，xl 及以上是 `Title` 才有的。
 * 模型想要大号数字却选错了组件。这里不换组件（会丢 weight / tabularNums 等参数），
 * 只把 size 钳到上界 `lg`。
 *
 * 中间的 `(?:...)` 把「完整字符串」和「完整括号组」当作整体吞掉，所以逗号不会在字符串
 * 或嵌套调用里被误当作参数分隔符（`Text("a, b", "3xl")` 也能正确命中第二个参数）。
 */
const TEXT_OVERSIZED_SIZE = /(\bText\s*\((?:[^"(),]|"[^"]*"|\([^()]*\))*?,\s*)"(xl|2xl|3xl|4xl)"/g;

/** Text 的 size 取值上界。 */
const TEXT_MAX_SIZE = "lg";

/**
 * 把程序体里写歪的地方改回规范写法。
 *
 * <p>返回 null 表示"没动过"，调用方据此跳过后续判断。只认 {@link ACTION_STEPS} /
 * {@link BUILTIN_FUNCTIONS} 里的名字，未知名字原样保留——宁可漏纠，也不要把没见过的写法
 * 改成别的意思。</p>
 *
 * <p>注意这里不做引号感知：一段展示文本里如果恰好含 `@//set/(`，也会被替换。调用方的
 * 「纠偏后可渲染才采用」策略会把这种误伤挡回去。</p>
 */
export function normalizeOpenUiProgram(text: string): string | null {
  if (!text.includes("@//") && !/\bText\s*\(/.test(text)) return null;

  let rewritten = false;

  const withActions = text.replace(MALFORMED_ACTION, (whole, rawName: string) => {
    const key = rawName.toLowerCase();
    const canonical = ACTION_STEPS[key] || BUILTIN_FUNCTIONS[key];
    if (!canonical) return whole;
    rewritten = true;
    return `@${canonical}(`;
  });

  const out = withActions.replace(TEXT_OVERSIZED_SIZE, (_whole, prefix: string) => {
    rewritten = true;
    return `${prefix}"${TEXT_MAX_SIZE}"`;
  });

  return rewritten ? out : null;
}
