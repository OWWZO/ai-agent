import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";

export type ConversationHistoryGroupKey = "today" | "earlier";

export type ConversationHistoryGroup = {
  key: ConversationHistoryGroupKey;
  label: string;
  items: LocalThreadListItem[];
};

const getTodayStart = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today.getTime();
};

export const groupConversationHistoryItems = (
  items: LocalThreadListItem[]
): ConversationHistoryGroup[] => {
  const todayStart = getTodayStart();
  const todayItems: LocalThreadListItem[] = [];
  const earlierItems: LocalThreadListItem[] = [];

  items.forEach((item) => {
    // 统一按当天零点分桶，保证侧边栏和搜索面板分组口径一致。
    if (item.updatedAt >= todayStart) {
      todayItems.push(item);
      return;
    }
    earlierItems.push(item);
  });

  return [
    {
      key: "today",
      label: "今天",
      items: todayItems,
    },
    {
      key: "earlier",
      label: "之前",
      items: earlierItems,
    },
  ];
};
