import { CheckIcon, ChevronDownIcon, Cpu } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import type { CatalogModelRecord } from "@/services/catalog";

type Props = {
  models: CatalogModelRecord[];
  value: string;
  onChange: (modelId: string) => void;
  disabled?: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  triggerClassName?: (active?: boolean, disabled?: boolean) => string;
};

const ModelPicker: CuteType.FC<Props> = ({
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
        <div className="px-2.5 pb-1 pt-1.5 text-ui-xs font-medium text-foreground-subtle">
          模型
        </div>
        <div className="space-y-0.5">
          {models.map((m) => {
            const active =
              m.modelId === value || m.modelName === value;
            return (
              <button
                key={m.modelId}
                type="button"
                className={cn(
                  "flex w-full items-center gap-2 rounded-[12px] px-2 py-2 text-left transition-colors",
                  active ? "bg-hover" : "hover:bg-hover/80"
                )}
                onClick={() => {
                  onChange(m.modelId);
                  onOpenChange(false);
                }}
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-ui-base font-medium text-foreground">
                    {m.modelName}
                  </span>
                  <span className="mt-0.5 block text-ui-xs leading-4 text-foreground-subtle">
                    {m.modelId}
                    {m.supportsThinking ? " · 思考" : ""}
                    {m.contextWindow
                      ? ` · ${(m.contextWindow / 1000).toFixed(0)}k`
                      : ""}
                  </span>
                </span>
                {active ? (
                  <CheckIcon className="size-3.5 shrink-0 text-foreground" />
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
