import type { EChartsOption } from "echarts";

import { defaultChartPresets } from "./chartPresets";

export type DataChatChartType =
  | "line"
  | "bar"
  | "hbar"
  | "pie"
  | "table"
  | "kpiGroup";

export interface DataChatColumn {
  name: string;
  guid?: string;
  borderColor?: string;
  col?: string;
  dataType?: string | null;
  order?: number | null;
}

export interface DataChatFilter {
  operator?: string;
  name?: string;
  optName?: string;
  val?: string;
  subFilters?: DataChatFilter[];
}

export type DataChatRow = Record<string, unknown> & {
  key?: number;
};

export interface DataChatSourceConfig {
  chartSuggest?: string;
  dimCols?: string[];
  measureCols?: string[];
  columnList?: DataChatColumn[];
  dataList?: DataChatRow[];
  filters?: DataChatFilter[];
  overwriteCalc?: string;
  overwriteSource?: Record<string, string>;
}

export interface DataChatTableColumn {
  title: string;
  dataIndex: string;
  key: string;
}

export interface DataChatKpiItem {
  label: string;
  value: unknown;
  showValue: unknown;
}

export interface DataChatTransformedConfig {
  chartType: DataChatChartType | string;
  option?: EChartsOption;
  columnList?: DataChatTableColumn[];
  dataList?: DataChatRow[];
  kpiList?: DataChatKpiItem[];
}

type ChartDatum = {
  value: unknown;
  showValue: unknown;
};

function clonePlainObject<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? {})) as T;
}

function findLastMatchedIndex(values: number[], matcher: (value: number) => boolean) {
  for (let index = values.length - 1; index >= 0; index -= 1) {
    if (matcher(values[index])) {
      return index;
    }
  }
  return -1;
}

function findColumn(columns: DataChatColumn[], guid: string) {
  return columns.find((item) => item.guid === guid);
}

function resolveTableColumnKey(column: DataChatColumn) {
  return column.guid || column.col || "";
}

function resolveKpiColumnKey(column: DataChatColumn) {
  return column.guid || column.borderColor || "";
}

function formatSeriesData(dataList: DataChatRow[], key: string): ChartDatum[] {
  return dataList.map((item) => ({
    value: item[key],
    showValue: item[`${key}_format`],
  }));
}

function buildAxisTooltip(
  params: Array<{
    seriesName: string;
    name: string;
    color: string;
    data: ChartDatum;
  }>
) {
  const htmlParts: string[] = [];
  params.forEach((series, index) => {
    if (index === 0) {
      htmlParts.push(`<div>${series.name}</div>`);
    }
    htmlParts.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
          <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${series.color};"></div>
          <div style="color: #6a6a6a">${series.seriesName}</div>
          <div style="color: #181818; flex: 1; text-align: end;">
            ${series.data.showValue}
          </div>
        </div>`);
  });
  return htmlParts.join("");
}

function attachHorizontalZoom(option: Record<string, unknown>, labels: unknown[]) {
  if (labels.length > 10) {
    option.dataZoom = [
      {
        type: "slider",
        show: true,
        brushSelect: false,
        height: 25,
        showDetail: false,
        startValue: labels.length - 11,
        endValue: labels.length - 1,
      },
    ];
  }
}

function formatData(cfg: DataChatSourceConfig) {
  const { dataFormat } = defaultChartPresets;
  const dataList = cfg.dataList || [];
  dataList.forEach((row) => {
    Object.keys(row).forEach((key) => {
      if (key.endsWith("_format")) {
        return;
      }

      let value = typeof row[key] === "undefined" || row[key] === null ? "-" : row[key];
      if (!isNaN(Number(value)) && value !== "-") {
        const [config, format] =
          dataFormat.indexOf("|") > -1 ? dataFormat.split("|") : ["{}", dataFormat];
        let formatStr = "";
        const {
          numberLevels = [],
          numberLevelType,
          fillZero,
        } = JSON.parse(config || "{}") as {
          numberLevels?: number[];
          numberLevelType?: number;
          fillZero?: boolean;
        };

        if (numberLevelType === 1) {
          const formatArr = JSON.parse(format) as string[];
          const valueStr = Math.abs(Number(value)).toString();
          const integerText = valueStr.split(".").shift() || "";
          const index = findLastMatchedIndex(
            numberLevels,
            (item) => integerText.length + 1 > item
          );
          formatStr = formatArr[index === -1 ? 0 : index];
        } else {
          formatStr = format;
        }

        try {
          const formatter = new Function("v", `return ${formatStr}`);
          let decimalLength = 0;
          const splitArr = `${value}`.split(".");
          if (splitArr.length === 2) {
            decimalLength = splitArr[1].length;
          }
          if (decimalLength === 0 || !isNaN(Number(value))) {
            value = formatter(value);
          } else {
            value =
              formatter(Number(value) * Math.pow(10, decimalLength)) /
              Math.pow(10, decimalLength);
          }
        } catch (error) {
          console.log(error);
        }

        const [integerPart, decimalPart] = `${value}`.split(".");
        if (decimalPart) {
          const unit = `${decimalPart}`.split("").reduce((result, char) => {
            if (isNaN(Number(char))) {
              result += char;
            }
            return result;
          }, "");
          if (fillZero === false) {
            const [, trimmed = ""] = Number.parseFloat(`0.${decimalPart}`)
              .toString()
              .split(".");
            value = `${trimmed ? `${integerPart}.${trimmed}` : integerPart}${unit}`;
          }
        }

        row[`${key}_format`] = value;
      }
    });
  });
}

function initTable(cfg: DataChatSourceConfig) {
  const columnList = cfg.columnList || [];
  const dataList = cfg.dataList || [];
  return {
    columnList: columnList.map((column) => ({
      title: column.name,
      dataIndex: resolveTableColumnKey(column),
      key: resolveTableColumnKey(column),
    })),
    dataList: dataList.map((item, index) => ({
      ...item,
      key: index,
    })),
  };
}

function initChartOption(
  cfg: DataChatSourceConfig,
  chartType: DataChatChartType
): EChartsOption | Record<string, unknown> {
  const dimCols = cfg.dimCols || [];
  const measureCols = cfg.measureCols || [];
  const dataList = cfg.dataList || [];
  const columnList = cfg.columnList || [];
  const common = clonePlainObject(defaultChartPresets.templateCommon);

  if (chartType === "line") {
    const typeOption = clonePlainObject(defaultChartPresets.templateline);
    const option = Object.assign({}, common, typeOption) as Record<string, unknown>;
    const dimColumn = findColumn(columnList, dimCols[0])!;
    option.legend = {
      ...(option.legend as Record<string, unknown>),
      data: [{ name: dimColumn.name }],
    };
    option.xAxis = {
      ...(option.xAxis as Record<string, unknown>),
      data: dataList.map((item) => item[dimCols[0]]),
    };
    option.series = measureCols.map((measureKey) => ({
      type: "line",
      name: findColumn(columnList, measureKey)!.name,
      data: formatSeriesData(dataList, measureKey),
      smooth: true,
      symbol: "circle",
      symbolSize: 10,
      showSymbol: false,
      itemStyle: {
        borderWidth: 2,
        borderColor: "#fff",
      },
      columnId: measureKey,
      label: {},
    }));
    attachHorizontalZoom(option, (option.xAxis as { data?: unknown[] }).data || []);
    option.tooltip = {
      ...(option.tooltip as Record<string, unknown>),
      formatter: buildAxisTooltip,
    };
    return option;
  }

  if (chartType === "bar") {
    const typeOption = clonePlainObject(defaultChartPresets.templatebar);
    const option = Object.assign({}, common, typeOption) as Record<string, unknown>;
    const dimColumn = findColumn(columnList, dimCols[0])!;
    option.legend = {
      ...(option.legend as Record<string, unknown>),
      data: [{ name: dimColumn.name }],
    };
    option.xAxis = {
      ...(option.xAxis as Record<string, unknown>),
      data: dataList.map((item) => item[dimCols[0]]),
    };
    option.series = measureCols.map((measureKey) => ({
      type: "bar",
      name: findColumn(columnList, measureKey)!.name,
      label: {
        fontWeight: "bold",
        color: "#1b1b1b",
        fontSize: "12px",
      },
      data: formatSeriesData(dataList, measureKey),
      barMaxWidth: 32,
      itemStyle: {
        borderRadius: 4,
      },
      emphasis: {
        label: {
          show: false,
        },
      },
    }));
    attachHorizontalZoom(option, (option.xAxis as { data?: unknown[] }).data || []);
    option.tooltip = {
      ...(option.tooltip as Record<string, unknown>),
      formatter: buildAxisTooltip,
    };
    return option;
  }

  if (chartType === "hbar") {
    const typeOption = clonePlainObject(defaultChartPresets.templatehbar);
    const option = Object.assign({}, common, typeOption) as Record<string, unknown>;
    const dimColumn = findColumn(columnList, dimCols[0])!;
    option.legend = {
      ...(option.legend as Record<string, unknown>),
      data: [{ name: dimColumn.name }],
    };
    option.yAxis = {
      ...(option.yAxis as Record<string, unknown>),
      data: dataList.map((item) => item[dimCols[0]]),
    };
    option.series = measureCols.map((measureKey) => ({
      name: findColumn(columnList, measureKey)!.name,
      data: formatSeriesData(dataList, measureKey),
      label: {
        fontWeight: "bold",
        color: "#1b1b1b",
        fontSize: "12px",
      },
      type: "bar",
      barMaxWidth: 32,
      itemStyle: {
        borderRadius: 4,
      },
      labelLayout: {
        hideOverlap: true,
      },
      emphasis: {
        label: {
          show: false,
        },
      },
    }));

    const yAxisData = (option.yAxis as { data?: unknown[] }).data || [];
    if (yAxisData.length > 10) {
      option.dataZoom = [
        {
          type: "slider",
          show: true,
          brushSelect: false,
          showDetail: false,
          startValue: yAxisData.length - 11,
          endValue: yAxisData.length - 1,
          width: 25,
          yAxisIndex: 0,
          left: "auto",
          right: 5,
        },
      ];
    }
    option.tooltip = {
      ...(option.tooltip as Record<string, unknown>),
      formatter: buildAxisTooltip,
    };
    return option;
  }

  if (chartType === "pie") {
    const typeOption = clonePlainObject(defaultChartPresets.templatepie);
    const option = Object.assign({}, common, typeOption) as Record<string, unknown>;
    const dimColumn = findColumn(columnList, dimCols[0])!;
    option.series = [
      {
        name: dimColumn.name,
        type: "pie",
        radius: ["0%", "55%"],
        label: {
          position: "outer",
          alignTo: "edge",
          bleedMargin: 5,
          distanceToLabelLine: 10,
        },
        data: dataList.map((item) => ({
          name: item[dimCols[0]],
          value: item[measureCols[0]],
          showValue: item[`${measureCols[0]}_format`],
        })),
      },
    ];
    option.tooltip = {
      ...(option.tooltip as Record<string, unknown>),
      formatter: (series: {
        seriesName: string;
        name: string;
        color: string;
        data: ChartDatum;
        percent: number;
      }) => {
        const htmlParts: string[] = [];
        htmlParts.push(`<div>${series.name}</div>`);
        htmlParts.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
        <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${series.color};"></div>
        <div style="color: #6a6a6a">${series.seriesName}</div>
        <div style="color: #181818; flex: 1; text-align: end;">
          ${series.data.showValue} (${series.percent}%)
        </div>
      </div>`);
        return htmlParts.join("");
      },
    };
    return option;
  }

  return {};
}

export function resolveChartType(cfg: DataChatSourceConfig): DataChatChartType {
  let chartType: DataChatChartType = "table";
  const dimCols = cfg.dimCols || [];
  const measureCols = cfg.measureCols || [];
  const dataList = cfg.dataList || [];
  const columnList = cfg.columnList || [];

  if (dimCols.length === 1 && measureCols.length > 0 && dataList.length > 1) {
    if (findColumn(columnList, dimCols[0])?.dataType === "DATE") {
      chartType = "line";
    } else if (findColumn(columnList, measureCols[0])?.order !== null) {
      chartType = "hbar";
    } else if (dataList.length < 6) {
      chartType = "pie";
    } else if (dataList.length >= 6) {
      chartType = "bar";
    }
  } else if (cfg.overwriteCalc) {
    chartType = "kpiGroup";
  } else if (
    dataList.length === 1 &&
    columnList.length > 0 &&
    columnList.findIndex((column) => isNaN(Number(dataList[0][column.guid || ""]))) === -1 &&
    columnList.length <= 8
  ) {
    chartType = "kpiGroup";
  }

  return chartType;
}

export function buildChartConfig(chartCfg: DataChatSourceConfig): DataChatTransformedConfig {
  const nextConfig = clonePlainObject(chartCfg);
  formatData(nextConfig);
  const chartType = nextConfig.chartSuggest || "table";
  const result: DataChatTransformedConfig = {
    chartType,
  };

  if (defaultChartPresets.chartTypes.includes(chartType as never)) {
    result.option = initChartOption(nextConfig, chartType as DataChatChartType);
  }

  if (chartType === "table") {
    const { columnList, dataList } = initTable(nextConfig);
    result.columnList = columnList;
    result.dataList = dataList;
  }

  if (chartType === "kpiGroup") {
    const columnList = nextConfig.columnList || [];
    const dataList = nextConfig.dataList || [];
    const firstRow = dataList[0] || {};
    result.kpiList = columnList.map((column) => {
      const key = resolveKpiColumnKey(column);
      return {
        label: column.name,
        value: firstRow[key],
        showValue: firstRow[`${key}_format`],
      };
    });
  }

  return result;
}
