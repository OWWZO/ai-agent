import Chart from "./Chart";
import SimpleTable from "./SimpleTable";
import Card from "./Card";
import ChatsUtils from "./ChartUtils";
import classNames from "classnames";
import { useState, useMemo } from "react";

/**
 * 图形切换Bar
 * @param props
 * @returns
 */
const TypeBar: GenieType.FC<{ currentType: string; chartCfg: Record<string, any>; onChange?: (val: string) => void }> = (props) => {
  const _chartTypes: Record<string, any>[] = [
    { type: "line", icon: "icon-zhexian" },
    { type: "bar", icon: "icon-zhuzhuang" },
    { type: "hbar", icon: "icon-tiaoxing" },
    { type: "pie", icon: "icon-shanxing" },
    { type: "table", icon: "icon-biaoge" },
  ];

  const { currentType, chartCfg, onChange } = props;
  const [showQueryArgs, setShowQueryArgs] = useState<boolean>(true);

  // 显示切换按钮
  const showType = useMemo(() => {
    const { dimCols, measureCols, dataList } = chartCfg;
    return dimCols?.length === 1 && measureCols?.length > 0 && dataList?.length > 1;
  }, [chartCfg]);

  // 维度显示条件
  const queryDims = useMemo(() => {
    const { dimCols, columnList } = chartCfg;
    return (dimCols || []).map((d: string) => {
      const findCol = columnList.find((n: any) => n.guid === d || n.col === d);
      return findCol?.name || d;
    });
  }, [chartCfg]);

  // 指标条件
  const queryMeas = useMemo(() => {
    const { measureCols, columnList } = chartCfg;
    return (measureCols || []).map((d: string) => {
      const findCol = columnList.find((n: any) => n.guid === d || n.col === d);
      return findCol?.name || d;
    });
  }, [chartCfg]);

  // 筛选条件
  const queryFils = useMemo(() => {
    const { filters } = chartCfg;
    return (filters || []).map((f: Record<string, any>) => {
      if (f.operator === "OR") {
        const _subList = (f.subFilters || []).map((s: Record<string, any>) => {
          return `${s.name}(${s.optName}${s.val?.replace(/^\%+/g, "").replace(/\%+$/g, "") || ""})`;
        });
        return _subList.join(" 或 ");
      }
      return `${f.name}(${f.optName}${f.val?.replace(/^\%+/g, "").replace(/\%+$/g, "") || ""})`;
    });
  }, [chartCfg]);

  // 计算公式
  const calcShow = useMemo(() => {
    const { overwriteSource = {}, overwriteCalc } = chartCfg;
    let _hasCalc = overwriteCalc;
    const _keys = Object.keys(overwriteSource);
    if (_hasCalc && _keys.length > 0) {
      _hasCalc = _hasCalc.replace(/^\$\{/, "").replace(/\}$/, "");
      _keys.forEach((k) => {
        const _reg = new RegExp(k, "g");
        _hasCalc = _hasCalc.replace(_reg, " " + overwriteSource[k] + " ");
      });
    }
    return _hasCalc || "";
  }, [chartCfg]);

  return (
    <>
      <div className="mb-[10px] flex justify-start items-center w-full">
        {/* 按钮切换组 */}
        {showType && (
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
          {queryDims.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-zhibiao text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">维度</span>
              <div className="flex gap-[4px] flex-wrap">
                {queryDims.map((item: any, i: number) => {
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
          {queryMeas.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-weidu text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">指标</span>
              <div className="flex gap-[4px] flex-wrap">
                {queryMeas.map((item: any, i: number) => {
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
          {queryFils.length > 0 && (
            <div className="flex items-baseline">
              <i className="font_family icon-shaixuan1 text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">筛选</span>
              <div className="flex gap-[4px] flex-wrap">
                {queryFils.map((item: any, i: number) => {
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
          {chartCfg.overwriteCalc && (
            <div className="flex items-baseline">
              <i className="font_family icon-bianliang text-[12px]"></i>
              <span className="mr-[8px] ml-[4px] w-[25px] whitespace-nowrap">公式</span>
              <div className="flex gap-[4px] flex-wrap">
                <div className="p-[0] pl-[8px] pr-[8px] rounded-[4px] text-[#c13ddb] bg-[#f9ecfb]">{calcShow}</div>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
};

const DataChat: GenieType.FC<{
  data?: Record<string, any>;
}> = (props) => {
  const { data } = props;
  const chartCfg: Record<string, any> = typeof data === "object" ? data : {};
  const [currentType, setCurrentType] = useState<string>(ChatsUtils.checkChartType(chartCfg));

  const transConfig = useMemo(() => {
    chartCfg.chartSuggest = currentType;
    return ChatsUtils.transConfig(chartCfg);
  }, [currentType]);

  return (
    <div className="w-full flex flex-col items-center max-w-[1200px] mt-[24px] bg-[#fff] p-[15px] rounded-[12px]">
      {/* 图形切换Bar */}
      <TypeBar currentType={currentType} chartCfg={chartCfg} onChange={(t) => setCurrentType(t)} />
      {/* 图形渲染 */}
      <div className="w-full flex flex-col items-center border rounded-[8px] border-[#e9e9f0] p-[10px]">
        {transConfig.chartType === "kpiGroup" && <Card data={transConfig} />}
        {transConfig.chartType === "table" && <SimpleTable data={transConfig} />}
        {ChatsUtils.defaultConfig.chartTypes.includes(transConfig.chartType) && <Chart data={transConfig} />}
      </div>
    </div>
  );
};

export default DataChat;
