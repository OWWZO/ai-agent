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
  as: Component = "span",
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

  const bgSize = `${Math.max(240, dynamicSpread + 220)}% 100%`;

  return (
    <MotionComponent
      animate={{ backgroundPosition: ["220% center", "-220% center"] }}
      className={cn("relative inline-block align-baseline", className)}
      initial={{ backgroundPosition: "220% center" }}
      style={
        {
          backgroundImage:
            "linear-gradient(90deg, var(--color-muted-foreground) 0%, var(--color-muted-foreground) 36%, var(--chat-text) 49.5%, var(--chat-text) 50.5%, var(--color-muted-foreground) 64%, var(--color-muted-foreground) 100%)",
          backgroundSize: bgSize,
          backgroundPosition: "220% center",
          backgroundRepeat: "no-repeat",
          WebkitBackgroundClip: "text",
          backgroundClip: "text",
          color: "transparent",
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
