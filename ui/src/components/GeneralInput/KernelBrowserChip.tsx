import { useCallback, useEffect, useState } from "react";
import { Cloud, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";
import { showMessage } from "@/utils";
import { kernelBrowserApi, type KernelBrowserStatus } from "@/services/kernelBrowser";

type Props = {
  disabled?: boolean;
};

export default function KernelBrowserChip({ disabled }: Props) {
  const [status, setStatus] = useState<KernelBrowserStatus | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setStatus(await kernelBrowserApi.status());
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
      showMessage()?.error("请允许弹出窗口后再打开云端浏览器");
      return;
    }
    popup.opener = null;
    setBusy(true);
    try {
      const liveView = await kernelBrowserApi.ensure();
      if (!liveView?.browserLiveViewUrl) {
        popup.close();
        showMessage()?.error("云端浏览器地址不可用");
        return;
      }
      popup.location.replace(liveView.browserLiveViewUrl);
      await refresh();
    } catch (error) {
      popup.close();
      showMessage()?.error(error instanceof Error ? error.message : "打开云端浏览器失败");
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    if (busy || disabled) return;
    setBusy(true);
    try {
      await kernelBrowserApi.reset();
      await refresh();
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "重置云端浏览器失败");
    } finally {
      setBusy(false);
    }
  };

  const lastUsedAt = status?.lastUsedAt
    ? new Date(status.lastUsedAt).toLocaleString("zh-CN")
    : null;
  const title = status?.exists
    ? `云端浏览器已启动${lastUsedAt ? `，最近使用：${lastUsedAt}` : ""}`
    : "打开当前用户的云端浏览器";

  return (
    <div className="flex min-w-0 items-center gap-0.5">
      <button
        type="button"
        disabled={disabled || busy}
        className={cn(
          "inline-flex h-8 max-w-[170px] min-w-0 items-center gap-1 rounded-md px-2 text-[12px] text-[#6b6b70] hover:bg-black/[0.04] hover:text-[#1d1d1f]",
          status?.exists && "text-[#16794b] hover:text-[#12633d]"
        )}
        title={title}
        onClick={() => void openLiveView()}
      >
        <Cloud className="size-3.5 shrink-0 opacity-80" />
        <span className="truncate">{status?.exists ? "云端浏览器" : "启动云端浏览器"}</span>
      </button>
      {status?.exists ? (
        <>
          <span className="hidden max-w-[118px] truncate text-[10px] text-[#86868b] sm:inline" title={lastUsedAt ?? "已启动"}>
            {lastUsedAt ? `最近 ${lastUsedAt}` : "已启动"}
          </span>
          <button
            type="button"
            aria-label="重置云端浏览器"
            disabled={disabled || busy}
            className="inline-flex size-7 shrink-0 items-center justify-center rounded-md text-[#6b6b70] hover:bg-black/[0.04] hover:text-[#1d1d1f] disabled:opacity-45"
            title="重置云端浏览器并清除页面状态"
            onClick={() => void reset()}
          >
            <RotateCcw className="size-3.5" />
          </button>
        </>
      ) : null}
    </div>
  );
}
