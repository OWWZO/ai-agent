import { useCallback } from 'react';
import {
  BuiltinActionType,
  Renderer,
  type ActionEvent,
  type LibraryActionEvent,
} from '@openuidev/react-lang';

import { runLibraryAction } from '@/lib/openui/actions';
import { library } from '@/lib/openui/library';

export type OpenUiActionHandler = (event: ActionEvent) => void;

export interface OpenUiRenderProps {
  /** openui-lang program text, without the marker lines. */
  text: string;
  /** True while the program is still arriving: form controls stay disabled. */
  streaming?: boolean;
  /** Built-in actions the host should carry out (@ToAssistant, @OpenUrl, ...). */
  onAction?: OpenUiActionHandler;
  /** Raw form state, so a caller can persist it for later hydration. */
  onStateUpdate?: (state: Record<string, unknown>) => void;
}

/**
 * Renders an openui-lang program with the shared component library.
 *
 * Library-level custom actions (e.g. @CopyToClipboard) are executed here; anything
 * else — ContinueConversation, OpenUrl — is forwarded to `onAction` so the host
 * decides what a click means in its own chat model.
 */
export function OpenUiRender({ text, streaming, onAction, onStateUpdate }: OpenUiRenderProps) {
  const handleAction = useCallback(
    (event: LibraryActionEvent<typeof library>) => {
      const libraryHandled = runLibraryAction(event as ActionEvent);
      if (libraryHandled) {
        void libraryHandled;
        return;
      }
      onAction?.(event as ActionEvent);
    },
    [onAction]
  );

  return (
    <Renderer
      response={text}
      library={library}
      isStreaming={!!streaming}
      onAction={handleAction}
      onStateUpdate={onStateUpdate}
      // 关掉向 globalThis[Symbol.for("openui.observability")] 发事件：
      // 那是给 OpenUI Inspect 浏览器扩展读的，我们不需要，还省掉每次 settle 的 JSON.stringify。
      publishObservability={false}
    />
  );
}

/** True when the event asks the host to send a new turn to the assistant. */
export function isContinueConversation(event: ActionEvent): boolean {
  return event.type === BuiltinActionType.ContinueConversation;
}

/** True when the event asks the host to open an external link. */
export function isOpenUrl(event: ActionEvent): boolean {
  return event.type === BuiltinActionType.OpenUrl;
}
