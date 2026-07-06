import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import React, { useEffect, useRef } from "react";

const Tabs = <V extends string | number>(props: ReactorType.ControlProps<V> & {
  options: (ReactorType.OptionsType & {split?: boolean})[];
  className?: string;
}) => {
  const { value, onChange, className, options } = props;

  const wrapRef = useRef<HTMLDivElement>(null);
  const slideRef = useRef<HTMLDivElement>(null);

  const adjustSlide = useMemoizedFn(() => {
    if (!wrapRef.current) return;
    const activeTab = wrapRef.current.querySelector<HTMLDivElement>(`[item-key="${value}"]`);
    if (!activeTab) return;

    const { width } = activeTab.getBoundingClientRect();
    const left = activeTab.offsetLeft;

    if (!slideRef.current) return;

    slideRef.current.style.width = `${width}px`;
    slideRef.current.style.transform = `translateX(${left}px)`;
  });

  useEffect(() => {
    adjustSlide();
    const observer = new ResizeObserver(adjustSlide);
    if (wrapRef.current) {
      observer.observe(wrapRef.current);
    }
    return () => {
      observer.disconnect();
    };
  }, [adjustSlide, value]);

  return (
    <div
      className={classNames(
        className,
        "relative flex w-fit items-center gap-1 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-1.5"
      )}
      ref={wrapRef}
    >
      {options.map((item) => (
        <React.Fragment key={item.value}>
          <div
            key={item.value}
            className={classNames(
              "relative z-10 px-4 h-8 rounded-lg cursor-pointer flex items-center justify-center shrink-0 whitespace-nowrap text-[13px] font-medium transition-colors duration-200",
              value === item.value
                ? "text-[var(--chat-text)]"
                : "text-[var(--chat-text-soft)] hover:text-[var(--chat-text)]"
            )}
            item-key={item.value}
            onClick={() => onChange?.(item.value as V)}
          >
            <span>{item.label}</span>
          </div>
          {item.split && <div className="mx-1 h-4 w-px shrink-0 bg-[var(--chat-divider)]" />}
        </React.Fragment>
      ))}
      {/* Active Background Slide */}
      <div
        ref={slideRef}
        className="absolute h-8 rounded-lg bg-[var(--chat-surface)] shadow-[var(--shadow-xs)] transition-[width,transform] duration-200 ease-out will-change-transform"
        style={{ top: "6px" }}
      />
    </div>
  );
};

export default Tabs;
