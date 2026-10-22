import React from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { CircleAlert, Download, Info, TriangleAlert } from "lucide-react";
import { downloadFile } from "@/utils";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";

const ERROR_CLASS =
  "m-0 min-w-[260px] max-w-full [&_data-[slot=alert-description]]:break-words [&_data-[slot=alert-description]]:whitespace-normal";

interface DocumentFallbackProps {
  label?: string;
  title: string;
  description?: string;
  fileName?: string;
  downloadUrl?: string;
  className?: string;
  type?: "error" | "info" | "warning";
}

const ALERT_VARIANT = {
  error: "destructive",
  info: "info",
  warning: "warning",
} as const;

const ALERT_ICON = {
  error: CircleAlert,
  info: Info,
  warning: TriangleAlert,
} as const;

/**
 * PDF/Word 预览失败或 .doc 不支持时的统一空态 + 下载。
 */
const DocumentFallback: CuteType.FC<DocumentFallbackProps> = React.memo(
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
    const variant = ALERT_VARIANT[type];
    const Icon = ALERT_ICON[type];

    return (
      <ViewerPanelShell
        label={label}
        subtitle={fileName}
        className={className}
        headerRight={
          canDownload ? (
            <button
              type="button"
              className="inline-flex h-6 items-center gap-1 rounded-md px-2 text-[13px] text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
              onClick={() => downloadFile(downloadUrl, fileName)}
            >
              <Download className="h-3.5 w-3.5" />
              下载
            </button>
          ) : null
        }
      >
        <Alert variant={variant} className={ERROR_CLASS}>
          <Icon />
          <AlertTitle>{title}</AlertTitle>
          {description ? <AlertDescription>{description}</AlertDescription> : null}
          {canDownload ? (
            <button
              type="button"
              className="mt-2 inline-flex h-8 items-center rounded-md bg-primary px-3 text-[13px] font-medium text-primary-foreground transition-opacity hover:opacity-90"
              onClick={() => downloadFile(downloadUrl, fileName)}
            >
              下载原件
            </button>
          ) : null}
        </Alert>
      </ViewerPanelShell>
    );
  }
);

DocumentFallback.displayName = "DocumentFallback";

export default DocumentFallback;
