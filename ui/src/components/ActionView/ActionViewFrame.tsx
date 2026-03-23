import { ChevronLeft } from "lucide-react";
import { cn } from "@/lib/utils";
import { Separator } from "@/components/ui/separator";

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
      {/* Header */}
      {titleNode && (
        <>
          <div className="flex items-center gap-2 px-4 py-3">
            <button
              onClick={onClickTitle}
              className="flex h-7 w-7 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <div className="flex-1 min-w-0 text-[13px] font-medium text-[#1d1d1f]">
              {titleNode}
            </div>
          </div>
          <Separator className="bg-[#e8e8ed]" />
        </>
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
