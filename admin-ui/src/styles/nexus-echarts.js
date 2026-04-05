/**
 * NEXUS COMMAND — ECharts Dark Theme
 */
/**
 * NEXUS COMMAND — ECharts Light Theme
 */
export const nexusLightChartTheme = {
  color: ['#d97706', '#0d9488', '#2563eb', '#0891b2', '#e11d48', '#475569', '#7c3aed', '#ea580c'],
  backgroundColor: 'transparent',
  textStyle: {
    color: '#64748b',
    fontFamily: "'IBM Plex Mono', 'JetBrains Mono', Consolas, monospace"
  },
  title: {
    textStyle: { color: '#1e293b', fontSize: 14, fontWeight: 500 },
    subtextStyle: { color: '#64748b' }
  },
  legend: {
    textStyle: { color: '#64748b', fontSize: 11 },
    pageTextStyle: { color: '#64748b' }
  },
  tooltip: {
    backgroundColor: '#ffffff',
    borderColor: '#e2e8f0',
    borderWidth: 1,
    textStyle: { color: '#1e293b', fontSize: 12 },
    extraCssText: 'border-radius: 2px; box-shadow: 0 4px 16px rgba(0,0,0,0.08);'
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(226, 232, 240, 0.6)', type: 'dashed' } },
    nameTextStyle: { color: '#64748b' }
  },
  yAxis: {
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(226, 232, 240, 0.6)', type: 'dashed' } },
    nameTextStyle: { color: '#64748b' }
  },
  line: {
    itemStyle: { borderWidth: 2 },
    lineStyle: { width: 2 },
    symbolSize: 6,
    smooth: true
  },
  bar: {
    itemStyle: { borderRadius: [2, 2, 0, 0] },
    barMaxWidth: 40
  },
  pie: {
    itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
    label: { color: '#64748b', fontSize: 11 }
  },
  categoryAxis: {
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b' },
    splitLine: { show: false }
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#64748b' },
    splitLine: { lineStyle: { color: 'rgba(226, 232, 240, 0.6)', type: 'dashed' } }
  }
}

export const nexusChartTheme = {
  color: ['#f59e0b', '#14b8a6', '#3b82f6', '#06b6d4', '#f43f5e', '#64748b', '#a78bfa', '#fb923c'],
  backgroundColor: 'transparent',
  textStyle: {
    color: '#8899aa',
    fontFamily: "'IBM Plex Mono', 'JetBrains Mono', Consolas, monospace"
  },
  title: {
    textStyle: { color: '#e2e8f0', fontSize: 14, fontWeight: 500 },
    subtextStyle: { color: '#8899aa' }
  },
  legend: {
    textStyle: { color: '#8899aa', fontSize: 11 },
    pageTextStyle: { color: '#8899aa' }
  },
  tooltip: {
    backgroundColor: '#1a2332',
    borderColor: '#2a4a6b',
    borderWidth: 1,
    textStyle: { color: '#e2e8f0', fontSize: 12 },
    extraCssText: 'border-radius: 2px; box-shadow: 0 4px 16px rgba(0,0,0,0.4);'
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    axisLine: { lineStyle: { color: '#1e2d3d' } },
    axisTick: { lineStyle: { color: '#1e2d3d' } },
    axisLabel: { color: '#8899aa', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(30, 45, 61, 0.5)', type: 'dashed' } },
    nameTextStyle: { color: '#8899aa' }
  },
  yAxis: {
    axisLine: { lineStyle: { color: '#1e2d3d' } },
    axisTick: { lineStyle: { color: '#1e2d3d' } },
    axisLabel: { color: '#8899aa', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(30, 45, 61, 0.5)', type: 'dashed' } },
    nameTextStyle: { color: '#8899aa' }
  },
  line: {
    itemStyle: { borderWidth: 2 },
    lineStyle: { width: 2 },
    symbolSize: 6,
    smooth: true
  },
  bar: {
    itemStyle: { borderRadius: [2, 2, 0, 0] },
    barMaxWidth: 40
  },
  pie: {
    itemStyle: { borderColor: '#111827', borderWidth: 2 },
    label: { color: '#8899aa', fontSize: 11 }
  },
  categoryAxis: {
    axisLine: { lineStyle: { color: '#1e2d3d' } },
    axisTick: { show: false },
    axisLabel: { color: '#8899aa' },
    splitLine: { show: false }
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#8899aa' },
    splitLine: { lineStyle: { color: 'rgba(30, 45, 61, 0.5)', type: 'dashed' } }
  }
}
