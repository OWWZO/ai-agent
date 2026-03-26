import React, { memo, useEffect, useMemo, useRef } from "react";
import classNames from "classnames";
import { motion } from "motion/react";
import { useMsgTypes } from "./useMsgTypes";
import HTMLRenderer from "./HTMLRenderer";
import useContent from "./useContent";
import MarkdownRenderer from "./MarkdownRenderer";
import TableRenderer from "./TableRenderer";
import FileRenderer from "./FileRenderer";
import ReactJsonPretty from "react-json-pretty";
import SearchListRenderer from "./SearchListRenderer";
import { PanelItemType } from "./type";
import { PanelProvider } from ".";
import { useMemoizedFn } from "ahooks";

interface ActionPanelProps {
  taskItem?: PanelItemType;
  allowShowToolBar?: boolean;
  className?: string;
  noPadding?: boolean;
}

// 内容包装动画组件
const ContentWrapper = ({ children, key }: { children: React.ReactNode; key?: string }) => (
  <motion.div
    key={key}
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
      <MarkdownRenderer markDownContent={content} isStreaming={isStreaming} />
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

const ActionPanel: GenieType.FC<ActionPanelProps> = React.memo((props) => {
  const { taskItem, className, allowShowToolBar } = props;

  const msgTypes = useMsgTypes(taskItem);
  const { markDownContent } = useContent(taskItem);

  const { resultMap, toolResult } = taskItem || {};
  const [ fileInfo ] = resultMap?.fileInfo || [];
  const htmlUrl = fileInfo?.domainUrl;
  const downloadHtmlUrl = fileInfo?.ossUrl;

  const { codeOutput } = resultMap || {};

  const panelNode = useMemo(() => {
    const renderContent = () => {
      if (!taskItem) return null;
      const { useHtml, useCode, useFile, isHtml, useExcel, useJSON, searchList, usePpt } = msgTypes || {};

      if (searchList?.length) {
        return (
          <ContentWrapper key="search">
            <SearchListRenderer list={searchList} />
          </ContentWrapper>
        );
      }

      if (useHtml || usePpt) {
        return (
          <ContentWrapper key="html">
            <HTMLRenderer
              htmlUrl={htmlUrl}
              className="h-full"
              downloadUrl={downloadHtmlUrl}
              outputCode={codeOutput}
              showToolBar={allowShowToolBar && resultMap?.isFinal}
              isStreaming={!resultMap?.isFinal}
            />
          </ContentWrapper>
        );
      }

      if (useCode && isHtml) {
        return (
          <ContentWrapper key="code">
            <HTMLRenderer
              htmlUrl={`data:text/html;charset=utf-8,${encodeURIComponent(toolResult?.toolResult || '')}`}
            />
          </ContentWrapper>
        );
      }

      if (useExcel) {
        return (
          <ContentWrapper key="excel">
            <TableRenderer fileUrl={fileInfo?.domainUrl} fileName={fileInfo?.fileName} />
          </ContentWrapper>
        );
      }

      if (useFile) {
        return (
          <ContentWrapper key="file">
            <FileRenderer fileUrl={fileInfo?.domainUrl} fileName={fileInfo?.fileName} />
          </ContentWrapper>
        );
      }

      if (useJSON) {
        return (
          <ContentWrapper key="json">
            <ReactJsonPretty
              data={JSON.parse(toolResult?.toolResult || '{}')}
              style={{ backgroundColor: '#000' }}
            />
          </ContentWrapper>
        );
      }

      return (
        <StreamingMarkdownWrapper
          content={markDownContent}
          isStreaming={!resultMap?.isFinal}
        />
      );
    };

    return renderContent();
  }, [
    taskItem,
    msgTypes,
    markDownContent,
    htmlUrl,
    downloadHtmlUrl,
    allowShowToolBar,
    resultMap?.isFinal,
    toolResult?.toolResult,
    fileInfo,
    codeOutput,
  ]);

  const ref = useRef<HTMLDivElement>(null);
  const shouldAutoFollowRef = useRef(true);
  const autoScrollFrameRef = useRef<number | null>(null);
  const autoScrollTargetRef = useRef(0);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const updateAutoFollowState = () => {
      const distanceToBottom =
        element.scrollHeight - element.scrollTop - element.clientHeight;
      // 只有用户明确滚离底部较远时才停止跟随，避免流式内容快速增长时误判。
      shouldAutoFollowRef.current = distanceToBottom < 240;
    };

    updateAutoFollowState();
    element.addEventListener("scroll", updateAutoFollowState, { passive: true });

    return () => {
      element.removeEventListener("scroll", updateAutoFollowState);
    };
  }, []);

  const cancelAutoScrollFrame = useMemoizedFn(() => {
    if (autoScrollFrameRef.current !== null) {
      cancelAnimationFrame(autoScrollFrameRef.current);
      autoScrollFrameRef.current = null;
    }
  });

  const animateAutoScroll = useMemoizedFn(() => {
    if (autoScrollFrameRef.current !== null) {
      return;
    }

    const step = () => {
      autoScrollFrameRef.current = requestAnimationFrame(() => {
        autoScrollFrameRef.current = null;
        const element = ref.current;
        if (!element || !shouldAutoFollowRef.current) {
          return;
        }

        const target = autoScrollTargetRef.current;
        const current = element.scrollTop;
        const delta = target - current;

        if (Math.abs(delta) <= 0.5) {
          element.scrollTop = target;
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

    if (immediate) {
      cancelAutoScrollFrame();
      element.scrollTop = autoScrollTargetRef.current;
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
