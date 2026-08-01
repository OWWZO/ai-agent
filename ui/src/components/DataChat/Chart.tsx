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
    if (!chartRef.current) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }

    if (option) {
      chartInstance.current.setOption(option, { notMerge: true });
    }

    const observer = new ResizeObserver(() => {
      chartInstance.current?.resize();
    });
    observer.observe(chartRef.current);

    return () => {
      observer.disconnect();
    };
  }, [option]);

  useEffect(() => {
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
