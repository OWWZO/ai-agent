import { useCallback, useEffect, useState } from "react";
import { Check, SlidersHorizontal } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import {
  sessionCapabilityApi,
  type SessionCapabilities,
} from "@/services/sessionCapability";
import { showMessage } from "@/utils";

type Props = {
  sessionId: string;
  disabled?: boolean;
  triggerClassName?: (active?: boolean, disabled?: boolean) => string;
};

const CapabilityPicker: ReactorType.FC<Props> = ({
  sessionId,
  disabled,
  triggerClassName,
}) => {
  const [open, setOpen] = useState(false);
  const [caps, setCaps] = useState<SessionCapabilities | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true);
    try {
      const data = await sessionCapabilityApi.get(sessionId);
      setCaps(data);
    } catch {
      setCaps({ locked: false, skills: [], mcpServers: [] });
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const enabledCount =
    (caps?.skills.filter((s) => s.enabled).length ?? 0) +
    (caps?.mcpServers.filter((m) => m.enabled).length ?? 0);

  const toggle = async (
    kind: "skill" | "mcp",
    refId: string,
    enabled: boolean
  ) => {
    if (caps?.locked) return;
    try {
      await sessionCapabilityApi.setEnabled(sessionId, kind, refId, enabled);
      setCaps((prev) => {
        if (!prev) return prev;
        if (kind === "skill") {
          return {
            ...prev,
            skills: prev.skills.map((s) =>
              s.refId === refId ? { ...s, enabled } : s
            ),
          };
        }
        return {
          ...prev,
          mcpServers: prev.mcpServers.map((m) =>
            m.refId === refId ? { ...m, enabled } : m
          ),
        };
      });
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "切换失败");
    }
  };

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          disabled={disabled || !sessionId}
          className={
            triggerClassName
              ? triggerClassName(enabledCount > 0, disabled)
              : undefined
          }
          title={
            caps?.locked
              ? "本会话能力集已锁定"
              : "选择本会话启用的技能与 MCP"
          }
        >
          <SlidersHorizontal className="size-3.5 shrink-0 opacity-80" />
          {enabledCount > 0 ? (
            <span className="text-[11px] tabular-nums">{enabledCount}</span>
          ) : (
            <span className="truncate">能力</span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        side="bottom"
        sideOffset={10}
        className="max-h-[360px] w-[280px] overflow-y-auto rounded-[18px] border border-black/[0.04] bg-white p-2 shadow-[0_12px_40px_-16px_rgba(15,23,42,0.28)]"
      >
        {loading && !caps ? (
          <div className="px-2 py-6 text-center text-[12px] text-[#aeaeb2]">
            加载中…
          </div>
        ) : null}
        {caps?.locked ? (
          <div className="mb-2 rounded-md bg-slate-50 px-2 py-1.5 text-[11px] text-slate-500">
            能力由配置锁定，只读
          </div>
        ) : null}
        <Section title="技能">
          {(caps?.skills?.length ?? 0) === 0 ? (
            <Empty>暂无技能</Empty>
          ) : (
            caps!.skills.map((s) => (
              <ToggleRow
                key={s.refId}
                label={s.name}
                hint={s.source}
                checked={s.enabled}
                locked={!!caps?.locked}
                onChange={(on) => void toggle("skill", s.refId, on)}
              />
            ))
          )}
        </Section>
        <Section title="MCP">
          {(caps?.mcpServers?.length ?? 0) === 0 ? (
            <Empty>暂无 MCP 连接</Empty>
          ) : (
            caps!.mcpServers.map((m) => (
              <ToggleRow
                key={m.refId}
                label={m.name}
                hint={m.refId}
                checked={m.enabled}
                locked={!!caps?.locked}
                onChange={(on) => void toggle("mcp", m.refId, on)}
              />
            ))
          )}
        </Section>
        <p className="mt-1 px-1 text-[10.5px] leading-relaxed text-[#aeaeb2]">
          关闭后从下一轮对话起不装入工具；刷新后状态保留。
        </p>
        <a
          href="/workspace/capabilities"
          target="_blank"
          rel="noreferrer"
          className="mt-1 block px-1 text-[11.5px] font-medium text-sky-600 hover:underline"
        >
          管理能力库（添加技能 / MCP）→
        </a>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-2">
      <div className="px-1.5 py-1 text-[10.5px] font-medium text-[#86868b]">
        {title}
      </div>
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return (
    <div className="px-2 py-2 text-[11.5px] text-[#aeaeb2]">{children}</div>
  );
}

function ToggleRow({
  label,
  hint,
  checked,
  locked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  locked: boolean;
  onChange: (on: boolean) => void;
}) {
  return (
    <button
      type="button"
      disabled={locked}
      className={cn(
        "flex w-full items-center gap-2 rounded-[12px] px-2 py-1.5 text-left transition-colors",
        locked ? "opacity-60" : "hover:bg-[#f5f5f7]"
      )}
      onClick={() => onChange(!checked)}
    >
      <span
        className={cn(
          "flex size-4 shrink-0 items-center justify-center rounded border",
          checked
            ? "border-sky-500 bg-sky-500 text-white"
            : "border-slate-300 bg-white"
        )}
      >
        {checked ? <Check className="size-3" strokeWidth={3} /> : null}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[12.5px] font-medium text-[#1d1d1f]">
          {label}
        </span>
        {hint ? (
          <span className="block truncate text-[10.5px] text-[#aeaeb2]">
            {hint}
          </span>
        ) : null}
      </span>
    </button>
  );
}

export default CapabilityPicker;
