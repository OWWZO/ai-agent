/** 模型接入页展示元数据 */

export type ProviderPreset = {
  id: string;
  label: string;
  baseUrl: string;
  completionsPath: string;
  keyHint: string;
};

/** 常用厂商预设：选中后自动填 baseUrl / path，key 仍手填 */
export const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: "micu",
    label: "Micu / 兼容中转",
    baseUrl: "https://www.micuapi.ai/v1",
    completionsPath: "/chat/completions",
    keyHint: "中转站后台复制 API Key",
  },
  {
    id: "openai",
    label: "OpenAI",
    baseUrl: "https://api.openai.com/v1",
    completionsPath: "/chat/completions",
    keyHint: "platform.openai.com → API keys",
  },
  {
    id: "deepseek",
    label: "DeepSeek",
    baseUrl: "https://api.deepseek.com/v1",
    completionsPath: "/chat/completions",
    keyHint: "platform.deepseek.com",
  },
  {
    id: "moonshot",
    label: "Kimi / Moonshot",
    baseUrl: "https://api.moonshot.cn/v1",
    completionsPath: "/chat/completions",
    keyHint: "platform.moonshot.cn",
  },
  {
    id: "custom",
    label: "自定义",
    baseUrl: "",
    completionsPath: "/chat/completions",
    keyHint: "手填 Base URL 与 Key",
  },
];

export function matchProviderId(baseUrl?: string): string {
  const u = (baseUrl || "").trim().toLowerCase();
  if (!u) return "custom";
  const hit = PROVIDER_PRESETS.find(
    (p) => p.id !== "custom" && p.baseUrl && u.startsWith(p.baseUrl.toLowerCase())
  );
  return hit?.id ?? "custom";
}
