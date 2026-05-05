import { useCallback, useRef, useState } from "react";

import { mapMragError, streamMragQuery } from "@/services/mragWorkspace";
import { showMessage } from "@/utils";

export function useMragQuery(
  toolBaseUrl: string,
  selectedKnowledgeBaseId: string
) {
  const queryAbortRef = useRef<AbortController | null>(null);
  const [question, setQuestion] = useState("");
  const [querying, setQuerying] = useState(false);
  const [queryAnswer, setQueryAnswer] = useState("");
  const [queryError, setQueryError] = useState("");
  const [queryRawChunks, setQueryRawChunks] = useState<unknown[]>([]);

  const handleSubmitQuery = useCallback(async () => {
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
      await streamMragQuery({
        toolBaseUrl,
        kbId: selectedKnowledgeBaseId,
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
      if (queryAbortRef.current === abortController) {
        queryAbortRef.current = null;
      }
      setQuerying(false);
    }
  }, [question, selectedKnowledgeBaseId, toolBaseUrl]);

  const handleStopQuery = useCallback(() => {
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
