import type { DataChatFilter, DataChatSourceConfig } from "./chartConfig";

export interface DataChatQuerySummary {
  showTypeSwitch: boolean;
  dims: string[];
  measures: string[];
  filters: string[];
  formula: string;
}

function resolveColumnLabel(chartCfg: DataChatSourceConfig, key: string) {
  const column = (chartCfg.columnList || []).find(
    (item) => item.guid === key || item.col === key
  );
  return column?.name || key;
}

function trimFilterValue(value?: string) {
  return value?.replace(/^\%+/g, "").replace(/\%+$/g, "") || "";
}

function resolveFilterLabel(filter: DataChatFilter) {
  if (filter.operator === "OR") {
    return (filter.subFilters || [])
      .map(
        (item) =>
          `${item.name}(${item.optName}${trimFilterValue(item.val)})`
      )
      .join(" 或 ");
  }
  return `${filter.name}(${filter.optName}${trimFilterValue(filter.val)})`;
}

function resolveFormula(chartCfg: DataChatSourceConfig) {
  const overwriteSource = chartCfg.overwriteSource || {};
  let formula = chartCfg.overwriteCalc || "";
  const keys = Object.keys(overwriteSource);
  if (formula && keys.length > 0) {
    formula = formula.replace(/^\$\{/, "").replace(/\}$/, "");
    keys.forEach((key) => {
      const regExp = new RegExp(key, "g");
      formula = formula.replace(regExp, ` ${overwriteSource[key]} `);
    });
  }
  return formula;
}

export function buildQuerySummary(chartCfg: DataChatSourceConfig): DataChatQuerySummary {
  const dimCols = chartCfg.dimCols || [];
  const measureCols = chartCfg.measureCols || [];
  const dataList = chartCfg.dataList || [];

  return {
    showTypeSwitch: dimCols.length === 1 && measureCols.length > 0 && dataList.length > 1,
    dims: dimCols.map((item) => resolveColumnLabel(chartCfg, item)),
    measures: measureCols.map((item) => resolveColumnLabel(chartCfg, item)),
    filters: (chartCfg.filters || []).map(resolveFilterLabel),
    formula: resolveFormula(chartCfg),
  };
}
