import { type ReactNode, useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";

/**
 * 摘要文案上滚（移植 ZCode `ToolCallBlocks/QueuedSummaryContent.tsx`）。
 *
 * 工具摘要里的文案在流式期间会不断刷新（「生成参数中…」→ 真实路径 → 文件名）。
 * 直接硬切会让摘要行频繁闪跳；这里让旧文案上移淡出、新文案自下方升起，
 * 并且**排队播放**：一次滚动 = 300ms 过渡 + 500ms 停留，最多排 2 条待播，
 * 中途到达的新文案只替换第三格，不会堆成一条长队列。
 *
 * 主线程繁忙时 `setTimeout` 会晚到。如果继续逐条补播旧摘要，
 * 卡顿恢复后用户会看到一串过期状态排队滚过 —— 所以漂移超过 250ms 时直接跳到最新一条。
 */
const SUMMARY_ROLL_TRANSITION_MS = 300;
const SUMMARY_ROLL_HOLD_MS = 500;
const SUMMARY_ROLL_TOTAL_MS = SUMMARY_ROLL_TRANSITION_MS + SUMMARY_ROLL_HOLD_MS;
const SUMMARY_ROLL_TIMER_DRIFT_SKIP_MS = 250;
const SUMMARY_ROLL_MAX_PENDING = 2;
const SUMMARY_ROLL_TRANSITION = {
  duration: SUMMARY_ROLL_TRANSITION_MS / 1000,
  ease: [0.4, 0, 0.2, 1],
} as const;

function getCurrentTimestamp() {
  if (typeof performance !== "undefined") {
    return performance.now();
  }
  return Date.now();
}

function resolveQueuedSummaryPlaybackQueue<T>(queuedContent: readonly T[], timerDriftMs: number): T[] {
  if (timerDriftMs > SUMMARY_ROLL_TIMER_DRIFT_SKIP_MS && queuedContent.length > 1) {
    return queuedContent.slice(-1);
  }
  return [...queuedContent];
}

function usePrefersReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return;
    }

    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setPrefersReducedMotion(query.matches);
    update();

    if (typeof query.addEventListener === "function") {
      query.addEventListener("change", update);
      return () => query.removeEventListener("change", update);
    }

    query.addListener(update);
    return () => query.removeListener(update);
  }, []);

  return prefersReducedMotion;
}

interface SummaryContentSnapshot {
  key: string;
  refreshVersion?: string;
  primaryText: ReactNode;
  secondaryText?: ReactNode;
  trailingText?: ReactNode;
}

function shouldRefreshQueuedSummaryContent(
  current: Pick<SummaryContentSnapshot, "key" | "refreshVersion" | "trailingText">,
  next: Pick<SummaryContentSnapshot, "key" | "refreshVersion" | "trailingText">
) {
  return (
    current.key === next.key &&
    (current.trailingText !== next.trailingText || current.refreshVersion !== next.refreshVersion)
  );
}

export function QueuedSummaryContent({
  contentKey,
  contentRefreshVersion,
  primaryText,
  secondaryText,
  trailingText,
  enabled,
  disableAnimation = false,
}: {
  contentKey: string;
  contentRefreshVersion?: string;
  primaryText: ReactNode;
  secondaryText?: ReactNode;
  trailingText?: ReactNode;
  enabled: boolean;
  disableAnimation?: boolean;
}) {
  const reducedMotion = usePrefersReducedMotion();
  const shouldAnimate = enabled && disableAnimation !== true && !reducedMotion;
  const createSnapshot = (): SummaryContentSnapshot => ({
    key: contentKey,
    refreshVersion: contentRefreshVersion,
    primaryText,
    secondaryText,
    trailingText,
  });
  const [displayedContent, setDisplayedContent] = useState(createSnapshot);
  const displayedContentRef = useRef(displayedContent);
  const queuedContentRef = useRef<SummaryContentSnapshot[]>([]);
  const animationTimerRef = useRef<number | null>(null);
  const isAnimatingRef = useRef(false);

  useEffect(() => {
    displayedContentRef.current = displayedContent;
  }, [displayedContent]);

  useEffect(() => {
    const clearAnimationTimer = () => {
      if (animationTimerRef.current !== null) {
        window.clearTimeout(animationTimerRef.current);
        animationTimerRef.current = null;
      }
    };
    const enqueue = (snapshot: SummaryContentSnapshot) => {
      const queue = queuedContentRef.current;
      const existingIndex = queue.findIndex((item) => item.key === snapshot.key);
      if (existingIndex >= 0) {
        // 同一条摘要正在等待播放时，持续到达的变化只更新同一快照：
        // 既不能因为 key 去重留下最早的计数，也不能为每次数值变化新增整条滚动项。
        const nextQueue = [...queue];
        nextQueue[existingIndex] = snapshot;
        queuedContentRef.current = nextQueue;
        return;
      }

      if (queue.length === 0) {
        queuedContentRef.current = [snapshot];
        return;
      }

      // 摘要滚动总共只能有三格：当前显示、下一条、可插队条。
      // queuedContentRef 只保存后两格；第二条不能被覆盖，新摘要只能替换第三格。
      queuedContentRef.current = [queue[0]!, snapshot].slice(0, SUMMARY_ROLL_MAX_PENDING);
    };
    const promote = (snapshot: SummaryContentSnapshot) => {
      displayedContentRef.current = snapshot;
      setDisplayedContent(snapshot);
      isAnimatingRef.current = true;
      clearAnimationTimer();
      const expectedTimerAt = getCurrentTimestamp() + SUMMARY_ROLL_TOTAL_MS;
      animationTimerRef.current = window.setTimeout(() => {
        isAnimatingRef.current = false;
        animationTimerRef.current = null;
        const queuedContent = queuedContentRef.current;
        const timerDrift = getCurrentTimestamp() - expectedTimerAt;
        const nextQueue = resolveQueuedSummaryPlaybackQueue(queuedContent, timerDrift);
        const [nextQueued, ...restQueued] = nextQueue;
        if (!nextQueued) {
          queuedContentRef.current = [];
          return;
        }
        queuedContentRef.current = restQueued;
        promote(nextQueued);
      }, SUMMARY_ROLL_TOTAL_MS);
    };
    const nextContent = createSnapshot();

    if (!shouldAnimate) {
      clearAnimationTimer();
      isAnimatingRef.current = false;
      queuedContentRef.current = [];
      displayedContentRef.current = nextContent;
      setDisplayedContent(nextContent);
      return;
    }

    if (displayedContentRef.current.key === nextContent.key) {
      // 同一 key 的变化不能重播整条摘要：只在尾部计数或显式 version 变化时原位刷新正文。
      if (shouldRefreshQueuedSummaryContent(displayedContentRef.current, nextContent)) {
        displayedContentRef.current = nextContent;
        setDisplayedContent(nextContent);
      }
      return;
    }

    if (isAnimatingRef.current || queuedContentRef.current.length > 0) {
      enqueue(nextContent);
      return;
    }

    promote(nextContent);
    // createSnapshot 每次渲染都会重建（依赖 primaryText 等 ReactNode），
    // 放进依赖数组会让整个 effect 每帧重跑，等于关掉排队逻辑 —— 与 ZCode 同款处理。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contentKey, contentRefreshVersion, shouldAnimate, trailingText]);

  useEffect(() => {
    return () => {
      if (animationTimerRef.current !== null) {
        window.clearTimeout(animationTimerRef.current);
      }
    };
  }, []);

  if (!shouldAnimate) {
    return (
      <>
        {primaryText}
        {secondaryText}
        {trailingText}
      </>
    );
  }

  return (
    <span className="kimi-tool-row-roll">
      <AnimatePresence initial={false} mode="popLayout">
        <motion.span
          key={displayedContent.key}
          className="kimi-tool-row-roll-item"
          initial={{
            y: "0.8em",
            opacity: 0,
          }}
          animate={{
            y: 0,
            opacity: 1,
          }}
          exit={{
            y: "-0.8em",
            opacity: 0,
          }}
          transition={SUMMARY_ROLL_TRANSITION}
        >
          {displayedContent.primaryText}
          {displayedContent.secondaryText}
        </motion.span>
      </AnimatePresence>
      {/* 变更计数与文件摘要属于同一排队快照，但数字自身继续独立翻动，
          不跟着整条摘要一起纵向滚走。 */}
      {displayedContent.trailingText}
    </span>
  );
}
