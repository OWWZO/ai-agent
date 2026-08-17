import api from "./index";

export type SkillRow = {
  name: string;
  description?: string | null;
  basePath?: string | null;
  source?: string;
};

export type SkillPackagePreview = {
  name: string;
  description?: string | null;
  contentPreview?: string;
  extraFiles?: string[];
  nameTaken?: boolean;
};

const BASE = "/api/v1/admin/skills";

export const skillAdminApi = {
  list: () =>
    api.get<SkillRow[]>(`${BASE}/list`) as unknown as Promise<SkillRow[]>,

  parsePackage: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    // 不手动设 Content-Type，交给浏览器带 boundary
    return api.post<SkillPackagePreview>(
      `${BASE}/parse-package`,
      form
    ) as unknown as Promise<SkillPackagePreview>;
  },

  upload: async (file: File, replace = false) => {
    const form = new FormData();
    form.append("file", file);
    return api.post<SkillRow>(
      `${BASE}/upload?replace=${replace ? "true" : "false"}`,
      form
    ) as unknown as Promise<SkillRow>;
  },

  create: (payload: {
    name?: string;
    description?: string;
    content: string;
    replace?: boolean;
  }) =>
    api.post<SkillRow>(`${BASE}/create`, payload) as unknown as Promise<SkillRow>,

  importUrl: (url: string, replace = false) =>
    api.post<SkillRow>(`${BASE}/import-url`, {
      url,
      replace,
    }) as unknown as Promise<SkillRow>,

  remove: (name: string) =>
    api.delete<boolean>(
      `${BASE}/${encodeURIComponent(name)}`
    ) as unknown as Promise<boolean>,

  reload: () =>
    api.post<boolean>(`${BASE}/reload`) as unknown as Promise<boolean>,
};
