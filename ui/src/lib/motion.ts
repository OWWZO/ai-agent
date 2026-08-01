import { useReducedMotion } from "motion/react";

/** Strong ease-out for UI enters (AUDIT --ease-out). */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;

/** Strong ease-in-out for on-screen movement (AUDIT --ease-in-out). */
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export const DURATION = {
  press: 0.15,
  tooltip: 0.15,
  dropdown: 0.2,
  panel: 0.25,
  modal: 0.3,
  message: 0.22,
  reduced: 0.12,
} as const;

/** Shared motion config for high-traffic surfaces. */
export function useMotionConfig() {
  const reduce = !!useReducedMotion();
  return {
    reduce,
    duration: reduce ? DURATION.reduced : DURATION.message,
    ease: EASE_OUT,
    fade: reduce
      ? {
        initial: { opacity: 0 },
        animate: { opacity: 1 },
        exit: { opacity: 0 },
      }
      : {
        initial: {
          opacity: 0,
          y: 8
        },
        animate: {
          opacity: 1,
          y: 0
        },
        exit: {
          opacity: 0,
          y: -6
        },
      },
    /** When reduce is true, do not pass this as transition.repeat. */
    loop: reduce ? 0 : Number.POSITIVE_INFINITY,
  };
}
