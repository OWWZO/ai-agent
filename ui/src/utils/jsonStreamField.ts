const STREAM_PREVIEW_FALLBACK_CHARS = 20480;

/**
 * 从尚未闭合的 JSON 工具入参字符串中尽量抽出某个 string 字段。
 * 用于 LLM 仍在生成 tool arguments 时的 live 预览。
 */
export function pickJsonStringField(
  key: string,
  raw: string,
  partialArgs?: Record<string, unknown>
): string {
  const fromPartial = partialArgs?.[key];
  if (typeof fromPartial === "string" && fromPartial.length > 0) {
    return fromPartial;
  }

  const source = typeof raw === "string" ? raw : "";
  if (!source) {
    return "";
  }

  try {
    const parsed = JSON.parse(source) as Record<string, unknown>;
    const value = parsed?.[key];
    if (typeof value === "string") {
      return value;
    }
  } catch {
    /* incomplete JSON while streaming */
  }

  const needle = `"${key}"`;
  const keyIdx = source.indexOf(needle);
  if (keyIdx === -1) {
    return "";
  }

  const slice = source.slice(keyIdx);
  const colon = slice.indexOf(":");
  if (colon === -1) {
    return "";
  }

  let rest = slice.slice(colon + 1).trimStart();
  if (!rest.startsWith('"')) {
    return "";
  }
  rest = rest.slice(1);

  let out = "";
  for (let i = 0; i < rest.length; i += 1) {
    const c = rest[i];
    if (c === "\\") {
      const n = rest[i + 1];
      if (n === "n") out += "\n";
      else if (n === "t") out += "\t";
      else if (n === "r") out += "\r";
      else if (n === '"' || n === "\\") out += n ?? "";
      else if (n === "u" && rest.length >= i + 6) {
        const hex = rest.slice(i + 2, i + 6);
        if (/^[0-9a-fA-F]{4}$/.test(hex)) {
          out += String.fromCharCode(parseInt(hex, 16));
          i += 5;
          continue;
        }
        out += n;
      } else if (n !== undefined) {
        out += n;
      }
      i += 1;
      continue;
    }
    if (c === '"') {
      break;
    }
    out += c ?? "";
  }
  return out;
}

/** 按候选 key 顺序取第一个非空 string 字段 */
export function pickJsonStringFieldAny(
  keys: string[],
  raw: string,
  partialArgs?: Record<string, unknown>
): string {
  for (const key of keys) {
    const value = pickJsonStringField(key, raw, partialArgs);
    if (value) {
      return value;
    }
  }
  return "";
}

/** 字段尚不可用时，回退展示原始入参前缀（避免空白预览） */
export function fallbackStreamRawPreview(raw: string, max = STREAM_PREVIEW_FALLBACK_CHARS): string {
  const source = (raw || "").trim();
  if (!source) {
    return "";
  }
  if (source.length <= max) {
    return source;
  }
  return `${source.slice(0, max)}\n…`;
}
