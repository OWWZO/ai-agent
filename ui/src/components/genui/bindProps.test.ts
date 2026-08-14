import { describe, expect, it } from "vitest";
import { normalizeParams } from "./parametricMath";
import { derivedScope, resolveBoundProps, resolveBoundValue } from "./bindProps";

describe("bindProps", () => {
  const scope = { a: 3, b: 4, c: 5 };

  it("resolves {{expr}} and $id", () => {
    expect(resolveBoundValue("{{a}}", scope)).toBe(3);
    expect(resolveBoundValue("$b", scope)).toBe(4);
    expect(resolveBoundValue("{{sqrt(a*a+b*b)}}", scope)).toBeCloseTo(5, 6);
    expect(resolveBoundValue("边长 {{a}}", scope)).toBe("边长 3");
  });

  it("resolves nested chart-like props", () => {
    const props = resolveBoundProps(
      {
        title: "a={{a}}",
        series: [{ name: "s", values: ["{{a}}", "{{b}}"] }],
        bind: "a",
      },
      scope
    );
    expect(props.title).toBe("a=3");
    expect(props.series[0].values).toEqual([3, 4]);
    expect(props.bind).toBe("a");
  });

  it("merges derived outputs", () => {
    const params = normalizeParams([{ id: "a", value: 3 }, { id: "b", value: 4 }]);
    const s = derivedScope(params, { a: 3, b: 4 }, [
      { id: "c", expr: "sqrt(a*a+b*b)" },
    ]);
    expect(s.c).toBeCloseTo(5, 6);
  });
});
