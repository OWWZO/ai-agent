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
  RefreshCwIcon,
  MoreHorizontalIcon,
} from "lucide-react";
import { normalizeMarkdownForDisplay } from "@/utils/markdown";

export type MessageToolbarProps = {
  response?: string;
  onRegenerate?: () => void;
};

export const MessageToolbar: FC<MessageToolbarProps> = ({
  response,
  onRegenerate,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    if (!response) {
      return;
    }

    navigator.clipboard.writeText(
      normalizeMarkdownForDisplay(response, { scope: "default" })
    ).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [response]);

  if (!response) {
    return null;
  }

  return (
    <MessageActions className="mt-2">
      <MessageAction tooltip="复制" onClick={handleCopy}>
        {copied
          ? <CheckIcon className="size-4" />
          : <CopyIcon className="size-4" />}
      </MessageAction>
      <MessageAction tooltip="重新生成" onClick={onRegenerate} disabled={!onRegenerate}>
        <RefreshCwIcon className="size-4" />
      </MessageAction>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <MessageAction tooltip="更多">
            <MoreHorizontalIcon className="size-4" />
          </MessageAction>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuItem onClick={handleCopy}>复制原文</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </MessageActions>
  );
};

MessageToolbar.displayName = "MessageToolbar";
