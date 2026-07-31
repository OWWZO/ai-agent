import React from "react";
import { Alert, Button } from "antd";
import { Download } from "lucide-react";
import { downloadFile } from "@/utils";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";

const ERROR_CLASS =
  "m-0 min-w-[260px] max-w-full [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

interface DocumentFallbackProps {
  label?: string;
  title: string;
  description?: string;
  fileName?: string;
  downloadUrl?: string;
  className?: string;
  type?: "error" | "info" | "warning";
}

/**
 * PDF/Word 预览失败或 .doc 不支持时的统一空态 + 下载。
 */
const DocumentFallback: ReactorType.FC<DocumentFallbackProps> = React.memo(
  (props) => {
    const {
      label = "DOC",
      title,
      description,
      fileName,
      downloadUrl,
      className,
      type = "info",
    } = props;

    const canDownload = Boolean(downloadUrl);

    return (
      <ViewerPanelShell
        label={label}
        subtitle={fileName}
        className={className}
        headerRight={
          canDownload ? (
            <Button
              type="text"
              size="small"
              icon={<Download className="h-3.5 w-3.5" />}
              onClick={() => downloadFile(downloadUrl, fileName)}
            >
              下载
            </Button>
          ) : null
        }
      >
        <Alert
          type={type}
          message={title}
          description={description}
          showIcon
          className={ERROR_CLASS}
          action={
            canDownload ? (
              <Button
                size="small"
                type="primary"
                onClick={() => downloadFile(downloadUrl, fileName)}
              >
                下载原件
              </Button>
            ) : undefined
          }
        />
      </ViewerPanelShell>
    );
  }
);

DocumentFallback.displayName = "DocumentFallback";

export default DocumentFallback;
