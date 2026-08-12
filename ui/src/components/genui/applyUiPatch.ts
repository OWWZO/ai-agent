type JsonPatch = {
  op: "add" | "replace" | "remove" | string;
  path: string;
  value?: unknown;
};

// RFC6901 使用 ~0/~1 转义节点名中的 ~ 和 /，先还原后才能访问真实对象键。
function unescapePointerToken(token: string) {
  return token.replace(/~1/g, "/").replace(/~0/g, "~");
}

function tokenize(path: string): string[] {
  if (!path || path === "/") return [];
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return normalized
    .slice(1)
    .split("/")
    .map(unescapePointerToken)
    .filter((t) => t.length > 0);
}

function cloneDeep<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

/**
 * 模型常见路径变体 → 相对当前 doc 可解析的 pointer。
 * 例如 tree 有 root 时：/props/title → /root/props/title
 */
function expandPathCandidates(path: string, doc: any): string[] {
  const raw = String(path || "").trim();
  if (!raw) return [];
  const withSlash = raw.startsWith("/") ? raw : `/${raw}`;
  const candidates = [withSlash];
  if (doc && typeof doc === "object" && doc.root && !withSlash.startsWith("/root")) {
    if (withSlash === "/") {
      candidates.push("/root");
    } else {
      candidates.push(`/root${withSlash}`);
    }
  }
  // 去掉重复 schema 前缀
  if (withSlash.startsWith("/schemaVersion")) {
    candidates.push(withSlash.replace(/^\/schemaVersion\/?/, "/") || "/");
  }
  return Array.from(new Set(candidates));
}

function applyOnePatch(doc: any, patch: JsonPatch): any {
  if (!patch || !patch.path) return doc;
  const candidates = expandPathCandidates(patch.path, doc);
  for (const candidate of candidates) {
    try {
      const tokens = tokenize(candidate);
      if (tokens.length === 0) {
        if (patch.op === "replace" || patch.op === "add") {
          return cloneDeep(patch.value);
        }
        continue;
      }
      let parent: any = doc;
      let ok = true;
      for (let i = 0; i < tokens.length - 1; i++) {
        const key = tokens[i];
        if (parent == null || typeof parent !== "object") {
          ok = false;
          break;
        }
        if (Array.isArray(parent)) {
          const idx = Number(key);
          parent = parent[idx];
        } else {
          parent = parent[key];
        }
      }
      if (!ok || parent == null || typeof parent !== "object") {
        continue;
      }
      const last = tokens[tokens.length - 1];
      if (patch.op === "remove") {
        if (Array.isArray(parent)) {
          const idx = Number(last);
          if (!Number.isNaN(idx)) parent.splice(idx, 1);
        } else {
          delete parent[last];
        }
        return doc;
      }
      if (patch.op === "add" || patch.op === "replace") {
        if (Array.isArray(parent)) {
          const idx = last === "-" ? parent.length : Number(last);
          if (patch.op === "add" && last === "-") {
            parent.push(cloneDeep(patch.value));
          } else if (!Number.isNaN(idx)) {
            if (patch.op === "add") parent.splice(idx, 0, cloneDeep(patch.value));
            else parent[idx] = cloneDeep(patch.value);
          }
        } else {
          parent[last] = cloneDeep(patch.value);
        }
        return doc;
      }
    } catch {
      // try next candidate
    }
  }
  return doc;
}

/**
 * 将 RFC6901 风格 patch 应用到 GenUI 树。
 *
 * 先复制 tree 再逐条修改，避免 SSE 事件重放或 React 旧状态被原地污染；路径
 * 不存在、父节点类型不匹配等情况按 best-effort 跳过，保留可渲染的上一版树。
 */
export function applyUiPatches(tree: any, patches: JsonPatch[]): any {
  if (!tree || !Array.isArray(patches) || patches.length === 0) {
    return tree;
  }
  let doc = cloneDeep(tree);
  for (const patch of patches) {
    doc = applyOnePatch(doc, patch);
  }
  return doc;
}
