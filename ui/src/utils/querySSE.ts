import { fetchEventSource, EventSourceMessage } from '@microsoft/fetch-event-source';

import { getDeviceId } from '@/services/agentConversation';
import { resolveServiceBaseUrl } from './origin';

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);
/**
 * 历史会话接口已下线，主聊天统一回到当前仍然保留的 Reactor SSE 入口。
 */
const DEFAULT_SSE_URL = `${customHost}/web/api/v1/gpt/queryAgentStreamIncr`;

const SSE_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
  'Cache-Control': 'no-cache',
  'Connection': 'keep-alive',
  'Accept': 'text/event-stream',
  'X-Device-Id': getDeviceId(),
};

interface SSEConfig<TMessage = unknown> {
  body: unknown;
  parser?: (raw: unknown) => TMessage;
  handleMessage: (data: TMessage) => void;
  handleError: (error: Error) => void;
  handleClose: () => void;
}

/**
 * 创建服务器发送事件（SSE）连接
 * @param config SSE 配置
 * @param url 可选的自定义 URL
 */
export default <TMessage = unknown>(
  config: SSEConfig<TMessage>,
  url: string = DEFAULT_SSE_URL
): void => {
  const { body = null, parser, handleMessage, handleError, handleClose } = config;

  fetchEventSource(url, {
    method: 'POST',
    credentials: 'include',
    headers: SSE_HEADERS,
    body: JSON.stringify(body),
    openWhenHidden: true,
    onmessage(event: EventSourceMessage) {
      if (event.data) {
        try {
          const parsedData = JSON.parse(event.data);
          handleMessage(parser ? parser(parsedData) : (parsedData as TMessage));
        } catch (error) {
          console.error('Error parsing SSE message:', error);
          handleError(new Error('Failed to parse SSE message'));
        }
      }
    },
    onerror(error: Error) {
      console.error('SSE error:', error);
      handleError(error);
    },
    onclose() {
      console.log('SSE connection closed');
      handleClose();
    }
  });
};
