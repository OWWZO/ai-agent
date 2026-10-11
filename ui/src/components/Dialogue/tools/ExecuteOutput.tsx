import { useLayoutEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

/** 底部判定容差：小于该像素数视为「已在底部」。 */
const AT_BOTTOM_THRESHOLD = 8;

/**
 * 命令输出区（对齐 ZCode `renderers/ExecuteOutput.tsx`）。
 *
 * 三件行为级动效，纯 CSS 做不到：
 * 1. **程序吸底**：流式增长时把 `scrollTop` 顶到 `scrollHeight`，用户始终看到最新输出；
 * 2. **用户上滚即冻结**：一旦用户主动往上滚（且不在底部），停止跟随并把当前内容冻结，
 *    否则新输出会把用户的阅读位置顶走；
 * 3. **回到底部恢复跟随**：滚回底部后自动解除冻结。
 *
 * `previousTop` 记录「浏览器实际落点」而不是我们请求的位置 —— 程序吸底后若内容变短，
 * `scrollTop` 会被浏览器钳制，此时若还拿期望值比较，会把程序滚动误判成用户上滚。
 */
export function ExecuteOutput({
  text,
  running,
  className,
}: {
  text: string;
  running: boolean;
  className?: string;
}) {
  const scroll = useRef<HTMLDivElement>(null);
  const previousTop = useRef(0);
  const hasStreamed = useRef(running);
  const [frozen, setFrozen] = useState<string | null>(null);

  const following = frozen === null;
  const display = frozen ?? text;

  useLayoutEffect(() => {
    if (running) hasStreamed.current = true;
    if (!hasStreamed.current || !following || !scroll.current) return;
    scroll.current.scrollTop = scroll.current.scrollHeight;
    // 吸底后记录浏览器实际位置，避免尾窗变短时把程序滚动误判为上滚。
    previousTop.current = scroll.current.scrollTop;
  }, [display, following, running]);

  return (
    <div
      ref={scroll}
      data-testid="bash-output-scroll"
      data-following={following}
      tabIndex={0}
      // 原预览与结果的高度上限不同且不吸底；共用五行上限，短内容自适应，结束时保留阅读状态。
      className={cn(
        "max-h-[5lh] min-w-0 max-w-full flex-none overflow-auto leading-5",
        className
      )}
      onScroll={(event) => {
        const el = event.currentTarget;
        const atBottom =
          el.scrollHeight - el.scrollTop - el.clientHeight <= AT_BOTTOM_THRESHOLD;
        if (
          hasStreamed.current &&
          following &&
          el.scrollTop < previousTop.current &&
          !atBottom
        ) {
          setFrozen(display);
        } else if (!following && atBottom) {
          setFrozen(null);
        }
        previousTop.current = el.scrollTop;
      }}
    >
      <pre
        data-testid={running ? "bash-output-preview-full" : "bash-result-output"}
        className="whitespace-pre-wrap break-words font-mono text-ui-base leading-5 text-foreground-subtle"
      >
        {display}
      </pre>
    </div>
  );
}
