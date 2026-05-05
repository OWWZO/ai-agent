import { useEffect, useState } from "react";

import { requestImageGenerationHistory } from "@/services/imageGeneration";
import type { ImageGenerationHistoryBatch } from "./types";

export const HISTORY_PAGE_SIZE = 10;

/**
 * 历史列表单独收口，页面只负责渲染，不再关心分页和 loading 细节。
 */
export function useImageGenerationHistory() {
  const [historyBatches, setHistoryBatches] = useState<ImageGenerationHistoryBatch[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPageNo, setHistoryPageNo] = useState(1);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyLoadingMore, setHistoryLoadingMore] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const loadHistory = async (pageNo = 1, replace = true) => {
    if (replace) {
      setHistoryLoading(true);
    } else {
      setHistoryLoadingMore(true);
    }
    setHistoryError("");

    try {
      const page = await requestImageGenerationHistory({
        pageNo,
        pageSize: HISTORY_PAGE_SIZE,
      });
      setHistoryTotal(page.total || 0);
      setHistoryPageNo(pageNo);
      setHistoryBatches((previous) =>
        replace ? page.list || [] : [...previous, ...(page.list || [])]
      );
    } catch (error) {
      setHistoryError(error instanceof Error ? error.message : "历史查询失败");
    } finally {
      setHistoryLoading(false);
      setHistoryLoadingMore(false);
    }
  };

  useEffect(() => {
    void loadHistory(1, true);
  }, []);

  return {
    historyBatches,
    historyTotal,
    historyPageNo,
    historyLoading,
    historyLoadingMore,
    historyError,
    loadHistory,
  };
}
