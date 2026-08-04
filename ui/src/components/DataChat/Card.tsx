import { useMemo } from "react";

type KpiItem = {
  label?: string;
  showValue?: string | number;
  value?: string | number;
};

const Card: ReactorType.FC<{ data: Record<string, any> }> = (props) => {
  const { data } = props;
  const kpiList: KpiItem[] = Array.isArray(data?.kpiList) ? data.kpiList : [];

  const items = useMemo(
    // showValue 优先于原始 value，统一成渲染层需要的 label/value/key 结构。
    () =>
      kpiList.map((item, index) => ({
        key: `kpi-${index}`,
        label: item.label || "未命名",
        value: item.showValue ?? item.value ?? "—",
      })),
    [kpiList]
  );

  if (!items.length) {
    return (
      <div className="flex h-[120px] w-full items-center justify-center text-[13px] text-[var(--chat-text-soft)]">
        暂无指标
      </div>
    );
  }

  return (
    <div className="grid w-full grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <div
          key={item.key}
          className="rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface-soft)]/40 px-4 py-3"
        >
          <div className="truncate text-[12px] font-medium text-[var(--chat-text-soft)]">
            {item.label}
          </div>
          <div className="mt-1 truncate text-[22px] font-semibold tracking-[-0.02em] text-[var(--chat-text)] tabular-nums">
            {item.value}
          </div>
        </div>
      ))}
    </div>
  );
};

export default Card;
