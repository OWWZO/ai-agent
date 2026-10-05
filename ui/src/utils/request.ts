import axios, {
  AxiosError,
  AxiosHeaders,
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { showMessage } from './utils';
import { getDeviceId } from '@/services/agentConversation';
import { resolveServiceBaseUrl } from './origin';
import { clearAuthSession, getAccessToken } from '@/stores/auth';
import { refreshAccessToken } from '@/services/authTransport';
import { emitAuthSessionExpired } from '@/services/authSessionExpired';
import { ROUTES } from '@/router/routes';

/**
 * 前端普通 HTTP 请求客户端。
 *
 * <p>拦截器统一注入设备标识、解包后端两种历史成功响应格式，并把认证失败、业务
 * 错误和网络错误转换为页面提示。SSE 请求不经过这里，而由 {@code querySSE} 单独
 * 维护长连接生命周期。</p>
 */
// 创建axios实例
const request: AxiosInstance = axios.create({
  baseURL: resolveServiceBaseUrl(SERVICE_BASE_URL),
  timeout: 10000,
  withCredentials: true,
  // 默认 JSON；上传 FormData 时在拦截器里去掉 Content-Type，避免 415
  headers: { "Content-Type": "application/json" },
});

type AuthRetryConfig = InternalAxiosRequestConfig & { _authRetry?: boolean };

function isAuthEndpoint(url?: string) {
  return /\/api\/auth\//.test(url || '');
}

function currentReturnUrl() {
  if (typeof window === 'undefined') {
    return ROUTES.HOME;
  }
  const { pathname, search, hash } = window.location;
  if (pathname === ROUTES.LOGIN || pathname === ROUTES.REGISTER) {
    return ROUTES.HOME;
  }
  return `${pathname}${search}${hash}` || ROUTES.HOME;
}

function redirectToLogin() {
  if (typeof window === 'undefined') {
    return;
  }
  if (
    window.location.pathname === ROUTES.LOGIN ||
    window.location.pathname === ROUTES.REGISTER
  ) {
    return;
  }
  // 认证失效只切换 SPA 路由，不重新加载 index.html；这样不会丢失浏览器标签页
  // 的运行时上下文，也不会把一次过期 token 伪装成浏览器 F5。
  emitAuthSessionExpired(currentReturnUrl());
}

function responseMessage(data: unknown, fallback: string) {
  if (data && typeof data === 'object') {
    const body = data as { msg?: string; info?: string; message?: string };
    return body.msg || body.info || body.message || fallback;
  }
  return fallback;
}

function retryAfterRefresh(
  config?: AuthRetryConfig,
  reason = '登录状态已失效'
): Promise<unknown> {
  if (!config) {
    clearAuthSession();
    redirectToLogin();
    return Promise.reject(new Error(reason));
  }

  if (isAuthEndpoint(config.url)) {
    return Promise.reject(new Error(reason));
  }

  if (config._authRetry) {
    clearAuthSession();
    redirectToLogin();
    return Promise.reject(new Error('登录状态已失效'));
  }

  return refreshAccessToken().then(
    (accessToken) => {
      config._authRetry = true;
      config.headers = AxiosHeaders.from(config.headers);
      config.headers.set('Authorization', `Bearer ${accessToken}`);
      return request(config);
    },
    (error: unknown) => {
      clearAuthSession();
      redirectToLogin();
      return Promise.reject(error);
    }
  );
}

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 兼容仍然依赖设备标识的上传与流式接口
    config.headers['X-Device-Id'] = getDeviceId();
    const accessToken = getAccessToken();
    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`);
    } else {
      config.headers.delete('Authorization');
    }
    // FormData 必须由浏览器带 multipart boundary；默认 application/json 会导致 415
    if (typeof FormData !== "undefined" && config.data instanceof FormData) {
      if (config.headers && typeof config.headers === "object") {
        // AxiosHeaders / 普通对象都兼容
        const h = config.headers as Record<string, unknown> & {
          delete?: (key: string) => void;
          set?: (key: string, value: string) => void;
        };
        if (typeof h.delete === "function") {
          h.delete("Content-Type");
          h.delete("content-type");
        } else {
          delete h["Content-Type"];
          delete h["content-type"];
        }
      }
      // 上传解析 zip 可能超过默认 10s
      if (config.timeout == null || config.timeout < 60000) {
        config.timeout = 60000;
      }
    }
    return config;
  },
  (error) => {
    console.error('请求错误:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data, status } = response;

    if (status === 200) {
      // 根据后端约定的数据结构处理
      // 兼容两种响应格式: {code:200, msg, data} 和 {code:"0000", info, data}
      if (data.code === 200 || data.code === '0000') {
        return data.data;
      } else if (data.code === 401 || data.code === '0003') {
        return retryAfterRefresh(
          response.config as AuthRetryConfig,
          responseMessage(data, '认证失败')
        );
      } else {
        const errMsg = data.msg || data.info || '请求失败';
        if (!isAuthEndpoint(response.config.url)) {
          showMessage()?.error(errMsg);
        }
        return Promise.reject(new Error(errMsg));
      }
    }

    return response;
  },
  (error: AxiosError) => {
    console.error('响应错误:', error);

    const message = showMessage();
    if (error.response) {
      const { status, data: resData } = error.response;
      const config = error.config as AuthRetryConfig | undefined;

      switch (status) {
        case 401:
          return retryAfterRefresh(
            config,
            responseMessage(resData, '认证失败')
          );
        case 403:
          if (!isAuthEndpoint(config?.url)) {
            message?.error(responseMessage(resData, error.message || '没有权限访问'));
          }
          break;
        case 404:
          if (!isAuthEndpoint(config?.url)) {
            message?.error(responseMessage(resData, error.message || '请求的资源不存在'));
          }
          break;
        case 500:
          if (!isAuthEndpoint(config?.url)) {
            message?.error(responseMessage(resData, error.message || '服务器内部错误'));
          }
          break;
        default:
          if (!isAuthEndpoint(config?.url)) {
            message?.error(responseMessage(resData, error.message || `请求失败，状态码: ${status}`));
          }
      }
    } else if (error.request) {
      if (!isAuthEndpoint(error.config?.url)) {
        message?.error(error.message || '网络错误，请检查网络连接');
      }
    } else {
      if (!isAuthEndpoint(error.config?.url)) {
        message?.error('请求配置错误');
      }
    }

    return Promise.reject(error);
  }
);

export default request;
