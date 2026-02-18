import React, { useMemo } from "react";
import { useRequest } from "ahooks";
import { Alert } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";
import Loading from "./Loading";

const LOADING_CLASS = 'mr-32';
const ERROR_CLASS = 'm-24';

interface FileRendererProps {
  /** 文件路径 */
  fileUrl: string;
  /** 文件名 */
  fileName?: string;
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

const FileRenderer: GenieType.FC<FileRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, className } = props;

  const ext = useMemo(() => getFileExtension(fileName), [fileName]);

  const { data, loading, error } = useRequest(async () => {
    const response = await fetch(fileUrl);
    if (!response.ok) {
      throw new Error('Network response was not ok');
    }
    return await response.text();
  }, { refreshDeps: [fileUrl] });

  const markStr = useMemo(() => formatFileContent(ext, data), [ext, data]);

  if (loading) {
    return <Loading className={LOADING_CLASS} />;
  }

  if (error) {
    return (
      <Alert
        type="error"
        message="加载失败"
        description={error.message}
        showIcon
        className={ERROR_CLASS}
      />
    );
  }

  return <MarkdownRenderer markDownContent={markStr} className={className} />;
});

FileRenderer.displayName = 'FileRenderer';

export default FileRenderer;
