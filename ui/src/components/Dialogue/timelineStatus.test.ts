import { describe, expect, it } from "vitest";

import {
  isTimelineTaskContainerCompleted,
  shouldShowTimelineGroupCompletedIcon,
} from "./timelineStatus";

describe("timelineStatus", () => {
  it("只把带 task 标题且子工具全部完成的 plansolve 任务组视为完成", () => {
    const completed = shouldShowTimelineGroupCompletedIcon({
      isPlanSolve: true,
      isLastGroup: false,
      loading: false,
      tasks: [
        {
          task: "生成调研报告",
          children: [
            {
              finish: true,
              isFinal: true,
            },
            {
              finish: true,
              isFinal: true,
            },
          ],
        },
      ],
    });

    expect(completed).toBe(true);
  });

  it("react 对话不显示任务完成勾", () => {
    const completed = shouldShowTimelineGroupCompletedIcon({
      isPlanSolve: false,
      isLastGroup: false,
      loading: false,
      tasks: [
        {
          task: "生成调研报告",
          children: [
            {
              finish: true,
              isFinal: true,
            },
          ],
        },
      ],
    });

    expect(completed).toBe(false);
  });

  it("没有真实 task 标题的工具占位组不显示完成勾", () => {
    const completed = shouldShowTimelineGroupCompletedIcon({
      isPlanSolve: true,
      isLastGroup: false,
      loading: false,
      tasks: [
        {
          task: "",
          children: [
            {
              finish: true,
              isFinal: true,
            },
          ],
        },
      ],
    });

    expect(completed).toBe(false);
  });

  it("最后一个仍在流式中的任务组继续显示加载态而不是完成勾", () => {
    const completed = shouldShowTimelineGroupCompletedIcon({
      isPlanSolve: true,
      isLastGroup: true,
      loading: true,
      tasks: [
        {
          task: "生成调研报告",
          children: [
            {
              finish: true,
              isFinal: true,
            },
          ],
        },
      ],
    });

    expect(completed).toBe(false);
  });

  it("子工具未全部结束时，任务容器不算完成", () => {
    const completed = isTimelineTaskContainerCompleted({
      task: "生成调研报告",
      children: [
        {
          finish: true,
          isFinal: true,
        },
        {
          finish: false,
          isFinal: false,
        },
      ],
    });

    expect(completed).toBe(false);
  });
});
