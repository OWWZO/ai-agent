import { iconType } from "@/utils/constants";
import docxIcon from "@/assets/icon/docx.png";
import { Tooltip } from "antd";

type Props = {
  files: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
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
        className={`group w-160 h-40 rounded-lg border border-[#E9E9F0] p-[6px] box-border flex items-center relative ${preview ? "cursor-pointer" : "cursor-default"}`}
        onClick={() => reviewFile(f)}
      >
        <img src={combinIcon(f)} alt={f.name} className="w-20 h-20 shrink-0 object-contain" />
        <div className="flex-1 ml-[6px] overflow-hidden">
          <Tooltip title={f.name}>
            <div className="w-full overflow-hidden whitespace-nowrap text-ellipsis text-[13px] text-[#27272A] leading-[18px]">
              {f.name}
            </div>
          </Tooltip>
          <div className="w-full text-[11px] text-[#9E9FA3] leading-[16px]">
            {formatSize(f.size)}
          </div>
        </div>
        {!preview ? (
          <i
            className="font_family icon-jia-1 absolute top-[6px] right-[6px] cursor-pointer hidden group-hover:block text-[12px]"
            onClick={() => removeFile(index)}
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
