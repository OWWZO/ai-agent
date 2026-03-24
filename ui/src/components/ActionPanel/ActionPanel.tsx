import React, { useMemo, useRef } from "react";
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
const StreamingMarkdownWrapper = ({ content }: { content: string }) => {
  const contentRef = useRef<HTMLDivElement>(null);

  return (
    <motion.div
      ref={contentRef}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
      className="h-full"
    >
      <MarkdownRenderer markDownContent={content} isStreaming />
    </motion.div>
  );
};

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
        <StreamingMarkdownWrapper content={markDownContent} />
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

  const scrollToBottom = useMemoizedFn(() => {
    if (!ref.current) return;
    const element = ref.current;
    const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 240;
    if (!nearBottom) return;
    requestAnimationFrame(() => {
      element.scrollTop = element.scrollHeight;
    });
  });

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
