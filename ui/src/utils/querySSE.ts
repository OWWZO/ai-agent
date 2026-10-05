import { fetchEventSource, EventSourceMessage } from '@microsoft/fetch-event-source';

import { getDeviceId } from '@/services/agentConversation';
import { refreshAccessToken } from '@/services/authTransport';
import { emitAuthSessionExpired } from '@/services/authSessionExpired';
import { clearAuthSession, getAccessToken } from '@/stores/auth';
import { resolveServiceBaseUrl } from './origin';

/**
 * Reactor Agent SSE 客户端适配器。
 *
 * <p>该模块只负责建立 POST SSE 连接、解析顶层 JSON 和转发 open/message/error/close
 * 生命周期；事件业务语义由调用方和 {@code sseParsers} 负责，避免网络层绑定具体任务模型。</p>
 */
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
  method?: 'GET' | 'POST';
  signal?: AbortSignal;
  /** 隐藏标签页时是否继续保持连接；主 Agent GET 观察流会显式关闭。 */
  openWhenHidden?: boolean;
  /** GET 续接时写入标准 Last-Event-ID。 */
  lastEventId?: string;
  /** 是否允许 fetch-event-source 在连接失败后自动重发请求。 */
  retryOnError?: boolean;
  /** 收到带 id 的业务事件时通知调用方保存游标。 */
  handleEventId?: (eventId: string) => void;
  /** HTTP SSE 响应成功建立后通知调用方。 */
  handleOpen?: () => void;
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
  const {
    body = null,
    method = 'POST',
    signal,
    openWhenHidden = true,
    lastEventId,
    // POST 发消息默认不重发原始请求，避免断线后重复创建任务；续接走 GET。
    retryOnError = false,
    handleEventId,
    handleOpen,
    parser,
    handleMessage,
    handleError,
    handleClose,
  } = config;

  const headers = { ...SSE_HEADERS };
  const accessToken = getAccessToken();
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId;
  }

  const fetchWithAuthRefresh: typeof fetch = async (input, init) => {
    const response = await fetch(input, init);
    if (response.status !== 401) {
      return response;
    }

    try {
      const accessToken = await refreshAccessToken();
      headers.Authorization = `Bearer ${accessToken}`;
      const retryHeaders = new Headers(init?.headers);
      retryHeaders.set('Authorization', `Bearer ${accessToken}`);
      return await fetch(input, {
        ...init,
        headers: retryHeaders,
      });
    } catch {
      // 让 onopen 统一把最终的非 2xx 响应交给业务层；这里不重复提交更多请求。
      clearAuthSession();
      emitAuthSessionExpired();
      return response;
    }
  };

  void fetchEventSource(url, {
    method,
    credentials: 'include',
    headers,
    signal,
    body: method === 'GET' ? undefined : JSON.stringify(body),
    openWhenHidden,
    fetch: fetchWithAuthRefresh,
    onopen(response: Response) {
      if (!response.ok) {
        throw new Error(`SSE 请求失败，状态码: ${response.status}`);
      }
      const contentType = response.headers.get('content-type') || '';
      if (!contentType.startsWith('text/event-stream')) {
        throw new Error(`SSE 响应类型错误: ${contentType || 'unknown'}`);
      }
      handleOpen?.();
      return Promise.resolve();
    },
    onmessage(event: EventSourceMessage) {
      if (event.id) {
        handleEventId?.(event.id);
      }
      if (event.data) {
        try {
          const parsedData = JSON.parse(event.data);
          handleMessage(parser ? parser(parsedData) : (parsedData as TMessage));
        } catch (error) {
          console.error('Error parsing SSE message:', error);
          // 单条协议帧解析失败不等于连接断开；保留连接并等待后续帧，
          // 避免把后端新增字段或脏帧误报成“任务仍在后台执行”。
        }
      }
    },
    onerror(error: Error) {
      console.error('SSE error:', error);
      // POST 流默认不重发原始请求，避免断线后重复 dispatch；需要恢复时由调用方
      // 使用已保存的 run 状态建立 follow 连接。
      if (!retryOnError) {
        throw error;
      }
      handleError(error);
    },
    onclose() {
      console.log('SSE connection closed');
      handleClose();
    }
  }).catch((error: unknown) => {
    // 主动 abort 是组件生命周期清理，不应冒泡成对话失败。
    if (
      (error instanceof DOMException && error.name === 'AbortError') ||
      (error instanceof Error && error.name === 'AbortError')
    ) {
      return;
    }
    if (error instanceof Error) {
      handleError(error);
    }
  });
};
