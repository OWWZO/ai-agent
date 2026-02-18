import { jumpUrl } from "@/utils";
import { useBoolean } from "ahooks";
import classNames from "classnames";
import { memo, useLayoutEffect, useMemo, useState } from "react";
import Loading from "./Loading";
import { Empty } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";

interface ToolItemProps {
  onClick?: () => void;
  title?: string;
}

const ToolItem: GenieType.FC<ToolItemProps> = memo((props) => {
  const { className, children, onClick, title } = props;
  return (
    <div
      className={classNames("cursor-pointer flex w-24 h-24 items-center justify-center rounded-[4px] hover:bg-[#eee]", className)}
      onClick={onClick}
      title={title}
    >
      {children}
    </div>
  );
});

ToolItem.displayName = 'ToolItem';

interface HTMLRendererProps {
  htmlUrl?: string;
  downloadUrl?: string;
  showToolBar?: boolean;
  outputCode?: string;
}

const TOOLBAR_CLASS = "absolute bottom-8 right-0 py-0 px-16 bg-[#fbfbff] h-[36px] rounded-[18px] flex items-center border-[#52649113] border-solid border-1 gap-12 text-primary";

const HTMLRenderer: GenieType.FC<HTMLRendererProps> = memo((props) => {
  const { htmlUrl, className, downloadUrl, showToolBar, outputCode } = props;

  const [loading, { setTrue: startLoading, setFalse: stopLoading }] = useBoolean(false);
  const [error, setError] = useState<string | null>(null);

  useLayoutEffect(() => {
    if (htmlUrl) {
      startLoading();
    }
  }, [htmlUrl, startLoading]);

  const toolBar = useMemo(() => showToolBar && (
    <div className={TOOLBAR_CLASS}>
      <ToolItem onClick={() => jumpUrl(htmlUrl)} title="在新窗口打开">
        <i className="font_family icon-zhengyan"></i>
      </ToolItem>
      <ToolItem onClick={() => jumpUrl(downloadUrl)} title="下载">
        <i className="font_family icon-xiazai"></i>
      </ToolItem>
    </div>
  ), [showToolBar, htmlUrl, downloadUrl]);

  const content = useMemo(() => {
    if (error) {
      return <div className="text-red-500">{error}</div>;
    }
    if (htmlUrl) {
      return (
        <iframe
          className='w-full h-full'
          src={htmlUrl}
          onLoad={stopLoading}
          onError={() => {
            setError('加载失败，请检查 URL 是否正确');
            stopLoading();
          }}
        >
        </iframe>
      );
    }
    return <Empty description="暂无内容" className="mt-32" />;
  }, [error, htmlUrl, stopLoading]);

  if (!htmlUrl && outputCode) {
    return <MarkdownRenderer markDownContent={outputCode} />;
  }

  return (
    <div className={classNames(className, 'relative')}>
      <Loading loading={!!htmlUrl && loading} className="absolute left-0 top-0 w-full h-full" />
      {content}
      {toolBar}
    </div>
  );
});

HTMLRenderer.displayName = 'HTMLRenderer';

export default HTMLRenderer;
