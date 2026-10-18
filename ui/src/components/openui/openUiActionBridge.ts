import { BuiltinActionType, type ActionEvent } from '@openuidev/react-lang';

import { dispatchGenUiAction } from '@/components/genui/genUiActionBus';

/**
 * 把 openui 的 action 事件接到现有的对话发送链路上。
 *
 * 复用 `genUiActionBus`：ChatView 里的 `GenUiActionBridge` 已经注册了 sendMessage / openUrl
 * 适配器（含 busy 拦截与 URL 白名单），这里只做事件格式转换，不重复实现发送逻辑。
 * `@Set` / `@Reset` 由 Renderer 内部处理，不会到这里。
 */
export function dispatchOpenUiAction(event: ActionEvent): void {
  if (event.type === BuiltinActionType.ContinueConversation) {
    const content = composeContinueMessage(event);
    if (!content) return;
    void dispatchGenUiAction({ type: 'send_message', payload: { content } });
    return;
  }

  if (event.type === BuiltinActionType.OpenUrl) {
    const url = typeof event.params?.url === 'string' ? event.params.url : '';
    if (!url) return;
    void dispatchGenUiAction({ type: 'open_url', payload: { url, external: true } });
  }
}

function composeContinueMessage(event: ActionEvent): string {
  const base = (event.humanFriendlyMessage || '').trim();
  const form = formatFormState(event.formState);
  if (!base) return '';
  return form ? `${base}\n\n${form}` : base;
}

/**
 * 表单值随消息一起回灌。现有消息结构没有隐藏上下文字段，所以拼在正文尾部：
 * 数量少、可读，模型能直接用它继续推理。
 */
function formatFormState(formState?: Record<string, unknown>): string {
  if (!formState) return '';
  const rows = Object.entries(formState)
    // $ 前缀是响应式绑定，不是用户填的字段
    .filter(([key, value]) => !key.startsWith('$') && value != null && String(value).trim() !== '')
    .map(([key, value]) => `- ${key}: ${formatValue(value)}`);
  return rows.length ? `表单值：\n${rows.join('\n')}` : '';
}

function formatValue(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map(formatValue).join('、');
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}
