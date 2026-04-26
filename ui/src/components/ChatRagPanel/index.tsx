import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  DatabaseZap,
  LoaderCircle,
  Search,
  Square,
  Trash2,
  ChevronDown,
  Sparkles,
  FileText,
} from "lucide-react";
import classNames from "classnames";

import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import {
  listKnowledgeBases,
  streamMragQuery,
} from "@/services/mragWorkspace";
import type { KnowledgeBase, MRagChunkEnvelope } from "@/pages/WorkspaceMRag/types";
import { loadMRagWorkspaceStoredState } from "@/pages/WorkspaceMRag/utils";

const TOOL_BASE_URL = import.meta.env.VITE_Mrag_TOOL_URL || "";

export default function ChatRagPanel() {
  const stored = useMemo(() => loadMRagWorkspaceStoredState(), []);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [knowledgeBasesLoading, setKnowledgeBasesLoading] = useState(false);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState(
    stored.selectedKnowledgeBaseId
  );
  const [question, setQuestion] = useState("");
  const [querying, setQuerying] = useState(false);
  const [queryAnswer, setQueryAnswer] = useState("");
  const [queryError, setQueryError] = useState("");
  const [queryRawChunks, setQueryRawChunks] = useState<unknown[]>([]);
  const [isKbDropdownOpen, setIsKbDropdownOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((k) => k.id === selectedKnowledgeBaseId) || null,
    [knowledgeBases, selectedKnowledgeBaseId]
  );

  // Load knowledge bases on mount
  useEffect(() => {
    const toolBaseUrl = TOOL_BASE_URL || stored.toolBaseUrl;
    if (!toolBaseUrl) return;
    setKnowledgeBasesLoading(true);
    listKnowledgeBases(toolBaseUrl)
      .then((list) => {
        setKnowledgeBases(list);
        if (list.length && !selectedKnowledgeBaseId) {
          setSelectedKnowledgeBaseId(list[0].id);
        }
      })
      .catch(() => {
        // silent fail
      })
      .finally(() => setKnowledgeBasesLoading(false));
  }, [stored.toolBaseUrl, selectedKnowledgeBaseId]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsKbDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSubmitQuery = useCallback(() => {
    if (!selectedKnowledgeBaseId || !question.trim() || querying) return;

    const toolBaseUrl = TOOL_BASE_URL || stored.toolBaseUrl;
    if (!toolBaseUrl) {
      setQueryError("未配置 MRAG Tool URL");
      return;
    }

    setQuerying(true);
    setQueryAnswer("");
    setQueryError("");
    setQueryRawChunks([]);

    const abort = new AbortController();
    abortRef.current = abort;

    const chunks: unknown[] = [];
    let accumulated = "";

    streamMragQuery({
      toolBaseUrl,
      kbId: selectedKnowledgeBaseId,
      question: question.trim(),
      signal: abort.signal,
      onChunk: (chunk: MRagChunkEnvelope) => {
        chunks.push(chunk.raw);
        if (chunk.content) {
          accumulated += chunk.content;
          setQueryAnswer(accumulated);
        }
        setQueryRawChunks([...chunks]);
      },
    })
      .catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : String(err);
        if (msg.includes("aborted")) {
          // user stopped, not an error
          return;
        }
        setQueryError(msg);
      })
      .finally(() => {
        setQuerying(false);
        abortRef.current = null;
      });
  }, [selectedKnowledgeBaseId, question, querying, stored.toolBaseUrl]);

  const handleStopQuery = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setQuerying(false);
  }, []);

  const handleClear = useCallback(() => {
    setQueryAnswer("");
    setQueryError("");
    setQueryRawChunks([]);
    setQuestion("");
  }, []);

  return (
    <div className="flex h-full w-full flex-col overflow-hidden bg-[var(--page-gradient)]">
      {/* Header */}
      <div className="flex shrink-0 items-center justify-between border-b border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-5 py-3 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--primary)]/10 text-[var(--primary)]">
            <DatabaseZap className="h-4.5 w-4.5" />
          </div>
          <div>
            <h2 className="text-[15px] font-semibold tracking-tight text-[var(--chat-text)]">
              RAG 知识检索
            </h2>
            <p className="text-[12px] text-[var(--chat-text-muted)]">
              基于多模态知识库的语义检索与回答
            </p>
          </div>
        </div>

        {/* Knowledge Base Selector */}
        <div className="relative" ref={dropdownRef}>
          <button
            type="button"
            onClick={() => setIsKbDropdownOpen((v) => !v)}
            disabled={knowledgeBasesLoading}
            className={classNames(
              "flex items-center gap-2 rounded-xl border px-3.5 py-2 text-[13px] font-medium transition-colors",
              isKbDropdownOpen
                ? "border-[var(--primary)]/30 bg-[var(--primary)]/5 text-[var(--primary)]"
                : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-soft)] hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
            )}
          >
            {knowledgeBasesLoading ? (
              <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <FileText className="h-3.5 w-3.5" />
            )}
            <span className="max-w-[160px] truncate">
              {selectedKnowledgeBase?.name || "选择知识库"}
            </span>
            <ChevronDown
              className={classNames(
                "h-3.5 w-3.5 transition-transform",
                isKbDropdownOpen && "rotate-180"
              )}
            />
          </button>

          <AnimatePresence>
            {isKbDropdownOpen && (
              <motion.div
                initial={{ opacity: 0, y: -4, scale: 0.97 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -4, scale: 0.97 }}
                transition={{ duration: 0.15 }}
                className="absolute right-0 top-full z-50 mt-1.5 min-w-[220px] overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[0_16px_48px_-12px_rgba(15,23,42,0.25)]"
              >
                {knowledgeBases.length === 0 ? (
                  <div className="px-4 py-3 text-[13px] text-[var(--chat-text-muted)]">
                    暂无知识库
                  </div>
                ) : (
                  <div className="max-h-[280px] overflow-y-auto py-1">
                    {knowledgeBases.map((kb) => (
                      <button
                        key={kb.id}
                        type="button"
                        onClick={() => {
                          setSelectedKnowledgeBaseId(kb.id);
                          setIsKbDropdownOpen(false);
                        }}
                        className={classNames(
                          "flex w-full items-center gap-2 px-3.5 py-2.5 text-left text-[13px] transition-colors",
                          kb.id === selectedKnowledgeBaseId
                            ? "bg-[var(--primary)]/5 text-[var(--primary)]"
                            : "text-[var(--chat-text)] hover:bg-[var(--chat-surface-soft)]"
                        )}
                      >
                        <DatabaseZap className="h-3.5 w-3.5 shrink-0" />
                        <span className="truncate">{kb.name}</span>
                      </button>
                    ))}
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-auto p-5">
        {/* Query Input Area */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
          className="mx-auto w-full max-w-[860px]"
        >
          <div className="rounded-[24px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-5 shadow-[var(--shadow-sm)]">
            <div className="mb-3 flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-[var(--primary)]" />
              <span className="text-[13px] font-medium text-[var(--chat-text-soft)]">
                输入问题，检索知识库
              </span>
              {selectedKnowledgeBase && (
                <span className="ml-auto rounded-full bg-[var(--primary)]/5 px-2.5 py-0.5 text-[11px] font-medium text-[var(--primary)]">
                  {selectedKnowledgeBase.name}
                </span>
              )}
            </div>

            <textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  handleSubmitQuery();
                }
              }}
              rows={3}
              placeholder="例如：这份资料里对接流程的关键步骤是什么？"
              className="w-full resize-none rounded-[16px] border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-3 text-[14px] leading-6 text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
            />

            <div className="mt-3 flex items-center justify-between">
              <div className="flex items-center gap-2 text-[12px] text-[var(--chat-text-muted)]">
                <span>
                  {queryRawChunks.length > 0
                    ? `已接收 ${queryRawChunks.length} 个 chunk`
                    : "按 Enter 直接发送"}
                </span>
              </div>
              <div className="flex gap-2">
                {(queryAnswer || queryError || queryRawChunks.length > 0) && (
                  <button
                    type="button"
                    onClick={handleClear}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--chat-border)] px-3.5 py-2 text-[13px] font-medium text-[var(--chat-text-soft)] transition-colors hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    清空
                  </button>
                )}
                {querying ? (
                  <button
                    type="button"
                    onClick={handleStopQuery}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-rose-500 px-4 py-2 text-[13px] font-medium text-white transition-colors hover:bg-rose-600"
                  >
                    <Square className="h-3.5 w-3.5" />
                    停止
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleSubmitQuery}
                    disabled={!selectedKnowledgeBaseId || !question.trim()}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--primary)] px-4 py-2 text-[13px] font-medium text-[var(--primary-foreground)] transition-colors hover:opacity-90 disabled:opacity-40"
                  >
                    <Search className="h-3.5 w-3.5" />
                    开始检索
                  </button>
                )}
              </div>
            </div>
          </div>
        </motion.div>

        {/* Result Area */}
        <AnimatePresence mode="wait">
          {queryError ? (
            <motion.div
              key="error"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="mx-auto w-full max-w-[860px] rounded-[20px] border border-rose-200 bg-rose-50 px-5 py-4 text-[14px] leading-6 text-rose-600"
            >
              {queryError}
            </motion.div>
          ) : queryAnswer || querying ? (
            <motion.div
              key="result"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.3 }}
              className="mx-auto w-full max-w-[860px]"
            >
              <div className="rounded-[24px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-5 shadow-[var(--shadow-sm)]">
                <div className="mb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-[var(--primary)]" />
                    <span className="text-[13px] font-medium text-[var(--chat-text)]">
                      检索结果
                    </span>
                  </div>
                  {querying && (
                    <span className="inline-flex items-center gap-1.5 text-[12px] text-[var(--chat-text-muted)]">
                      <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                      接收中...
                    </span>
                  )}
                </div>

                {queryAnswer ? (
                  <MarkdownRenderer
                    markDownContent={queryAnswer}
                    isStreaming={querying}
                    className="min-h-[200px] text-[14px] leading-7"
                  />
                ) : (
                  <div className="flex min-h-[200px] items-center justify-center text-[14px] text-[var(--chat-text-muted)]">
                    <LoaderCircle className="mr-2 h-5 w-5 animate-spin" />
                    正在检索知识库，请稍候...
                  </div>
                )}
              </div>

              {/* Raw chunks debug */}
              {queryRawChunks.length > 0 && (
                <details className="mt-3 overflow-hidden rounded-[20px] border border-[var(--chat-border)] bg-[var(--chat-surface-soft)]">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-[13px] font-medium text-[var(--chat-text-soft)]">
                    <span className="inline-flex items-center gap-2">
                      <DatabaseZap className="h-3.5 w-3.5" />
                      <span>调试原始 SSE Chunk</span>
                    </span>
                    <span className="text-[11px] text-[var(--chat-text-muted)]">
                      共 {queryRawChunks.length} 条
                    </span>
                  </summary>
                  <pre className="max-h-[240px] overflow-auto border-t border-[var(--chat-border)] px-4 py-3 whitespace-pre-wrap font-mono text-[11px] leading-5 text-[var(--chat-text-muted)]">
                    {queryRawChunks
                      .map((c) =>
                        typeof c === "string" ? c : JSON.stringify(c, null, 2)
                      )
                      .join("\n---\n")}
                  </pre>
                </details>
              )}
            </motion.div>
          ) : null}
        </AnimatePresence>

        {/* Empty state hint */}
        {!queryAnswer && !queryError && !querying && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.3 }}
            className="mx-auto mt-8 flex max-w-[400px] flex-col items-center text-center"
          >
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--chat-surface-soft)]">
              <DatabaseZap className="h-7 w-7 text-[var(--chat-text-muted)]" />
            </div>
            <h3 className="mb-1 text-[15px] font-medium text-[var(--chat-text-soft)]">
              知识库检索
            </h3>
            <p className="text-[13px] leading-6 text-[var(--chat-text-muted)]">
              选择左侧知识库，输入问题即可开始语义检索。系统会自动在文档、图片和页面中查找最相关的内容并生成回答。
            </p>
          </motion.div>
        )}
      </div>
    </div>
  );
}
