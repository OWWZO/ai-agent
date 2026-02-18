import { useToggle } from "ahooks";
import { Timeline } from "antd";
import classNames from "classnames";
import { useEffect, useRef, forwardRef, useImperativeHandle } from "react";
import PlanItem from "./PlanItem";
import { getStatusIcon } from "./config";
import { throttle } from "lodash";

export type PlanViewAction = {
  /**
   * 打开任务进度面板
   * @returns
   */
  closePlanView: () => void;
  /**
   * 关闭任务进度面板
   * @returns
   */
  openPlanView: () => void;
  /**
   * 反向任务进度面板
   */
  togglePlanView: () => void;
}

const PlanView: GenieType.FC<{
  plan?: CHAT.Plan;
  ref?: React.Ref<PlanViewAction>;
}> = forwardRef((props, ref) => {
  const { plan } = props;

  const { stages, stepStatus, steps } = plan || {};

  const showStageIndex = stages?.reduce((pre, _cur, index, arr) => {
    const curStatus = stepStatus?.[index];
    if (curStatus === 'completed') {
      return Math.min(index + 1, arr.length - 1);
    }
    return pre;
  }, 0) || 0;

  const showStageStatus = stepStatus?.[showStageIndex];
  const showStage = stages?.[showStageIndex];

  const [ showComplete, { toggle, setLeft: closePlanView, setRight: openPlanView} ] = useToggle(false);

  const wrapRef = useRef<HTMLDivElement>(null);

  useImperativeHandle(ref, () => ({
    openPlanView,
    closePlanView,
    togglePlanView: toggle,
    // setPlanView: (plan?: CHAT.Plan) => {
    //   openPlanView();
    //   setTimeout(() => {
    //     const itemRef = wrapRef.current?.querySelector('.plan-item');
    //     if (itemRef) {
    //       let height = (itemRef?.clientHeight || 63) + 32;
    //       wrapRef.current!.style.height = `${height}px`;
    //     }
    //   }, 0);
    // }
  }));

  const generateItem = (show?: boolean) => {
    return <>
      <div className="flex items-center h-32">
        任务进度
        <div className="ml-auto flex items-center text-[#848581]">
          <span className="mr-4 text-[12px]">{showStageIndex + 1} / {stages?.length}</span>
          {/* <UpOutlined   onClick={toggle} /> */}
          <i className={classNames('transition-all font_family icon-shouqi size-16 flex items-center justify-center hover:bg-gray-300 rounded-[4px] cursor-pointer', { 'rotate-z-180': showComplete })} onClick={toggle} />
        </div>
      </div>
      <PlanItem title={showStage} status={showStageStatus} className={classNames({ 'hidden': !show })} />
    </>;
  };

  useEffect(() => {
    const adjustHeight = throttle(() => {
      if (!wrapRef.current || !plan) {
        return;
      }
      // 计算高度，并设置动画效果
      const itemRef = wrapRef.current?.querySelector('.plan-item');
      let height = (itemRef?.clientHeight || 63) + 32;
      if (showComplete) {
        height = wrapRef.current?.scrollHeight;
      }
      wrapRef.current.style.height = `${height}px`;
    }, 30);
    adjustHeight();
    const observer = new ResizeObserver(adjustHeight);
    if (wrapRef.current) {
      observer.observe(wrapRef.current);
    }
    return () => {
      observer.disconnect();
    };
  }, [plan, showComplete]);

  if (!plan) {
    return null;
  }

  return <div className="w-full border-[#e9e9f0] mt-[16px] p-[16px] relative">
    <div className="opacity-0">
      {generateItem(true)}
    </div>
    <div
      className={classNames(
        'w-full rounded-[12px] border-solid border-1 border-[#e9e9f0] mt-[16px] p-[16px] bg-[#fff]',
        'absolute bottom-0 left-0',
        'transition-all duration-300 overflow-hidden',
      )}
      ref={wrapRef}
    >
      <div className="plan-item">
        {generateItem(!showComplete)}
      </div>
      {showComplete && <Timeline
        className={classNames(
          "px-12 pb-0 pt-32 bg-[#f9f9fc] rounded-[6px] transition-all duration-300",
        )}
        items={stages?.map((name, index) => {
          const status = stepStatus?.[index];
          const stepDesc = steps?.[index];

          return {
            dot: getStatusIcon(status),
            children: <div>
              <div className="text-[#80d1ee6]">{name}</div>
              <div className="text-gray text-[12px] text-[#2029459e]">{stepDesc}</div>
            </div>,
            key: name,
          };
        })}
      />}
    </div>
  </div>;
});

export default PlanView;