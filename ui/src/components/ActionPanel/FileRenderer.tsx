import React, { useMemo } from "react";
import { useRequest } from "ahooks";
import { Alert } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";
import Loading from "./Loading";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";

const LOADING_CLASS = 'mr-32';
const ERROR_CLASS = "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

interface FileRendererProps {
  /** 文件路径 */
  fileUrl: string;
  /** 文件名 */
  fileName?: string;
  /** 明确的缺失原因 */
  missingReason?: string;
  /** 自定义样式 */
  className?: string;
}

/**
 * 获取文件扩展名
 * @param fileName 文件名
 * @returns 小写的文件扩展名
 */
const getFileExtension = (fileName?: string): string | undefined => {
  return fileName?.split('.').pop()?.toLowerCase();
};

/**
 * 格式化文件内容
 * @param ext 文件扩展名
 * @param data 文件内容
 * @returns 格式化后的文件内容
 */
const formatFileContent = (ext: string | undefined, data: string | undefined): string => {
  if (ext === 'md' || ext === 'txt') {
    return data || '';
  }
  return `\`\`\`${ext}\n${data || ''}\n\`\`\``;
};

const resolveUnavailableReason = (error: Error) => {
  const message = error?.message || '';
  if (
    message.includes('Failed to fetch') ||
    message.includes('Network response was not ok') ||
    message.includes('NetworkError')
  ) {
    return '引用资源不存在或已失效';
  }
  return message || '引用资源不存在或已失效';
};

const FileRenderer: ReactorType.FC<FileRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, missingReason, className } = props;

  const ext = useMemo(() => getFileExtension(fileName), [fileName]);

  const { data, loading, error } = useRequest(async () => {
    if (missingReason) {
      throw new Error(missingReason);
    }
    if (!fileUrl) {
      throw new Error('引用资源不存在或已失效');
    }
    const response = await fetch(fileUrl);
    if (!response.ok) {
      throw new Error('Network response was not ok');
    }
    return await response.text();
  }, { refreshDeps: [fileUrl, missingReason] });

  const markStr = useMemo(() => formatFileContent(ext, data), [ext, data]);

  if (loading) {
    return (
      <ViewerPanelShell
        label="FILE"
        subtitle={fileName || "文件预览"}
        className={className}
      >
        <Loading className={LOADING_CLASS} />
      </ViewerPanelShell>
    );
  }

  if (error) {
    return (
      <ViewerPanelShell
        label="FILE"
        subtitle={fileName || "文件预览"}
        className={className}
      >
        <Alert
          type="error"
          message="内容不可读取"
          description={resolveUnavailableReason(error as Error)}
          showIcon
          className={ERROR_CLASS}
        />
      </ViewerPanelShell>
    );
  }

  return (
    <ViewerPanelShell
      label="FILE"
      subtitle={fileName || "文件预览"}
      className={className}
    >
      <MarkdownRenderer
        markDownContent={markStr}
        normalizationScope="default"
      />
    </ViewerPanelShell>
  );
});

FileRenderer.displayName = 'FileRenderer';

export default FileRenderer;
