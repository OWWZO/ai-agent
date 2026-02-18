import { formatSecondsToMinutes } from "@/utils";
import { useMemoizedFn, useSafeState } from "ahooks";
import classNames from "classnames";
import { useEffect, useRef } from "react";
import { getStatusIcon } from "./config";

const PlanItem: GenieType.FC<{
  title?: React.ReactNode;
  status?: CHAT.PlanStatus;
}> = (props) => {
  const { title, status, className } = props;
  const [ runTime, setRunTime ] = useSafeState<number>();

  const ref = useRef<{t?: NodeJS.Timeout}>({});

  const updateRunTime = useMemoizedFn((val?: number) => {
    clearTimeout(ref.current.t);
    if (status !== 'in_progress') {
      setRunTime(undefined);
      return;
    }
    setRunTime(val ?? ((runTime || 0) + 1));
    ref.current.t = setTimeout(() => {
      updateRunTime();
    }, 1000);
  });

  useEffect(() => {
    if (status === 'in_progress' && title) {
      updateRunTime(0);
    }
  }, [status, title, updateRunTime]);

  useEffect(() => {
    return () => {
      clearTimeout(ref.current.t);
    };
  }, []);

  return (
    <div className={classNames('w-full rounded-[12px] mt-[8px] flex items-center', className)}>
      <span className="mr-10">
        {getStatusIcon(status)}
      </span>
      { title }
      {typeof runTime === 'number' && status === 'in_progress' && <span className="text-[12px] ml-8 text-[#848581]">
        {formatSecondsToMinutes(runTime)}
        <span className="ml-4">
          执行中
        </span>
      </span>}
    </div>
  );
};

export default PlanItem;