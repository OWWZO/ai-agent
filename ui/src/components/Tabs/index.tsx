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
        "relative flex items-center gap-1 rounded-xl bg-[#f5f5f7] p-1.5 w-fit"
      )}
      ref={wrapRef}
    >
      {options.map((item) => (
        <React.Fragment key={item.value}>
          <div
            key={item.value}
            className={classNames(
              "relative z-10 px-4 h-8 rounded-lg cursor-pointer flex items-center justify-center shrink-0 whitespace-nowrap text-[13px] font-medium transition-colors duration-200",
              value === item.value ? "text-[#1d1d1f]" : "text-[#86868b] hover:text-[#1d1d1f]"
            )}
            item-key={item.value}
            onClick={() => onChange?.(item.value as V)}
          >
            <span>{item.label}</span>
          </div>
          {item.split && <div className="mx-1 bg-[#e8e8ed] w-px h-4 shrink-0" />}
        </React.Fragment>
      ))}
      {/* Active Background Slide */}
      <div
        ref={slideRef}
        className="absolute h-8 rounded-lg bg-white shadow-[0_1px_3px_rgba(0,0,0,0.1)] transition-[width,transform] duration-200 ease-out will-change-transform"
        style={{ top: "6px" }}
      />
    </div>
  );
};

export default Tabs;
