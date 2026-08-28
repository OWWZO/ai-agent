import { CheckIcon, ChevronDownIcon, Cpu } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import type { LlmModelRecord } from "@/services/llmModelAdmin";

type Props = {
  models: LlmModelRecord[];
  value: string;
  onChange: (modelId: string) => void;
  disabled?: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  triggerClassName?: (active?: boolean, disabled?: boolean) => string;
};

const ModelPicker: ReactorType.FC<Props> = ({
  models,
  value,
  onChange,
  disabled,
  open,
  onOpenChange,
  triggerClassName,
}) => {
  if (!models.length) return null;

  const current =
    models.find((m) => m.modelId === value || m.modelName === value) ??
    models[0];

  return (
    <DropdownMenu open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          disabled={disabled}
          className={
            triggerClassName
              ? triggerClassName(Boolean(value), disabled)
              : undefined
          }
          title="选择本轮模型"
        >
          <Cpu className="size-3.5 shrink-0 opacity-80" />
          <span className="max-w-[100px] truncate">
            {current?.modelName || "模型"}
          </span>
          <ChevronDownIcon className="size-3.5 shrink-0 opacity-50" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        side="bottom"
        sideOffset={10}
        className="w-[240px] rounded-[18px] border border-black/[0.04] bg-white p-1 shadow-[0_12px_40px_-16px_rgba(15,23,42,0.28)]"
      >
        <div className="px-2.5 pb-1 pt-1.5 text-[11px] font-medium text-[#86868b]">
          模型
        </div>
        <div className="space-y-0.5">
          {models.map((m) => {
            const active =
              m.modelId === value || m.modelName === value;
            return (
              <button
                key={m.id}
                type="button"
                className={cn(
                  "flex w-full items-center gap-2 rounded-[12px] px-2 py-2 text-left transition-colors",
                  active ? "bg-[#f5f5f7]" : "hover:bg-[#f5f5f7]/80"
                )}
                onClick={() => {
                  onChange(m.modelId);
                  onOpenChange(false);
                }}
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-[13.5px] font-medium text-[#1d1d1f]">
                    {m.modelName}
                  </span>
                  <span className="mt-0.5 block text-[11px] leading-4 text-[#86868b]">
                    {m.modelId}
                    {m.supportsThinking ? " · 思考" : ""}
                    {m.contextWindow
                      ? ` · ${(m.contextWindow / 1000).toFixed(0)}k`
                      : ""}
                  </span>
                </span>
                {active ? (
                  <CheckIcon className="size-3.5 shrink-0 text-[#1d1d1f]" />
                ) : null}
              </button>
            );
          })}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ModelPicker;
