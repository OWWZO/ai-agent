import { ImageIcon, X } from "lucide-react";
import { isImageFileLike } from "@/utils/taskArtifacts";
import { cn } from "@/lib/utils";
import {
  FILE_KIND_TONE,
  type FileKind,
  resolveFileKind,
} from "@/utils/fileKind";

type Props = {
  files?: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
  showWorkspaceFilesEntry?: boolean;
  onOpenWorkspaceFiles?: () => void;
};

function AllFilesIcon() {
  return (
    <svg viewBox="0 0 40 40" className="size-10" fill="none" aria-hidden="true">
      <rect
        x="11"
        y="7"
        width="16"
        height="20"
        rx="2.5"
        className="fill-surface-hover stroke-border"
      />
      <rect
        x="9"
        y="10"
        width="18"
        height="20"
        rx="2.5"
        className="fill-card stroke-border"
      />
      <path
        d="M7 18.2c0-1.2.9-2.2 2.1-2.2h5.4c.5 0 .9-.2 1.2-.6l.9-1.1c.3-.4.8-.6 1.3-.6h10.2c1.2 0 2.1 1 2.1 2.2V29c0 1.5-1.2 2.7-2.7 2.7H9.7C8.2 31.7 7 30.5 7 29V18.2z"
        className="fill-hover stroke-border"
      />
    </svg>
  );
}

function WorkspaceFilesEntry({ onOpen }: { onOpen?: () => void }) {
  return (
    <button
      type="button"
      className="group flex w-full items-center gap-3 rounded-xl border border-card-border bg-surface px-3.5 py-3 text-left transition-colors hover:bg-surface-hover"
      onClick={() => onOpen?.()}
    >
      <span className="flex size-10 shrink-0 items-center justify-center">
        <AllFilesIcon />
      </span>
      <div className="min-w-0 flex-1">
        <div className="truncate text-ui-base font-medium text-foreground">
          全部文件
        </div>
        <div className="mt-0.5 truncate text-ui-sm text-foreground-subtle">
          查看或下载文件
        </div>
      </div>
      <span className="inline-flex h-8 shrink-0 items-center rounded-lg border border-card-border bg-card px-3.5 text-ui-sm font-medium text-foreground transition-colors group-hover:bg-hover">
        预览
      </span>
    </button>
  );
}

function resolveExt(file: CHAT.TFile): string {
  return (file.type || file.name?.split(".").pop() || "").toLowerCase();
}

/** Attachment 列表把 py 并入 code 展示。 */
function resolveKind(file: CHAT.TFile): FileKind {
  if (isImageFileLike(file)) {
    return "img";
  }
  const kind = resolveFileKind(file.type, file.name);
  return kind === "py" ? "code" : kind;
}

function typeLabel(file: CHAT.TFile, kind: FileKind): string {
  const ext = resolveExt(file).toUpperCase();
  switch (kind) {
    case "img":
      return file.originFileName || describeFromName(file.name) || `Image · ${ext || "PNG"}`;
    case "xlsx":
      return `Spreadsheet · ${ext || "XLSX"}`;
    case "md":
      return describeFromName(file.name) || `Document · ${ext || "MD"}`;
    case "html":
      return "Web page · HTML";
    case "pdf":
      return "Document · PDF";
    case "css":
      return "样式文件 · CSS";
    case "code":
    case "py":
      return `Code · ${ext || "FILE"}`;
    default:
      return describeFromName(file.name) || (ext ? `File · ${ext}` : "File");
  }
}

/** 从文件名提炼副标题（去掉扩展名后的短描述） */
function describeFromName(name?: string): string {
  if (!name) return "";
  const base = name.replace(/\.[^.]+$/, "").trim();
  if (base.length <= 28) return base;
  return `${base.slice(0, 26)}…`;
}

function displayTitle(file: CHAT.TFile): string {
  const name = file.name || "未命名文件";
  // 主卡标题尽量用可读名，去掉过长扩展噪声
  return name;
}

function KindBadge({ kind, className }: { kind: FileKind; className?: string }) {
  // 对齐 ClawsGO：X / T / 图片图标字母角标
  if (kind === "xlsx") {
    return (
      <span className={cn("text-ui-base font-semibold tracking-tight", className)}>
        X
      </span>
    );
  }
  if (kind === "md" || kind === "pdf" || kind === "file" || kind === "css") {
    return (
      <span className={cn("text-ui-base font-semibold tracking-tight", className)}>
        T
      </span>
    );
  }
  if (kind === "html" || kind === "code" || kind === "py") {
    return (
      <span className={cn("text-ui-sm font-semibold tracking-tight", className)}>
        &lt;/&gt;
      </span>
    );
  }
  return <ImageIcon className={cn("size-5", className)} strokeWidth={1.6} />;
}

const AttachmentList: CuteType.FC<Props> = (props) => {
  const {
    files,
    preview,
    remove,
    review,
    showWorkspaceFilesEntry,
    onOpenWorkspaceFiles,
  } = props;
  const attachmentList = Array.isArray(files) ? files : [];

  if (!preview && !attachmentList.length) {
    return null;
  }

  // 交付态：重点文件主卡 + 其余 mini chips；工作区其余文件收成一张入口卡
  if (preview) {
    if (!attachmentList.length && !showWorkspaceFilesEntry) {
      return null;
    }
    const [primary, ...rest] = attachmentList;
    const primaryKind = primary ? resolveKind(primary) : null;

    return (
      <div className="mt-3.5 flex w-full max-w-full flex-col gap-2">
        {primary && primaryKind ? (
          <>
            <button
              type="button"
              className="group flex w-full items-center gap-3 rounded-xl border border-card-border bg-surface px-3.5 py-3 text-left transition-colors hover:bg-surface-hover"
              onClick={() => review?.(primary)}
            >
              <span
                className={cn(
                  "flex size-10 shrink-0 items-center justify-center rounded-lg",
                  FILE_KIND_TONE
                )}
              >
                {isImageFileLike(primary) && primary.url ? (
                  <img
                    src={primary.url}
                    alt=""
                    className="size-10 rounded-lg object-cover"
                  />
                ) : (
                  <KindBadge kind={primaryKind} />
                )}
              </span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-ui-base font-medium text-foreground">
                  {displayTitle(primary)}
                </div>
                <div className="mt-0.5 truncate text-ui-sm text-foreground-subtle">
                  {typeLabel(primary, primaryKind)}
                </div>
              </div>
              <span className="inline-flex h-8 shrink-0 items-center rounded-lg border border-card-border bg-card px-3.5 text-ui-sm font-medium text-foreground transition-colors group-hover:bg-hover">
                打开文件
              </span>
            </button>

            {rest.length > 0 ? (
              <div className="flex w-full flex-wrap gap-2">
                {rest.map((file, index) => {
                  const kind = resolveKind(file);
                  return (
                    <button
                      key={`${file.resourceKey || file.name}-${index}`}
                      type="button"
                      className="flex min-w-0 max-w-[calc(33.33%-6px)] flex-[1_1_140px] items-center gap-2 rounded-xl border border-card-border bg-surface px-2.5 py-2 text-left transition-colors hover:bg-surface-hover"
                      onClick={() => review?.(file)}
                      title={file.name}
                    >
                      <span
                        className={cn(
                          "flex size-7 shrink-0 items-center justify-center rounded-lg",
                          FILE_KIND_TONE
                        )}
                      >
                        {isImageFileLike(file) && file.url ? (
                          <img
                            src={file.url}
                            alt=""
                            className="size-7 rounded-lg object-cover"
                          />
                        ) : kind === "img" ? (
                          <ImageIcon className="size-3.5" strokeWidth={1.6} />
                        ) : (
                          <KindBadge kind={kind} className="text-ui-sm" />
                        )}
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-ui-sm font-medium leading-tight text-foreground">
                          {displayTitle(file)}
                        </span>
                        <span className="mt-0.5 block truncate text-ui-xs text-foreground-subtlest">
                          {typeLabel(file, kind)}
                        </span>
                      </span>
                    </button>
                  );
                })}
              </div>
            ) : null}
          </>
        ) : null}
        {showWorkspaceFilesEntry ? (
          <WorkspaceFilesEntry onOpen={onOpenWorkspaceFiles} />
        ) : null}
      </div>
    );
  }

  // 输入态附件：紧凑可删除
  return (
    <div className="flex w-full flex-wrap gap-2">
      {attachmentList.map((file, index) => {
        const kind = resolveKind(file);
        return (
          <div
            key={`${file.resourceKey || file.name}-${index}`}
            className="group relative flex max-w-sm items-center gap-2 rounded-xl border border-card-border bg-surface px-2.5 py-2"
          >
            <span
              className={cn(
                "flex size-8 shrink-0 items-center justify-center rounded-lg",
                FILE_KIND_TONE
              )}
            >
              {isImageFileLike(file) && file.url ? (
                <img
                  src={file.url}
                  alt=""
                  className="size-8 rounded-lg object-cover"
                />
              ) : (
                <KindBadge kind={kind} className="text-ui-sm" />
              )}
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-ui-base font-medium text-foreground">
                {displayTitle(file)}
              </div>
              <div className="truncate text-ui-xs text-foreground-subtle">
                {typeLabel(file, kind)}
              </div>
            </div>
            {remove ? (
              <button
                type="button"
                className="absolute -right-1.5 -top-1.5 hidden size-5 items-center justify-center rounded-full border border-card-border bg-card text-foreground-subtle shadow-sm group-hover:flex hover:text-foreground"
                onClick={(e) => {
                  e.stopPropagation();
                  remove(index);
                }}
                aria-label="移除附件"
              >
                <X className="size-3" />
              </button>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

export default AttachmentList;
