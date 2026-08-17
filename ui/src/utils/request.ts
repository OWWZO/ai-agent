import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { jumpUrl, showMessage } from './utils';
import { getDeviceId } from '@/services/agentConversation';
import { resolveServiceBaseUrl } from './origin';

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

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 兼容仍然依赖设备标识的上传与流式接口
    config.headers['X-Device-Id'] = getDeviceId();
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

const noAuth = (url?: string) => {
  showMessage()?.error('未登录');
  if (url) {
    jumpUrl(url);
  }
};

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
        noAuth(data.redirectUrl);
      } else {
        const errMsg = data.msg || data.info || '请求失败';
        showMessage()?.error(errMsg);
        return Promise.reject(new Error(errMsg));
      }
    }

    return response;
  },
  (error) => {
    console.error('响应错误:', error);

    const message = showMessage();
    if (error.response) {
      const { status, data: resData } = error.response;

      switch (status) {
        case 401:
          // 未授权，清除token并跳转登录
          noAuth(resData.redirectUrl);
          break;
        case 403:
          message?.error(error.message || '没有权限访问');
          break;
        case 404:
          message?.error(error.message || '请求的资源不存在');
          break;
        case 500:
          message?.error(error.message || '服务器内部错误');
          break;
        default:
          message?.error(error.message || `请求失败，状态码: ${status}`);
      }
    } else if (error.request) {
      message?.error(error.message || '网络错误，请检查网络连接');
    } else {
      message?.error('请求配置错误');
    }

    return Promise.reject(error);
  }
);

export default request;
