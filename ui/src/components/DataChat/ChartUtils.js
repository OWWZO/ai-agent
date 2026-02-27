const defaultConfig = {
  chartTypes: ['line', 'bar', 'hbar', 'pie'],
  templateline: {
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      className: 'custom_tooltip',
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
        show: true,
        interval: 'auto',
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: 'mid',
      },
      type: 'category',
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
      },
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      triggerEvent: true,
    },
  },
  templatebar: {
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      className: 'custom_tooltip',
      axisPointer: {
        type: 'shadow',
        shadowStyle: {
          color: 'rgba(0,0,0,0.04)',
        },
      },
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
        show: true,
        interval: 'auto',
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: 'mid',
      },
      data: [],
      dataZoom: {},
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
      },
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      triggerEvent: true,
    },
  },
  templatehbar: {
    tooltip: {
      axisPointer: {
        type: 'shadow',
        shadowStyle: {
          color: 'rgba(0,0,0,0.04)',
        },
      },
      trigger: 'axis',
      appendToBody: true,
      className: 'custom_tooltip',
    },
    xAxis: {
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
        show: true,
        interval: 'auto',
        rotate: 0,
        overHide: true,
        overHideLen: 10,
        overHidePos: 'mid',
      },
      triggerEvent: true,
    },
    yAxis: {
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: '#E1E3E6',
        },
      },
      axisLabel: {
        color: '#898E99',
      },
      axisLine: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      axisTick: {
        lineStyle: {
          color: '#E1E3E6',
        },
      },
      data: [],
      inverse: true,
      nameLocation: 'start',
      triggerEvent: true,
    },
    grid: {
      top: '20',
      right: '60',
      bottom: '10',
      left: '4%',
      containLabel: true,
    },
  },
  templatepie: {
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      className: 'custom_tooltip',
    },
  },
  // 公共属性
  templateCommon: {
    color: [
      '#4687F7',
      '#48D7F1',
      '#53B2EE',
      '#82D78F',
      '#FCC55A',
      '#B383F9',
      '#FA86B9',
      '#6E7FED',
      '#CD64E2',
      '#F57546',
      '#898E99',
    ],
    grid: {
      top: '20',
      right: '4%',
      bottom: '60',
      left: '4%',
      containLabel: true,
    },
    legend: {
      type: 'plain',
      itemWidth: 14,
      itemHeight: 12,
      textStyle: {
        color: '#6a6a6a',
        fontSizeNum: 12,
        fontUnit: 'px',
        fontStyle: 'normal',
        fontWeight: 'normal',
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
  dataFormat: '{"type":"number","digits":2,"numberLevel":0,"minus":"default","thousandflag":true,"numberLevelType":0,"numberLevels":[],"fillZero":false}|((Math.round(v*100)/100/1).toFixed(2)+\'\').replace(/\\B(?=(\\d{3})+(?!\\d))/g, \',\').replace(/(?<=\\.\\d*),/g, \'\')'
};

/**
 * chartSuggest
 *  options: line, bar, hbar, pie
 *  kpi
 *  table
 * @param {*} jsonData
 * @param {*} chartSuggest
 * @returns
 */
const transConfig = (cfg) => {
  const rtnObj = {};
  // 格式化数据
  _formatData(cfg);
  // 判断应该用什么图形
  rtnObj.chartType = cfg.chartSuggest;
  // 如果是echart图，则去生成option
  if (defaultConfig.chartTypes.includes(rtnObj.chartType)) {
    rtnObj.option = _initChart(cfg, rtnObj.chartType);
  }
  // 如果是表格，则组装表头和数据数组
  if (rtnObj.chartType === 'table') {
    const { columnList, dataList } = _initTable(cfg);
    rtnObj.columnList = columnList;
    rtnObj.dataList = dataList;
  }
  // 如果是kpi kpiGroup
  if (rtnObj.chartType === 'kpiGroup') {
    const { dataList, columnList } = cfg;
    rtnObj.kpiList = columnList.map((c) => {
      return {
        label: c.name,
        value: dataList[0][c.guid || c.borderColor],
        showValue: dataList[0][(c.guid || c.borderColor) + '_format'],
      };
    });
  }

  return rtnObj;
};

const checkChartType = (cfg) => {
  let chartType = 'table';
  const { dimCols, measureCols, dataList, columnList } = cfg;
  if (dimCols?.length === 1 && measureCols?.length > 0 && dataList?.length > 1) {
    // 维度为日期字段，折线图
    if (columnList.find((c) => c.guid === dimCols[0])?.dataType === 'DATE') {
      chartType = 'line';
    } else if (columnList.find((c) => c.guid === measureCols[0])?.order !== null) {
      // 如果是有序数值，则是条形图
      chartType = 'hbar';
    } else if (dataList?.length < 6) {
      chartType = 'pie';
    } else if (dataList?.length >= 6) {
      chartType = 'bar';
    }
  } else if (cfg.overwriteCalc) {
    chartType = 'kpiGroup';
  } else if (
    dataList?.length === 1 &&
    columnList?.length > 0 &&
    columnList.findIndex((f) => isNaN(dataList[0][f.guid])) === -1 &&
    columnList.length <= 8
  ) {
    // 如果是的单行数据，且列少于8个，且值全部是数字，则按组显示
    chartType = 'kpiGroup';
  }

  return chartType;
};

/**
 * 数据格式化
 * @param {*} cfg
 */
const _formatData = (cfg) => {
  const { dataFormat } = defaultConfig;
  cfg.dataList.forEach((d) => {
    Object.keys(d).forEach((k) => {
      if (k.endsWith('_format')) {
        return;
      }

      let val = (typeof d[k] === 'undefined' || d[k] === null) ? '-' : d[k];
      if (!isNaN(val) && val !== '-') {
        // 兼容非自定义格式化
        const [config, format] = dataFormat.indexOf('|') > -1 ? dataFormat.split('|') : ['{}', dataFormat];
        let formatStr = '';
        const { numberLevels, numberLevelType, fillZero } = JSON.parse(config || '{}');
        if (numberLevelType === 1) {
          const formatArr = JSON.parse(format);
          // 将数字转换为字符串
          const valStr = Math.abs(val).toString();
          // 需要考虑小数情况 截取整数判断 如0.4444
          const numStr = valStr.split('.').shift();
          // 根据数字长度判断级别
          const ind = numberLevels.findLastIndex((n) => numStr.length + 1 > n);
          formatStr = formatArr[ind === -1 ? 0 : ind];
        } else {
          formatStr = format;
        }
        try {
          const func = new Function('v', `return ${formatStr}`);
          let num = 0;
          const splitArr = `${val}`.split('.');
          if (splitArr.length === 2) {
            num = splitArr[1].length;
          }
          if (num === 0 || !isNaN(val)) {
            val = func(val);
          } else {
            // 改为整数再计算，避免溢出
            val = func(val * Math.pow(10, num)) / Math.pow(10, num);
          }
        } catch (e) {
          console.log(e);
        }

        // 处理补0和现实原始值问题；
        const [itg, dcm] = `${val}`.split('.');
        if (dcm) {
          // 获取单位
          const _unit = `${dcm}`.split('').reduce((r, c) => {
            isNaN(c) && (r += c);
            return r;
          }, '');
          // 如果要求不补0，则去掉小数后面的0
          if (fillZero === false) {
            const [, b = ''] = Number(parseFloat('0.' + dcm))
              .toString()
              .split('.');
            val = (b ? `${itg}.${b}` : `${itg}`) + _unit;
          }
        }
        d[k + '_format'] = val;
      }
    })
  })
};

const _initChart = (cfg, chartType) => {
  const { dimCols, measureCols, dataList, columnList } = cfg;
  const common = JSON.parse(JSON.stringify(defaultConfig.templateCommon));

  // 如果是line
  if (chartType === 'line') {
    const typeOption = JSON.parse(JSON.stringify(defaultConfig.templateline));
    const option = Object.assign({}, common, typeOption);
    option.legend.data = [{ name: columnList.find((c) => c.guid === dimCols[0]).name }];
    option.xAxis.data = dataList.map((d) => d[dimCols[0]]);
    option.series = measureCols.map((m) => {
      return {
        type: 'line',
        name: columnList.find((c) => c.guid === m).name,
        data: dataList.map((d) => {
          return {
            value: d[m],
            showValue: d[m + '_format'],
          }
        }),
        smooth: true,
        symbol: 'circle',
        symbolSize: 10,
        showSymbol: false,
        itemStyle: {
          borderWidth: 2,
          borderColor: '#fff',
        },
        columnId: m,
        label: {},
      };
    });
    // x轴 datazoom
    if (option.xAxis.data.length > 10) {
      option.dataZoom = [
        {
          type: 'slider',
          show: true,
          brushSelect: false,
          height: 25,
          showDetail: false,
          startValue: option.xAxis.data.length - 11,
          endValue: option.xAxis.data.length - 1,
        },
      ];
    }
    // 添加tooltip格式化显示数据
    option.tooltip.formatter = (params) => {
      const _htmlArr = [];
      params.forEach((ser, idx) => {
        const { seriesName, name, color, data } = ser;

        if (idx === 0) {
          _htmlArr.push(`<div>${name}</div>`);
        }
        _htmlArr.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
          <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${color};"></div>
          <div style="color: #6a6a6a">${seriesName}</div>
          <div style="color: #181818; flex: 1; text-align: end;">
            ${data.showValue}
          </div>
        </div>`);
      });
      return _htmlArr.join('');
    }

    return option;
  } else if (chartType === 'bar') {
    const typeOption = JSON.parse(JSON.stringify(defaultConfig.templatebar));
    const option = Object.assign({}, common, typeOption);
    option.legend.data = [{ name: columnList.find((c) => c.guid === dimCols[0]).name }];
    option.xAxis.data = dataList.map((d) => d[dimCols[0]]);
    option.series = measureCols.map((m) => {
      return {
        type: 'bar',
        name: columnList.find((c) => c.guid === m).name,
        label: {
          fontWeight: 'bold',
          color: '#1b1b1b',
          fontSize: '12px',
        },
        data: dataList.map((d) => {
          return {
            value: d[m],
            showValue: d[m + '_format'],
          }
        }),
        barMaxWidth: 32,
        itemStyle: {
          borderRadius: 4,
        },
        emphasis: {
          label: {
            show: false,
          },
        },
      };
    });
    // x轴 datazoom
    if (option.xAxis.data.length > 10) {
      option.dataZoom = [
        {
          type: 'slider',
          show: true,
          brushSelect: false,
          height: 25,
          showDetail: false,
          startValue: option.xAxis.data.length - 11,
          endValue: option.xAxis.data.length - 1,
        },
      ];
    }
    // 添加tooltip格式化显示数据
    option.tooltip.formatter = (params) => {
      const _htmlArr = [];
      params.forEach((ser, idx) => {
        const { seriesName, name, color, data } = ser;

        if (idx === 0) {
          _htmlArr.push(`<div>${name}</div>`);
        }
        _htmlArr.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
          <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${color};"></div>
          <div style="color: #6a6a6a">${seriesName}</div>
          <div style="color: #181818; flex: 1; text-align: end;">
            ${data.showValue}
          </div>
        </div>`);
      });
      return _htmlArr.join('');
    }

    return option;
  } else if (chartType === 'hbar') {
    const typeOption = JSON.parse(JSON.stringify(defaultConfig.templatehbar));
    const option = Object.assign({}, common, typeOption);
    option.legend.data = [{ name: columnList.find((c) => c.guid === dimCols[0]).name }];
    option.yAxis.data = dataList.map((d) => d[dimCols[0]]);
    option.series = measureCols.map((m) => {
      return {
        name: columnList.find((c) => c.guid === m).name,
        data: dataList.map((d) => {
          return {
            value: d[m],
            showValue: d[m + '_format'],
          }
        }),
        label: {
          fontWeight: 'bold',
          color: '#1b1b1b',
          fontSize: '12px',
        },
        type: 'bar',
        barMaxWidth: 32,
        itemStyle: {
          borderRadius: 4,
        },
        labelLayout: {
          hideOverlap: true,
        },
        emphasis: {
          label: {
            show: false,
          },
        },
      };
    });

    if (option.yAxis.data.length > 10) {
      option.dataZoom = [
        {
          type: 'slider',
          show: true,
          brushSelect: false,
          showDetail: false,
          startValue: option.yAxis.data.length - 11,
          endValue: option.yAxis.data.length - 1,
          width: 25,
          yAxisIndex: 0,
          left: 'auto',
          right: 5,
        },
      ];
    }
    // 添加tooltip格式化显示数据
    option.tooltip.formatter = (params) => {
      const _htmlArr = [];
      params.forEach((ser, idx) => {
        const { seriesName, name, color, data } = ser;

        if (idx === 0) {
          _htmlArr.push(`<div>${name}</div>`);
        }
        _htmlArr.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
          <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${color};"></div>
          <div style="color: #6a6a6a">${seriesName}</div>
          <div style="color: #181818; flex: 1; text-align: end;">
            ${data.showValue}
          </div>
        </div>`);
      });
      return _htmlArr.join('');
    }

    return option;
  } else if (chartType === 'pie') {
    const typeOption = JSON.parse(JSON.stringify(defaultConfig.templatepie));
    const option = Object.assign({}, common, typeOption);
    option.series = [
      {
        name: columnList.find((c) => c.guid === dimCols[0]).name,
        type: 'pie',
        radius: ['0%', '55%'],
        label: {
          position: 'outer',
          alignTo: 'edge',
          bleedMargin: 5,
          distanceToLabelLine: 10,
        },
        data: dataList.map((d) => {
          return {
            name: d[dimCols[0]],
            value: d[measureCols[0]],
            showValue: d[measureCols[0] + '_format'],
          };
        }),
      },
    ];
    // 添加tooltip格式化显示数据
    option.tooltip.formatter = (ser) => {
      const _htmlArr = [];
      const { seriesName, name, color, data, percent } = ser;
      _htmlArr.push(`<div>${name}</div>`);
      _htmlArr.push(`<div style="display: flex; font-size: 12px; margin-top: 3px;align-items: center;gap: 10px">
        <div style="width: 10px; height: 10px; border-radius: 50%; background-color: ${color};"></div>
        <div style="color: #6a6a6a">${seriesName}</div>
        <div style="color: #181818; flex: 1; text-align: end;">
          ${data.showValue} (${percent}%)
        </div>
      </div>`);
      return _htmlArr.join('');
    }

    return option;
  }
  return {};
};

const _initTable = (cfg) => {
  const { columnList, dataList } = cfg;
  return {
    columnList: columnList.map((c) => {
      return {
        title: c.name,
        dataIndex: c.guid || c.col,
        key: c.guid || c.col,
      };
    }),
    dataList: dataList.map((d, idx) => {
      d.key = idx;
      return d;
    }),
  };
};

export default { transConfig, checkChartType, defaultConfig };
