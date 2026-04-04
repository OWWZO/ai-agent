"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";
import { ChevronDown } from "lucide-react";
import type { ComponentProps } from "react";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { Shimmer } from "./shimmer";

type PlanContextValue = {
  isStreaming: boolean;
};

const PlanContext = createContext<PlanContextValue | null>(null);

const usePlan = () => {
  const context = useContext(PlanContext);
  if (!context) {
    throw new Error("Plan components must be used within Plan");
  }
  return context;
};

/** 每次从非流式重新进入流式时递增，强制 Shimmer 重挂载，避免 motion 第二次循环把字「洗没」 */
function useShimmerRemountKey(isStreaming: boolean) {
  const [key, setKey] = useState(0);
  const wasStreamingRef = useRef(false);

  useEffect(() => {
    if (isStreaming && !wasStreamingRef.current) {
      setKey((k) => k + 1);
    }
    wasStreamingRef.current = isStreaming;
  }, [isStreaming]);

  return key;
}

export type PlanProps = ComponentProps<typeof Collapsible> & {
  isStreaming?: boolean;
};

export const Plan = ({
  className,
  isStreaming = false,
  children,
  ...props
}: PlanProps) => (
  <PlanContext.Provider value={{ isStreaming }}>
    <Collapsible asChild data-slot="plan" {...props}>
      <Card
        className={cn(
          "gap-0 rounded-2xl border-0 bg-[var(--chat-surface-soft)]/92 py-0 text-[var(--chat-text)] shadow-[var(--shadow-sm)] ring-0",
          className
        )}
      >
        {children}
      </Card>
    </Collapsible>
  </PlanContext.Provider>
);

export type PlanHeaderProps = ComponentProps<typeof CardHeader>;

export const PlanHeader = ({ className, ...props }: PlanHeaderProps) => (
  <CardHeader
    className={cn(
      "flex flex-row items-center justify-between gap-3 space-y-0 px-5 pb-3 pt-4",
      className
    )}
    data-slot="plan-header"
    {...props}
  />
);

export type PlanTitleProps = Omit<
  ComponentProps<typeof CardTitle>,
  "children"
> & {
  children: string;
};

export const PlanTitle = ({ children, className, ...props }: PlanTitleProps) => {
  const { isStreaming } = usePlan();
  const shimmerKey = useShimmerRemountKey(isStreaming);

  return (
    <CardTitle
      className={cn(
        "text-[15px] font-semibold leading-snug tracking-[-0.02em] text-[var(--chat-text)]",
        className
      )}
      data-slot="plan-title"
      {...props}
    >
      {isStreaming ? (
        <Shimmer key={shimmerKey} as="span">
          {children}
        </Shimmer>
      ) : (
        children
      )}
    </CardTitle>
  );
};

export type PlanDescriptionProps = Omit<
  ComponentProps<typeof CardDescription>,
  "children"
> & {
  children: string;
};

export const PlanDescription = ({
  className,
  children,
  ...props
}: PlanDescriptionProps) => {
  const { isStreaming } = usePlan();
  const shimmerKey = useShimmerRemountKey(isStreaming);

  return (
    <CardDescription
      className={cn("text-balance", className)}
      data-slot="plan-description"
      {...props}
    >
      {isStreaming ? (
        <Shimmer key={shimmerKey} as="span">
          {children}
        </Shimmer>
      ) : (
        children
      )}
    </CardDescription>
  );
};

export type PlanActionProps = ComponentProps<typeof CardAction>;

export const PlanAction = (props: PlanActionProps) => (
  <CardAction data-slot="plan-action" {...props} />
);

export type PlanContentProps = ComponentProps<typeof CardContent>;

export const PlanContent = ({ className, ...props }: PlanContentProps) => (
  <CollapsibleContent asChild>
    <CardContent
      className={cn("space-y-2 px-5 pb-5 pt-0", className)}
      data-slot="plan-content"
      {...props}
    />
  </CollapsibleContent>
);

export type PlanFooterProps = ComponentProps<"div">;

export const PlanFooter = (props: PlanFooterProps) => (
  <CardFooter data-slot="plan-footer" {...props} />
);

export type PlanTriggerProps = ComponentProps<typeof CollapsibleTrigger>;

export const PlanTrigger = ({ className, ...props }: PlanTriggerProps) => (
  <CollapsibleTrigger asChild>
    <Button
      className={cn(
        "size-9 shrink-0 rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] data-[state=open]:bg-[var(--chat-surface-muted)]/80 [&_svg]:transition-transform data-[state=open]:[&_svg]:rotate-180",
        className
      )}
      data-slot="plan-trigger"
      size="icon"
      variant="ghost"
      {...props}
    >
      <ChevronDown className="size-4" strokeWidth={2} />
      <span className="sr-only">展开或收起任务进度</span>
    </Button>
  </CollapsibleTrigger>
);
