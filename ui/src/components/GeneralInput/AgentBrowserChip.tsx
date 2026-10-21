import { useCallback, useEffect, useState } from "react";
import { Cloud, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { agentBrowserApi, type AgentBrowserStatus } from "@/services/agentBrowser";

type Props = {
  disabled?: boolean;
};

export default function AgentBrowserChip({ disabled }: Props) {
  const [status, setStatus] = useState<AgentBrowserStatus | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setStatus(await agentBrowserApi.status());
    } catch {
      setStatus(null);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openLiveView = async () => {
    if (busy || disabled) return;
    const popup = window.open("about:blank", "_blank");
    if (!popup) {
      toast.error("请允许弹出窗口后再打开 Agent 浏览器");
      return;
    }
    popup.opener = null;
    setBusy(true);
    try {
      const liveView = await agentBrowserApi.ensure();
      if (!liveView?.browserLiveViewUrl) {
        popup.close();
        toast.error("Agent 浏览器地址不可用");
        return;
      }
      popup.location.replace(liveView.browserLiveViewUrl);
      await refresh();
    } catch (error) {
      popup.close();
      toast.error(error instanceof Error ? error.message : "打开 Agent 浏览器失败");
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    if (busy || disabled) return;
    setBusy(true);
    try {
      await agentBrowserApi.reset();
      await refresh();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "重置 Agent 浏览器失败");
    } finally {
      setBusy(false);
    }
  };

  const lastUsedAt = status?.lastUsedAt
    ? new Date(status.lastUsedAt).toLocaleString("zh-CN")
    : null;
  const title = status?.exists
    ? `Agent 浏览器已启动${lastUsedAt ? `，最近使用：${lastUsedAt}` : ""}`
    : "打开当前用户的 Agent 浏览器";

  return (
    <div className="flex min-w-0 items-center gap-0.5">
      <button
        type="button"
        disabled={disabled || busy}
        className={cn(
          "inline-flex h-8 max-w-[170px] min-w-0 items-center gap-1 rounded-md px-2 text-ui-sm text-foreground-subtle hover:bg-black/[0.04] hover:text-foreground",
          status?.exists && "text-success hover:text-success"
        )}
        title={title}
        onClick={() => void openLiveView()}
      >
        <Cloud className="size-3.5 shrink-0 opacity-80" />
        <span className="truncate">{status?.exists ? "Agent 浏览器" : "启动 Agent 浏览器"}</span>
      </button>
      {status?.exists ? (
        <>
          <span className="hidden max-w-[118px] truncate text-ui-xs text-foreground-subtle sm:inline" title={lastUsedAt ?? "已启动"}>
            {lastUsedAt ? `最近 ${lastUsedAt}` : "已启动"}
          </span>
          <button
            type="button"
            aria-label="重置 Agent 浏览器"
            disabled={disabled || busy}
            className="inline-flex size-7 shrink-0 items-center justify-center rounded-md text-foreground-subtle hover:bg-black/[0.04] hover:text-foreground disabled:opacity-45"
            title="重置 Agent 浏览器并清除页面状态"
            onClick={() => void reset()}
          >
            <RotateCcw className="size-3.5" />
          </button>
        </>
      ) : null}
    </div>
  );
}
