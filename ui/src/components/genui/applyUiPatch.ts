type JsonPatch = {
  op: "add" | "replace" | "remove" | string;
  path: string;
  value?: unknown;
};

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
 * Apply RFC6901-ish patches onto a GenUI tree object (best-effort).
 */
export function applyUiPatches(tree: any, patches: JsonPatch[]): any {
  if (!tree || !Array.isArray(patches) || patches.length === 0) {
    return tree;
  }
  let doc = cloneDeep(tree);
  for (const patch of patches) {
    if (!patch || !patch.path) continue;
    const tokens = tokenize(patch.path);
    if (tokens.length === 0) {
      if (patch.op === "replace" || patch.op === "add") {
        doc = cloneDeep(patch.value);
      }
      continue;
    }
    let parent: any = doc;
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
    if (patch.op === "remove") {
      if (Array.isArray(parent)) {
        const idx = Number(last);
        if (!Number.isNaN(idx)) parent.splice(idx, 1);
      } else {
        delete parent[last];
      }
    } else if (patch.op === "add" || patch.op === "replace") {
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
