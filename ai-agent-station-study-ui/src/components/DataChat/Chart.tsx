import { useEffect, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";

interface ChartProps {
  data: {
    option?: EChartsOption; // 使用ECharts官方类型定义
  };
}

const Chart: GenieType.FC<ChartProps> = ({ data }) => {
  const { option } = data;
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<echarts.EChartsType | null>(null);

  // 初始化或更新图表
  useEffect(() => {
    // 验证必要条件
    if (!chartRef.current) return;

    // 销毁已存在的实例（避免重复创建）
    if (chartInstance.current) {
      chartInstance.current.dispose();
    }

    try {
      // 初始化图表实例
      chartInstance.current = echarts.init(chartRef.current);

      // 只有当option存在时才设置配置
      if (option) {
        chartInstance.current.setOption(option, true);
      } else {
        console.warn("图表配置项不存在");
      }
    } catch (error) {
      console.error("初始化图表失败:", error);
      chartInstance.current = null;
    }

    // 响应式处理
    const handleResize = () => {
      chartInstance.current?.resize();
    };

    // 监听窗口大小变化（使用被动监听提高性能）
    window.addEventListener("resize", handleResize, { passive: true });

    // 清理函数
    return () => {
      window.removeEventListener("resize", handleResize);
      if (chartInstance.current) {
        chartInstance.current.dispose();
        chartInstance.current = null;
      }
    };
  }, [option]); // 仅当option变化时重新渲染

  return (
    <div
      ref={chartRef}
      className="min-h-[400px] w-full"
      aria-label="数据可视化图表" // 增加可访问性标签
    />
  );
};

export default Chart;
