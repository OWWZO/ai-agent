import classNames from "classnames";
import { Blocks, Bot, Cpu, DatabaseZap, WandSparkles } from "lucide-react";
import { Link, useLocation } from "react-router-dom";

import { ROUTES } from "@/router/routes";

type WorkspaceToolItem = {
  key: "mrag" | "image-generation" | "sub-agents" | "models" | "capabilities";
  label: string;
  description: string;
  icon: typeof DatabaseZap;
  to: string;
};

const workspaceToolItems: WorkspaceToolItem[] = [
  {
    key: "mrag",
    label: "MRAG 文件工作台",
    description: "知识库、文件与检索调试",
    icon: DatabaseZap,
    to: ROUTES.WORKSPACE_MRAG,
  },
  {
    key: "image-generation",
    label: "绘图智能体",
    description: "图片生成与 Base64 解析",
    icon: WandSparkles,
    to: ROUTES.WORKSPACE_IMAGE_GENERATION,
  },
  {
    key: "sub-agents",
    label: "子 Agent 装配",
    description: "自定义 prompt 与工具调度",
    icon: Bot,
    to: ROUTES.WORKSPACE_SUB_AGENTS,
  },
  {
    key: "models",
    label: "模型接入",
    description: "API Key / 模型热配置",
    icon: Cpu,
    to: ROUTES.WORKSPACE_MODELS,
  },
  {
    key: "capabilities",
    label: "能力库",
    description: "技能 zip / MCP 连接器",
    icon: Blocks,
    to: ROUTES.WORKSPACE_CAPABILITIES,
  },
];

function isActiveWorkspaceTool(pathname: string, target: string): boolean {
  return pathname === target || pathname.startsWith(`${target}/`);
}

const WorkspaceToolSwitcher: ReactorType.FC = ({ className }) => {
  const location = useLocation();

  return (
    <div
      className={classNames(
        "workspace-tool-switcher inline-flex max-w-full items-center gap-0.5 overflow-x-auto rounded-lg border border-[var(--color-line)] bg-[var(--color-surface-sunken)] p-1",
        className,
      )}
      role="navigation"
      aria-label="工作台模块"
    >
      {workspaceToolItems.map((item) => {
        const active = isActiveWorkspaceTool(location.pathname, item.to);

        return (
          <Link
            key={item.key}
            to={item.to}
            target="_blank"
            rel="noreferrer"
            aria-current={active ? "page" : undefined}
            className={classNames(
              "group inline-flex min-h-[30px] shrink-0 items-center gap-1.5 rounded-md border border-transparent px-2.5 text-[13px] font-medium transition-[background-color,border-color,color] duration-[160ms] ease-[var(--ease-out)]",
              active
                ? "border-[var(--color-accent-bd)] bg-[var(--color-accent-soft)] text-[var(--color-accent)]"
                : "text-[var(--color-text-muted)] hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]",
            )}
          >
            <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span className="max-w-[132px] truncate">{item.label}</span>
          </Link>
        );
      })}
    </div>
  );
};

export default WorkspaceToolSwitcher;
