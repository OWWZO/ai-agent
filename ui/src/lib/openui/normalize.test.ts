import { describe, expect, it } from "vitest";

import { normalizeOpenUiProgram } from "./normalize";

describe("normalizeOpenUiProgram", () => {
  it("rewrites the slash form of @Set", () => {
    expect(normalizeOpenUiProgram(`b = Button("7", @//set/($display, 7))`)).toBe(
      `b = Button("7", @Set($display, 7))`
    );
  });

  it("rewrites the slash form of @Reset and keeps every argument", () => {
    expect(normalizeOpenUiProgram(`c = Button("C", @//reset/($display, $stored, $op))`)).toBe(
      `c = Button("C", @Reset($display, $stored, $op))`
    );
  });

  it("rewrites every step inside Action([...])", () => {
    const source = `d = Button("÷", Action([@//set/($stored, $display), @//set/($op, "÷")]))`;
    expect(normalizeOpenUiProgram(source)).toBe(
      `d = Button("÷", Action([@Set($stored, $display), @Set($op, "÷")]))`
    );
  });

  it("tolerates casing and stray whitespace", () => {
    expect(normalizeOpenUiProgram(`b = Button("7", @// SET / ($display, 7))`)).toBe(
      `b = Button("7", @Set($display, 7))`
    );
  });

  it("rewrites the other action steps too", () => {
    expect(normalizeOpenUiProgram(`b = Button("Go", @//openurl/("https://a.test"))`)).toBe(
      `b = Button("Go", @OpenUrl("https://a.test"))`
    );
    expect(normalizeOpenUiProgram(`b = Button("Ask", @//toassistant/("more"))`)).toBe(
      `b = Button("Ask", @ToAssistant("more"))`
    );
  });

  it("returns null when nothing looks malformed", () => {
    expect(normalizeOpenUiProgram(`b = Button("7", @Set($display, 7))`)).toBeNull();
    expect(normalizeOpenUiProgram("root = Col([t])\nt = Title(\"hi\")")).toBeNull();
  });

  it("leaves an unknown step name untouched", () => {
    // 宁可漏纠，也不要把没见过的写法改成别的意思。
    expect(normalizeOpenUiProgram(`b = Button("x", @//whatever/($a, 1))`)).toBeNull();
  });

  it("leaves a bare @// with no call parens untouched", () => {
    expect(normalizeOpenUiProgram(`t = Text("@//set/ 用法")`)).toBeNull();
  });
});

describe("normalizeOpenUiProgram — oversized Text size", () => {
  it("clamps a size only Title supports down to lg", () => {
    // 不换组件：换 Title 会丢掉 weight / tabularNums 这些参数，计算器那种等宽数字更要紧。
    expect(normalizeOpenUiProgram(`t = Text($display + "", "3xl", null, "semibold", false, false, true)`)).toBe(
      `t = Text($display + "", "lg", null, "semibold", false, false, true)`
    );
  });

  it("leaves Title alone, which does support 3xl", () => {
    expect(normalizeOpenUiProgram(`t = Title("¥12.4M", "3xl")`)).toBeNull();
  });

  it("leaves a legal Text size alone", () => {
    expect(normalizeOpenUiProgram(`t = Text("hi", "md", "muted")`)).toBeNull();
  });

  it("does not mistake a comma inside the content string for an argument separator", () => {
    expect(normalizeOpenUiProgram(`t = Text("a, b", "2xl")`)).toBe(`t = Text("a, b", "lg")`);
  });

  it("sees past a nested call in the content argument", () => {
    expect(normalizeOpenUiProgram(`t = Text(@Join(items, ", "), "xl")`)).toBe(
      `t = Text(@Join(items, ", "), "lg")`
    );
  });
});
