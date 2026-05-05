export const defaultChartPresets = {
  chartTypes: ["line", "bar", "hbar", "pie"],
  templateline: {
    tooltip: {
      trigger: "axis",
      appendToBody: true,
      className: "custom_tooltip",
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
        show: true,
        interval: "auto",
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: "mid",
      },
      type: "category",
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: "dashed",
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
      },
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      triggerEvent: true,
    },
  },
  templatebar: {
    tooltip: {
      trigger: "axis",
      appendToBody: true,
      className: "custom_tooltip",
      axisPointer: {
        type: "shadow",
        shadowStyle: {
          color: "rgba(0,0,0,0.04)",
        },
      },
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
        show: true,
        interval: "auto",
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: "mid",
      },
      data: [],
      dataZoom: {},
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: "dashed",
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
      },
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      triggerEvent: true,
    },
  },
  templatehbar: {
    tooltip: {
      axisPointer: {
        type: "shadow",
        shadowStyle: {
          color: "rgba(0,0,0,0.04)",
        },
      },
      trigger: "axis",
      appendToBody: true,
      className: "custom_tooltip",
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
        show: true,
        interval: "auto",
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: "mid",
      },
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: "dashed",
          color: "#E1E3E6",
        },
      },
      axisLabel: {
        color: "#898E99",
      },
      axisLine: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      axisTick: {
        lineStyle: {
          color: "#E1E3E6",
        },
      },
      data: [],
      inverse: true,
      nameLocation: "start",
      triggerEvent: true,
    },
    grid: {
      top: "20",
      right: "60",
      bottom: "10",
      left: "4%",
      containLabel: true,
    },
  },
  templatepie: {
    tooltip: {
      trigger: "item",
      appendToBody: true,
      className: "custom_tooltip",
    },
  },
  templateCommon: {
    color: [
      "#4687F7",
      "#48D7F1",
      "#53B2EE",
      "#82D78F",
      "#FCC55A",
      "#B383F9",
      "#FA86B9",
      "#6E7FED",
      "#CD64E2",
      "#F57546",
      "#898E99",
    ],
    grid: {
      top: "20",
      right: "4%",
      bottom: "60",
      left: "4%",
      containLabel: true,
    },
    legend: {
      type: "plain",
      itemWidth: 14,
      itemHeight: 12,
      textStyle: {
        color: "#6a6a6a",
        fontSizeNum: 12,
        fontUnit: "px",
        fontStyle: "normal",
        fontWeight: "normal",
      },
      itemGap: 20,
      itemStyle: {
        borderWidth: 0,
      },
      left: 0,
      top: 0,
      show: false,
    },
  },
  dataFormat:
    "{\"type\":\"number\",\"digits\":2,\"numberLevel\":0,\"minus\":\"default\",\"thousandflag\":true,\"numberLevelType\":0,\"numberLevels\":[],\"fillZero\":false}|((Math.round(v*100)/100/1).toFixed(2)+'').replace(/\\B(?=(\\d{3})+(?!\\d))/g, ',').replace(/(?<=\\.\\d*),/g, '')",
} as const;
