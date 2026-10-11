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
      {/* Header — 与 `ActionView` 顶栏同规格：不画分隔线，层级交给间距。 */}
      {titleNode && (
        <div className="flex h-10 shrink-0 items-center gap-1 px-2">
          <button
            onClick={onClickTitle}
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground active:scale-[0.97]"
          >
            <ChevronLeft className="size-4" />
          </button>
          <div className="min-w-0 flex-1 truncate px-1 text-ui-base text-foreground">
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
