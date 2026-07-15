import { useCallback, useEffect, useState } from "react";

import type { MRagSessionDetail, MRagSessionSummary } from "./types";
import {
  createMragSession,
  getMragSessionDetail,
  listMragSessions,
} from "@/services/mragWorkspace";

export function useMragHistory(toolBaseUrl: string, selectedKnowledgeBaseId: string) {
  const [sessions, setSessions] = useState<MRagSessionSummary[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [sessionsError, setSessionsError] = useState("");
  const [activeSessionId, setActiveSessionId] = useState("");
  const [sessionDetail, setSessionDetail] = useState<MRagSessionDetail | null>(null);
  const [sessionDetailLoading, setSessionDetailLoading] = useState(false);

  const refreshSessions = useCallback(async () => {
    setSessionsLoading(true);
    setSessionsError("");
    try {
      const nextSessions = await listMragSessions(toolBaseUrl);
      setSessions(nextSessions);
      return nextSessions;
    } catch (error) {
      setSessionsError(error instanceof Error ? error.message : "历史加载失败");
      return [];
    } finally {
      setSessionsLoading(false);
    }
  }, [toolBaseUrl]);

  const loadSessionDetail = useCallback(async (sessionId: string) => {
    if (!sessionId) {
      setSessionDetail(null);
      return null;
    }
    setSessionDetailLoading(true);
    try {
      const detail = await getMragSessionDetail(toolBaseUrl, sessionId);
      setSessionDetail(detail);
      setActiveSessionId(sessionId);
      return detail;
    } finally {
      setSessionDetailLoading(false);
    }
  }, [toolBaseUrl]);

  const ensureActiveSession = useCallback(async () => {
    if (activeSessionId) {
      return activeSessionId;
    }

    const created = await createMragSession(toolBaseUrl, {
      kbId: selectedKnowledgeBaseId,
    });
    setActiveSessionId(created.sessionId);
    setSessionDetail({
      session: created,
      turns: [],
    });
    setSessions((previous) => [created, ...previous.filter((item) => item.sessionId !== created.sessionId)]);
    return created.sessionId;
  }, [activeSessionId, selectedKnowledgeBaseId, toolBaseUrl]);

  const createSession = useCallback(async () => {
    const created = await createMragSession(toolBaseUrl, {
      kbId: selectedKnowledgeBaseId,
    });
    setActiveSessionId(created.sessionId);
    setSessionDetail({
      session: created,
      turns: [],
    });
    setSessions((previous) => [created, ...previous.filter((item) => item.sessionId !== created.sessionId)]);
    return created;
  }, [selectedKnowledgeBaseId, toolBaseUrl]);

  useEffect(() => {
    void refreshSessions().then((nextSessions) => {
      if (!activeSessionId && nextSessions[0]?.sessionId) {
        void loadSessionDetail(nextSessions[0].sessionId);
      }
    });
  }, [activeSessionId, loadSessionDetail, refreshSessions]);

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      return;
    }
    if (sessionDetail?.session.coverKbId && sessionDetail.session.coverKbId !== selectedKnowledgeBaseId) {
      setActiveSessionId("");
      setSessionDetail(null);
    }
  }, [selectedKnowledgeBaseId, sessionDetail]);

  return {
    sessions,
    sessionsLoading,
    sessionsError,
    activeSessionId,
    sessionDetail,
    sessionDetailLoading,
    setActiveSessionId,
    refreshSessions,
    loadSessionDetail,
    ensureActiveSession,
    createSession,
  };
}
