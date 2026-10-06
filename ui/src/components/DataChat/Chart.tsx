import { memo, useEffect, useRef, useState } from "react";
import type { EChartsOption, EChartsType } from "echarts";
import {
  WORKSPACE_RESIZE_END_EVENT,
  WORKSPACE_RESIZING_SELECTOR,
  isWorkspaceResizeEventFor,
} from "@/utils/workspaceResize";
import { loadEcharts } from "@/lib/lazy/echarts";

interface ChartProps {
  data: {
    option?: EChartsOption;
  };
}

const Chart: ReactorType.FC<ChartProps> = memo(({ data }) => {
  const { option } = data;
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<EChartsType | null>(null);
  const resizeFrameRef = useRef<number | null>(null);
  const [chartStatus, setChartStatus] = useState<
    "idle" | "loading" | "ready" | "error"
  >("idle");

  useEffect(() => {
    // 图表实例只初始化一次，后续 option 更新采用 notMerge 避免残留旧 series。
    const node = chartRef.current;
    if (!node || !option) {
      setChartStatus("idle");
      return;
    }

    let cancelled = false;
    setChartStatus("loading");
    void loadEcharts()
      .then((echarts) => {
        if (cancelled || !chartRef.current) return;
        if (!chartInstance.current) {
          chartInstance.current = echarts.init(chartRef.current);
        }
        chartInstance.current.setOption(option, { notMerge: true });
        setChartStatus("ready");
      })
      .catch((error) => {
        if (!cancelled) {
          console.error("加载 ECharts 失败", error);
          setChartStatus("error");
        }
      });

    return () => {
      cancelled = true;
    };
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
    <div className="relative min-h-[400px] w-full" aria-label="数据可视化图表">
      <div ref={chartRef} className="h-full min-h-[400px] w-full" />
      {chartStatus === "loading" ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-[var(--chat-text-soft)]">
          加载图表中
        </div>
      ) : null}
      {chartStatus === "error" ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-[var(--chat-text-soft)]">
          图表加载失败
        </div>
      ) : null}
    </div>
  );
});

Chart.displayName = "Chart";

export default Chart;
