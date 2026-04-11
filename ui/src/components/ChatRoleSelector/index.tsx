import { CheckIcon, ChevronDownIcon, UserRoundIcon } from "lucide-react";

import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

type Props = {
  roles: CHAT.FixRole[];
  selectedRole?: CHAT.ConversationRole | null;
  disabled?: boolean;
  onSelect: (role: CHAT.FixRole) => void;
};

const ChatRoleSelector: ReactorType.FC<Props> = ({ roles, selectedRole, disabled, onSelect }) => {
  const hasRoles = roles.length > 0;
  const label = selectedRole?.agentName || (hasRoles ? roles[0].agentName : "暂无角色");
  const unavailable = selectedRole?.available === false;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          disabled={disabled || !hasRoles}
          className={cn(
            "group inline-flex h-9 max-w-full items-center gap-2 rounded-full border border-transparent px-3 text-[14px] font-medium transition-all duration-200",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#b9d9ff] focus-visible:ring-offset-2 focus-visible:ring-offset-white",
            disabled || !hasRoles ? "cursor-not-allowed opacity-50" : "hover:bg-white",
            unavailable ? "bg-[#fff1f2] text-[#b42318]" : "bg-transparent text-[#111827]"
          )}
        >
          <span
            className={cn(
              "flex size-[26px] shrink-0 items-center justify-center rounded-full transition-all duration-200",
              unavailable ? "bg-[#ffe4e6] text-[#b42318]" : "bg-[#e8f2ff] text-[#0a74da]"
            )}
          >
            <UserRoundIcon className="size-4" />
          </span>
          <span className="max-w-[120px] truncate">{label}</span>
          <ChevronDownIcon className="size-4 shrink-0 text-[var(--chat-text-muted)]" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        side="bottom"
        sideOffset={12}
        className="w-[240px] rounded-[16px] border-0 bg-white p-1 shadow-[0_10px_28px_-18px_rgba(15,23,42,0.2)]"
      >
        <div className="px-2 pb-1 pt-0.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-[#6b7280]">
          角色库
        </div>
        <div className="space-y-1">
          {roles.map((role) => {
            const active = role.agentId === selectedRole?.agentId;
            return (
              <button
                key={role.agentId}
                type="button"
                className={cn(
                  "flex w-full gap-2 rounded-xl border border-transparent px-2 py-2 text-left transition-all duration-200",
                  active ? "bg-[#e8f2ff]" : "bg-transparent hover:bg-[#f9fafb]"
                )}
                onClick={() => onSelect(role)}
              >
                <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-lg bg-[#e8f2ff] text-[#0a74da]">
                  <UserRoundIcon className="size-3.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[14px] font-medium text-[var(--chat-text)]">
                    {role.agentName}
                  </span>
                  <span className="mt-0.5 line-clamp-2 text-[11px] leading-4 text-[var(--chat-text-soft)]">
                    {role.description || "Fix 模式角色"}
                  </span>
                </span>
                {active ? <CheckIcon className="mt-1 size-3 shrink-0 text-[#0a74da]" /> : null}
              </button>
            );
          })}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ChatRoleSelector;
