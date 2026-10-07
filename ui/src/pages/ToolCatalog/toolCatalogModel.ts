import type {
  ToolAccess,
  ToolCatalogItem,
  ToolCatalogKind,
  ToolCategory,
  ToolField,
} from "./toolCatalog";

export type ToolCatalogFilterCategory = ToolCategory | "all";
export type ToolFieldErrors = Record<string, string>;

export type ToolCatalogCardMetadata = Pick<
  ToolCatalogItem,
  "id" | "kind" | "title" | "description" | "category" | "iconKey"
> & {
  functionCount: number;
};

export function filterToolCatalog(
  items: readonly ToolCatalogItem[],
  category: ToolCatalogFilterCategory = "all",
  query = ""
): ToolCatalogItem[] {
  const normalizedQuery = query.trim().toLowerCase();

  return items.filter((item) => {
    if (category !== "all" && item.category !== category) {
      return false;
    }
    if (!normalizedQuery) {
      return true;
    }

    const searchableText = [
      item.id,
      item.title,
      item.description,
      item.category,
      ...item.functions.flatMap((toolFunction) => [
        toolFunction.id,
        toolFunction.title,
        toolFunction.description,
        toolFunction.command ?? "",
        ...toolFunction.fields.flatMap((field) => [
          field.label,
          field.name,
          field.description ?? "",
        ]),
      ]),
    ]
      .join(" ")
      .toLowerCase();

    return searchableText.includes(normalizedQuery);
  });
}

export function getToolFunctionCount(item: ToolCatalogItem): number {
  return item.functions.length;
}

export function getToolCatalogCardMetadata(
  item: ToolCatalogItem
): ToolCatalogCardMetadata {
  return {
    id: item.id,
    kind: item.kind,
    title: item.title,
    description: item.description,
    category: item.category,
    iconKey: item.iconKey,
    functionCount: getToolFunctionCount(item),
  };
}

export function initializeToolFieldValues(
  fields: readonly ToolField[]
): Record<string, unknown> {
  return fields.reduce<Record<string, unknown>>((values, field) => {
    if (field.defaultValue !== undefined) {
      values[field.name] = Array.isArray(field.defaultValue)
        ? [...field.defaultValue]
        : field.defaultValue;
    } else if (
      field.type === "boolean" ||
      field.type === "multi-select" ||
      field.type === "file" ||
      field.type === "files"
    ) {
      values[field.name] = field.type === "boolean" ? false : [];
    } else {
      values[field.name] = "";
    }

    return values;
  }, {});
}

export function validateToolFieldValues(
  fields: readonly ToolField[],
  values: Record<string, unknown>
): ToolFieldErrors {
  const errors: ToolFieldErrors = {};

  for (const field of fields) {
    const value = values[field.name];
    const empty = isEmptyValue(value);

    if (field.required && empty) {
      errors[field.name] = "此字段为必填项";
      continue;
    }
    if (empty) {
      continue;
    }

    if (field.type === "number") {
      const numberValue =
        typeof value === "number"
          ? value
          : typeof value === "string"
            ? Number(value)
            : Number.NaN;
      if (!Number.isFinite(numberValue)) {
        errors[field.name] = "请输入有效数字";
        continue;
      }
      if (
        (field.min !== undefined && numberValue < field.min) ||
        (field.max !== undefined && numberValue > field.max)
      ) {
        errors[field.name] = buildNumberRangeError(field.min, field.max);
        continue;
      }
    }

    if (field.type === "select" && typeof value !== "string") {
      errors[field.name] = "请选择配置中的有效选项";
      continue;
    }
    if (field.type === "multi-select" && !Array.isArray(value)) {
      errors[field.name] = "请选择配置中的有效选项";
      continue;
    }

    if (
      (field.type === "select" || field.type === "multi-select") &&
      field.options
    ) {
      const selectedValues = Array.isArray(value) ? value : [value];
      const allowedValues = new Set(field.options.map((option) => option.value));
      if (
        selectedValues.some(
          (selectedValue) =>
            typeof selectedValue !== "string" ||
            !allowedValues.has(selectedValue)
        )
      ) {
        errors[field.name] = "请选择配置中的有效选项";
        continue;
      }
    }

    if (field.type === "url") {
      if (typeof value !== "string") {
        errors[field.name] = "请输入有效的 HTTP 或 HTTPS 链接";
        continue;
      }
      try {
        const parsedUrl = new URL(value.trim());
        if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
          errors[field.name] = "请输入有效的 HTTP 或 HTTPS 链接";
          continue;
        }
      } catch {
        errors[field.name] = "请输入有效的 HTTP 或 HTTPS 链接";
        continue;
      }
    }

    if (
      field.maxLength !== undefined &&
      typeof value === "string" &&
      value.length > field.maxLength
    ) {
      errors[field.name] = `内容不能超过 ${field.maxLength} 个字符`;
      continue;
    }

    if (field.type === "file" || field.type === "files") {
      const fileCount = countFiles(value);
      const minFiles = field.minFiles ?? (field.required ? 1 : 0);
      const maxFiles = field.type === "file" ? 1 : field.maxFiles;
      if (fileCount < minFiles || (maxFiles !== undefined && fileCount > maxFiles)) {
        errors[field.name] = buildFileCountError(minFiles, maxFiles);
      }
    }
  }

  return errors;
}

export function serializeToolPromptParameters(
  fields: readonly ToolField[],
  values: Record<string, unknown>,
  includeArgumentNames = false,
): string {
  const lines: string[] = [];

  const labelFor = (field: ToolField) =>
    includeArgumentNames
      ? `${field.label} (${field.positional ? `位置参数 ${field.name}` : `--${field.name}`})`
      : field.label;

  for (const field of fields) {
    const value = values[field.name];
    if (isEmptyValue(value)) {
      continue;
    }

    if (field.type === "file" || field.type === "files") {
      if (countFiles(value) > 0) {
        lines.push(`- ${labelFor(field)}：本轮上传文件`);
      }
      continue;
    }

    if (typeof value === "boolean") {
      lines.push(`- ${labelFor(field)}：${value ? "是" : "否"}`);
      continue;
    }

    if (Array.isArray(value)) {
      for (const entry of value) {
        if (!isEmptyValue(entry)) {
          lines.push(`- ${labelFor(field)}：${String(entry).trim()}`);
        }
      }
      continue;
    }

    lines.push(`- ${labelFor(field)}：${String(value).trim()}`);
  }

  return lines.join("\n");
}

export function buildToolPrompt(input: {
  kind: ToolCatalogKind;
  command: string;
  access: ToolAccess;
  browser?: boolean;
  fields: readonly ToolField[];
  values: Record<string, unknown>;
}): string {
  const { kind, command, access, browser, fields, values } = input;
  const isOpenCli = kind === "opencli-site";
  const taskName = isOpenCli
    ? `OpenCLI 的 ${command}`
    : `BaseTool 的 ${command}`;
  const header =
    access === "write"
      ? `这是一个写入操作，请${isOpenCli ? "执行" : "使用"} ${taskName} 功能。`
      : `请${isOpenCli ? "使用" : "调用"} ${taskName} 功能。`;
  const writeNotice = (() => {
    if (access !== "write") return "";
    if (isOpenCli) {
      return browser !== false
        ? "此任务会操作用户本机浏览器。"
        : "此任务会对目标站点执行写入操作。";
    }
    if (command === "browser") return "此任务会操作用户本机浏览器。";
    if (command === "agent_browser") return "此任务会操作 Agent 浏览器。";
    return "此任务可能生成或修改文件。";
  })();
  const parameters = serializeToolPromptParameters(fields, values, isOpenCli);
  const parameterSection = parameters ? `任务参数：\n${parameters}` : "";
  const completionInstruction =
    isOpenCli && access === "write"
      ? "请完成操作并返回执行结果。如果需要登录或浏览器人工接管，请直接说明。"
      : "请按上述要求完成任务并整理关键结果；如果执行失败，请说明失败原因。";

  return [header, writeNotice, parameterSection, completionInstruction]
    .filter(Boolean)
    .join("\n\n");
}

function isEmptyValue(value: unknown): boolean {
  if (value === undefined || value === null) {
    return true;
  }
  if (typeof value === "string") {
    return value.trim().length === 0;
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  return false;
}

function countFiles(value: unknown): number {
  if (isEmptyValue(value)) {
    return 0;
  }
  if (Array.isArray(value)) {
    return value.length;
  }
  if (
    typeof value === "object" &&
    value !== null &&
    "length" in value &&
    typeof value.length === "number"
  ) {
    return value.length;
  }
  return 1;
}

function buildNumberRangeError(min?: number, max?: number): string {
  if (min !== undefined && max !== undefined) {
    return `请输入 ${min} 到 ${max} 之间的数字`;
  }
  if (min !== undefined) {
    return `请输入不小于 ${min} 的数字`;
  }
  if (max !== undefined) {
    return `请输入不大于 ${max} 的数字`;
  }
  return "请输入有效数字";
}

function buildFileCountError(min: number, max?: number): string {
  if (min > 0 && max !== undefined) {
    return `请上传 ${min} 到 ${max} 个文件`;
  }
  if (min > 0) {
    return `请至少上传 ${min} 个文件`;
  }
  if (max !== undefined) {
    return `最多上传 ${max} 个文件`;
  }
  return "文件数量不符合要求";
}
