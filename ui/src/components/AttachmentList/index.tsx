import { iconType } from "@/utils/constants";
import docxIcon from "@/assets/icon/docx.png";
import { Tooltip } from "antd";

type Props = {
  files: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

const GeneralInput: ReactorType.FC<Props> = (props) => {
  const { files, preview, remove, review } = props;

  const formatSize = (size: number) => {
    const units = ["B", "KB", "MB", "GB"];
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size?.toFixed(2)} ${units[unitIndex]}`;
  };

  const combinIcon = (f: CHAT.TFile) => {
    const imgType = ["jpg", "png", "jpeg"];
    if (imgType.includes(f.type)) {
      return f.url;
    } else {
      return iconType[f.type] || docxIcon;
    }
  };

  const removeFile = (index: number) => {
    remove?.(index);
  };

  const reviewFile = (f: CHAT.TFile) => {
    review?.(f);
  };

  const renderFile = (f: CHAT.TFile, index: number) => {
    return (
      <div
        key={index}
        className={`group relative box-border flex w-full max-w-sm items-center gap-2 rounded-lg px-1 py-1 transition-colors ${
          preview ? "cursor-pointer hover:bg-muted/35" : "cursor-default"
        }`}
        onClick={() => reviewFile(f)}
      >
        <img src={combinIcon(f)} alt={f.name} className="h-9 w-9 shrink-0 object-contain" />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-1.5">
            <Tooltip title={f.name}>
              <div className="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap text-[13px] leading-snug text-[#27272A]">
                {f.name}
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
      {files.map((f, index) => renderFile(f, index))}
    </div>
  );
};

export default GeneralInput;
