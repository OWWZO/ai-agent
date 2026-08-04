import { useCallback, useRef, useState } from "react";

import { mapMragError, streamMragQuery } from "@/services/mragWorkspace";
import { showMessage } from "@/utils";

export function useMragQuery(
  toolBaseUrl: string,
  selectedKnowledgeBaseId: string,
  sessionId: string,
  onCompleted?: () => void
) {
  const queryAbortRef = useRef<AbortController | null>(null);
  const [question, setQuestion] = useState("");
  const [querying, setQuerying] = useState(false);
  const [queryAnswer, setQueryAnswer] = useState("");
  const [queryError, setQueryError] = useState("");
  const [queryRawChunks, setQueryRawChunks] = useState<unknown[]>([]);

  const handleSubmitQuery = useCallback(async (overrideSessionId?: string) => {
    // 每次提交先校验知识库和问题，并取消上一条请求，保证只保留一个活动流。
    const currentQuestion = question.trim();
    if (!selectedKnowledgeBaseId) {
      showMessage()?.error("请先选择知识库");
      return;
    }
    if (!currentQuestion) {
      showMessage()?.error("请输入问题");
      return;
    }

    queryAbortRef.current?.abort();
    const abortController = new AbortController();
    queryAbortRef.current = abortController;

    setQuerying(true);
    setQueryError("");
    setQueryAnswer("");
    setQueryRawChunks([]);

    try {
      // answer 累积文本，raw chunks 只保留最近 50 条用于诊断和调试展示。
      await streamMragQuery({
        toolBaseUrl,
        kbId: selectedKnowledgeBaseId,
        sessionId: overrideSessionId || sessionId,
        question: currentQuestion,
        signal: abortController.signal,
        onChunk(chunk) {
          if (chunk.content) {
            setQueryAnswer((previous) => previous + chunk.content);
          }
          setQueryRawChunks((previous) => [...previous, chunk.raw].slice(-50));
        },
      });
    } catch (error) {
      if (!abortController.signal.aborted) {
        setQueryError(mapMragError(error));
      }
    } finally {
      // 只有当前 controller 才能清空 ref；旧请求的 finally 不得覆盖新请求状态。
      if (queryAbortRef.current === abortController) {
        queryAbortRef.current = null;
      }
      setQuerying(false);
      if (!abortController.signal.aborted) {
        onCompleted?.();
      }
    }
  }, [onCompleted, question, selectedKnowledgeBaseId, sessionId, toolBaseUrl]);

  const handleStopQuery = useCallback(() => {
    // 主动停止走同一 AbortController，错误回调会识别 aborted 并跳过失败提示。
    if (!queryAbortRef.current) {
      return;
    }

    queryAbortRef.current.abort();
    queryAbortRef.current = null;
    setQuerying(false);
    showMessage()?.info("已停止当前检索");
  }, []);

  const handleClearQueryResult = useCallback(() => {
    setQueryAnswer("");
    setQueryError("");
    setQueryRawChunks([]);
  }, []);

  return {
    question,
    querying,
    queryAnswer,
    queryError,
    queryRawChunks,
    setQuestion,
    handleSubmitQuery,
    handleStopQuery,
    handleClearQueryResult,
  };
}
