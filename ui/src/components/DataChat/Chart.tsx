import { useEffect, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";

interface ChartProps {
  data: {
    option?: EChartsOption;
  };
}

const Chart: ReactorType.FC<ChartProps> = ({ data }) => {
  const { option } = data;
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<echarts.EChartsType | null>(null);

  useEffect(() => {
    // 图表实例只初始化一次，后续 option 更新采用 notMerge 避免残留旧 series。
    if (!chartRef.current) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }

    if (option) {
      chartInstance.current.setOption(option, { notMerge: true });
    }

    const observer = new ResizeObserver(() => {
      // 容器变化时主动 resize，保证抽屉/响应式布局下坐标轴仍可见。
      chartInstance.current?.resize();
    });
    observer.observe(chartRef.current);

    return () => {
      observer.disconnect();
    };
  }, [option]);

  useEffect(() => {
    // 组件销毁时释放 ECharts 实例及其事件监听，避免切换对话后继续占用资源。
    return () => {
      chartInstance.current?.dispose();
      chartInstance.current = null;
    };
  }, []);

  return (
    <div
      ref={chartRef}
      className="min-h-[400px] w-full"
      aria-label="数据可视化图表"
    />
  );
};

export default Chart;
