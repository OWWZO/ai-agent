import type { FC } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

type MessageSkeletonProps = {
  className?: string;
};

const lineWidths = ["w-[92%]", "w-[84%]", "w-[96%]", "w-[72%]"];

const MessageSkeleton: FC<MessageSkeletonProps> = ({ className }) => {
  return (
    <div
      className={cn(
        "w-full max-w-[760px] rounded-[28px] bg-white/62 px-4 py-4 shadow-[0_18px_42px_-34px_rgba(15,23,42,0.24)] backdrop-blur-sm",
        className
      )}
    >
      <div className="flex items-center gap-3">
        <Skeleton className="size-9 rounded-[16px] bg-black/7" />
        <div className="flex-1 space-y-2">
          <Skeleton className="h-3.5 w-24 rounded-full bg-black/8" />
          <Skeleton className="h-2.5 w-16 rounded-full bg-black/5" />
        </div>
      </div>

      <div className="mt-5 space-y-3">
        {lineWidths.map((width, index) => (
          <Skeleton
            key={index}
            className={cn("h-3.5 rounded-full bg-black/6", width)}
          />
        ))}
      </div>

      <div className="mt-5 flex items-center gap-2">
        <Skeleton className="h-8 w-24 rounded-full bg-black/7" />
        <Skeleton className="h-8 w-20 rounded-full bg-black/5" />
      </div>
    </div>
  );
};

export default MessageSkeleton;
