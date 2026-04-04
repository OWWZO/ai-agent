import { formatSecondsToMinutes } from "@/utils";
import { useMemoizedFn, useSafeState } from "ahooks";
import classNames from "classnames";
import { useEffect, useRef } from "react";
import { getStatusIcon } from "./config";

const PlanItem: ReactorType.FC<{
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
    <div
      className={classNames(
        "mt-2 flex w-full items-center gap-3 rounded-xl bg-[var(--chat-surface-soft)]/80 px-3 py-2.5",
        className
      )}
    >
      {getStatusIcon(status)}
      <div className="min-w-0 flex-1">{title}</div>
      {typeof runTime === "number" && status === "in_progress" ? (
        <span className="shrink-0 text-[12px] tabular-nums text-[var(--chat-text-muted)]">
          {formatSecondsToMinutes(runTime)}
          <span className="ml-2 text-[var(--chat-text-soft)]">执行中</span>
        </span>
      ) : null}
    </div>
  );
};

export default PlanItem;