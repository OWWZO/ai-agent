import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import { ArrowLeft, Bot, Loader2, Plus, RefreshCcw, Trash2 } from "lucide-react";
import { Modal, Select, Switch } from "antd";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ROUTES } from "@/router/routes";
import {
  subAgentDefinitionAdminApi,
  type SubAgentDefinitionRecord,
  type SubAgentDefinitionUpsertPayload,
} from "@/services/subAgentDefinitionAdmin";
import { showMessage } from "@/utils";

type Draft = SubAgentDefinitionUpsertPayload & { isNew: boolean };

const EMPTY_DRAFT = (): Draft => ({
  isNew: true,
  agentKey: "",
  displayName: "",
  whenToUse: "",
  systemPrompt: "",
  allowedTools: ["*"],
  disallowedTools: [],
  maxSteps: 10,
  status: 1,
});

function recordToDraft(record: SubAgentDefinitionRecord): Draft {
  return {
    isNew: false,
    agentKey: record.agentKey,
    displayName: record.displayName || "",
    whenToUse: record.whenToUse || "",
    systemPrompt: record.systemPrompt || "",
    allowedTools: record.allowedTools?.length ? [...record.allowedTools] : ["*"],
    disallowedTools: record.disallowedTools ? [...record.disallowedTools] : [],
    maxSteps: record.maxSteps ?? null,
    status: record.status ?? 1,
  };
}

type SubAgentAdminProps = {
  embedded?: boolean;
};

const SubAgentAdmin: ReactorType.FC<SubAgentAdminProps> = ({ embedded }) => {
  const [items, setItems] = useState<SubAgentDefinitionRecord[]>([]);
  const [catalog, setCatalog] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT());

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [list, tools] = await Promise.all([
        subAgentDefinitionAdminApi.queryList(),
        subAgentDefinitionAdminApi.toolCatalog().catch(() => [] as string[]),
      ]);
      setItems(Array.isArray(list) ? list : []);
      setCatalog(Array.isArray(tools) && tools.length ? tools : ["*"]);
      setSelectedKey((prev) => {
        if (prev && list?.some((item) => item.agentKey === prev)) {
          return prev;
        }
        return null;
      });
    } catch (error) {
      showMessage()?.error(
        error instanceof Error ? error.message : "加载子 Agent 列表失败"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!selectedKey) {
      return;
    }
    const hit = items.find((item) => item.agentKey === selectedKey);
    if (hit) {
      setDraft(recordToDraft(hit));
    }
  }, [items, selectedKey]);

  const onNew = () => {
    setSelectedKey(null);
    setDraft(EMPTY_DRAFT());
  };

  const onSelect = (key: string) => {
    setSelectedKey(key);
    const hit = items.find((item) => item.agentKey === key);
    if (hit) {
      setDraft(recordToDraft(hit));
    }
  };

  const onSave = async () => {
    if (!draft.agentKey.trim()) {
      showMessage()?.error("agentKey 不能为空");
      return;
    }
    if (!draft.whenToUse.trim() || !draft.systemPrompt.trim()) {
      showMessage()?.error("whenToUse 与 systemPrompt 必填");
      return;
    }
    setSaving(true);
    try {
      const payload: SubAgentDefinitionUpsertPayload = {
        agentKey: draft.agentKey.trim(),
        displayName: draft.displayName?.trim() || undefined,
        whenToUse: draft.whenToUse.trim(),
        systemPrompt: draft.systemPrompt,
        allowedTools: draft.allowedTools?.length ? draft.allowedTools : undefined,
        disallowedTools: draft.disallowedTools?.length
          ? draft.disallowedTools
          : undefined,
        maxSteps: draft.maxSteps ?? null,
        status: draft.status ?? 1,
      };
      if (draft.isNew) {
        await subAgentDefinitionAdminApi.create(payload);
        showMessage()?.success("已创建并热加载");
      } else {
        await subAgentDefinitionAdminApi.update(payload);
        showMessage()?.success("已更新并热加载");
      }
      await refresh();
      setSelectedKey(payload.agentKey);
    } catch (error) {
      showMessage()?.error(
        error instanceof Error ? error.message : "保存失败"
      );
    } finally {
      setSaving(false);
    }
  };

  const onDelete = () => {
    if (!selectedKey || draft.isNew) {
      return;
    }
    Modal.confirm({
      title: "删除子 Agent",
      content: `确认软删除「${selectedKey}」？主 Agent 将无法再调度该类型。`,
      okText: "删除",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await subAgentDefinitionAdminApi.remove(selectedKey);
          showMessage()?.success("已删除并热加载");
          setSelectedKey(null);
          setDraft(EMPTY_DRAFT());
          await refresh();
        } catch (error) {
          showMessage()?.error(
            error instanceof Error ? error.message : "删除失败"
          );
        }
      },
    });
  };

  const onReload = async () => {
    try {
      const count = await subAgentDefinitionAdminApi.reload();
      showMessage()?.success(`Registry 已重载，配置条数 ${count}`);
      await refresh();
    } catch (error) {
      showMessage()?.error(
        error instanceof Error ? error.message : "重载失败"
      );
    }
  };

  const toolOptions = useMemo(
    () => catalog.map((name) => ({ label: name, value: name })),
    [catalog]
  );

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--chat-bg,#f8fafc)]">
      {!embedded ? (
        <div className="border-b border-slate-200 bg-white/90 px-4 py-3 sm:px-6">
          <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <Link
                to={ROUTES.HOME}
                className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900"
              >
                <ArrowLeft className="h-4 w-4" />
                返回
              </Link>
              <div className="flex items-center gap-2">
                <Bot className="h-5 w-5 text-sky-600" />
                <div>
                  <div className="text-base font-semibold text-slate-900">
                    子 Agent 装配
                  </div>
                  <div className="text-[12px] text-slate-400">
                    自定义 prompt / 工具，供主 Agent 的 Agent 工具调度
                  </div>
                </div>
              </div>
            </div>
            <WorkspaceToolSwitcher />
          </div>
        </div>
      ) : (
        <div className="border-b border-slate-200 bg-white/90 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            <Bot className="h-5 w-5 text-sky-600" />
            <div>
              <div className="text-base font-semibold text-slate-900">
                子 Agent 装配
              </div>
              <div className="text-[12px] text-slate-400">
                自定义 prompt / 工具，供主 Agent 的 Agent 工具调度
              </div>
            </div>
          </div>
        </div>
      )}

      <div
        className={classNames(
          "flex min-h-0 w-full flex-1 gap-4 p-4 sm:p-6",
          embedded ? "" : "mx-auto max-w-[1400px]"
        )}
      >
        <aside className="flex w-full max-w-[320px] shrink-0 flex-col rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2.5">
            <span className="text-sm font-medium text-slate-700">定义列表</span>
            <div className="flex gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => void refresh()}
                disabled={loading}
              >
                {loading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <RefreshCcw className="h-4 w-4" />
                )}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={onNew}>
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {items.length === 0 && !loading ? (
              <div className="px-2 py-8 text-center text-sm text-slate-400">
                暂无自定义子 Agent
              </div>
            ) : null}
            {items.map((item) => (
              <button
                key={item.agentKey}
                type="button"
                onClick={() => onSelect(item.agentKey)}
                className={classNames(
                  "mb-1 w-full rounded-xl px-3 py-2.5 text-left transition",
                  selectedKey === item.agentKey
                    ? "bg-sky-50 text-sky-900 ring-1 ring-sky-100"
                    : "hover:bg-slate-50 text-slate-700"
                )}
              >
                <div className="truncate text-sm font-semibold">
                  {item.displayName || item.agentKey}
                </div>
                <div className="mt-0.5 truncate text-[12px] text-slate-400">
                  {item.agentKey}
                  {item.status === 0 ? " · 禁用" : " · 启用"}
                </div>
              </button>
            ))}
          </div>
          <div className="border-t border-slate-100 p-2">
            <Button
              type="button"
              variant="outline"
              className="w-full"
              onClick={() => void onReload()}
            >
              重载 Registry
            </Button>
          </div>
        </aside>

        <section className="min-w-0 flex-1 overflow-y-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div className="text-base font-semibold text-slate-900">
              {draft.isNew ? "新建子 Agent" : `编辑 · ${draft.agentKey}`}
            </div>
            <div className="flex flex-wrap gap-2">
              {!draft.isNew ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={onDelete}
                  disabled={saving}
                >
                  <Trash2 className="mr-1 h-4 w-4" />
                  删除
                </Button>
              ) : null}
              <Button type="button" onClick={() => void onSave()} disabled={saving}>
                {saving ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : null}
                保存
              </Button>
            </div>
          </div>

          <div className="grid gap-4">
            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                agentKey（subagent_type）
              </span>
              <Input
                value={draft.agentKey}
                disabled={!draft.isNew || saving}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, agentKey: e.target.value }))
                }
                placeholder="如 code-reviewer"
              />
            </label>

            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                展示名
              </span>
              <Input
                value={draft.displayName || ""}
                disabled={saving}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, displayName: e.target.value }))
                }
                placeholder="代码审查"
              />
            </label>

            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                whenToUse（主 Agent 何时调用）
              </span>
              <Input
                value={draft.whenToUse}
                disabled={saving}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, whenToUse: e.target.value }))
                }
                placeholder="只读代码审查、安全与可维护性建议"
              />
            </label>

            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                systemPrompt
              </span>
              <Textarea
                value={draft.systemPrompt}
                disabled={saving}
                rows={10}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, systemPrompt: e.target.value }))
                }
                placeholder="子 Agent 系统提示词"
              />
            </label>

            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                允许工具（* 表示全部）
              </span>
              <Select
                mode="multiple"
                className="w-full"
                disabled={saving}
                options={toolOptions}
                value={draft.allowedTools || []}
                onChange={(value) =>
                  setDraft((d) => ({ ...d, allowedTools: value as string[] }))
                }
                placeholder="选择工具"
              />
            </label>

            <label className="grid gap-2">
              <span className="text-[13px] font-medium text-slate-700">
                禁止工具
              </span>
              <Select
                mode="multiple"
                className="w-full"
                disabled={saving}
                options={toolOptions.filter((o) => o.value !== "*")}
                value={draft.disallowedTools || []}
                onChange={(value) =>
                  setDraft((d) => ({
                    ...d,
                    disallowedTools: value as string[],
                  }))
                }
                placeholder="可选"
              />
            </label>

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-2">
                <span className="text-[13px] font-medium text-slate-700">
                  maxSteps
                </span>
                <Input
                  type="number"
                  value={draft.maxSteps ?? ""}
                  disabled={saving}
                  onChange={(e) => {
                    const raw = e.target.value;
                    setDraft((d) => ({
                      ...d,
                      maxSteps: raw === "" ? null : Number(raw),
                    }));
                  }}
                  placeholder="默认沿用 React 配置"
                />
              </label>
              <label className="flex items-center gap-3 pt-6">
                <span className="text-[13px] font-medium text-slate-700">
                  启用
                </span>
                <Switch
                  checked={(draft.status ?? 1) === 1}
                  disabled={saving}
                  onChange={(checked) =>
                    setDraft((d) => ({ ...d, status: checked ? 1 : 0 }))
                  }
                />
              </label>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};

export default SubAgentAdmin;
