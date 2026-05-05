import request from '@/utils/request';
import type { AxiosRequestConfig } from "axios";

interface ApiResponse<T> {
  code: number | string
  data: T
  msg?: string
  info?: string
}

export const api = {
  get: <T>(url: string, params?: any, config?: AxiosRequestConfig) =>
    request.get<ApiResponse<T>>(url, { ...config, params }),

  post: <T>(url: string, data?: any, config?: AxiosRequestConfig) =>
    request.post<ApiResponse<T>>(url, data, config),

  put: <T>(url: string, data?: any, config?: AxiosRequestConfig) =>
    request.put<ApiResponse<T>>(url, data, config),

  delete: <T>(url: string, params?: any, config?: AxiosRequestConfig) =>
    request.delete<ApiResponse<T>>(url, { ...config, params }),
};

export default api;
