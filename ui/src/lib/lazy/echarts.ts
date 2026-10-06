export type EChartsModule = typeof import("echarts");

let echartsPromise: Promise<EChartsModule> | undefined;

export function loadEcharts(): Promise<EChartsModule> {
  return (echartsPromise ??= import("echarts"));
}
