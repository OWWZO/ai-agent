import type { ReactNode } from "react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

/**
 * tab 标题的悬停提示（移植 ZCode `SidePaneTabTitleTooltip.tsx`）。
 *
 * 契约三条，缺一条手感就不对：
 * - `delayDuration={1500}`：标题本身是**渐隐**裁切而不是省略号，不悬停看不到全名，
 *   所以提示不能太急；1500ms 是 ZCode 的取值。
 * - `skipDelayDuration={0}`：关掉「刚看过一个就立刻显示下一个」的快通道 ——
 *   鼠标横扫一排 tab 时不该连续弹提示。
 * - `side="bottom"`：提示在 tab 条下方，不遮住 tab 自己。
 *
 * 组件内部自带 Provider（ZCode 也是每个 tab 一个）：这样调用方只需替换原来的
 * `<button>`，不用在 tab 列表外面多套一层容器、也不必给整块内容重排缩进。
 */
export function TabTitleTooltip({
  label,
  onActivate,
  children,
}: {
  label: string;
  onActivate: () => void;
  children: ReactNode;
}) {
  return (
    <TooltipProvider delayDuration={1500} skipDelayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            onClick={onActivate}
            className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
          >
            {children}
          </button>
        </TooltipTrigger>
        <TooltipContent side="bottom" sideOffset={6}>
          {label}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
