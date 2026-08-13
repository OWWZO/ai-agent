import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import {
  ArrowLeft,
  ClipboardList,
  Loader2,
  Plus,
  RefreshCcw,
  Search,
  Trash2,
} from "lucide-react";
import { Modal, Select } from "antd";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import { ROUTES } from "@/router/routes";
import {
  deleteSop,
  listSops,
  recallTestSop,
  setSopStatus,
  upsertSop,
} from "@/services/sopWorkspace";
import { showMessage } from "@/utils";

import type { SopEditorDraft, SopItem, SopRecallTestResult, SopStatus } from "./types";
import {
  createEmptyDraft,
  createEmptyStep,
  resolveToolBaseUrl,
  sopItemToDraft,
  statusLabel,
} from "./utils";

interface WorkspaceSopProps {
  embedded?: boolean;
}

const WorkspaceSop: ReactorType.FC<WorkspaceSopProps> = ({ embedded }) => {
  const toolBaseUrl = useMemo(() => resolveToolBaseUrl(), []);
  const [items, setItems] = useState<SopItem[]>([]);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [loadingList, setLoadingList] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draft, setDraft] = useState<SopEditorDraft>(createEmptyDraft());
  const [recallQuery, setRecallQuery] = useState("");
  const [recallLoading, setRecallLoading] = useState(false);
  const [recallResult, setRecallResult] = useState<SopRecallTestResult | null>(null);

  const refreshList = useCallback(
    async (options?: { keyword?: string }) => {
      const nextKeyword =
        options && "keyword" in options ? options.keyword || "" : appliedKeyword;
      setLoadingList(true);
      try {
        // 列表刷新只替换服务端事实；若当前选中项已被删除则清空选择，仍存在的草稿不被
        // 无意义地重置，避免用户在切换筛选条件时丢失未保存编辑。
        const next = await listSops(toolBaseUrl, {
          keyword: nextKeyword,
          status: statusFilter || undefined,
        });
        setItems(next);
        setSelectedId((previous) =>
          previous && !next.some((item) => item.sopId === previous)
            ? null
            : previous
        );
      } catch (error) {
        showMessage()?.error(
          error instanceof Error ? error.message : "加载 SOP 列表失败"
        );
      } finally {
        setLoadingList(false);
      }
    },
    [appliedKeyword, statusFilter, toolBaseUrl]
  );

  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  const handleSelect = (item: SopItem) => {
    setSelectedId(item.sopId);
    setDraft(sopItemToDraft(item));
  };

  const handleCreate = () => {
    setSelectedId(null);
    setDraft(createEmptyDraft());
  };

  const updateStepTitle = (index: number, title: string) => {
    setDraft((prev) => {
      const sopSteps = [...prev.sopSteps];
      sopSteps[index] = { ...sopSteps[index], title };
      return { ...prev, sopSteps };
    });
  };

  const updateSubStep = (stepIndex: number, subIndex: number, value: string) => {
    setDraft((prev) => {
      const sopSteps = [...prev.sopSteps];
      const steps = [...(sopSteps[stepIndex]?.steps || [])];
      steps[subIndex] = value;
      sopSteps[stepIndex] = { ...sopSteps[stepIndex], steps };
      return { ...prev, sopSteps };
    });
  };

  const addStep = () => {
    setDraft((prev) => ({
      ...prev,
      sopSteps: [...prev.sopSteps, createEmptyStep()],
    }));
  };

  const removeStep = (index: number) => {
    setDraft((prev) => {
      if (prev.sopSteps.length <= 1) {
        return prev;
      }
      return {
        ...prev,
        sopSteps: prev.sopSteps.filter((_, i) => i !== index),
      };
    });
  };

  const addSubStep = (stepIndex: number) => {
    setDraft((prev) => {
      const sopSteps = [...prev.sopSteps];
      const steps = [...(sopSteps[stepIndex]?.steps || []), ""];
      sopSteps[stepIndex] = { ...sopSteps[stepIndex], steps };
      return { ...prev, sopSteps };
    });
  };

  const removeSubStep = (stepIndex: number, subIndex: number) => {
    setDraft((prev) => {
      const sopSteps = [...prev.sopSteps];
      const steps = [...(sopSteps[stepIndex]?.steps || [])];
      if (steps.length <= 1) {
        steps[0] = "";
      } else {
        steps.splice(subIndex, 1);
      }
      sopSteps[stepIndex] = { ...sopSteps[stepIndex], steps };
      return { ...prev, sopSteps };
    });
  };

  const handleSave = async () => {
    if (!draft.sopName.trim()) {
      showMessage()?.warning("请填写 SOP 名称");
      return;
    }
    setSaving(true);
    try {
      // 保存前清洗空步骤/空子步骤，服务端只接收可召回的 SOP 内容；保存成功后再用返回值
      // 回填 draft，保证本地状态与后端生成的 sopId、状态和规范化步骤一致。
      const saved = await upsertSop(toolBaseUrl, {
        sopId: draft.sopId,
        sopName: draft.sopName.trim(),
        sopDesc: draft.sopDesc.trim(),
        sopType: draft.sopType || "list",
        sopSteps: draft.sopSteps
          .map((step) => ({
            title: step.title.trim(),
            steps: step.steps.map((s) => s.trim()).filter(Boolean),
          }))
          .filter((step) => step.title || step.steps.length > 0),
        status: draft.status,
      });
      showMessage()?.success("保存成功");
      setSelectedId(saved.sopId);
      setDraft(sopItemToDraft(saved));
      await refreshList();
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = () => {
    if (!draft.sopId) {
      setDraft(createEmptyDraft());
      return;
    }
    Modal.confirm({
      title: "删除 SOP",
      content: `确认删除「${draft.sopName || draft.sopId}」？删除后不可恢复。`,
      okText: "删除",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        try {
          await deleteSop(toolBaseUrl, draft.sopId!);
          showMessage()?.success("已删除");
          setSelectedId(null);
          setDraft(createEmptyDraft());
          await refreshList();
        } catch (error) {
          showMessage()?.error(error instanceof Error ? error.message : "删除失败");
          throw error;
        }
      },
    });
  };

  const handleToggleStatus = async (status: SopStatus) => {
    if (!draft.sopId) {
      setDraft((prev) => ({ ...prev, status }));
      return;
    }
    setSaving(true);
    try {
      // 已持久化 SOP 的状态切换直接调用后端；新建草稿只修改本地状态，等保存时一并提交。
      const next = await setSopStatus(toolBaseUrl, draft.sopId, status);
      setDraft(sopItemToDraft(next));
      showMessage()?.success(status === "online" ? "已上线" : "已更新状态");
      await refreshList();
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "状态更新失败");
    } finally {
      setSaving(false);
    }
  };

  const handleRecallTest = async () => {
    if (!recallQuery.trim()) {
      showMessage()?.warning("请输入测试 query");
      return;
    }
    setRecallLoading(true);
    try {
      // 试召回使用当前输入的 query，不依赖编辑器草稿是否已保存，用于验证线上索引实际
      // 返回的模式、命中项和注入文本。
      const result = await recallTestSop(toolBaseUrl, recallQuery.trim());
      setRecallResult(result);
    } catch (error) {
      showMessage()?.error(error instanceof Error ? error.message : "试召回失败");
    } finally {
      setRecallLoading(false);
    }
  };

  return (
    <div
      className={classNames(
        "flex h-full min-h-0 flex-col bg-[var(--chat-bg)]",
        embedded ? "" : "px-4 py-4 sm:px-6"
      )}
    >
      {!embedded ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Link
              to={ROUTES.HOME}
              target="_blank"
              rel="noreferrer"
              className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition hover:text-slate-900"
              title="返回首页"
            >
              <ArrowLeft className="h-4 w-4" />
            </Link>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-sky-600" />
                <h1 className="truncate text-lg font-semibold text-slate-900">
                  SOP 工作台
                </h1>
              </div>
              <p className="text-xs text-slate-400">
                管理 PlanSolve 语义召回的标准作业程序（直连 reactor-tool / Qdrant）
              </p>
            </div>
          </div>
          <WorkspaceToolSwitcher />
        </div>
      ) : null}

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[280px_minmax(0,1fr)_320px]">
        {/* 左：列表 */}
        <section className="flex min-h-0 flex-col rounded-3xl border border-slate-200 bg-white/90 p-3 shadow-[var(--shadow-sm)]">
          <div className="mb-3 flex items-center gap-2">
            <div className="relative min-w-0 flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
              <input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    setAppliedKeyword(keyword.trim());
                  }
                }}
                placeholder="搜索名称/描述"
                className="h-9 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm outline-none transition focus:border-sky-300 focus:bg-white"
              />
            </div>
            <button
              type="button"
              onClick={() => {
                setAppliedKeyword(keyword.trim());
                void refreshList({ keyword: keyword.trim() });
              }}
              className="inline-flex h-9 w-9 items-center justify-center rounded-2xl border border-slate-200 text-slate-500 hover:bg-slate-50"
              title="刷新"
            >
              <RefreshCcw className={classNames("h-3.5 w-3.5", loadingList && "animate-spin")} />
            </button>
            <button
              type="button"
              onClick={handleCreate}
              className="inline-flex h-9 w-9 items-center justify-center rounded-2xl bg-sky-600 text-white hover:bg-sky-500"
              title="新建"
            >
              <Plus className="h-4 w-4" />
            </button>
          </div>
          <Select
            allowClear
            placeholder="全部状态"
            className="mb-3 w-full"
            value={statusFilter || undefined}
            onChange={(value) => setStatusFilter(value || "")}
            options={[
              { value: "online", label: "已上线" },
              { value: "offline", label: "已下线" },
              { value: "draft", label: "草稿" },
            ]}
          />
          <div className="min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
            {loadingList && items.length === 0 ? (
              <div className="flex items-center justify-center py-10 text-sm text-slate-400">
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                加载中…
              </div>
            ) : null}
            {!loadingList && items.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-400">
                暂无 SOP，点击 + 新建
              </div>
            ) : null}
            {items.map((item) => {
              const active = item.sopId === selectedId;
              return (
                <button
                  key={item.sopId}
                  type="button"
                  onClick={() => handleSelect(item)}
                  className={classNames(
                    "w-full rounded-2xl border px-3 py-2.5 text-left transition",
                    active
                      ? "border-sky-200 bg-sky-50 shadow-sm"
                      : "border-slate-100 bg-white hover:border-slate-200 hover:bg-slate-50"
                  )}
                >
                  <div className="truncate text-sm font-semibold text-slate-800">
                    {item.sopName || "未命名"}
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-2">
                    <span className="truncate text-[12px] text-slate-400">
                      {item.sopDesc || item.sopId}
                    </span>
                    <span
                      className={classNames(
                        "shrink-0 rounded-full px-2 py-0.5 text-[11px]",
                        item.status === "online"
                          ? "bg-emerald-50 text-emerald-600"
                          : item.status === "draft"
                            ? "bg-[var(--chat-surface-muted)] text-[var(--chat-text-soft)]"
                            : "bg-slate-100 text-slate-500"
                      )}
                    >
                      {statusLabel(item.status)}
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </section>

        {/* 中：编辑器 */}
        <section className="flex min-h-0 flex-col rounded-3xl border border-slate-200 bg-white/90 p-4 shadow-[var(--shadow-sm)]">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold text-slate-900">
                {draft.sopId ? "编辑 SOP" : "新建 SOP"}
              </h2>
              {draft.sopId ? (
                <p className="text-[12px] text-slate-400">ID: {draft.sopId}</p>
              ) : null}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value={draft.status}
                className="w-[120px]"
                onChange={(value) => void handleToggleStatus(value as SopStatus)}
                options={[
                  { value: "online", label: "上线" },
                  { value: "offline", label: "下线" },
                  { value: "draft", label: "草稿" },
                ]}
              />
              {draft.sopId ? (
                <button
                  type="button"
                  onClick={handleDelete}
                  className="inline-flex h-9 items-center gap-1.5 rounded-2xl border border-rose-200 px-3 text-sm text-rose-600 hover:bg-rose-50"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  删除
                </button>
              ) : null}
              <button
                type="button"
                disabled={saving}
                onClick={() => void handleSave()}
                className="inline-flex h-9 items-center gap-1.5 rounded-2xl bg-sky-600 px-4 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-60"
              >
                {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
                保存并索引
              </button>
            </div>
          </div>

          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
            <label className="block">
              <span className="mb-1.5 block text-xs font-medium text-slate-500">名称</span>
              <input
                value={draft.sopName}
                onChange={(event) =>
                  setDraft((prev) => ({ ...prev, sopName: event.target.value }))
                }
                className="h-10 w-full rounded-2xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none focus:border-sky-300 focus:bg-white"
                placeholder="例如：对销售数据进行综合分析"
              />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-xs font-medium text-slate-500">描述</span>
              <textarea
                value={draft.sopDesc}
                onChange={(event) =>
                  setDraft((prev) => ({ ...prev, sopDesc: event.target.value }))
                }
                rows={3}
                className="w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-sky-300 focus:bg-white"
                placeholder="该 SOP 适用的业务场景与目标"
              />
            </label>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500">步骤</span>
                <button
                  type="button"
                  onClick={addStep}
                  className="text-xs font-medium text-sky-600 hover:text-sky-500"
                >
                  + 添加步骤
                </button>
              </div>
              <div className="space-y-3">
                {draft.sopSteps.map((step, stepIndex) => (
                  <div
                    key={`step-${stepIndex}`}
                    className="rounded-2xl border border-slate-100 bg-slate-50/80 p-3"
                  >
                    <div className="mb-2 flex items-center gap-2">
                      <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white text-[11px] font-semibold text-slate-500 shadow-sm">
                        {stepIndex + 1}
                      </span>
                      <input
                        value={step.title}
                        onChange={(event) =>
                          updateStepTitle(stepIndex, event.target.value)
                        }
                        className="h-9 min-w-0 flex-1 rounded-xl border border-slate-200 bg-white px-3 text-sm outline-none focus:border-sky-300"
                        placeholder="步骤标题"
                      />
                      <button
                        type="button"
                        onClick={() => removeStep(stepIndex)}
                        className="text-xs text-slate-400 hover:text-rose-500"
                      >
                        删除
                      </button>
                    </div>
                    <div className="space-y-2 pl-8">
                      {step.steps.map((sub, subIndex) => (
                        <div key={`sub-${stepIndex}-${subIndex}`} className="flex gap-2">
                          <input
                            value={sub}
                            onChange={(event) =>
                              updateSubStep(stepIndex, subIndex, event.target.value)
                            }
                            className="h-9 min-w-0 flex-1 rounded-xl border border-slate-200 bg-white px-3 text-sm outline-none focus:border-sky-300"
                            placeholder="子步骤说明（可写工具/动作）"
                          />
                          <button
                            type="button"
                            onClick={() => removeSubStep(stepIndex, subIndex)}
                            className="shrink-0 text-xs text-slate-400 hover:text-rose-500"
                          >
                            移除
                          </button>
                        </div>
                      ))}
                      <button
                        type="button"
                        onClick={() => addSubStep(stepIndex)}
                        className="text-[12px] text-sky-600 hover:text-sky-500"
                      >
                        + 子步骤
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* 右：试召回 */}
        <section className="flex min-h-0 flex-col rounded-3xl border border-slate-200 bg-white/90 p-4 shadow-[var(--shadow-sm)]">
          <h2 className="mb-1 text-base font-semibold text-slate-900">试召回</h2>
          <p className="mb-3 text-[12px] text-slate-400">
            用真实用户问题验证 SOP 是否进入 HIGH/COMMON 模式
          </p>
          <textarea
            value={recallQuery}
            onChange={(event) => setRecallQuery(event.target.value)}
            rows={4}
            className="mb-3 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none focus:border-sky-300 focus:bg-white"
            placeholder="例如：帮我做一份销售综合分析报告"
          />
          <button
            type="button"
            disabled={recallLoading}
            onClick={() => void handleRecallTest()}
            className="mb-4 inline-flex h-9 items-center justify-center gap-1.5 rounded-2xl bg-slate-900 px-4 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {recallLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
            执行召回
          </button>

          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto">
            {recallResult ? (
              <>
                <div className="rounded-2xl border border-slate-100 bg-slate-50 px-3 py-2">
                  <div className="text-[11px] uppercase tracking-wide text-slate-400">
                    mode
                  </div>
                  <div className="mt-0.5 text-sm font-semibold text-slate-800">
                    {recallResult.sopMode || "-"}
                  </div>
                </div>
                {recallResult.hits.length > 0 ? (
                  <div className="space-y-1.5">
                    <div className="text-xs font-medium text-slate-500">Top hits</div>
                    {recallResult.hits.map((hit) => (
                      <div
                        key={`${hit.sopId}-${hit.sopName}`}
                        className="rounded-xl border border-slate-100 px-2.5 py-2 text-[12px]"
                      >
                        <div className="truncate font-medium text-slate-700">
                          {hit.sopName || hit.sopId}
                        </div>
                        <div className="text-slate-400">
                          score: {hit.score ?? "-"}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : null}
                <div>
                  <div className="mb-1 text-xs font-medium text-slate-500">
                    注入文本预览
                  </div>
                  <pre className="max-h-[320px] overflow-auto whitespace-pre-wrap rounded-2xl border border-slate-100 bg-slate-50 p-3 text-[12px] leading-5 text-slate-600">
                    {recallResult.choosedSopString || "(空)"}
                  </pre>
                </div>
              </>
            ) : (
              <div className="rounded-2xl border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-400">
                保存 online SOP 后在此验证召回
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
};

export default WorkspaceSop;
