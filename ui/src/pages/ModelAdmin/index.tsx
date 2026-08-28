import { useCallback, useEffect, useMemo, useState } from "react";
import classNames from "classnames";
import { Check, Cpu, Info, Loader2, Plus, Trash2, Zap } from "lucide-react";
import { Checkbox, Modal, Switch } from "antd";

import WorkspaceAdminHeader from "@/components/WorkspaceAdminHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  isFallbackModelUsage,
  llmModelAdminApi,
  type LlmApiRecord,
  type LlmModelRecord,
  type LlmModelUpsertPayload,
} from "@/services/llmModelAdmin";
import { showMessage } from "@/utils";

import { matchProviderId, PROVIDER_PRESETS, type ProviderPreset } from "./meta";

type EditorState = {
  id?: number;
  modelId: string;
  modelName: string;
  baseUrl: string;
  apiKey: string;
  completionsPath: string;
  embeddingsPath: string;
  modelType: string;
  modelUsage: string;
  isFallback: boolean;
  supportsThinking: number;
  contextWindow: number | null;
  status: number;
  apiId?: string;
  existingApiKey?: string;
  providerId: string;
};

type TestResult = { ok: boolean; ms: number; message?: string };

const EMPTY_EDITOR = (preset?: ProviderPreset): EditorState => ({
  modelId: "",
  modelName: "",
  baseUrl: preset?.baseUrl ?? "https://www.micuapi.ai/v1",
  apiKey: "",
  completionsPath: preset?.completionsPath ?? "/chat/completions",
  embeddingsPath: "/embeddings",
  modelType: "openai",
  modelUsage: "default",
  isFallback: false,
  supportsThinking: 0,
  contextWindow: null,
  status: 1,
  providerId: preset?.id ?? "micu",
});

function maskKey(key?: string) {
  if (!key) return "";
  if (key.length <= 10) return "••••";
  return `${key.slice(0, 6)}••••${key.slice(-4)}`;
}

function modelKey(model: LlmModelRecord) {
  return String(model.id);
}

function toEditor(model: LlmModelRecord, api?: LlmApiRecord): EditorState {
  const modelUsage = model.modelUsage || "default";
  return {
    id: model.id,
    modelId: model.modelId,
    modelName: model.modelName,
    baseUrl: api?.baseUrl || "",
    apiKey: maskKey(api?.apiKey),
    existingApiKey: api?.apiKey,
    completionsPath: api?.completionsPath || "/chat/completions",
    embeddingsPath: api?.embeddingsPath || "/embeddings",
    modelType: model.modelType || "openai",
    modelUsage,
    isFallback: isFallbackModelUsage(modelUsage),
    supportsThinking: model.supportsThinking ?? 0,
    contextWindow: model.contextWindow ?? null,
    status: model.status ?? 1,
    apiId: model.apiId,
    providerId: matchProviderId(api?.baseUrl),
  };
}

function Field({
  label,
  hint,
  children,
  className,
  required,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
  className?: string;
  required?: boolean;
}) {
  return (
    <label className={classNames("block", className)}>
      <span className="workspace-admin-field-label">
        {label}
        {required ? (
          <span className="text-[var(--color-danger)]"> *</span>
        ) : null}
      </span>
      {children}
      {hint ? <span className="workspace-admin-field-hint">{hint}</span> : null}
    </label>
  );
}

type ModelAdminProps = {
  /** 嵌在 Home 主布局内时隐藏返回/工作台切换，只保留标题与新增 */
  embedded?: boolean;
};

const ModelAdmin: ReactorType.FC<ModelAdminProps> = ({ embedded }) => {
  const [models, setModels] = useState<LlmModelRecord[]>([]);
  const [apis, setApis] = useState<LlmApiRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [selectedId, setSelectedId] = useState<string>("");
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [addForm, setAddForm] = useState<EditorState>(() => EMPTY_EDITOR());

  const apiMap = useMemo(() => {
    const map = new Map<string, LlmApiRecord>();
    apis.forEach((a) => map.set(a.apiId, a));
    return map;
  }, [apis]);

  const selected = useMemo(
    () => models.find((m) => modelKey(m) === selectedId) ?? null,
    [models, selectedId],
  );

  const refresh = useCallback(
    async (keepId?: string, preferredModelId?: string) => {
      setLoading(true);
      try {
        const [modelList, apiList] = await Promise.all([
          llmModelAdminApi.listModels(),
          llmModelAdminApi.listApis(),
        ]);
        const nextModels = Array.isArray(modelList) ? modelList : [];
        const nextApis = Array.isArray(apiList) ? apiList : [];
        setModels(nextModels);
        setApis(nextApis);

        const preferred = preferredModelId
          ? nextModels.find((m) => m.modelId === preferredModelId)
          : undefined;
        let prefer = "";
        if (keepId && nextModels.some((m) => modelKey(m) === keepId)) {
          prefer = keepId;
        } else if (preferred) {
          prefer = modelKey(preferred);
        } else if (
          selectedId && nextModels.some((m) => modelKey(m) === selectedId)
        ) {
          prefer = selectedId;
        } else if (nextModels[0]) {
          prefer = modelKey(nextModels[0]);
        }
        setSelectedId(prefer);
        if (prefer) {
          const hit = nextModels.find((m) => modelKey(m) === prefer);
          const api = hit
            ? nextApis.find((a) => a.apiId === hit.apiId)
            : undefined;
          setEditor(hit ? toEditor(hit, api) : null);
        } else {
          setEditor(null);
        }
        setTestResult(null);
      } catch (error) {
        showMessage()?.error(
          error instanceof Error ? error.message : "加载模型列表失败",
        );
      } finally {
        setLoading(false);
      }
    },
    [selectedId],
  );

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首屏加载
  }, []);

  const select = (modelId: string) => {
    setSelectedId(modelId);
    setTestResult(null);
    const hit = models.find((m) => modelKey(m) === modelId);
    setEditor(hit ? toEditor(hit, apiMap.get(hit.apiId)) : null);
  };

  const patchEditor = (patch: Partial<EditorState>) => {
    setEditor((prev) =>
      prev
        ? {
          ...prev,
          ...patch,
        }
        : prev,
    );
  };

  const applyProvider = (target: "editor" | "add", providerId: string) => {
    const preset =
      PROVIDER_PRESETS.find((p) => p.id === providerId) ??
      PROVIDER_PRESETS[PROVIDER_PRESETS.length - 1];
    const patch: Partial<EditorState> = {
      providerId: preset.id,
      baseUrl:
        preset.baseUrl ||
        (target === "editor" ? editor?.baseUrl : addForm.baseUrl) ||
        "",
      completionsPath: preset.completionsPath,
    };
    if (target === "editor") patchEditor(patch);
    else
      setAddForm((f) => ({
        ...f,
        ...patch,
      }));
  };

  const toPayload = (state: EditorState): LlmModelUpsertPayload => ({
    id: state.id,
    modelId: state.modelId.trim(),
    modelName: state.modelName.trim(),
    baseUrl: state.baseUrl.trim(),
    apiKey: state.apiKey,
    completionsPath: state.completionsPath.trim() || "/chat/completions",
    embeddingsPath: state.embeddingsPath.trim() || "/embeddings",
    modelType: state.modelType.trim() || "openai",
    modelUsage: state.isFallback
      ? "fallback"
      : isFallbackModelUsage(state.modelUsage)
        ? "default"
        : state.modelUsage.trim() || "default",
    supportsThinking: state.supportsThinking ?? 0,
    contextWindow: state.contextWindow,
    status: state.status,
    apiId: state.apiId,
  });

  const onSave = async () => {
    if (!editor) return;
    if (!editor.modelId.trim() || !editor.modelName.trim()) {
      showMessage()?.error("模型 ID 与上游模型名必填");
      return;
    }
    if (!editor.baseUrl.trim()) {
      showMessage()?.error("Base URL 必填");
      return;
    }
    setSaving(true);
    try {
      await llmModelAdminApi.upsertBinding(toPayload(editor), {
        isNew: false,
        existingApiKey: editor.existingApiKey,
      });
      showMessage()?.success("模型已保存，下一轮对话生效");
      await refresh(editor.id == null ? undefined : String(editor.id));
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onAdd = async () => {
    if (!addForm.modelId.trim() || !addForm.modelName.trim()) {
      showMessage()?.error("模型 ID 与上游模型名必填");
      return;
    }
    if (!addForm.baseUrl.trim() || !addForm.apiKey.trim()) {
      showMessage()?.error("Base URL 与 API Key 必填");
      return;
    }
    setSaving(true);
    try {
      const result = await llmModelAdminApi.upsertBinding(toPayload(addForm), {isNew: true,});
      showMessage()?.success("已新增模型，可直接在对话中选用");
      setAddOpen(false);
      setAddForm(EMPTY_EDITOR());
      await refresh(undefined, result.modelId);
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "新增失败");
    } finally {
      setSaving(false);
    }
  };

  const onDelete = (model: LlmModelRecord) => {
    Modal.confirm({
      title: "删除模型",
      content: `确认删除「${model.modelId}」配置 #${model.id}？若无其它模型共用其 API，将一并删除凭据配置。`,
      okText: "删除",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const apiId = model.apiId;
          await llmModelAdminApi.deleteModel(model.id);
          if (apiId) {
            const stillUsed = models.some(
              (m) => m.apiId === apiId && m.id !== model.id,
            );
            if (!stillUsed) {
              await llmModelAdminApi.deleteApi(apiId).catch(() => undefined);
            }
          }
          showMessage()?.success("已删除");
          await refresh();
        } catch (error) {
          showMessage()?.error(
            error instanceof Error ? error.message : "删除失败",
          );
        }
      },
    });
  };

  const onTest = async () => {
    if (editor?.id == null) return;
    setTesting(true);
    setTestResult(null);
    try {
      const result = await llmModelAdminApi.testConnectionById(editor.id);
      setTestResult(result);
      if (!result.ok) {
        showMessage()?.warning(result.message || "连接失败");
      }
    } catch (error) {
      setTestResult({
        ok: false,
        ms: 0,
        message: error instanceof Error ? error.message : "测试失败",
      });
    } finally {
      setTesting(false);
    }
  };

  const currentPreset = PROVIDER_PRESETS.find(
    (p) => p.id === (editor?.providerId || "custom"),
  );

  return (
    <div className="workspace-admin-shell">
      <WorkspaceAdminHeader
        title="模型配置"
        description="接入用于 Agent 推理的大语言模型，支持 OpenAI 兼容 Chat Completions。保存后热生效。"
        icon={Cpu}
        embedded={embedded}
        actions={
          <Button
            type="button"
            className="workspace-admin-primary"
            onClick={() => {
              setAddForm(EMPTY_EDITOR());
              setAddOpen(true);
            }}
          >
            <Plus className="size-[15px]" />
            新增模型
          </Button>
        }
      />

      <div className="workspace-admin-body">
        <div className="workspace-admin-body-inner">
          <div className="workspace-admin-workbench items-start">
            <aside className="workspace-admin-rail">
              <div className="workspace-admin-section-head">
                <div>
                  <div className="workspace-admin-section-title">
                    已接入模型
                  </div>
                  <div className="workspace-admin-section-meta">
                    {models.length} 个配置
                  </div>
                </div>
              </div>
              <div className="workspace-admin-list">
                {loading && models.length === 0 ? (
                  <div className="workspace-admin-empty">
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    加载中…
                  </div>
                ) : null}
                {models.map((m) => {
                  const active = modelKey(m) === selectedId;
                  const enabled = (m.status ?? 1) === 1;
                  const isFallback = isFallbackModelUsage(m.modelUsage);
                  return (
                    <div
                      key={modelKey(m)}
                      data-active={active}
                      className="workspace-admin-model-item"
                    >
                      <button
                        type="button"
                        data-active={active}
                        className={classNames(
                          "workspace-admin-list-item",
                          active && "font-medium",
                        )}
                        onClick={() => select(modelKey(m))}
                      >
                        <div className="flex min-w-0 items-center gap-2">
                          <span
                            className={classNames(
                              "workspace-admin-status-dot",
                              enabled
                                ? "bg-[var(--color-success)]"
                                : "bg-[var(--color-text-faint)]",
                            )}
                          />
                          <span className="workspace-admin-list-item-title">
                            {isFallback
                              ? m.modelName
                              : m.modelUsage && m.modelUsage !== "default"
                                ? m.modelUsage
                                : m.modelName}
                          </span>
                          <Badge
                            variant="secondary"
                            className="ml-auto text-[10.5px]"
                          >
                            {isFallback ? "备用" : "Chat"}
                          </Badge>
                        </div>
                        <div className="workspace-admin-list-item-meta pl-3.5">
                          <span className="workspace-admin-list-item-code">
                             #{m.id} · {m.modelName || "未设置模型标识"}
                          </span>
                        </div>
                      </button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="workspace-admin-model-delete workspace-admin-danger"
                        onClick={() => onDelete(m)}
                        aria-label={`删除模型 ${m.modelId} 配置 ${m.id}`}
                        title="删除模型"
                      >
                        <Trash2 className="size-3.5" strokeWidth={2} />
                      </Button>
                    </div>
                  );
                })}
                {!loading && models.length === 0 ? (
                  <div className="workspace-admin-empty">
                    暂无模型，点击右上角「新增模型」
                  </div>
                ) : null}
              </div>
            </aside>

            {editor && selected ? (
              <div className="min-w-0 flex-1">
                <Card className="gap-0 overflow-hidden rounded-[var(--kimi-radius-md)] border border-[var(--color-line)] bg-[var(--color-surface-raised)] py-0 shadow-none ring-0">
                  <CardHeader className="flex flex-row items-center justify-between gap-2.5 border-b border-[var(--color-line)] px-5 py-3.5">
                    <div>
                      <div className="workspace-admin-panel-title">
                        连接配置
                      </div>
                      <div className="workspace-admin-panel-subtitle">
                        {editor.modelId} · 修改后下一轮对话生效
                      </div>
                    </div>
                    <div className="flex items-center gap-2.5">
                      <span
                        className="workspace-admin-status"
                        data-disabled={editor.status !== 1}
                      >
                        <span
                          className="workspace-admin-status-dot"
                          aria-hidden="true"
                        />
                        {editor.status === 1 ? "已启用" : "已停用"}
                      </span>
                      <Switch
                        checked={editor.status === 1}
                        onChange={(checked) =>
                          patchEditor({ status: checked ? 1 : 0 })
                        }
                      />
                    </div>
                  </CardHeader>

                  <CardContent className="p-5">
                    <div className="grid grid-cols-1 gap-4 gap-x-[18px] sm:grid-cols-2">
                      <Field label="服务商">
                        <select
                          className="workspace-admin-select"
                          value={editor.providerId}
                          onChange={(e) =>
                            applyProvider("editor", e.target.value)
                          }
                        >
                          {PROVIDER_PRESETS.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.label}
                            </option>
                          ))}
                        </select>
                      </Field>
                    </div>

                    {currentPreset?.keyHint ? (
                      <div className="workspace-admin-callout mt-3">
                        <Info
                          className="mt-px size-[14px] shrink-0"
                          strokeWidth={2}
                        />
                        <span>{currentPreset.keyHint}</span>
                      </div>
                    ) : null}

                    <div className="mt-4">
                      <Field
                        label="Base URL"
                        hint={
                          editor.providerId !== "custom"
                            ? "由所选服务商预设；改成「自定义」可手填代理地址"
                            : undefined
                        }
                      >
                        <Input
                          value={editor.baseUrl}
                          readOnly={editor.providerId !== "custom"}
                          className={classNames(
                            "font-mono text-[12.5px]",
                            editor.providerId !== "custom" &&
                              "bg-[var(--color-surface-sunken)] text-[var(--color-text-muted)]",
                          )}
                          onChange={(e) =>
                            patchEditor({ baseUrl: e.target.value })
                          }
                        />
                      </Field>
                    </div>

                    <div className="mt-4 grid grid-cols-1 gap-4 gap-x-[18px] sm:grid-cols-2">
                      <Field
                        label="模型标识（上游 model）"
                        className="sm:col-span-2"
                        hint="与厂商文档逐字一致，如 grok-4.5 / gpt-4o"
                      >
                        <Input
                          value={editor.modelName}
                          className="font-mono text-[12.5px]"
                          placeholder="gpt-4o / claude-3-5-sonnet"
                          onChange={(e) =>
                            patchEditor({
                              modelName: e.target.value,
                              modelId: editor.modelId || e.target.value,
                            })
                          }
                        />
                      </Field>
                      <Field label="模型 ID（业务主键，对话引用）">
                        <Input
                          value={editor.modelId}
                          disabled
                          className="bg-[var(--color-surface-sunken)] font-mono text-[12.5px] text-[var(--color-text-muted)]"
                        />
                      </Field>
                      <Field
                        label="API Key"
                        hint="脱敏显示；要轮换 key 请重新输入明文后保存"
                      >
                        <Input
                          type="password"
                          value={editor.apiKey}
                          className="font-mono text-[12.5px]"
                          placeholder="sk-…"
                          onChange={(e) =>
                            patchEditor({ apiKey: e.target.value })
                          }
                        />
                      </Field>
                      <Field label="Completions 路径">
                        <Input
                          value={editor.completionsPath}
                          className="font-mono text-[12.5px]"
                          onChange={(e) =>
                            patchEditor({ completionsPath: e.target.value })
                          }
                        />
                      </Field>
                      <Field label="模型类型">
                        <Input
                          value={editor.modelType}
                          className="text-[13px]"
                          placeholder="openai / deepseek / claude"
                          onChange={(e) =>
                            patchEditor({ modelType: e.target.value })
                          }
                        />
                      </Field>
                      <div className="flex items-center gap-2 pt-5">
                        <Switch
                          checked={editor.supportsThinking === 1}
                          onChange={(checked) =>
                            patchEditor({ supportsThinking: checked ? 1 : 0 })
                          }
                        />
                        <span className="text-[13px] text-[var(--color-text)]">
                          支持深度思考
                        </span>
                      </div>
                      <div className="flex items-center gap-2 pt-5">
                        <Checkbox
                          checked={editor.isFallback}
                          onChange={(event) =>
                            patchEditor({ isFallback: event.target.checked })
                          }
                        >
                          备用模型
                        </Checkbox>
                      </div>
                      <Field label="上下文窗口（token）">
                        <Input
                          type="number"
                          value={editor.contextWindow ?? ""}
                          className="text-[13px]"
                          placeholder="如 128000"
                          onChange={(e) => {
                            const raw = e.target.value.trim();
                            patchEditor({
                              contextWindow:
                                raw === "" ? null : Number(raw) || null,
                            });
                          }}
                        />
                      </Field>
                    </div>
                  </CardContent>

                  <CardFooter className="flex flex-wrap items-center gap-3 border-t border-[var(--color-line)] bg-[var(--color-surface-sunken)] px-5 py-3.5">
                    <Button
                      type="button"
                      variant="outline"
                      className="workspace-admin-secondary"
                      disabled={testing}
                      onClick={() => void onTest()}
                    >
                      {testing ? (
                        <Loader2 className="mr-1 size-[15px] animate-spin" />
                      ) : (
                        <Zap className="mr-1 size-[15px]" />
                      )}
                      测试连接
                    </Button>
                    {testResult?.ok ? (
                      <span className="workspace-admin-status">
                        <Check className="size-[15px]" strokeWidth={2.2} />
                        连接成功 · 延迟 {testResult.ms}ms
                      </span>
                    ) : null}
                    {testResult && !testResult.ok ? (
                      <span className="max-w-md truncate text-[12.5px] font-medium text-[var(--color-danger)]">
                        {testResult.message ?? "连接失败"}
                      </span>
                    ) : null}
                    <Button
                      type="button"
                      className="workspace-admin-primary ml-auto"
                      disabled={saving}
                      onClick={() => void onSave()}
                    >
                      {saving ? "保存中…" : "保存模型"}
                    </Button>
                  </CardFooter>
                </Card>
              </div>
            ) : (
              <div className="workspace-admin-panel flex min-h-[280px] items-center justify-center border-dashed bg-[var(--color-surface)] p-12 text-center">
                选择左侧模型，或点击「新增模型」
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 新增模型 Dialog（对齐参考 openFormModal） */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>新增模型</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <Field label="服务商">
              <select
                className="workspace-admin-select"
                value={addForm.providerId}
                onChange={(e) => applyProvider("add", e.target.value)}
              >
                {PROVIDER_PRESETS.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Base URL" required>
              <Input
                value={addForm.baseUrl}
                readOnly={addForm.providerId !== "custom"}
                className={classNames(
                  "font-mono text-[12.5px]",
                  addForm.providerId !== "custom" &&
                    "bg-[var(--color-surface-sunken)]",
                )}
                onChange={(e) =>
                  setAddForm((f) => ({
                    ...f,
                    baseUrl: e.target.value,
                  }))
                }
              />
            </Field>
            <Field label="模型标识（上游 model）" required>
              <Input
                value={addForm.modelName}
                className="font-mono text-[12.5px]"
                placeholder="与厂商文档逐字一致"
                onChange={(e) => {
                  const modelName = e.target.value;
                  setAddForm((f) => ({
                    ...f,
                    modelName,
                    modelId: f.modelId || modelName,
                  }));
                }}
              />
            </Field>
            <Field label="模型 ID" required>
              <Input
                value={addForm.modelId}
                className="font-mono text-[12.5px]"
                placeholder="业务主键，对话可引用"
                onChange={(e) =>
                  setAddForm((f) => ({
                    ...f,
                    modelId: e.target.value,
                  }))
                }
              />
            </Field>
            <Field label="API Key" required>
              <Input
                type="password"
                value={addForm.apiKey}
                className="font-mono text-[12.5px]"
                placeholder="sk-…"
                onChange={(e) =>
                  setAddForm((f) => ({
                    ...f,
                    apiKey: e.target.value,
                  }))
                }
              />
            </Field>
            <div className="pt-1">
              <Checkbox
                checked={addForm.isFallback}
                onChange={(event) =>
                  setAddForm((f) => ({
                    ...f,
                    isFallback: event.target.checked,
                  }))
                }
              >
                备用模型
              </Checkbox>
              <div className="workspace-admin-field-hint mt-1 pl-6">
                主模型失败后按配置顺序尝试
              </div>
            </div>
            <Field label="Completions 路径">
              <Input
                value={addForm.completionsPath}
                className="font-mono text-[12.5px]"
                onChange={(e) =>
                  setAddForm((f) => ({
                    ...f,
                    completionsPath: e.target.value,
                  }))
                }
              />
            </Field>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setAddOpen(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              className="workspace-admin-primary"
              disabled={saving}
              onClick={() => void onAdd()}
            >
              {saving ? "提交中…" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default ModelAdmin;
