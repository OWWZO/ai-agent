import { useCallback, useEffect, useMemo, useState } from "react";
import { Bot, Loader2, Plus, RefreshCcw, Trash2 } from "lucide-react";
import { Modal, Select, Switch } from "antd";

import WorkspaceAdminHeader from "@/components/WorkspaceAdminHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
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
  // 编辑时复制数组字段，避免表单修改直接污染列表缓存中的服务端快照。
  return {
    isNew: false,
    agentKey: record.agentKey,
    displayName: record.displayName || "",
    whenToUse: record.whenToUse || "",
    systemPrompt: record.systemPrompt || "",
    allowedTools: record.allowedTools?.length
      ? [...record.allowedTools]
      : ["*"],
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
    // 列表和工具目录并行加载；目录失败可降级为通配符，不阻断定义列表展示。
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
        error instanceof Error ? error.message : "加载子 Agent 列表失败",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    // 列表刷新后重新把当前 key 映射到 draft，保证外部更新能反映到编辑表单。
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
    // 先校验最小可运行契约，再统一 trim 文本并把空数组转为未配置。
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
        allowedTools: draft.allowedTools?.length
          ? draft.allowedTools
          : undefined,
        disallowedTools: draft.disallowedTools?.length
          ? draft.disallowedTools
          : undefined,
        maxSteps: draft.maxSteps ?? null,
        status: draft.status ?? 1,
      };
      if (draft.isNew) {
        // 新建和更新共用 payload，但分别调用后端生命周期操作。
        await subAgentDefinitionAdminApi.create(payload);
        showMessage()?.success("已创建并热加载");
      } else {
        await subAgentDefinitionAdminApi.update(payload);
        showMessage()?.success("已更新并热加载");
      }
      await refresh();
      setSelectedKey(payload.agentKey);
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onDelete = () => {
    // 删除是软删除且会影响运行时 Registry，因此必须绑定当前已保存定义并二次确认。
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
            error instanceof Error ? error.message : "删除失败",
          );
        }
      },
    });
  };

  const onReload = async () => {
    // Registry 重载完成后重新拉取列表，让管理页显示运行时实际生效的定义数量。
    try {
      const count = await subAgentDefinitionAdminApi.reload();
      showMessage()?.success(`Registry 已重载，配置条数 ${count}`);
      await refresh();
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "重载失败");
    }
  };

  const toolOptions = useMemo(
    () => catalog.map((name) => ({
      label: name,
      value: name
    })),
    [catalog],
  );

  return (
    <div className="workspace-admin-shell">
      <WorkspaceAdminHeader
        title="子 Agent 装配"
        description="定义可被主 Agent 调度的角色、指令与工具边界。保存后立即热加载。"
        icon={Bot}
        embedded={embedded}
      />

      <div className="workspace-admin-body">
        <div className="workspace-admin-body-inner">
          <div className="workspace-admin-workbench">
            <aside className="workspace-admin-rail">
              <div className="workspace-admin-section-head">
                <div>
                  <div className="workspace-admin-section-title">定义列表</div>
                  <div className="workspace-admin-section-meta">
                    {items.length} 个可调度角色
                  </div>
                </div>
                <div className="flex gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => void refresh()}
                    disabled={loading}
                    aria-label="刷新子 Agent 列表"
                    title="刷新列表"
                  >
                    {loading ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <RefreshCcw className="h-4 w-4" />
                    )}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={onNew}
                    aria-label="新建子 Agent"
                    title="新建子 Agent"
                  >
                    <Plus className="h-4 w-4" />
                  </Button>
                </div>
              </div>

              <div className="workspace-admin-list">
                {items.length === 0 && !loading ? (
                  <div className="workspace-admin-empty">
                    暂无自定义子 Agent
                  </div>
                ) : null}
                {items.map((item) => (
                  <button
                    key={item.agentKey}
                    type="button"
                    onClick={() => onSelect(item.agentKey)}
                    data-active={selectedKey === item.agentKey}
                    className="workspace-admin-list-item"
                  >
                    <span className="workspace-admin-list-item-title">
                      {item.displayName || item.agentKey}
                    </span>
                    <span className="workspace-admin-list-item-meta">
                      <span className="workspace-admin-list-item-code">
                        {item.agentKey}
                      </span>
                      <span
                        className="workspace-admin-status"
                        data-disabled={item.status === 0}
                      >
                        <span
                          className="workspace-admin-status-dot"
                          aria-hidden="true"
                        />
                        {item.status === 0 ? "禁用" : "启用"}
                      </span>
                    </span>
                  </button>
                ))}
              </div>

              <div className="workspace-admin-footer">
                <Button
                  type="button"
                  variant="outline"
                  className="workspace-admin-secondary w-full"
                  onClick={() => void onReload()}
                >
                  重载 Registry
                </Button>
              </div>
            </aside>

            <section className="workspace-admin-panel">
              <div className="workspace-admin-panel-head">
                <div>
                  <div className="workspace-admin-panel-title">
                    {draft.isNew ? "新建子 Agent" : `编辑 · ${draft.agentKey}`}
                  </div>
                  <div className="workspace-admin-panel-subtitle">
                    角色定义会影响主 Agent 的调度描述与工具权限。
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  {!draft.isNew ? (
                    <Button
                      type="button"
                      variant="ghost"
                      className="workspace-admin-danger"
                      onClick={onDelete}
                      disabled={saving}
                    >
                      <Trash2 className="h-4 w-4" />
                      删除
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    className="workspace-admin-primary"
                    onClick={() => void onSave()}
                    disabled={saving}
                  >
                    {saving ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : null}
                    保存定义
                  </Button>
                </div>
              </div>

              <div className="workspace-admin-panel-body">
                <div className="workspace-admin-form-section">
                  <div className="workspace-admin-form-section-title">
                    角色身份
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label>
                      <span className="workspace-admin-field-label">
                        agentKey（subagent_type）
                      </span>
                      <Input
                        value={draft.agentKey}
                        disabled={!draft.isNew || saving}
                        onChange={(e) =>
                          setDraft((d) => ({
                            ...d,
                            agentKey: e.target.value
                          }))
                        }
                        placeholder="如 code-reviewer"
                        className="font-mono"
                      />
                    </label>
                    <label>
                      <span className="workspace-admin-field-label">
                        展示名
                      </span>
                      <Input
                        value={draft.displayName || ""}
                        disabled={saving}
                        onChange={(e) =>
                          setDraft((d) => ({
                            ...d,
                            displayName: e.target.value,
                          }))
                        }
                        placeholder="代码审查"
                      />
                    </label>
                  </div>
                </div>

                <div className="workspace-admin-form-section">
                  <div className="workspace-admin-form-section-title">
                    调度指令
                  </div>
                  <div className="grid gap-4">
                    <label>
                      <span className="workspace-admin-field-label">
                        whenToUse（主 Agent 何时调用）
                      </span>
                      <Input
                        value={draft.whenToUse}
                        disabled={saving}
                        onChange={(e) =>
                          setDraft((d) => ({
                            ...d,
                            whenToUse: e.target.value
                          }))
                        }
                        placeholder="只读代码审查、安全与可维护性建议"
                      />
                    </label>
                    <label>
                      <span className="workspace-admin-field-label">
                        systemPrompt
                      </span>
                      <Textarea
                        value={draft.systemPrompt}
                        disabled={saving}
                        rows={10}
                        onChange={(e) =>
                          setDraft((d) => ({
                            ...d,
                            systemPrompt: e.target.value,
                          }))
                        }
                        placeholder="子 Agent 系统提示词"
                        className="font-mono text-[12px]"
                      />
                    </label>
                  </div>
                </div>

                <div className="workspace-admin-form-section">
                  <div className="workspace-admin-form-section-title">
                    工具边界
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label>
                      <span className="workspace-admin-field-label">
                        允许工具（* 表示全部）
                      </span>
                      <Select
                        mode="multiple"
                        className="workspace-admin-multi-select w-full"
                        disabled={saving}
                        options={toolOptions}
                        value={draft.allowedTools || []}
                        onChange={(value) =>
                          setDraft((d) => ({
                            ...d,
                            allowedTools: value as string[],
                          }))
                        }
                        placeholder="选择工具"
                      />
                    </label>
                    <label>
                      <span className="workspace-admin-field-label">
                        禁止工具
                      </span>
                      <Select
                        mode="multiple"
                        className="workspace-admin-multi-select w-full"
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
                  </div>
                </div>

                <div className="workspace-admin-form-section">
                  <div className="workspace-admin-form-section-title">
                    执行边界
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label>
                      <span className="workspace-admin-field-label">
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
                    <label className="flex items-center gap-3 self-end pb-2">
                      <Switch
                        checked={(draft.status ?? 1) === 1}
                        disabled={saving}
                        onChange={(checked) =>
                          setDraft((d) => ({
                            ...d,
                            status: checked ? 1 : 0
                          }))
                        }
                      />
                      <span>
                        <span className="workspace-admin-field-label mb-0">
                          启用此定义
                        </span>
                        <span className="workspace-admin-field-hint mt-1">
                          禁用后主 Agent 不会再调度它。
                        </span>
                      </span>
                    </label>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SubAgentAdmin;
