import { CheckIcon, Lightbulb } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type ThinkingEffort = "low" | "medium" | "high" | null;

type Props = {
  /** 当前模型是否声明支持思考；false 时不渲染 */
  supported: boolean;
  thinking: boolean;
  effort: ThinkingEffort;
  onChange: (thinking: boolean, effort: ThinkingEffort) => void;
  disabled?: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  triggerClassName?: (active?: boolean, disabled?: boolean) => string;
};

const OPTIONS: Array<{ key: ThinkingEffort; label: string }> = [
  { key: null, label: "关闭" },
  { key: "low", label: "低" },
  { key: "medium", label: "中" },
  { key: "high", label: "高" },
];

const ThinkingToggle: ReactorType.FC<Props> = ({
  supported,
  thinking,
  effort,
  onChange,
  disabled,
  open,
  onOpenChange,
  triggerClassName,
}) => {
  if (!supported) return null;

  const label = thinking
    ? OPTIONS.find((o) => o.key === effort)?.label ?? "开"
    : null;

  return (
    <DropdownMenu open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          disabled={disabled}
          className={
            triggerClassName
              ? triggerClassName(thinking, disabled)
              : undefined
          }
          title={thinking ? `深度思考：${label}` : "开启深度思考"}
        >
          <Lightbulb className="size-3.5 shrink-0 opacity-80" />
          {label ? (
            <span className="text-[11px] font-medium">{label}</span>
          ) : (
            <span className="truncate">思考</span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        side="bottom"
        sideOffset={10}
        className="w-[140px] rounded-[18px] border border-black/[0.04] bg-white p-1 shadow-[0_12px_40px_-16px_rgba(15,23,42,0.28)]"
      >
        <div className="px-2.5 pb-1 pt-1.5 text-[11px] font-medium text-[#86868b]">
          深度思考
        </div>
        {OPTIONS.map((o) => {
          const active = thinking ? effort === o.key : o.key === null;
          return (
            <button
              key={o.label}
              type="button"
              className={cn(
                "flex w-full items-center gap-1.5 rounded-[12px] px-2 py-1.5 text-left text-[12.5px] transition-colors",
                active ? "bg-[#f5f5f7]" : "hover:bg-[#f5f5f7]/80"
              )}
              onClick={() => {
                onChange(o.key !== null, o.key);
                onOpenChange(false);
              }}
            >
              <CheckIcon
                className={cn(
                  "size-3 shrink-0",
                  active ? "text-[#1d1d1f]" : "opacity-0"
                )}
              />
              {o.label}
            </button>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ThinkingToggle;
