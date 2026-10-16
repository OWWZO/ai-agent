import classNames from "classnames";
import { ActivityIcon, BotIcon, ListTree } from "lucide-react";
import { FILE_KIND_TONE } from "@/utils/fileKind";
import type { WorkspaceTabIconSpec } from "./workspaceTabDisplay";

const GLYPH_ICONS = {
  bot: BotIcon,
  "list-tree": ListTree,
  activity: ActivityIcon,
} as const;

/**
 * tab 图标的唯一渲染入口。
 *
 * 与 `resolveWorkspaceTabDisplay` 分工：那边只出 spec（纯数据、可单测），
 * 这边把 spec 渲染成节点 —— tab 条用 `size="sm"`、标签页列表浮层用 `size="md"`
 * （对齐 ZCode：浮层里的图标比条上大一号）。
 */
export function WorkspaceTabIcon({
  spec,
  size = "sm",
}: {
  spec: WorkspaceTabIconSpec;
  size?: "sm" | "md";
}) {
  const box = size === "md" ? "size-5" : "size-4";

  if (spec.kind === "glyph") {
    const Icon = GLYPH_ICONS[spec.glyph] || BotIcon;
    return (
      <Icon className={classNames(box, "shrink-0 text-foreground-subtle")} />
    );
  }

  return (
    <span
      className={classNames(
        "flex shrink-0 items-center justify-center rounded-sm font-bold leading-none",
        FILE_KIND_TONE,
        size === "md" ? "size-5 text-ui-sm" : "size-4 text-ui-xs"
      )}
    >
      {spec.badge}
    </span>
  );
}
