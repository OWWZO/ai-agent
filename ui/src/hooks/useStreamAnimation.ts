import { useEffect, useRef, useState, useCallback } from "react";
import { useSpring, useMotionValue, animate } from "motion/react";

export interface StreamAnimationOptions {
  /** 是否启用流式动画 */
  enabled?: boolean;
  /** 打字速度 (字符/秒) */
  speed?: number;
  /** 是否启用淡入效果 */
  fadeIn?: boolean;
  /** 淡入持续时间 (ms) */
  fadeInDuration?: number;
  /** 是否启用字符弹跳效果 */
  bounce?: boolean;
  /** 是否启用平滑滚动 */
  smoothScroll?: boolean;
}

/**
 * 流式文本动画 Hook
 * 提供平滑的打字机效果和滚动跟随
 */
export const useStreamAnimation = (
  text: string,
  isStreaming: boolean,
  options: StreamAnimationOptions = {}
) => {
  const {
    enabled = true,
    speed = 30,
    fadeIn = true,
    bounce = false,
    smoothScroll = true,
  } = options;

  const [displayText, setDisplayText] = useState(text);
  const [charIndex, setCharIndex] = useState(0);
  const textRef = useRef<HTMLDivElement>(null);
  const lastScrollRef = useRef(0);
  const rafRef = useRef<number | undefined>(undefined);

  // 平滑的透明度动画
  const opacity = useMotionValue(0);
  const smoothOpacity = useSpring(opacity, { stiffness: 100, damping: 20 });

  // 字符高度动画（用于弹跳效果）
  const y = useMotionValue(0);
  const smoothY = useSpring(y, { stiffness: 300, damping: 20 });

  useEffect(() => {
    if (!enabled) {
      setDisplayText(text);
      return;
    }

    if (!isStreaming) {
      setDisplayText(text);
      setCharIndex(text.length);
      opacity.set(1);
      return;
    }

    // 流式显示逻辑
    const targetLength = text.length;
    const currentLength = displayText.length;

    if (targetLength > currentLength) {
      const charsToAdd = targetLength - currentLength;

      let i = 0;
      const addChars = () => {
        if (i < charsToAdd) {
          const chunkSize = Math.min(3, charsToAdd - i);
          setDisplayText((prev) => text.slice(0, prev.length + chunkSize));
          setCharIndex((prev) => prev + chunkSize);
          i += chunkSize;

          // 触发弹跳动画
          if (bounce) {
            y.set(-2);
            setTimeout(() => y.set(0), 100);
          }

          // 使用 RAF 进行下一帧更新
          rafRef.current = requestAnimationFrame(addChars);
        }
      };

      rafRef.current = requestAnimationFrame(addChars);
    }

    // 淡入效果
    if (fadeIn && opacity.get() < 1) {
      opacity.set(1);
    }

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [text, isStreaming, enabled, speed, bounce, fadeIn, opacity, y, displayText.length]);

  // 平滑滚动
  useEffect(() => {
    if (!smoothScroll || !isStreaming || !textRef.current) return;

    const now = Date.now();
    if (now - lastScrollRef.current < 50) return; // 限制滚动频率

    lastScrollRef.current = now;
    const element = textRef.current;
    const parent = element.parentElement;

    if (parent) {
      const isNearBottom = parent.scrollHeight - parent.scrollTop - parent.clientHeight < 100;
      if (isNearBottom) {
        parent.scrollTo({
          top: parent.scrollHeight,
          behavior: "smooth",
        });
      }
    }
  }, [displayText, isStreaming, smoothScroll]);

  // 手动滚动到底部
  const scrollToBottom = useCallback(() => {
    if (textRef.current?.parentElement) {
      const parent = textRef.current.parentElement;
      animate(parent.scrollTop, parent.scrollHeight, {
        duration: 0.3,
        onUpdate: (value) => {
          parent.scrollTop = value;
        },
      });
    }
  }, []);

  return {
    displayText,
    charIndex,
    textRef,
    opacity: smoothOpacity,
    y: smoothY,
    scrollToBottom,
  };
};

/**
 * 使用平滑的数字动画
 */
export const useSmoothNumber = (
  target: number,
  options: { delay?: number } = {}
) => {
  const { delay = 0 } = options;
  const motionValue = useMotionValue(0);
  const springValue = useSpring(motionValue, {
    stiffness: 100,
    damping: 30,
    restDelta: 0.001,
  });

  useEffect(() => {
    const timeout = setTimeout(() => {
      motionValue.set(target);
    }, delay * 1000);

    return () => clearTimeout(timeout);
  }, [target, motionValue, delay]);

  return springValue;
};

/**
 * 脉冲动画 Hook
 */
export const usePulseAnimation = (isActive: boolean, intensity: number = 1) => {
  const scale = useMotionValue(1);
  const smoothScale = useSpring(scale, { stiffness: 200, damping: 15 });

  useEffect(() => {
    if (!isActive) {
      scale.set(1);
      return;
    }

    let direction = 1;
    const doAnimate = () => {
      const maxScale = 1 + 0.05 * intensity;
      const minScale = 1 - 0.02 * intensity;

      if (direction === 1) {
        scale.set(maxScale);
      } else {
        scale.set(minScale);
      }

      direction *= -1;
    };

    const interval = setInterval(doAnimate, 800);
    return () => clearInterval(interval);
  }, [isActive, intensity, scale]);

  return smoothScale;
};

/**
 * 交错动画 Hook
 */
export const useStaggerAnimation = (itemCount: number, baseDelay: number = 0.05) => {
  const [visibleItems, setVisibleItems] = useState<number[]>([]);

  useEffect(() => {
    const timeouts: number[] = [];

    for (let i = 0; i < itemCount; i++) {
      const timeout = window.setTimeout(() => {
        setVisibleItems((prev) => [...prev, i]);
      }, i * baseDelay * 1000);
      timeouts.push(timeout);
    }

    return () => timeouts.forEach(clearTimeout);
  }, [itemCount, baseDelay]);

  return visibleItems;
};

/**
 * 呼吸动画 Hook
 */
export const useBreathingAnimation = (isActive: boolean) => {
  const opacity = useMotionValue(0.5);
  const smoothOpacity = useSpring(opacity, { stiffness: 50, damping: 20 });

  useEffect(() => {
    if (!isActive) {
      opacity.set(1);
      return;
    }

    let direction = 1;
    const interval = setInterval(() => {
      if (direction === 1) {
        opacity.set(0.8);
      } else {
        opacity.set(0.4);
      }
      direction *= -1;
    }, 1500);

    return () => clearInterval(interval);
  }, [isActive, opacity]);

  return smoothOpacity;
};

export default useStreamAnimation;
