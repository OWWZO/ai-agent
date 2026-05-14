import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import VisitorBootstrapScreen from "./VisitorBootstrapScreen";

describe("VisitorBootstrapScreen", () => {
  it("展示独立加载界面且不出现浏览器识别文案", () => {
    const html = renderToStaticMarkup(<VisitorBootstrapScreen />);

    expect(html).toContain("正在进入工作台");
    expect(html).toContain("准备你的 AI 协作环境");
    expect(html).not.toContain("识别当前浏览器");
  });
});
