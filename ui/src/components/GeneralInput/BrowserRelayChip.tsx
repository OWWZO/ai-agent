import { useCallback, useEffect, useState } from "react";
import { Globe } from "lucide-react";
import { cn } from "@/lib/utils";
import { showMessage } from "@/utils";
import { browserRelayApi, type BrowserPairing, type BrowserRelayStatus } from "@/services/browserRelay";

type Props = {
  disabled?: boolean;
};

export default function BrowserRelayChip({ disabled }: Props) {
  const [status, setStatus] = useState<BrowserRelayStatus>({ connected: false });
  const [pairing, setPairing] = useState<BrowserPairing | null>(null);
  const [busy, setBusy] = useState(false);

  const sendPairingToExtension = useCallback((data: BrowserPairing) => {
    window.postMessage(
      { source: "reactor", type: "browser-pair", token: data.token, relayUrl: data.relayUrl },
      window.location.origin
    );
  }, []);

  const refresh = useCallback(async () => {
    try {
      const data = await browserRelayApi.status();
      setStatus(data ?? { connected: false });
      if (data?.connected) {
        setPairing(null);
      }
    } catch {
      setStatus({ connected: false });
    }
  }, []);

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (event.source !== window) return;
      const msg = event.data as { source?: string; type?: string; ok?: boolean; error?: string } | null;
      if (msg?.source !== "reactor-extension") return;
      if (msg.type !== "browser-pair-result") return;
      if (msg.ok) {
        void refresh();
        return;
      }
      if (msg.error) showMessage({ content: msg.error, type: "error" });
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [refresh]);

  useEffect(() => {
    void refresh();
    const interval = pairing && !status.connected ? 1000 : 5000;
    const timer = window.setInterval(() => void refresh(), interval);
    return () => window.clearInterval(timer);
  }, [refresh, pairing, status.connected]);

  const onConnect = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const data = await browserRelayApi.pairing();
      if (!data?.token || !data?.relayUrl) {
        showMessage({ content: "发起配对失败", type: "error" });
        return;
      }
      setPairing(data);
      sendPairingToExtension(data);
    } catch (error) {
      showMessage({ content: error instanceof Error ? error.message : "发起配对失败", type: "error" });
    } finally {
      setBusy(false);
    }
  };

  const onDisconnect = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await browserRelayApi.disconnect();
      setPairing(null);
      setStatus({ connected: false });
    } finally {
      setBusy(false);
    }
  };

  const connected = Boolean(status.connected);
  const origin = pairing?.relayUrl
    ? pairing.relayUrl.replace(/^wss:/, "https:").replace(/^ws:/, "http:").replace(/\/api\/agent\/browser\/relay.*$/, "")
    : "";

  return (
    <div className="flex min-w-0 items-center gap-1">
      <button
        type="button"
        disabled={disabled || busy}
        className={cn(
          "inline-flex h-8 max-w-[140px] items-center gap-1 rounded-md px-2 text-[12px] text-[#6b6b70] hover:bg-black/[0.04] hover:text-[#1d1d1f]",
          connected && "bg-[#3b82f6] text-white hover:bg-[#2563eb] hover:text-white"
        )}
        title={
          connected
            ? (status.tabTitle || status.tabUrl || "浏览器已连接")
            : pairing
              ? `等待扩展连接；配对码 ${pairing.code}，服务地址 ${origin}`
              : "连接本机 Chrome/Edge"
        }
        onClick={connected ? onDisconnect : onConnect}
      >
        <Globe className="size-3.5 shrink-0 opacity-80" />
        <span className="truncate">{connected ? "浏览器已连接" : "连接浏览器"}</span>
      </button>
      {pairing && !connected ? (
        <span className="truncate text-[11px] text-[#6b6b70]" title={`扩展弹窗输入配对码；服务地址 ${origin}`}>
          码 {pairing.code}
        </span>
      ) : null}
    </div>
  );
}
