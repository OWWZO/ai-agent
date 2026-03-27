"use client";

import { cn } from "@/lib/utils";
import { motion } from "motion/react";
import {
  type CSSProperties,
  type ElementType,
  type JSX,
  memo,
  useMemo,
} from "react";

export type TextShimmerProps = {
  children: string;
  as?: ElementType;
  className?: string;
  duration?: number;
  spread?: number;
};

const ShimmerComponent = ({
  children,
  as: Component = "p",
  className,
  duration = 2,
  spread = 2,
}: TextShimmerProps) => {
  const MotionComponent = useMemo(
    () => motion.create(Component as keyof JSX.IntrinsicElements),
    [Component]
  );

  const dynamicSpread = useMemo(
    () => (children?.length ?? 0) * spread,
    [children, spread]
  );

  return (
    <MotionComponent
      animate={{ backgroundPosition: ["220% center", "-220% center"] }}
      className={cn(
        "relative inline-block bg-clip-text text-transparent",
        className
      )}
      initial={false}
      style={
        {
          backgroundImage:
            "linear-gradient(90deg, var(--color-muted-foreground) 0%, var(--color-muted-foreground) 42%, rgba(255,255,255,0.98) 50%, var(--color-muted-foreground) 58%, var(--color-muted-foreground) 100%)",
          backgroundSize: `${Math.max(240, dynamicSpread + 220)}% 100%`,
          backgroundRepeat: "no-repeat",
          willChange: "background-position",
        } as CSSProperties
      }
      transition={{
        repeat: Number.POSITIVE_INFINITY,
        duration,
        ease: "linear",
        repeatType: "loop",
      }}
    >
      {children}
    </MotionComponent>
  );
};

export const Shimmer = memo(ShimmerComponent);
