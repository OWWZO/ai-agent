import api from "./index";

export const agentApi = {
  loginIn: () => api.get(`/api/login`),
  getWhiteList: () => api.get(`/api/getWhiteList`),
  apply: (data: string) => api.get(`/api/genie/apply`, { email: data }),
  allModels: () => api.get(`/data/allModels`),
  previewData: (modelCode: string) => api.get(`/data/previewData?modelCode=${modelCode}`),
};
