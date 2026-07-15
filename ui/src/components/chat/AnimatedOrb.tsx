import type { CSSProperties } from "react";

import { cn } from "@/lib/utils";

type AnimatedOrbProps = {
  className?: string;
  variant?: "default" | "red";
  size?: number;
};

export function AnimatedOrb({
  className,
  variant = "default",
  size = 32,
}: AnimatedOrbProps) {
  const colors =
    variant === "red"
      ? {
        bg: "#fef2f2",
        circle1: "#ef4444",
        circle2: "#f87171",
        circle3: "#dc2626",
        circle4: "#fca5a5",
        circle5: "#fb7185",
      }
      : {
        bg: "#cff1f4",
        circle1: "#9e9fef",
        circle2: "#c471ec",
        circle3: "#9bc761",
        circle4: "#ccd4f2",
        circle5: "#f472b6",
      };

  const blurAmount = Math.max(6, size * 0.15);
  const circle1Size = size * 0.45;
  const circle2Size = size * 0.35;
  const circle3Size = size * 0.5;
  const circle4Size = size * 0.25;
  const circle5Size = size * 0.3;

  return (
    <div
      className={cn("relative overflow-hidden rounded-full", className)}
      style={{
        width: size,
        height: size,
        backgroundColor: colors.bg,
        animation: "orb-hue-rotate 8s linear infinite",
        boxShadow: "rgba(17, 12, 46, 0.15) 0px 48px 100px 0px",
      }}
      aria-hidden="true"
    >
      <div
        className="absolute inset-0 flex items-center justify-center"
        style={
          {
            "--orb-blur": `${blurAmount}px`,
            animation: "orb-hue-rotate-blur 6s linear infinite reverse",
          } as CSSProperties
        }
      >
        <div
          className="orb-circle-1 absolute rounded-full"
          style={{
            width: circle1Size,
            height: circle1Size,
            opacity: 0.9,
            backgroundColor: colors.circle1,
          }}
        />
        <div
          className="orb-circle-2 absolute rounded-full"
          style={{
            width: circle2Size,
            height: circle2Size,
            opacity: 0.85,
            backgroundColor: colors.circle2,
          }}
        />
        <div
          className="orb-circle-3 absolute rounded-full"
          style={{
            width: circle3Size,
            height: circle3Size,
            opacity: 0.9,
            backgroundColor: colors.circle3,
          }}
        />
        <div
          className="orb-circle-4 absolute rounded-full"
          style={{
            width: circle4Size,
            height: circle4Size,
            opacity: 0.8,
            backgroundColor: colors.circle4,
          }}
        />
        <div
          className="orb-circle-5 absolute rounded-full"
          style={{
            width: circle5Size,
            height: circle5Size,
            opacity: 0.85,
            backgroundColor: colors.circle5,
          }}
        />
      </div>

      <div
        className="pointer-events-none absolute inset-0 rounded-full"
        style={{
          background:
            "linear-gradient(to bottom, rgba(255, 255, 255, 0.4) 0%, transparent 100%)",
        }}
      />
    </div>
  );
}
