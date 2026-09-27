import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";

import {
  featuredConversationApi,
  type FeaturedConversationDetail,
} from "@/services/featuredConversation";
import type {
  ConversationHistoryDetail,
  ConversationRunReplay,
} from "@/services/agentConversation";

import { FeaturedConversationDetailView } from "./view";

interface FeaturedConversationDetailPageProps {
  embedded?: boolean;
  featuredId?: string;
  onBack?: () => void;
}

export default function FeaturedConversationDetailPage(
  props: FeaturedConversationDetailPageProps
) {
  const params = useParams();
  const featuredId = props.featuredId || params.featuredId || "";
  const featuredIdRef = useRef(featuredId);
  featuredIdRef.current = featuredId;
  const [detail, setDetail] = useState<FeaturedConversationDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [replayRuns, setReplayRuns] = useState<Map<string, ConversationRunReplay>>(
    new Map()
  );
  const replayRequestsRef = useRef<
    Map<string, Promise<ConversationRunReplay | null>>
  >(new Map());

  const requestRunReplay = useCallback(
    (requestId: string) => {
      const key = `${featuredId}::${requestId}`;
      const inFlight = replayRequestsRef.current.get(key);
      if (inFlight) {
        return inFlight;
      }

      const request = featuredConversationApi
        .getFeaturedRunReplay(featuredId, requestId)
        .then((replay) => {
          if (featuredIdRef.current !== featuredId) {
            return replay;
          }
          setReplayRuns((previous) => {
            const next = new Map(previous);
            next.set(requestId, replay);
            return next;
          });
          return replay;
        })
        .catch((error) => {
          console.error("加载精品对话 run 回放失败", error);
          return null;
        })
        .finally(() => {
          replayRequestsRef.current.delete(key);
        });

      replayRequestsRef.current.set(key, request);
      return request;
    },
    [featuredId]
  );

  useEffect(() => {
    // featuredId 同时支持路由参数和嵌入 props；没有 ID 时清空旧详情。
    if (!featuredId) {
      setDetail(null);
      setReplayRuns(new Map());
      return;
    }

    // 组件卸载或 ID 切换后忽略旧请求，避免详情闪回上一条会话。
    let disposed = false;
    setLoading(true);
    setReplayRuns(new Map());

    featuredConversationApi
      .detail(featuredId)
      .then((data) => {
        if (disposed) {
          return;
        }
        setDetail(data || null);
      })
      .catch((error) => {
        console.error("加载精品对话详情失败", error);
        if (disposed) {
          return;
        }
        setDetail(null);
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [featuredId]);

  const renderedDetail = useMemo(() => {
    if (!detail?.historyDetail) {
      return detail;
    }
    const historyDetail: ConversationHistoryDetail = {
      ...detail.historyDetail,
      runs: detail.historyDetail.runs.map((run) => {
        const replay = replayRuns.get(run.requestId);
        if (replay) {
          return replay;
        }
        return {
          requestId: run.requestId,
          status: run.status,
          queryText:
            "queryPreview" in run
              ? run.queryPreview
              : (run as ConversationRunReplay).queryText,
          finalSummaryText:
            "finalSummaryPreview" in run
              ? run.finalSummaryPreview
              : (run as ConversationRunReplay).finalSummaryText,
          startedAt: run.startedAt,
          finishedAt: run.finishedAt,
          durationMs: run.durationMs,
          replayFrames: [],
        };
      }),
    };
    return {
      ...detail,
      historyDetail,
    };
  }, [detail, replayRuns]);

  return (
    <FeaturedConversationDetailView
      embedded={props.embedded}
      loading={loading}
      detail={renderedDetail}
      onBack={props.onBack}
      onRequestRunReplay={async (requestId) => {
        await requestRunReplay(requestId);
      }}
    />
  );
}
