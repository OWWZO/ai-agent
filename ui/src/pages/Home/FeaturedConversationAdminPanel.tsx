import type { ConversationSessionItem } from "@/services/agentConversation";
import type { FeaturedConversationAdminRecord } from "@/services/featuredConversationAdmin";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

import type { FeaturedConversationFormState } from "./featuredConversationAdminModel";
import { getFeaturedConversationStatusLabel } from "./featuredConversationAdminModel";

type FeaturedConversationAdminPanelProps = {
  session: ConversationSessionItem;
  form: FeaturedConversationFormState;
  record: FeaturedConversationAdminRecord | null;
  loading: boolean;
  submitting: boolean;
  onChange: (patch: Partial<FeaturedConversationFormState>) => void;
  onClose: () => void;
  onSaveDraft: () => void;
  onPublish: () => void;
};

function renderActionLabel(record: FeaturedConversationAdminRecord | null) {
  if (!record) {
    return "创建并上线";
  }
  return record.status?.toUpperCase() === "ONLINE" ? "下线" : "发布上线";
}

export default function FeaturedConversationAdminPanel(
  props: FeaturedConversationAdminPanelProps
) {
  const {
    session,
    form,
    record,
    loading,
    submitting,
    onChange,
    onClose,
    onSaveDraft,
    onPublish,
  } = props;

  if (loading) {
    return (
      <div className="space-y-2 py-10 text-center">
        <div className="text-[16px] font-medium text-[var(--chat-text)]">
          正在加载精品配置...
        </div>
        <div className="text-[13px] text-[var(--chat-text-soft)]">
          会根据当前 sessionId 自动回填已有发布信息
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <div className="text-[18px] font-semibold text-[var(--chat-text)]">
          设为精品
        </div>
        <div className="text-[13px] text-[var(--chat-text-soft)]">
          为当前会话补充公开展示信息，并按需上线到精品对话列表。
        </div>
      </div>

      <div className="rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/70 p-4 text-[13px] text-[var(--chat-text-soft)]">
        <div className="font-medium text-[var(--chat-text)]">{session.title}</div>
        <div className="mt-2 break-all">sessionId：{session.sessionId}</div>
        {record ? (
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <span>featuredId：{record.featuredId}</span>
            <span>
              当前状态：
              <span className="ml-1 font-medium text-[var(--chat-text)]">
                {getFeaturedConversationStatusLabel(record.status)}
              </span>
            </span>
          </div>
        ) : null}
      </div>

      <div className="grid gap-4">
        <label className="grid gap-2">
          <span className="text-[13px] font-medium text-[var(--chat-text)]">
            精品标题
          </span>
          <Input
            value={form.title}
            disabled={submitting}
            onChange={(event) => onChange({ title: event.target.value })}
            placeholder="请输入公开展示标题"
          />
        </label>

        <label className="grid gap-2">
          <span className="text-[13px] font-medium text-[var(--chat-text)]">
            精品摘要
          </span>
          <Textarea
            value={form.summary}
            disabled={submitting}
            onChange={(event) => onChange({ summary: event.target.value })}
            placeholder="请输入公开展示摘要"
            rows={4}
          />
        </label>

        <div className="grid gap-4 md:grid-cols-2">
          <label className="grid gap-2">
            <span className="text-[13px] font-medium text-[var(--chat-text)]">
              封面图 URL
            </span>
            <Input
              value={form.coverUrl}
              disabled={submitting}
              onChange={(event) => onChange({ coverUrl: event.target.value })}
              placeholder="可选"
            />
          </label>

          <label className="grid gap-2">
            <span className="text-[13px] font-medium text-[var(--chat-text)]">
              标签
            </span>
            <Input
              value={form.tagsText}
              disabled={submitting}
              onChange={(event) => onChange({ tagsText: event.target.value })}
              placeholder="多个标签用逗号分隔"
            />
          </label>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <label className="grid gap-2">
            <span className="text-[13px] font-medium text-[var(--chat-text)]">
              排序值
            </span>
            <Input
              value={form.sortOrder}
              disabled={submitting}
              onChange={(event) => onChange({ sortOrder: event.target.value })}
              placeholder="默认 100"
            />
          </label>

          <label className="grid gap-2">
            <span className="text-[13px] font-medium text-[var(--chat-text)]">
              操作人
            </span>
            <Input
              value={form.operator}
              disabled={submitting}
              onChange={(event) => onChange({ operator: event.target.value })}
              placeholder="记录本次发布操作者"
            />
          </label>
        </div>
      </div>

      <div className="flex flex-col-reverse gap-2 border-t border-[var(--chat-border)] pt-4 sm:flex-row sm:justify-end">
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          取消
        </Button>
        <Button variant="outline" disabled={submitting} onClick={onSaveDraft}>
          {record ? "保存修改" : "保存草稿"}
        </Button>
        <Button
          variant={
            record?.status?.toUpperCase() === "ONLINE" ? "destructive" : "default"
          }
          disabled={submitting}
          onClick={onPublish}
        >
          {renderActionLabel(record)}
        </Button>
      </div>
    </div>
  );
}
