import classNames from "classnames";
import { DatabaseZap, WandSparkles } from "lucide-react";
import { Link, useLocation } from "react-router-dom";

import { ROUTES } from "@/router/routes";

type WorkspaceToolItem = {
  key: "mrag" | "image-generation";
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
];

function isActiveWorkspaceTool(pathname: string, target: string): boolean {
  return pathname === target || pathname.startsWith(`${target}/`);
}

const WorkspaceToolSwitcher: ReactorType.FC = ({ className }) => {
  const location = useLocation();

  return (
    <div
      className={classNames(
        "inline-flex flex-wrap items-center gap-2 rounded-[20px] border border-slate-200 bg-slate-100/85 p-1.5",
        className
      )}
    >
      {workspaceToolItems.map((item) => {
        const active = isActiveWorkspaceTool(location.pathname, item.to);

        return (
          <Link
            key={item.key}
            to={item.to}
            className={classNames(
              "group flex min-w-[180px] flex-1 items-center gap-3 rounded-[16px] px-3.5 py-2.5 transition sm:flex-none",
              active
                ? "bg-white text-slate-900 shadow-[0_10px_24px_-18px_rgba(15,23,42,0.45)]"
                : "text-slate-500 hover:bg-white/80 hover:text-slate-900"
            )}
          >
            <span
              className={classNames(
                "flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border transition",
                active
                  ? "border-sky-100 bg-sky-50 text-sky-600"
                  : "border-slate-200 bg-white/85 text-slate-400 group-hover:text-slate-700"
              )}
            >
              <item.icon className="h-4.5 w-4.5" />
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">{item.label}</span>
              <span className="block truncate text-[12px] text-slate-400">
                {item.description}
              </span>
            </span>
          </Link>
        );
      })}
    </div>
  );
};

export default WorkspaceToolSwitcher;
