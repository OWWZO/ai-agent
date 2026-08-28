import { describe, expect, it } from "vitest";
import {
  buildComposerPlanModel,
  findLatestPlanApproval,
  pickPlanApprovalFields,
} from "./planComposerModel";

describe("planComposerModel", () => {
  it("picks planContent from nested resultMap", () => {
    const task = {
      messageType: "plan_approval",
      resultMap: {
        resultMap: {
          approvalId: "a1",
          planContent: "## Steps\n1. A",
          planFilePath: ".reactor/plan.md",
          status: "pending",
        },
      },
    } as unknown as CHAT.Task;

    expect(pickPlanApprovalFields(task)).toMatchObject({
      approvalId: "a1",
      planContent: "## Steps\n1. A",
      status: "pending",
    });
  });

  it("prefers latest plan_approval with body for composer", () => {
    const taskList = [
      {
        messageType: "plan_approval",
        resultMap: {
          approvalId: "a2",
          planContent: "final plan",
          status: "pending",
        },
      },
    ] as unknown as CHAT.Task[];

    const latest = findLatestPlanApproval(undefined, taskList);
    const latestMap = latest?.resultMap as unknown as Record<string, unknown> | undefined;
    expect(latestMap?.planContent || latestMap?.approvalId).toBeTruthy();

    const model = buildComposerPlanModel({ taskList });
    expect(model?.source).toBe("plan_approval");
    expect(model?.planContent).toBe("final plan");
    expect(model?.title).toBe("实现计划");
  });

  it("falls back to structured plan stages", () => {
    const model = buildComposerPlanModel({
      structuredPlan: {
        title: "路线",
        notes: [],
        stages: ["探索", "实现"],
        steps: ["探索代码", "改接口"],
        stepStatus: ["completed", "not_started"],
      },
    });
    expect(model?.source).toBe("structured_plan");
    expect(model?.planContent).toContain("探索代码");
  });

  it("does not turn a decided approval back into an approval composer", () => {
    const model = buildComposerPlanModel({
      taskList: [
        {
          messageType: "plan_approval",
          resultMap: {
            approvalId: "a-done",
            planContent: "已批准的计划",
            status: "decided",
          },
        },
      ] as unknown as CHAT.Task[],
    });

    expect(model).toBeNull();
  });

  it("treats finish-without-status as decided, not pending composer", () => {
    const model = buildComposerPlanModel({
      taskList: [
        {
          messageType: "plan_approval",
          finish: true,
          isFinal: true,
          resultMap: {
            approvalId: "a-fin",
            planContent: "收尾计划",
            isFinal: true,
          },
        },
      ] as unknown as CHAT.Task[],
    });

    expect(model).toBeNull();
  });
});
