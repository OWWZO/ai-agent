import { ChevronLeft } from "lucide-react";
import { cn } from "@/lib/utils";

interface ActionViewFrameProps {
  titleNode?: React.ReactNode;
  onClickTitle?: () => void;
  footer?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

const ActionViewFrame: React.FC<ActionViewFrameProps> = ({
  children,
  className,
  titleNode,
  footer,
  onClickTitle,
}) => {
  return (
    <div className="flex h-full flex-col">
      {/* Header — 去掉独立分隔线，改用 border-b，padding 收紧 */}
      {titleNode && (
        <div className="flex items-center gap-2 border-b border-[#e8e8ed]/80 px-3 py-2">
          <button
            onClick={onClickTitle}
            className="flex h-7 w-7 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div className="min-w-0 flex-1 text-[13px] font-medium text-[#1d1d1f]">
            {titleNode}
          </div>
        </div>
      )}

      {/* Content */}
      <div
        className={cn(
          "flex-1 overflow-auto",
          className
        )}
      >
        {children}
      </div>

      {/* Footer */}
      {footer}
    </div>
  );
};

export default ActionViewFrame;
