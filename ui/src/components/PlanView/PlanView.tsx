import { useState, forwardRef, useImperativeHandle } from "react";
import {
  Plan,
  PlanHeader,
  PlanTitle,
  PlanTrigger,
  PlanContent,
} from "@/components/ai-elements/plan";
import { getStatusIcon } from "./config";

export type PlanViewAction = {
  closePlanView: () => void;
  openPlanView: () => void;
  togglePlanView: () => void;
};

const PlanView: GenieType.FC<{
  plan?: CHAT.Plan;
  ref?: React.Ref<PlanViewAction>;
}> = forwardRef((props, ref) => {
  const { plan } = props;
  const { stages, stepStatus, steps } = plan || {};

  const [open, setOpen] = useState(false);

  useImperativeHandle(ref, () => ({
    openPlanView: () => setOpen(true),
    closePlanView: () => setOpen(false),
    togglePlanView: () => setOpen((v) => !v),
  }));

  const isStreaming = Boolean(plan && !plan.stepStatus?.some((s) => s === "completed"));

  if (!plan) {
    return null;
  }

  return (
    <div className="w-full mt-[16px] px-[16px]">
      <Plan open={open} onOpenChange={setOpen} isStreaming={isStreaming}>
        <PlanHeader>
          <PlanTitle>任务进度</PlanTitle>
          <PlanTrigger />
        </PlanHeader>
        <PlanContent>
          {stages?.map((name, index) => (
            <div key={name} className="flex items-center gap-2 py-1">
              {getStatusIcon(stepStatus?.[index])}
              <div>
                <div className="text-[14px]">{name}</div>
                <div className="text-xs text-muted-foreground">{steps?.[index]}</div>
              </div>
            </div>
          ))}
        </PlanContent>
      </Plan>
    </div>
  );
});

export default PlanView;
