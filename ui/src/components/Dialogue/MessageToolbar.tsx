import { FC, useCallback, useState } from "react";
import {
  MessageActions,
  MessageAction,
} from "@/components/ai-elements/message";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  CopyIcon,
  CheckIcon,
  Undo2Icon,
  MoreHorizontalIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
export type MessageToolbarProps = {
  response?: string;
  /** Kimi 式：撤销本轮并回填输入框 */
  onUndo?: () => void;
  /** 对齐 kimi-web a-msg-ft：终答脚常显，不依赖 hover */
  alwaysVisible?: boolean;
};

export const MessageToolbar: FC<MessageToolbarProps> = ({
  response,
  onUndo,
  alwaysVisible = false,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    if (!response) {
      return;
    }

    navigator.clipboard.writeText(response).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [response]);

  if (!response && !onUndo) {
    return null;
  }

  return (
    <MessageActions className={cn("mt-2", alwaysVisible && "opacity-100")}>
      {response ? (
        <MessageAction tooltip="复制" onClick={handleCopy}>
          {copied
            ? <CheckIcon className="size-4" />
            : <CopyIcon className="size-4" />}
        </MessageAction>
      ) : null}
      {onUndo ? (
        <MessageAction tooltip="撤销并编辑" onClick={onUndo}>
          <Undo2Icon className="size-4" />
        </MessageAction>
      ) : null}
      {response ? (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <MessageAction tooltip="更多">
              <MoreHorizontalIcon className="size-4" />
            </MessageAction>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuItem onClick={handleCopy}>复制原文</DropdownMenuItem>
            {onUndo ? (
              <DropdownMenuItem onClick={onUndo}>撤销并编辑</DropdownMenuItem>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      ) : null}
    </MessageActions>
  );
};

MessageToolbar.displayName = "MessageToolbar";
