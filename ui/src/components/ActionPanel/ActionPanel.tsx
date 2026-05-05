import React, { memo, useEffect, useMemo, useRef } from "react";
import classNames from "classnames";
import { motion } from "motion/react";
import { useMsgTypes } from "./useMsgTypes";
import HTMLRenderer from "./HTMLRenderer";
import useContent from "./useContent";
import MarkdownRenderer from "./MarkdownRenderer";
import TableRenderer from "./TableRenderer";
import FileRenderer from "./FileRenderer";
import ImageRenderer from "./ImageRenderer";
import SearchListRenderer from "./SearchListRenderer";
import { JsonViewer } from "./JsonViewer";
import { PanelItemType } from "./type";
import { PanelProvider } from ".";
import { useMemoizedFn } from "ahooks";
import { getPrimaryTaskFile } from "@/utils/taskArtifacts";
import { resolvePanelView } from "./panelResolver";

interface ActionPanelProps {
  taskItem?: PanelItemType;
  allowShowToolBar?: boolean;
  className?: string;
  noPadding?: boolean;
}

// 内容包装动画组件
const ContentWrapper = ({ children }: { children: React.ReactNode }) => (
  <motion.div
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -6 }}
    transition={{
      duration: 0.22,
      ease: [0.25, 0.46, 0.45, 0.94],
    }}
    className="h-full"
  >
    {children}
  </motion.div>
);

// Markdown 流式内容动画包装
const StreamingMarkdownWrapper = memo(({
  content,
  isStreaming,
}: {
  content: string;
  isStreaming: boolean;
}) => {
  return (
    <div
      className="flex min-h-full flex-col"
    >
      <MarkdownRenderer
        markDownContent={content}
        isStreaming={isStreaming}
        normalizationScope="default"
      />
      <div
        aria-hidden
        className="shrink-0 transition-[height] duration-300 ease-out"
        style={{
          height: isStreaming ? "clamp(180px, 34vh, 320px)" : "24px",
        }}
      />
    </div>
  );
}, (prevProps, nextProps) => {
  return (
    prevProps.content === nextProps.content &&
    prevProps.isStreaming === nextProps.isStreaming
  );
});

StreamingMarkdownWrapper.displayName = "StreamingMarkdownWrapper";

const ActionPanel: ReactorType.FC<ActionPanelProps> = React.memo((props) => {
  const { taskItem, className, allowShowToolBar } = props;

  const msgTypes = useMsgTypes(taskItem);
  const { markDownContent } = useContent(taskItem);

  const { resultMap, toolResult } = taskItem || {};
  const primaryFile = useMemo(() => getPrimaryTaskFile(taskItem), [taskItem]);
  const htmlUrl = primaryFile?.url;
  const downloadHtmlUrl = primaryFile?.downloadUrl;
  const missingReason = primaryFile?.missingReason;

  const { codeOutput } = resultMap || {};

  const panelView = useMemo(() => {
    return resolvePanelView({
      taskItem,
      msgTypes,
      markDownContent,
      htmlUrl,
      downloadHtmlUrl,
      missingReason,
      allowShowToolBar,
      isFinal: resultMap?.isFinal,
      codeOutput,
      toolResultText: toolResult?.toolResult,
      primaryFile,
    });
  }, [
    taskItem,
    msgTypes,
    markDownContent,
    htmlUrl,
    downloadHtmlUrl,
    missingReason,
    allowShowToolBar,
    resultMap?.isFinal,
    toolResult?.toolResult,
    primaryFile,
    codeOutput,
  ]);

  const panelNode = useMemo(() => {
    switch (panelView.type) {
      case "empty":
        return null;
      case "search":
        return (
          <ContentWrapper key="search">
            <SearchListRenderer list={panelView.searchList} />
          </ContentWrapper>
        );
      case "html":
        return (
          <ContentWrapper key="html">
            <HTMLRenderer
              htmlUrl={panelView.htmlUrl}
              className="h-full"
              downloadUrl={panelView.downloadUrl}
              missingReason={panelView.missingReason}
              outputCode={panelView.outputCode}
              showToolBar={panelView.showToolBar}
              isStreaming={panelView.isStreaming}
            />
          </ContentWrapper>
        );
      case "inline-html":
        return (
          <ContentWrapper key="code">
            <HTMLRenderer htmlUrl={panelView.htmlUrl} />
          </ContentWrapper>
        );
      case "excel":
        return (
          <ContentWrapper key="excel">
            <TableRenderer
              fileUrl={panelView.fileUrl}
              fileName={panelView.fileName}
              missingReason={panelView.missingReason}
            />
          </ContentWrapper>
        );
      case "image":
        return (
          <ContentWrapper key="image">
            <ImageRenderer
              imageUrl={panelView.fileUrl}
              fileName={panelView.fileName}
              missingReason={panelView.missingReason}
            />
          </ContentWrapper>
        );
      case "file":
        return (
          <ContentWrapper key="file">
            <FileRenderer
              fileUrl={panelView.fileUrl}
              fileName={panelView.fileName}
              missingReason={panelView.missingReason}
            />
          </ContentWrapper>
        );
      case "json":
        return (
          <ContentWrapper key="json">
            <JsonViewer data={panelView.jsonData} />
          </ContentWrapper>
        );
      case "markdown":
        return (
          <StreamingMarkdownWrapper
            content={panelView.content}
            isStreaming={panelView.isStreaming}
          />
        );
      default:
        return null;
    }
  }, [panelView]);

  const ref = useRef<HTMLDivElement>(null);
  const shouldAutoFollowRef = useRef(true);
  const autoScrollFrameRef = useRef<number | null>(null);
  const autoScrollTargetRef = useRef(0);
  const programmaticScrollRef = useRef(false);
  const lastScrollTopRef = useRef(0);

  const cancelAutoScrollFrame = useMemoizedFn(() => {
    if (autoScrollFrameRef.current !== null) {
      cancelAnimationFrame(autoScrollFrameRef.current);
      autoScrollFrameRef.current = null;
    }
  });

  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const resumeFollowThreshold = 32;

    const updateAutoFollowState = () => {
      if (programmaticScrollRef.current) {
        lastScrollTopRef.current = element.scrollTop;
        return;
      }

      const currentScrollTop = element.scrollTop;
      const distanceToBottom =
        element.scrollHeight - currentScrollTop - element.clientHeight;
      const isBackToBottom = distanceToBottom <= resumeFollowThreshold;
      const isUserScrollingUp = currentScrollTop < lastScrollTopRef.current - 1;

      // 用户主动往上翻历史时，立即停止自动跟随。
      if (isUserScrollingUp) {
        shouldAutoFollowRef.current = false;
        cancelAutoScrollFrame();
      }

      // 用户自己滑回到底部后，恢复自动跟随。
      if (isBackToBottom) {
        shouldAutoFollowRef.current = true;
      }

      lastScrollTopRef.current = currentScrollTop;
    };

    const handleWheel = (event: WheelEvent) => {
      if (event.deltaY >= 0) {
        return;
      }

      // 滚轮向上说明用户想看更早内容，优先尊重用户操作。
      shouldAutoFollowRef.current = false;
      programmaticScrollRef.current = false;
      cancelAutoScrollFrame();
    };

    lastScrollTopRef.current = element.scrollTop;
    updateAutoFollowState();
    element.addEventListener("wheel", handleWheel, { passive: true });
    element.addEventListener("scroll", updateAutoFollowState, { passive: true });

    return () => {
      element.removeEventListener("wheel", handleWheel);
      element.removeEventListener("scroll", updateAutoFollowState);
    };
  }, [cancelAutoScrollFrame]);

  const animateAutoScroll = useMemoizedFn(() => {
    if (autoScrollFrameRef.current !== null) {
      return;
    }

    const step = () => {
      autoScrollFrameRef.current = requestAnimationFrame(() => {
        autoScrollFrameRef.current = null;
        const element = ref.current;
        if (!element || !shouldAutoFollowRef.current) {
          programmaticScrollRef.current = false;
          return;
        }

        const target = autoScrollTargetRef.current;
        const current = element.scrollTop;
        const delta = target - current;

        if (Math.abs(delta) <= 0.5) {
          element.scrollTop = target;
          programmaticScrollRef.current = false;
          return;
        }

        // 使用缓动追踪目标位，让工作区流式跟随更像自然滚动而不是生硬跳变。
        const nextScrollTop = current + delta * 0.18;
        element.scrollTop = nextScrollTop;
        step();
      });
    };

    step();
  });

  const scrollToFollowTarget = useMemoizedFn((immediate = false) => {
    const element = ref.current;
    if (!element || !shouldAutoFollowRef.current) return;

    autoScrollTargetRef.current = Math.max(0, element.scrollHeight - element.clientHeight);
    programmaticScrollRef.current = true;

    if (immediate) {
      cancelAutoScrollFrame();
      element.scrollTop = autoScrollTargetRef.current;
      requestAnimationFrame(() => {
        programmaticScrollRef.current = false;
      });
      return;
    }

    animateAutoScroll();
  });

  const scrollToBottom = useMemoizedFn(() => {
    scrollToFollowTarget(false);
  });

  useEffect(() => {
    if (!taskItem || !shouldAutoFollowRef.current) return;
    scrollToFollowTarget(true);
  }, [scrollToFollowTarget, taskItem?.id, taskItem?.messageId, taskItem?.messageTime]);

  useEffect(() => {
    return () => {
      cancelAutoScrollFrame();
      programmaticScrollRef.current = false;
    };
  }, [cancelAutoScrollFrame]);

  return (
    <PanelProvider value={{
      wrapRef: ref,
      scrollToBottom,
    }}>
      <div
        className={classNames('w-full px-16 overflow-auto', className)}
        ref={ref}
      >
        {panelNode}
      </div>
    </PanelProvider>
  );
});

ActionPanel.displayName = 'ActionPanel';

export default ActionPanel;
