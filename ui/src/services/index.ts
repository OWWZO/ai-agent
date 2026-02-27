import request from '@/utils/request';

interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export const api = {
  get: <T>(url: string, params?: any) =>
    request.get<ApiResponse<T>>(url, { params }),

  post: <T>(url: string, data?: any) =>
    request.post<ApiResponse<T>>(url, data),

  put: <T>(url: string, data?: any) =>
    request.put<ApiResponse<T>>(url, data),

  delete: <T>(url: string, params?: any) =>
    request.delete<ApiResponse<T>>(url, { params }),
};

export default api;