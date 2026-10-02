import api from "./index";
import { resolveServiceBaseUrl } from "@/utils/origin";
import { submitAcceptedCommand } from "./agentRun";

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);

export const DESKTOP_CONTROL_RESUME_URL = `${customHost}/api/agent/desktop-control/resume`;

export type DesktopControlCompletePayload = {
  controlId: string;
};

export type DesktopControlCompleteResult = {
  controlId?: string;
  accepted?: boolean;
  idempotent?: boolean;
  resumeRequestId?: string;
  sessionId?: string;
  status?: string;
  message?: string;
};

export const DESKTOP_CONTROL_RESUME_EVENT = "reactor-desktop-control-resume";
export const DESKTOP_CONTROL_PREVIEW_EVENT = "reactor-desktop-control-preview";

export type DesktopControlResumeEventDetail = {
  resumeRequestId: string;
  sessionId?: string;
  controlId?: string;
};

export const desktopControlApi = {
  complete: (payload: DesktopControlCompletePayload) =>
    api.post<DesktopControlCompleteResult>(
      "/api/agent/desktop-control/complete",
      payload
    ) as unknown as Promise<DesktopControlCompleteResult>,

  pending: (sessionId: string) =>
    api.get<Record<string, unknown>[]>(
      "/api/agent/desktop-control/pending",
      { sessionId }
    ) as unknown as Promise<Record<string, unknown>[]>,
};

export function submitDesktopControlResume(resumeRequestId: string) {
  return submitAcceptedCommand(DESKTOP_CONTROL_RESUME_URL, { resumeRequestId });
}

export function dispatchDesktopControlResume(detail: DesktopControlResumeEventDetail) {
  if (typeof window === "undefined" || !detail?.resumeRequestId) {
    return;
  }
  window.dispatchEvent(new CustomEvent(DESKTOP_CONTROL_RESUME_EVENT, { detail }));
}

export function dispatchDesktopControlPreview() {
  if (typeof window === "undefined") {
    return;
  }
  window.dispatchEvent(new Event(DESKTOP_CONTROL_PREVIEW_EVENT));
}
