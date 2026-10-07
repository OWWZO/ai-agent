import type { ToolFunction, ToolTaskDraft } from "./toolCatalog";
import { validateToolFieldValues, type ToolFieldErrors } from "./toolCatalogModel";

export async function submitToolTask(
  toolFunction: ToolFunction,
  values: Record<string, unknown>,
  onStartToolTask: (draft: ToolTaskDraft) => void,
): Promise<ToolFieldErrors> {
  const errors = validateToolFieldValues(toolFunction.fields, values);
  if (Object.keys(errors).length > 0) {
    return errors;
  }

  const files: File[] = [];
  const seenFiles = new Set<File>();
  for (const field of toolFunction.fields) {
    if (field.type !== "file" && field.type !== "files") {
      continue;
    }
    const value = values[field.name];
    const fieldFiles = Array.isArray(value) ? value : [value];
    for (const candidate of fieldFiles) {
      if (isToolTaskFile(candidate) && !seenFiles.has(candidate)) {
        seenFiles.add(candidate);
        files.push(candidate);
      }
    }
  }

  await onStartToolTask({
    message: toolFunction.buildPrompt(values),
    files,
  });
  return {};
}

export function isToolTaskFile(value: unknown): value is File {
  if (typeof File !== "undefined" && value instanceof File) {
    return true;
  }
  return (
    typeof value === "object" &&
    value !== null &&
    "name" in value &&
    typeof value.name === "string" &&
    "size" in value &&
    typeof value.size === "number" &&
    "arrayBuffer" in value
  );
}
