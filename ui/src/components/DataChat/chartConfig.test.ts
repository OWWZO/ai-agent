import { describe, expect, it } from "vitest";

import { buildChartConfig } from "./chartConfig";

describe("chartConfig", () => {
  it("构建图表配置时不应修改输入对象", () => {
    const input = {
      chartSuggest: "line",
      dimCols: ["dt"],
      measureCols: ["gmv"],
      columnList: [
        {
          guid: "dt",
          name: "日期",
          dataType: "DATE",
        },
        {
          guid: "gmv",
          name: "GMV",
          order: null,
        },
      ],
      dataList: [
        {
          dt: "2026-05-01",
          gmv: 10,
        },
      ],
    };
    const frozen = Object.freeze({ ...input });

    const result = buildChartConfig(frozen);

    expect(result.chartType).toBe("line");
    expect(frozen.chartSuggest).toBe("line");
    expect(frozen.dataList[0]).not.toHaveProperty("gmv_format");
  });
});
