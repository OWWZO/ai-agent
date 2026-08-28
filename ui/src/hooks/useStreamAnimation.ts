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
  const displayTextRef = useRef(text);
  const targetTextRef = useRef(text);
  const textRef = useRef<HTMLDivElement>(null);
  const lastScrollRef = useRef(0);
  const lastFrameTimeRef = useRef(0);
  const rafRef = useRef<number | undefined>(undefined);

  // 平滑的透明度动画
  const opacity = useMotionValue(0);
  const smoothOpacity = useSpring(opacity, {
    stiffness: 100,
    damping: 20,
  });

  // 字符高度动画（用于弹跳效果）
  const y = useMotionValue(0);
  const smoothY = useSpring(y, {
    stiffness: 300,
    damping: 20,
  });

  useEffect(() => {
    targetTextRef.current = text;

    // 流式期间只追赶新增文本，非流式或关闭动画时直接同步完整文本。
    if (!enabled) {
      displayTextRef.current = text;
      setDisplayText(text);
      setCharIndex(text.length);
      return;
    }

    if (!isStreaming) {
      displayTextRef.current = text;
      setDisplayText(text);
      setCharIndex(text.length);
      opacity.set(1);
      return;
    }

    // 目标可能因重连或切换消息而回退，先对齐共同前缀，避免显示旧内容。
    const currentText = displayTextRef.current;
    if (!text.startsWith(currentText)) {
      const commonLength = commonPrefixLength(currentText, text);
      displayTextRef.current = text.slice(0, commonLength);
      setDisplayText(displayTextRef.current);
      setCharIndex(commonLength);
    }

    const addChars = (timestamp: number) => {
      rafRef.current = undefined;
      const current = displayTextRef.current;
      const target = targetTextRef.current;
      if (current === target) return;

      const elapsed = timestamp - lastFrameTimeRef.current;
      const chunkSize = Math.max(1, Math.min(12, Math.floor((speed * Math.max(elapsed, 16)) / 1000)));
      const next = target.slice(0, current.length + chunkSize);
      displayTextRef.current = next;
      setDisplayText(next);
      setCharIndex(next.length);

      if (bounce) {
        y.set(-2);
        window.setTimeout(() => y.set(0), 100);
      }

      lastFrameTimeRef.current = timestamp;
      rafRef.current = requestAnimationFrame(addChars);
    };

    if (displayTextRef.current.length < targetTextRef.current.length && rafRef.current === undefined) {
      lastFrameTimeRef.current = performance.now();
      rafRef.current = requestAnimationFrame(addChars);
    }

    // 淡入效果
    if (fadeIn && opacity.get() < 1) {
      opacity.set(1);
    }

    return () => {
      // React 卸载或文本变化时取消未完成帧，防止异步回调在已失效组件上更新状态。
      if (rafRef.current !== undefined) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = undefined;
      }
    };
  }, [text, isStreaming, enabled, speed, bounce, fadeIn, opacity, y]);

  // 平滑滚动
  useEffect(() => {
    // 只有用户已经接近底部时才自动跟随，避免用户回看历史时被流式内容强行拉回；
    // 50ms 节流用于限制高频 token 事件触发的滚动布局计算。
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
  const smoothScale = useSpring(scale, {
    stiffness: 200,
    damping: 15,
  });

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
  const smoothOpacity = useSpring(opacity, {
    stiffness: 50,
    damping: 20,
  });

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

function commonPrefixLength(a: string, b: string): number {
  const length = Math.min(a.length, b.length);
  let index = 0;
  while (index < length && a.charCodeAt(index) === b.charCodeAt(index)) {
    index += 1;
  }
  return index;
}
