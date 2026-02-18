import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import React, { useEffect, useRef } from "react";

const Tabs = <V extends string | number>(props: GenieType.ControlProps<V> & {
  options: (GenieType.OptionsType & {split?: boolean})[];
  className?: string;
}) => {
  const { value, onChange, className, options } = props;

  const wrapRef = useRef<HTMLDivElement>(null);

  const slideRef = useRef<HTMLDivElement>(null);

  const adjustSlide = useMemoizedFn(() => {
    if (!wrapRef.current) {
      return;
    }
    const activeTab = wrapRef.current.querySelector<HTMLDivElement>(`[item-key="${value}"]`);
    if (!activeTab) {
      return;
    }

    const { width } = activeTab.getBoundingClientRect();

    const left = activeTab.offsetLeft;

    if (!slideRef.current) {
      return;
    }

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

  return <div className={classNames(className, "flex items-center relative box-border gap-4")} ref={wrapRef}>
    {options.map(item => {
      return <React.Fragment key={item.value}>
        <div
          key={item.value}
          className={classNames('pl-16 pr-16 h-32 rounded-[16px] cursor-pointer flex items-center', 'sed-item')}
          item-key={item.value}
          onClick={() => onChange?.(item.value as V)}
        >
          <span>{item.label}</span>
        </div>
        {
          item.split && <div className="m-[8px] mt-0 mb-0 bg-[#dcdfe6] w-1 h-[1em]"></div>
        }
      </React.Fragment>;
    })}
    <div ref={slideRef} className="h-full rounded-[16px] bg-[#f4f4f9] absolute left-0 top-0 transition-all -z-1"></div>
  </div>;
};

export default Tabs;