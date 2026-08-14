import { describe, expect, it } from "vitest";
import {
  evalLabExpr,
  formatLabNumber,
  interpolateTemplate,
  normalizeParams,
} from "./parametricMath";

describe("parametricMath", () => {
  it("evaluates pythagoras expr", () => {
    expect(evalLabExpr("sqrt(a*a + b*b)", { a: 3, b: 4 })).toBeCloseTo(5, 6);
  });

  it("supports ^ and pi", () => {
    expect(evalLabExpr("a^2", { a: 3 })).toBe(9);
    expect(evalLabExpr("2*pi*r", { r: 1 })).toBeCloseTo(2 * Math.PI, 6);
  });

  it("rejects unsafe identifiers", () => {
    expect(Number.isNaN(evalLabExpr("window", {}))).toBe(true);
    expect(Number.isNaN(evalLabExpr("constructor", {}))).toBe(true);
    expect(Number.isNaN(evalLabExpr("a; alert(1)", { a: 1 }))).toBe(true);
  });

  it("normalizes params and interpolates svg placeholders", () => {
    const params = normalizeParams([
      { id: "a", value: 3, min: 1, max: 10 },
      { name: "b", label: "边 b", value: 4 },
    ]);
    expect(params.map((p) => p.id)).toEqual(["a", "b"]);
    expect(
      interpolateTemplate('<circle r="{{a}}" cx="$b"/>', { a: 3, b: 4 }, {})
    ).toBe('<circle r="3" cx="4"/>');
  });

  it("formats numbers", () => {
    expect(formatLabNumber(5, "fixed:2")).toBe("5.00");
    expect(formatLabNumber(NaN)).toBe("—");
  });
});
