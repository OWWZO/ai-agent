import type { ReactNode } from "react";
import { ArrowLeft, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import classNames from "classnames";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import { ROUTES } from "@/router/routes";

import "@/styles/workspace-admin.css";

type WorkspaceAdminHeaderProps = {
  title: string;
  description: string;
  icon: LucideIcon;
  embedded?: boolean;
  actions?: ReactNode;
};

export default function WorkspaceAdminHeader({
  title,
  description,
  icon: Icon,
  embedded,
  actions,
}: WorkspaceAdminHeaderProps) {
  return (
    <header className="workspace-admin-header">
      <div
        className={classNames(
          "workspace-admin-header-inner",
          embedded && "workspace-admin-header-inner-embedded",
        )}
      >
        <div className="workspace-admin-heading">
          {!embedded ? (
            <Link
              to={ROUTES.HOME}
              target="_blank"
              rel="noreferrer"
              className="workspace-admin-back"
              aria-label="返回首页"
              title="返回首页"
            >
              <ArrowLeft aria-hidden="true" />
            </Link>
          ) : null}
          <span className="workspace-admin-mark" aria-hidden="true">
            <Icon />
          </span>
          <div className="workspace-admin-heading-copy">
            <span className="workspace-admin-kicker">Reactor / 工作台</span>
            <h1 className="workspace-admin-title">{title}</h1>
            <p className="workspace-admin-description">{description}</p>
          </div>
        </div>

        <div className="workspace-admin-header-actions">
          {!embedded ? <WorkspaceToolSwitcher /> : null}
          {actions}
        </div>
      </div>
    </header>
  );
}
