import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import {
  ArrowLeft,
  Check,
  Cpu,
  Info,
  Loader2,
  Plus,
  Trash2,
  Zap,
} from "lucide-react";
import { Modal, Switch } from "antd";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ROUTES } from "@/router/routes";
import {
  llmModelAdminApi,
  type LlmApiRecord,
  type LlmModelRecord,
  type LlmModelUpsertPayload,
} from "@/services/llmModelAdmin";
import { showMessage } from "@/utils";

import { matchProviderId, PROVIDER_PRESETS, type ProviderPreset } from "./meta";

type EditorState = {
  modelId: string;
  modelName: string;
  displayName: string;
  baseUrl: string;
  apiKey: string;
  completionsPath: string;
  embeddingsPath: string;
  modelType: string;
  modelUsage: string;
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
  displayName: "",
  baseUrl: preset?.baseUrl ?? "https://www.micuapi.ai/v1",
  apiKey: "",
  completionsPath: preset?.completionsPath ?? "/chat/completions",
  embeddingsPath: "/embeddings",
  modelType: "openai",
  modelUsage: "default",
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

function toEditor(model: LlmModelRecord, api?: LlmApiRecord): EditorState {
  return {
    modelId: model.modelId,
    modelName: model.modelName,
    displayName: model.modelUsage || model.modelName,
    baseUrl: api?.baseUrl || "",
    apiKey: maskKey(api?.apiKey),
    existingApiKey: api?.apiKey,
    completionsPath: api?.completionsPath || "/chat/completions",
    embeddingsPath: api?.embeddingsPath || "/embeddings",
    modelType: model.modelType || "openai",
    modelUsage: model.modelUsage || "default",
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
      <span className="mb-1.5 block text-[13px] font-medium text-slate-700">
        {label}
        {required ? <span className="text-red-500"> *</span> : null}
      </span>
      {children}
      {hint ? (
        <span className="mt-1 block text-[11.5px] leading-relaxed text-slate-400">
          {hint}
        </span>
      ) : null}
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
    () => models.find((m) => m.modelId === selectedId) ?? null,
    [models, selectedId]
  );

  const refresh = useCallback(async (keepId?: string) => {
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

      const prefer =
        keepId && nextModels.some((m) => m.modelId === keepId)
          ? keepId
          : selectedId && nextModels.some((m) => m.modelId === selectedId)
            ? selectedId
            : nextModels[0]?.modelId || "";
      setSelectedId(prefer);
      if (prefer) {
        const hit = nextModels.find((m) => m.modelId === prefer);
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
        error instanceof Error ? error.message : "加载模型列表失败"
      );
    } finally {
      setLoading(false);
    }
  }, [selectedId]);

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首屏加载
  }, []);

  const select = (modelId: string) => {
    setSelectedId(modelId);
    setTestResult(null);
    const hit = models.find((m) => m.modelId === modelId);
    setEditor(hit ? toEditor(hit, apiMap.get(hit.apiId)) : null);
  };

  const patchEditor = (patch: Partial<EditorState>) => {
    setEditor((prev) => (prev ? { ...prev, ...patch } : prev));
  };

  const applyProvider = (
    target: "editor" | "add",
    providerId: string
  ) => {
    const preset =
      PROVIDER_PRESETS.find((p) => p.id === providerId) ??
      PROVIDER_PRESETS[PROVIDER_PRESETS.length - 1];
    const patch: Partial<EditorState> = {
      providerId: preset.id,
      baseUrl: preset.baseUrl || (target === "editor" ? editor?.baseUrl : addForm.baseUrl) || "",
      completionsPath: preset.completionsPath,
    };
    if (target === "editor") patchEditor(patch);
    else setAddForm((f) => ({ ...f, ...patch }));
  };

  const toPayload = (state: EditorState): LlmModelUpsertPayload => ({
    modelId: state.modelId.trim(),
    modelName: state.modelName.trim(),
    baseUrl: state.baseUrl.trim(),
    apiKey: state.apiKey,
    completionsPath: state.completionsPath.trim() || "/chat/completions",
    embeddingsPath: state.embeddingsPath.trim() || "/embeddings",
    modelType: state.modelType.trim() || "openai",
    modelUsage: state.displayName.trim() || state.modelUsage || "default",
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
      await refresh(editor.modelId);
    } catch (error) {
      showMessage()?.error(
        error instanceof Error ? error.message : "保存失败"
      );
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
      const result = await llmModelAdminApi.upsertBinding(toPayload(addForm), {
        isNew: true,
      });
      showMessage()?.success("已新增模型，可直接在对话中选用");
      setAddOpen(false);
      setAddForm(EMPTY_EDITOR());
      await refresh(result.modelId);
    } catch (error) {
      showMessage()?.error(
        error instanceof Error ? error.message : "新增失败"
      );
    } finally {
      setSaving(false);
    }
  };

  const onDelete = (modelId: string) => {
    Modal.confirm({
      title: "删除模型",
      content: `确认删除「${modelId}」？若无其它模型共用其 API，将一并删除凭据配置。`,
      okText: "删除",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const hit = models.find((m) => m.modelId === modelId);
          const apiId = hit?.apiId;
          await llmModelAdminApi.deleteModel(modelId);
          if (apiId) {
            const stillUsed = models.some(
              (m) => m.apiId === apiId && m.modelId !== modelId
            );
            if (!stillUsed) {
              await llmModelAdminApi.deleteApi(apiId).catch(() => undefined);
            }
          }
          showMessage()?.success("已删除");
          await refresh();
        } catch (error) {
          showMessage()?.error(
            error instanceof Error ? error.message : "删除失败"
          );
        }
      },
    });
  };

  const onTest = async () => {
    if (!selectedId) return;
    setTesting(true);
    setTestResult(null);
    try {
      const result = await llmModelAdminApi.testConnection(selectedId);
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
    (p) => p.id === (editor?.providerId || "custom")
  );

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--chat-bg,#f8fafc)]">
      <div className="border-b border-slate-200 bg-white/90 px-4 py-3 sm:px-6">
        <div
          className={classNames(
            "flex flex-wrap items-start justify-between gap-3",
            embedded ? "" : "mx-auto max-w-[1400px]"
          )}
        >
          <div className="flex items-start gap-3">
            {!embedded ? (
              <Link
                to={ROUTES.HOME}
                target="_blank"
                rel="noreferrer"
                className="mt-1 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900"
              >
                <ArrowLeft className="h-4 w-4" />
                返回
              </Link>
            ) : null}
            <div>
              <div className="flex items-center gap-2 text-[15px] font-semibold text-slate-900">
                <Cpu className="h-5 w-5 text-sky-600" />
                模型配置
              </div>
              <div className="mt-[3px] max-w-xl text-[13px] text-slate-500">
                接入用于 Agent 推理的大语言模型（OpenAI 兼容 Chat
                Completions）。保存后热生效，无需重启服务。
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {!embedded ? <WorkspaceToolSwitcher /> : null}
            <Button
              type="button"
              onClick={() => {
                setAddForm(EMPTY_EDITOR());
                setAddOpen(true);
              }}
            >
              <Plus className="mr-1 size-[15px]" />
              新增模型
            </Button>
          </div>
        </div>
      </div>

      <div
        className={classNames(
          "min-h-0 w-full flex-1 overflow-y-auto p-4 sm:p-6",
          embedded ? "" : "mx-auto max-w-[1400px]"
        )}
      >
        <div className="grid grid-cols-1 items-start gap-4 md:grid-cols-[340px_1fr]">
          {/* 左：模型卡片列表（参考 rag-models） */}
          <div className="flex flex-col gap-2.5">
            {loading && models.length === 0 ? (
              <div className="flex items-center justify-center rounded-lg border border-slate-200 bg-white p-8 text-slate-400">
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                加载中…
              </div>
            ) : null}
            {models.map((m) => {
              const active = m.modelId === selectedId;
              const enabled = (m.status ?? 1) === 1;
              return (
                <button
                  key={m.modelId}
                  type="button"
                  className={classNames(
                    "rounded-lg border p-4 text-left shadow-sm transition-colors",
                    active
                      ? "border-[1.5px] border-sky-500 bg-sky-50/80"
                      : "border-slate-200 bg-white hover:border-slate-300"
                  )}
                  onClick={() => select(m.modelId)}
                >
                  <div className="mb-[7px] flex items-center gap-2">
                    <span
                      className={classNames(
                        "size-[7px] shrink-0 rounded-full",
                        enabled ? "bg-emerald-500" : "bg-slate-300"
                      )}
                    />
                    <span className="min-w-0 flex-1 truncate text-[13.5px] font-semibold text-slate-900">
                      {m.modelUsage && m.modelUsage !== "default"
                        ? m.modelUsage
                        : m.modelName}
                    </span>
                    <Badge variant="secondary" className="text-[10.5px]">
                      Chat
                    </Badge>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate font-mono text-[11.5px] text-slate-400">
                      {m.modelName || "未设置模型标识"}
                    </span>
                    <span
                      role="button"
                      tabIndex={0}
                      className="flex size-6 shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(m.modelId);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.stopPropagation();
                          onDelete(m.modelId);
                        }
                      }}
                    >
                      <Trash2 className="size-3.5" strokeWidth={2} />
                    </span>
                  </div>
                </button>
              );
            })}
            {!loading && models.length === 0 ? (
              <div className="rounded-lg border border-slate-200 bg-white p-6 text-center text-xs text-slate-400">
                暂无模型，点击右上角「新增模型」
              </div>
            ) : null}
          </div>

          {/* 右：连接配置 Card */}
          {editor && selected ? (
            <div className="flex flex-col gap-4">
              <Card className="gap-0 py-0 ring-slate-200">
                <CardHeader className="flex flex-row items-center justify-between gap-2.5 border-b border-slate-100 px-5 py-3.5">
                  <div className="text-sm font-semibold text-slate-900">
                    连接配置
                  </div>
                  <div className="flex items-center gap-2.5">
                    <span className="text-xs text-slate-500">启用</span>
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
                    <Field label="展示名称">
                      <Input
                        value={editor.displayName}
                        className="text-[13px]"
                        placeholder="便于分辨的名字"
                        onChange={(e) =>
                          patchEditor({ displayName: e.target.value })
                        }
                      />
                    </Field>
                    <Field label="服务商">
                      <select
                        className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-[13px] outline-none focus:border-sky-400"
                        value={editor.providerId}
                        onChange={(e) => applyProvider("editor", e.target.value)}
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
                    <div className="mt-3 flex items-start gap-2 rounded-md bg-slate-50 px-3 py-2 text-xs leading-relaxed text-slate-500">
                      <Info className="mt-px size-[14px] shrink-0" strokeWidth={2} />
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
                            "bg-slate-50 text-slate-500"
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
                        className="font-mono text-[12.5px] bg-slate-50 text-slate-500"
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
                      <span className="text-[13px] text-slate-700">
                        支持深度思考
                      </span>
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

                <CardFooter className="flex flex-wrap items-center gap-3 border-t border-slate-100 px-5 py-3.5">
                  <Button
                    type="button"
                    variant="outline"
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
                    <span className="inline-flex items-center gap-1.5 text-[12.5px] font-medium text-emerald-600">
                      <Check className="size-[15px]" strokeWidth={2.2} />
                      连接成功 · 延迟 {testResult.ms}ms
                    </span>
                  ) : null}
                  {testResult && !testResult.ok ? (
                    <span className="max-w-md truncate text-[12.5px] font-medium text-red-600">
                      {testResult.message ?? "连接失败"}
                    </span>
                  ) : null}
                  <Button
                    type="button"
                    className="ml-auto"
                    disabled={saving}
                    onClick={() => void onSave()}
                  >
                    {saving ? "保存中…" : "保存模型"}
                  </Button>
                </CardFooter>
              </Card>

              <div className="flex items-start gap-3 rounded-lg border border-sky-200/80 bg-sky-50/60 p-4">
                <Cpu
                  className="mt-px size-[17px] shrink-0 text-sky-600"
                  strokeWidth={2}
                />
                <div className="text-[12.5px] leading-relaxed text-slate-600">
                  已启用的模型会出现在对话输入框的模型下拉里。请求体会按 OpenAI
                  Chat Completions 协议出站；改 Key / Base
                  URL 后保存即可热切换，无需重启。
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-slate-200 bg-white p-12 text-center text-sm text-slate-400">
              选择左侧模型，或点击「新增模型」
            </div>
          )}
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
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-[13px]"
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
                  addForm.providerId !== "custom" && "bg-slate-50"
                )}
                onChange={(e) =>
                  setAddForm((f) => ({ ...f, baseUrl: e.target.value }))
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
                    displayName: f.displayName || modelName,
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
                  setAddForm((f) => ({ ...f, modelId: e.target.value }))
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
                  setAddForm((f) => ({ ...f, apiKey: e.target.value }))
                }
              />
            </Field>
            <Field label="展示名称">
              <Input
                value={addForm.displayName}
                placeholder="选完模型可改成便于分辨的名字"
                onChange={(e) =>
                  setAddForm((f) => ({ ...f, displayName: e.target.value }))
                }
              />
            </Field>
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
            <Button type="button" disabled={saving} onClick={() => void onAdd()}>
              {saving ? "提交中…" : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default ModelAdmin;
