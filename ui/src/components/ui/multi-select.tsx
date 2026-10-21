import { useMemo, useState } from "react";
import { Check, ChevronsUpDown, X } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

export interface MultiSelectOption {
  label: string;
  value: string;
}

interface MultiSelectProps {
  options: MultiSelectOption[];
  value: string[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /** 已选项超过该数量时折叠成「N 项」，避免撑高表单。 */
  maxVisibleTags?: number;
}

/**
 * 可搜索的多选下拉。
 *
 * 替代 antd `Select mode="multiple"`：工具清单可能上百项，必须能搜索；
 * 已选项以标签展示，逐个可移除。
 */
export function MultiSelect({
  options,
  value,
  onChange,
  placeholder = "请选择",
  disabled,
  className,
  maxVisibleTags = 3,
}: MultiSelectProps) {
  const [open, setOpen] = useState(false);

  const selected = useMemo(
    () => options.filter((option) => value.includes(option.value)),
    [options, value]
  );
  const visibleTags = selected.slice(0, maxVisibleTags);
  const hiddenCount = selected.length - visibleTags.length;

  const toggle = (optionValue: string) => {
    onChange(
      value.includes(optionValue)
        ? value.filter((item) => item !== optionValue)
        : [...value, optionValue]
    );
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn(
            "h-auto min-h-8 w-full justify-between gap-1 px-2 py-1 font-normal",
            className
          )}
        >
          <span className="flex min-w-0 flex-wrap items-center gap-1">
            {visibleTags.length === 0 ? (
              <span className="text-muted-foreground">{placeholder}</span>
            ) : (
              visibleTags.map((option) => (
                <Badge key={option.value} variant="secondary" className="gap-1">
                  {option.label}
                  <span
                    role="button"
                    tabIndex={0}
                    aria-label={`移除 ${option.label}`}
                    className="rounded-sm opacity-60 hover:opacity-100"
                    onClick={(event) => {
                      event.stopPropagation();
                      toggle(option.value);
                    }}
                    onKeyDown={(event) => {
                      if (event.key !== "Enter" && event.key !== " ") return;
                      event.preventDefault();
                      event.stopPropagation();
                      toggle(option.value);
                    }}
                  >
                    <X className="size-3" />
                  </span>
                </Badge>
              ))
            )}
            {hiddenCount > 0 ? (
              <Badge variant="outline">+{hiddenCount}</Badge>
            ) : null}
          </span>
          <ChevronsUpDown className="size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command>
          <CommandInput placeholder="搜索…" />
          <CommandList>
            <CommandEmpty>没有匹配项</CommandEmpty>
            <CommandGroup>
              {options.map((option) => {
                const isSelected = value.includes(option.value);
                return (
                  <CommandItem
                    key={option.value}
                    value={option.value}
                    onSelect={() => toggle(option.value)}
                  >
                    <Check
                      className={cn(
                        "size-4",
                        isSelected ? "opacity-100" : "opacity-0"
                      )}
                    />
                    {option.label}
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
