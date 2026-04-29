import { iconType } from "@/utils/constants";
import docxIcon from "@/assets/icon/docx.png";
import { Tooltip } from "antd";
import { isImageFileLike } from "@/utils/taskArtifacts";

type Props = {
  files?: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

const AttachmentList: ReactorType.FC<Props> = (props) => {
  const { files, preview, remove, review } = props;
  // 附件字段可能缺失，这里统一兜底成空数组，避免界面直接崩溃。
  const attachmentList = Array.isArray(files) ? files : [];

  const formatSize = (size?: number) => {
    if (typeof size !== "number" || Number.isNaN(size) || size < 0) {
      return "未知大小";
    }
    const units = ["B", "KB", "MB", "GB"];
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size?.toFixed(2)} ${units[unitIndex]}`;
  };

  const combinIcon = (f: CHAT.TFile) => {
    const fileType = f.type?.toLowerCase?.() || "";
    if (isImageFileLike(f) && f.url) {
      return f.url;
    } else {
      return iconType[fileType] || docxIcon;
    }
  };

  const removeFile = (index: number) => {
    remove?.(index);
  };

  const reviewFile = (f: CHAT.TFile) => {
    review?.(f);
  };

  if (!attachmentList.length) {
    return null;
  }

  const renderFile = (f: CHAT.TFile, index: number) => {
    return (
      <div
        key={index}
        className={`group relative box-border flex w-full max-w-sm items-center gap-2 rounded-lg px-1 py-1 transition-colors ${
          preview ? "cursor-pointer hover:bg-muted/35" : "cursor-default"
        }`}
        onClick={() => reviewFile(f)}
      >
        <img src={combinIcon(f)} alt={f.name || "附件"} className="h-9 w-9 shrink-0 object-contain" />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-1.5">
            <Tooltip title={f.name || "未命名文件"}>
              <div className="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap text-[13px] leading-snug text-[#27272A]">
                {f.name || "未命名文件"}
              </div>
            </Tooltip>
            <span className="shrink-0 text-[11px] leading-snug text-[#C4C4C8]" aria-hidden>
              ·
            </span>
            <span className="shrink-0 tabular-nums text-[11px] leading-snug text-[#9E9FA3]">
              {formatSize(f.size)}
            </span>
          </div>
        </div>
        {!preview ? (
          <i
            className="font_family icon-jia-1 absolute right-2 top-2 hidden cursor-pointer text-[12px] group-hover:block"
            onClick={(e) => {
              e.stopPropagation();
              removeFile(index);
            }}
          ></i>
        ) : null}
      </div>
    );
  };

  return (
    <div className="w-full flex gap-8 flex-wrap">
      {attachmentList.map((f, index) => renderFile(f, index))}
    </div>
  );
};

export default AttachmentList;
