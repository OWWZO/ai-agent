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
  if (!path.startsWith("/")) {
    throw new Error(`Invalid JSON pointer: ${path}`);
  }
  return path
    .slice(1)
    .split("/")
    .map(unescapePointerToken);
}

function cloneDeep<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
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
  // patch 是顺序敏感的：后续操作可能依赖前一条 add/replace 已经创建的节点。
  let doc = cloneDeep(tree);
  for (const patch of patches) {
    if (!patch || !patch.path) continue;
    const tokens = tokenize(patch.path);
    if (tokens.length === 0) {
      // 空路径代表替换整个文档；remove 不处理，避免无根对象导致渲染链崩溃。
      if (patch.op === "replace" || patch.op === "add") {
        doc = cloneDeep(patch.value);
      }
      continue;
    }
    let parent: any = doc;
    // 先定位父容器，数组按索引访问，对象按属性名访问。
    for (let i = 0; i < tokens.length - 1; i++) {
      const key = tokens[i];
      if (parent == null || typeof parent !== "object") {
        parent = undefined;
        break;
      }
      if (Array.isArray(parent)) {
        const idx = Number(key);
        parent = parent[idx];
      } else {
        parent = parent[key];
      }
    }
    if (parent == null || typeof parent !== "object") {
      continue;
    }
    const last = tokens[tokens.length - 1];
    // remove 对数组执行删除并收缩索引，对对象则删除指定属性。
    if (patch.op === "remove") {
      if (Array.isArray(parent)) {
        const idx = Number(last);
        if (!Number.isNaN(idx)) parent.splice(idx, 1);
      } else {
        delete parent[last];
      }
    } else if (patch.op === "add" || patch.op === "replace") {
      // 数组的 - 只对 add 有效；普通 add 插入，replace 覆盖现有槽位。
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
    }
  }
  return doc;
}
