import { memo, useEffect, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";
import {
  WORKSPACE_RESIZE_END_EVENT,
  WORKSPACE_RESIZING_SELECTOR,
  isWorkspaceResizeEventFor,
} from "@/utils/workspaceResize";

interface ChartProps {
  data: {
    option?: EChartsOption;
  };
}

const Chart: ReactorType.FC<ChartProps> = memo(({ data }) => {
  const { option } = data;
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<echarts.EChartsType | null>(null);
  const resizeFrameRef = useRef<number | null>(null);

  useEffect(() => {
    // 图表实例只初始化一次，后续 option 更新采用 notMerge 避免残留旧 series。
    if (!chartRef.current) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }

    if (option) {
      chartInstance.current.setOption(option, { notMerge: true });
    }
  }, [option]);

  useEffect(() => {
    const node = chartRef.current;
    if (!node) return;

    const scheduleResize = () => {
      if (node.closest(WORKSPACE_RESIZING_SELECTOR)) return;
      if (resizeFrameRef.current !== null) return;
      resizeFrameRef.current = requestAnimationFrame(() => {
        resizeFrameRef.current = null;
        chartInstance.current?.resize();
      });
    };
    const observer = new ResizeObserver(scheduleResize);
    observer.observe(node);
    const handleWorkspaceResizeEnd = (event: Event) => {
      if (isWorkspaceResizeEventFor(event, node)) {
        scheduleResize();
      }
    };
    document.addEventListener(WORKSPACE_RESIZE_END_EVENT, handleWorkspaceResizeEnd);

    return () => {
      observer.disconnect();
      document.removeEventListener(
        WORKSPACE_RESIZE_END_EVENT,
        handleWorkspaceResizeEnd
      );
      if (resizeFrameRef.current !== null) {
        cancelAnimationFrame(resizeFrameRef.current);
        resizeFrameRef.current = null;
      }
    };
  }, []);

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
});

Chart.displayName = "Chart";

export default Chart;
