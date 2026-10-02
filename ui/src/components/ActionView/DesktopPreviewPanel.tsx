import { ExternalLink, Monitor, PanelRightClose } from "lucide-react";

type DesktopPreviewPanelProps = {
  streamUrl: string;
  onClose: () => void;
};

const iconButtonClass =
  "reactor-action-icon flex h-8 w-8 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]";

const DesktopPreviewPanel: ReactorType.FC<DesktopPreviewPanelProps> = ({
  streamUrl,
  onClose,
}) => (
  <section
    className="flex h-full min-h-0 w-full flex-col overflow-hidden bg-[var(--color-bg)]"
    data-testid="desktop-preview-panel"
  >
    <header className="flex shrink-0 items-center justify-between gap-3 border-b border-[var(--color-line)] px-3 py-2.5">
      <div className="flex min-w-0 items-center gap-2">
        <Monitor
          className="h-4 w-4 shrink-0 text-[var(--color-text-muted)]"
          aria-hidden="true"
        />
        <h2 className="truncate text-[13px] font-medium text-[var(--color-text)]">
          桌面预览
        </h2>
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <a
          href={streamUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={iconButtonClass}
          title="在新窗口打开桌面"
          aria-label="在新窗口打开桌面"
        >
          <ExternalLink className="h-4 w-4" aria-hidden="true" />
        </a>
        <button
          type="button"
          onClick={onClose}
          className={iconButtonClass}
          title="关闭工作区"
          aria-label="关闭桌面预览"
        >
          <PanelRightClose className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </header>
    <div className="min-h-0 flex-1 bg-black">
      <iframe
        title="E2B 桌面实时预览"
        src={streamUrl}
        allow="clipboard-read; clipboard-write"
        className="h-full w-full border-0 bg-black"
      />
    </div>
  </section>
);

export default DesktopPreviewPanel;
