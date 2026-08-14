import { describe, expect, it } from "vitest";
import {
  defaultConceptDemo,
  estimateConceptLabelWidth,
  estimateConceptNodeSize,
  layoutConceptFlow,
  normalizeConceptSteps,
  resolveConceptScene,
  wrapConceptLabel,
} from "./conceptDemoModel";

describe("conceptDemoModel", () => {
  it("resolves scene aliases", () => {
    expect(resolveConceptScene("formula_transform")).toBe("formula");
    expect(resolveConceptScene("FLOW")).toBe("flow");
  });

  it("normalizes steps from strings and objects", () => {
    const steps = normalizeConceptSteps([
      "概述",
      {
        title: "深入",
        caption: "说明",
        duration: 3000,
        highlight: ["n1", "e1"],
        badge: "x→y",
      },
    ]);
    expect(steps).toHaveLength(2);
    expect(steps[0].title).toBe("概述");
    expect(steps[1].duration).toBe(3000);
    expect(steps[1].highlight).toEqual(["n1", "e1"]);
  });

  it("provides non-empty defaults per scene", () => {
    for (const scene of ["flow", "stack", "tree", "formula", "compare", "sequence"] as const) {
      const d = defaultConceptDemo(scene);
      expect(d.steps.length).toBeGreaterThan(1);
    }
  });

  it("keeps short flows on one row", () => {
    const layout = layoutConceptFlow([
      {
        id: "a",
        label: "请求",
      },
      {
        id: "b",
        label: "网关",
      },
      {
        id: "c",
        label: "服务",
      },
    ]);
    const ys = new Set(layout.boxes.map((b) => b.y));
    expect(ys.size).toBe(1);
    expect(layout.boxes.every((b) => b.w >= 56)).toBe(true);
  });

  it("wraps long flows instead of squeezing into one row", () => {
    const nodes = Array.from({ length: 10 }, (_, i) => ({
      id: `n${i}`,
      label: `步骤${i + 1}`,
    }));
    const layout = layoutConceptFlow(nodes);
    const ys = new Set(layout.boxes.map((b) => b.y));
    expect(ys.size).toBeGreaterThan(1);
    expect(layout.height).toBeGreaterThan(160);
    const boxes = layout.boxes;
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        const a = boxes[i];
        const b = boxes[j];
        const overlapX = Math.abs(a.x - b.x) < (a.w + b.w) / 2 - 1;
        const overlapY = Math.abs(a.y - b.y) < (a.h + b.h) / 2 - 1;
        expect(overlapX && overlapY).toBe(false);
      }
    }
  });

  it("sizes CJK labels wider than latin", () => {
    expect(estimateConceptLabelWidth("负载均衡器网关节点")).toBeGreaterThan(
      estimateConceptLabelWidth("Gateway")
    );
  });

  it("wraps long mixed labels instead of overflowing one line", () => {
    const label = "API Gateway 鉴权·限流·路由";
    const lines = wrapConceptLabel(label, 140);
    expect(lines.length).toBeGreaterThan(1);
    expect(lines.join("")).toContain("Gateway");
    const size = estimateConceptNodeSize(label);
    expect(size.h).toBeGreaterThan(44);
    expect(size.w).toBeLessThanOrEqual(188);
    expect(size.lines.length).toBe(lines.length);
  });

  it("keeps long-label nodes from overlapping after wrap", () => {
    const nodes = [
      "Client 浏览器 / App",
      "DNS / LB 域名解析",
      "API Gateway 鉴权·限流·路由",
      "Auth / IAM Token 校验",
      "Backend Service 业务逻辑",
      "Data Store DB / Cache",
    ].map((label, i) => ({ id: `n${i}`, label }));
    const layout = layoutConceptFlow(nodes, { maxRowWidth: 720 });
    const boxes = layout.boxes;
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        const a = boxes[i];
        const b = boxes[j];
        const overlapX = Math.abs(a.x - b.x) < (a.w + b.w) / 2 - 1;
        const overlapY = Math.abs(a.y - b.y) < (a.h + b.h) / 2 - 1;
        expect(overlapX && overlapY).toBe(false);
      }
    }
  });

  it("keeps wrap order left-to-right", () => {
    const nodes = Array.from({ length: 8 }, (_, i) => ({
      id: `n${i}`,
      label: `步骤${i + 1}`,
    }));
    const layout = layoutConceptFlow(nodes, { maxRowWidth: 420 });
    const first = layout.boxById.get("n0");
    const second = layout.boxById.get("n1");
    expect(first && second && second.x > first.x).toBe(true);
    const wrapped = layout.boxes.filter((b) => b.y > (first?.y || 0) + 8);
    expect(wrapped.length).toBeGreaterThan(0);
    expect(wrapped[0].id).not.toBe("n7");
  });

  it("fits seven compact nodes on a wide row", () => {
    const nodes = [
      "客户端",
      "API网关",
      "鉴权/限流",
      "路由转发",
      "订单服务",
      "数据库",
      "响应返回",
    ].map((label, i) => ({
      id: `n${i}`,
      label,
    }));
    const layout = layoutConceptFlow(nodes, { maxRowWidth: 1300 });
    expect(new Set(layout.boxes.map((b) => b.y)).size).toBe(1);
  });
});
