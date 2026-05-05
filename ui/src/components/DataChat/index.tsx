import Chart from "./Chart";
import SimpleTable from "./SimpleTable";
import Card from "./Card";
import classNames from "classnames";
import { useState, useMemo } from "react";
import {
  buildChartConfig,
  resolveChartType,
  type DataChatSourceConfig,
} from "./chartConfig";
import { defaultChartPresets } from "./chartPresets";
import { buildQuerySummary } from "./querySummary";

/**
 * 图形切换Bar
 * @param props
 * @returns
 */
const TypeBar: ReactorType.FC<{
  currentType: string;
  chartCfg: DataChatSourceConfig;
  onChange?: (val: string) => void;
}> = (props) => {
  const _chartTypes: Record<string, any>[] = [
    { type: "line", icon: "icon-zhexian" },
    { type: "bar", icon: "icon-zhuzhuang" },
    { type: "hbar", icon: "icon-tiaoxing" },
    { type: "pie", icon: "icon-shanxing" },
    { type: "table", icon: "icon-biaoge" },
  ];

  const { currentType, chartCfg, onChange } = props;
  const [showQueryArgs, setShowQueryArgs] = useState<boolean>(true);
  const summary = useMemo(() => buildQuerySummary(chartCfg), [chartCfg]);

  return (
    <>
      <div className="mb-[10px] flex justify-start items-center w-full">
        {/* 按钮切换组 */}
        {summary.showTypeSwitch && (
          <div className="p-[2px] border border-[#dcdee0] rounded-[4px] flex bg-[#f8f8f9] mr-[10px]">
            {_chartTypes.map((item, index) => {
              return (
                <div
                  key={index}
                  className={classNames("p-[2px] pl-[8px] pr-[8px]", {
                    "bg-[white]": currentType === item.type,
                    "cursor-pointer": currentType !== item.type,
                    "cursor-default": currentType === item.type,
                  })}
                  onClick={() => onChange?.(item.type)}
                >
                  <i className={classNames("font_family", { [item.icon]: true })}></i>
                </div>
              );
            })}
          </div>
        )}
        {/* 收起展开按钮 */}
        {
          <div
            className="query_arguments cursor-pointer border border-[#dcdee0] rounded-[4px] pl-[12px] pr-[12px] pt-[3px] pb-[3px]"
            onClick={() => setShowQueryArgs(!showQueryArgs)}
          >
            <span>分析参数</span>
            <i className={classNames("font_family", { "icon-zhankai": showQueryArgs, "icon-shouqi": !showQueryArgs })}></i>
          </div>
        }
      </div>
      {showQueryArgs && (
        <div className="mb-[15px] mt-[10px] w-full leading-[24px] text-[12px] text-[#6a6a6a] flex flex-col gap-y-[10px]">
          {/* 维度行 */}
          {summary.dims.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-zhibiao text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">维度</span>
              <div className="flex gap-[4px] flex-wrap">
                {summary.dims.map((item, i) => {
                  return (
                    <div key={i} className="p-[0] pl-[8px] pr-[8px] rounded-[4px] text-[#4a5fe8] bg-[#edeffd]">
                      {item}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          {/* 指标行 */}
          {summary.measures.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-weidu text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">指标</span>
              <div className="flex gap-[4px] flex-wrap">
                {summary.measures.map((item, i) => {
                  return (
                    <div key={i} className="p-[0] pl-[8px] pr-[8px] rounded-[4px] text-[#2fbc44] bg-[#eaf8ec]">
                      {item}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          {/* 筛选行 */}
          {summary.filters.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-shaixuan1 text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">筛选</span>
              <div className="flex gap-[4px] flex-wrap">
                {summary.filters.map((item, i) => {
                  return (
                    <div key={i} className="p-[0] pl-[8px] pr-[8px] rounded-[4px] text-[#8031f5] bg-[#f2eafe]">
                      {item}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          {/* 计算公式 */}
          {summary.formula && (
            <div className="flex items-baseline">
              <i className="font_family icon-bianliang text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">公式</span>
              <div className="flex gap-[4px] flex-wrap">
                <div className="p-[0] pl-[8px] pr-[8px] rounded-[4px] text-[#c13ddb] bg-[#f9ecfb]">
                  {summary.formula}
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
};

const DataChat: ReactorType.FC<{
  data?: DataChatSourceConfig;
}> = (props) => {
  const { data } = props;
  const chartCfg = typeof data === "object" && data ? data : {};
  const [currentType, setCurrentType] = useState<string>(resolveChartType(chartCfg));

  const transConfig = useMemo(() => {
    return buildChartConfig({
      ...chartCfg,
      chartSuggest: currentType,
    });
  }, [chartCfg, currentType]);

  return (
    <div className="w-full flex flex-col items-center max-w-[1200px] mt-[24px] bg-[#fff] p-[15px] rounded-[12px]">
      {/* 图形切换Bar */}
      <TypeBar currentType={currentType} chartCfg={chartCfg} onChange={(t) => setCurrentType(t)} />
      {/* 图形渲染 */}
      <div className="w-full flex flex-col items-center border rounded-[8px] border-[#e9e9f0] p-[10px]">
        {transConfig.chartType === "kpiGroup" && <Card data={transConfig} />}
        {transConfig.chartType === "table" && <SimpleTable data={transConfig} />}
        {defaultChartPresets.chartTypes.includes(transConfig.chartType as never) && <Chart data={transConfig} />}
      </div>
    </div>
  );
};

export default DataChat;
