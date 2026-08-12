import { useEffect } from "react";
import { registerGenUiActionAdapters } from "./genUiActionBus";
import {
  applyLocalUiPatches,
  genUiLocalScopeKey,
} from "./genUiLocalTreeStore";

type Props = {
  /**
   * Explicit send_message only. In-UI controls default to local patch/form.
   */
  sendMessage?: (message: string) => void;
  /** True while an agent run is in progress — chat inject is ignored. */
  busy?: boolean;
};

/**
 * Mount near ChatView root so Button/Form actions stay inside GenUI by default.
 */
export function GenUiActionBridge({ sendMessage, busy }: Props) {
  useEffect(() => {
    registerGenUiActionAdapters({
      sendMessage(payload) {
        const content = (payload.content || "").trim();
        if (!content || busy || !sendMessage) return;
        sendMessage(content);
      },
      patchUi(payload, ctx) {
        const key = genUiLocalScopeKey(ctx.sessionId, ctx.messageId);
        applyLocalUiPatches(key, payload.patches || []);
      },
      submitForm(payload, ctx) {
        // 表单值已在 genUiFormsStore 内；提交默认留在 UI，不发聊天。
        // 若模型同时给了 patch_ui 语义，可在 action 上直接用 type: patch_ui。
        // 需要 Agent 接手时使用 type: send_message。
        void payload;
        void ctx;
      },
      openUrl(payload) {
        if (typeof window === "undefined") return;
        const target = payload.external === false ? "_self" : "_blank";
        window.open(payload.url, target, "noopener,noreferrer");
      },
      navigate(payload) {
        if (typeof window === "undefined") return;
        window.location.href = payload.route;
      },
    });
  }, [sendMessage, busy]);

  return null;
}

export default GenUiActionBridge;
