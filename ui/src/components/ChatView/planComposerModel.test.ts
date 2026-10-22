import { describe, expect, it } from "vitest";
import {
  buildComposerPlanModel,
  findLatestPlanApproval,
  isPlanApprovalApproved,
  pickPlanApprovalFields,
} from "./planComposerModel";

describe("planComposerModel", () => {
  it("treats approved decisions as hidden plan summaries", () => {
    expect(isPlanApprovalApproved("approved")).toBe(true);
    expect(isPlanApprovalApproved("decided")).toBe(true);
    expect(isPlanApprovalApproved("decided", false)).toBe(false);
    expect(isPlanApprovalApproved("rejected", false)).toBe(false);
  });

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
      planFilePath: ".reactor/plan.md",
      status: "pending",
    });
  });

  it("reads absolute planFilePath through live SSE extra resultMap wrapping", () => {
    const planFilePath =
      "D:\\Java Code\\ai-agent\\Cute-agent\\cute-tool\\skilloutput\\session-1788672643180-455\\.reactor\\plan.md";
    const task = {
      messageType: "plan_approval",
      resultMap: {
        agentType: 5,
        messageType: "plan_approval",
        resultMap: {
          messageType: "plan_approval",
          approvalId: "pa_live",
          planContent: "## Plan",
          planFilePath,
          status: "pending",
        },
      },
    } as unknown as CHAT.Task;

    expect(pickPlanApprovalFields(task).planFilePath).toBe(planFilePath);
    expect(buildComposerPlanModel({ taskList: [task] })?.planFilePath).toBe(
      planFilePath
    );
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

describe("findLatestPlanApproval 回退顺序", () => {
  const approval = (id: string, planContent: string) =>
    ({
      messageType: "plan_approval",
      messageId: id,
      resultMap: {
        approvalId: id,
        planContent
      },
    }) as unknown as CHAT.Task;

  it("有正文的最后一个优先，即使后面还有空正文卡", () => {
    const taskList = [
      approval("a", "计划一"),
      approval("b", ""),
      approval("c", "计划三"),
    ];
    expect(findLatestPlanApproval(undefined, taskList)?.messageId).toBe("c");
  });

  it("全都无正文时返回任意最后一个，不是第一个", () => {
    // 这一例是回归测试：合并双循环时若把 last 赋值放在正文判断之前，
    // 反向遍历会让 last 被更早的元素覆盖，返回 'a'。
    const taskList = [approval("a", ""), approval("b", "")];
    expect(findLatestPlanApproval(undefined, taskList)?.messageId).toBe("b");
  });

  it("只有第一个有正文时仍返回它", () => {
    const taskList = [approval("a", "计划一"), approval("b", "")];
    expect(findLatestPlanApproval(undefined, taskList)?.messageId).toBe("a");
  });
});
