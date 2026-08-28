import { FC, memo, useEffect, useMemo, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";
import {
  WORKSPACE_RESIZE_END_EVENT,
  WORKSPACE_RESIZING_SELECTOR,
  isWorkspaceResizeEventFor,
} from "@/utils/workspaceResize";

type SeriesItem = { name?: string; values?: number[] };

type Props = {
  title?: string;
  chart?: string;
  categories?: string[];
  series?: SeriesItem[];
  height?: number;
  stacked?: boolean;
  showLegend?: boolean;
  showGrid?: boolean;
};

const SERIES_COLORS = ["#0ea5e9", "#10b981", "#8b5cf6", "#f59e0b", "#ef4444", "#64748b"];

const GenUiChart: FC<Props> = memo(
  ({
    title,
    chart = "bar",
    categories = [],
    series = [],
    height = 280,
    stacked,
    showLegend = true,
    showGrid = true,
  }) => {
    const ref = useRef<HTMLDivElement | null>(null);
    const instance = useRef<echarts.EChartsType | null>(null);
    const resizeFrameRef = useRef<number | null>(null);

    const option: EChartsOption = useMemo(() => {
      // option 只依赖可序列化 props；饼图和坐标图分别使用 ECharts 的数据模型。
      const type = (chart || "bar").toLowerCase();
      const cats = categories.length
        ? categories
        : (series[0]?.values || []).map((_, i) => String(i + 1));

      if (type === "pie") {
        const values = series[0]?.values || [];
        const data = cats.map((name, i) => ({
          name,
          value: Number(values[i]) || 0,
          itemStyle: { color: SERIES_COLORS[i % SERIES_COLORS.length] },
        }));
        return {
          color: SERIES_COLORS,
          animationDuration: 600,
          animationEasing: "cubicOut",
          title: title
            ? { text: title, left: "center", top: 8, textStyle: { fontSize: 13, fontWeight: 600 } }
            : undefined,
          tooltip: {
            trigger: "item",
            formatter: "{b}: {c} ({d}%)",
            backgroundColor: "rgba(255,255,255,0.96)",
            borderColor: "#e2e8f0",
            borderWidth: 1,
            textStyle: { color: "#1a1a2e", fontSize: 12 },
          },
          legend: showLegend
            ? {
                orient: "horizontal",
                bottom: 4,
                type: "scroll",
                textStyle: { fontSize: 11, color: "#64748b" },
              }
            : undefined,
          series: [
            {
              type: "pie",
              radius: ["38%", "68%"],
              center: ["50%", title ? "52%" : "48%"],
              data,
              itemStyle: { borderRadius: 4, borderColor: "#fff", borderWidth: 2 },
              label: { fontSize: 11, color: "#475569" },
              emphasis: {
                scale: true,
                scaleSize: 6,
                itemStyle: { shadowBlur: 12, shadowColor: "rgba(0,0,0,0.12)" },
              },
            },
          ],
        };
      }

      const chartType = type === "area" ? "line" : type === "line" || type === "bar" ? type : "bar";
      return {
        color: SERIES_COLORS,
        animationDuration: 500,
        animationEasing: "cubicOut",
        title: title
          ? { text: title, left: "left", top: 4, textStyle: { fontSize: 13, fontWeight: 600 } }
          : undefined,
        tooltip: {
          trigger: "axis",
          backgroundColor: "rgba(255,255,255,0.96)",
          borderColor: "#e2e8f0",
          borderWidth: 1,
          textStyle: { color: "#1a1a2e", fontSize: 12 },
        },
        legend: showLegend
          ? { top: title ? 28 : 4, type: "scroll", textStyle: { fontSize: 11, color: "#64748b" } }
          : undefined,
        grid: {
          left: 44,
          right: 16,
          top: title ? (showLegend ? 56 : 40) : showLegend ? 36 : 20,
          bottom: 32,
          containLabel: false,
        },
        xAxis: {
          type: "category",
          data: cats,
          axisLabel: { fontSize: 11, color: "#64748b" },
          axisLine: { lineStyle: { color: "#e2e8f0" } },
          axisTick: { show: false },
        },
        yAxis: {
          type: "value",
          axisLabel: { fontSize: 11, color: "#64748b" },
          splitLine: showGrid
            ? { lineStyle: { type: "dashed", color: "#e2e8f0" } }
            : { show: false },
          axisLine: { show: false },
        },
        series: series.map((s) => ({
          name: s.name || "series",
          type: chartType as "line" | "bar",
          data: (s.values || []).map((v) => Number(v) || 0),
          smooth: chartType === "line",
          areaStyle: type === "area" ? { opacity: 0.18 } : undefined,
          stack: stacked ? "total" : undefined,
          barMaxWidth: 32,
          itemStyle: chartType === "bar" ? { borderRadius: [4, 4, 0, 0] } : undefined,
          symbolSize: chartType === "line" ? 6 : undefined,
          lineStyle: chartType === "line" ? { width: 2 } : undefined,
          emphasis: { focus: "series" },
        })),
      };
    }, [title, chart, categories, series, stacked, showLegend, showGrid]);

    useEffect(() => {
      if (!ref.current) return;
      // 实例在 DOM 容器上复用，option 更新只替换配置。
      if (!instance.current) {
        instance.current = echarts.init(ref.current);
      }
      instance.current.setOption(option, true);
    }, [option]);

    useEffect(() => {
      const node = ref.current;
      if (!node) return;

      const scheduleResize = () => {
        if (ref.current?.closest(WORKSPACE_RESIZING_SELECTOR)) return;
        if (resizeFrameRef.current !== null) return;
        resizeFrameRef.current = requestAnimationFrame(() => {
          resizeFrameRef.current = null;
          instance.current?.resize();
        });
      };
      window.addEventListener("resize", scheduleResize);
      const handleWorkspaceResizeEnd = (event: Event) => {
        if (isWorkspaceResizeEventFor(event, node)) {
          scheduleResize();
        }
      };
      document.addEventListener(
        WORKSPACE_RESIZE_END_EVENT,
        handleWorkspaceResizeEnd
      );
      const ro =
        typeof ResizeObserver !== "undefined"
          ? new ResizeObserver(scheduleResize)
          : null;
      ro?.observe(node);

      return () => {
        window.removeEventListener("resize", scheduleResize);
        document.removeEventListener(
          WORKSPACE_RESIZE_END_EVENT,
          handleWorkspaceResizeEnd
        );
        ro?.disconnect();
        if (resizeFrameRef.current !== null) {
          cancelAnimationFrame(resizeFrameRef.current);
          resizeFrameRef.current = null;
        }
      };
    }, []);

    useEffect(() => {
      return () => {
        // ECharts 持有 canvas 和事件监听器，组件卸载时必须显式 dispose。
        instance.current?.dispose();
        instance.current = null;
      };
    }, []);

    const empty =
      !series.length ||
      (chart?.toLowerCase() === "pie"
        ? !(series[0]?.values || []).length
        : series.every((s) => !(s.values || []).length));

    if (empty) {
      return (
        <div className="rounded-xl border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface-muted)]/40 px-4 py-8 text-center text-sm text-[var(--chat-text-soft)]">
          No chart data
        </div>
      );
    }

    return (
      <div className="w-full min-w-0 space-y-1">
        <div
          className="w-full min-h-[240px] max-h-[min(52vh,560px)] aspect-[16/9] rounded-xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface)] p-2"
          style={
            height && height !== 280
              ? { minHeight: Math.max(200, height), height, maxHeight: "none", aspectRatio: "auto" }
              : undefined
          }
        >
          <div ref={ref} className="h-full w-full" />
        </div>
      </div>
    );
  }
);

GenUiChart.displayName = "GenUiChart";

export default GenUiChart;
