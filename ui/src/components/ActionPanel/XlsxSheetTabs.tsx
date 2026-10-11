import type { KeyboardEvent } from "react";
import type { XlsxViewerController } from "@extend-ai/react-xlsx";
import { cn } from "@/lib/utils";

/**
 * 工作表切换条。
 *
 * `@extend-ai/react-xlsx` 的默认工具栏同时承载编辑动作和 sheet tabs；
 * 预览关闭默认工具栏后会丢失多表导航，因此只通过 `toolbar` render prop
 * 补回只读的 sheet tabs（含键盘导航），与 ZCode `XlsxSheetTabs` 对齐。
 */
function getNextSheetTabIndex({
  currentIndex,
  key,
  tabCount,
}: {
  currentIndex: number;
  key: string;
  tabCount: number;
}): number | null {
  if (tabCount < 1) {
    return null;
  }
  if (key === "Home") {
    return 0;
  }
  if (key === "End") {
    return tabCount - 1;
  }
  if (key === "ArrowLeft") {
    return (currentIndex - 1 + tabCount) % tabCount;
  }
  if (key === "ArrowRight") {
    return (currentIndex + 1) % tabCount;
  }
  return null;
}

interface XlsxSheetTabsProps {
  controller: XlsxViewerController;
  /** tablist 的无障碍标签。 */
  label: string;
}

export function XlsxSheetTabs({ controller, label }: XlsxSheetTabsProps) {
  const { activeTabIndex, setActiveTabIndex, tabs } = controller;

  if (tabs.length <= 1) {
    return null;
  }

  const selectTab = (tabIndex: number) => {
    setActiveTabIndex(tabIndex);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, tabIndex: number) => {
    const nextTabIndex = getNextSheetTabIndex({
      currentIndex: tabIndex,
      key: event.key,
      tabCount: tabs.length,
    });
    if (nextTabIndex === null) {
      return;
    }

    event.preventDefault();
    selectTab(nextTabIndex);
    const tabButtons =
      event.currentTarget.parentElement?.querySelectorAll<HTMLElement>('[role="tab"]');
    tabButtons?.item(nextTabIndex).focus();
  };

  return (
    <div
      aria-label={label}
      className="flex min-h-0 shrink-0 gap-1 overflow-x-auto border-b border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-2 py-1"
      role="tablist"
    >
      {tabs.map((tab, tabIndex) => {
        const isActive = tabIndex === activeTabIndex;
        return (
          <button
            aria-selected={isActive}
            className={cn(
              "h-7 max-w-48 shrink-0 truncate rounded-md px-2 text-[12px] transition-colors",
              isActive
                ? "bg-[var(--chat-surface-muted)] font-medium text-[var(--chat-text)]"
                : "text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
            )}
            key={tab.id}
            onClick={() => selectTab(tabIndex)}
            onKeyDown={(event) => handleKeyDown(event, tabIndex)}
            role="tab"
            tabIndex={isActive ? 0 : -1}
            title={tab.name}
            type="button"
          >
            {tab.name}
          </button>
        );
      })}
    </div>
  );
}

export default XlsxSheetTabs;
