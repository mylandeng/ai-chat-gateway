<template>
  <div class="nx-dashboard">
    <!-- API Key 无效提示 -->
    <el-alert v-if="apiKeyInvalid" title="请在左侧面板输入有效的 API Key 后查看数据 "
      type="warning" show-icon :closable="false" style="margin-bottom: 20px" />

    <!-- 统计卡片 -->
    <div class="nx-stats-row">
      <div v-for="stat in stats" :key="stat.label" class="nx-stat-card nx-panel">
        <div class="nx-stat-value">{{ stat.value }}</div>
        <div class="nx-stat-label">{{ stat.label }}</div>
        <div class="nx-stat-indicator" :style="{ background: stat.color }"></div>
      </div>
    </div>

    <!-- 用量趋势 -->
    <div class="nx-panel nx-chart-panel">
      <div class="nx-panel-header">
        <span>Token 用量趋势</span>
        <span class="nx-panel-sub">近 7 天</span>
      </div>
      <div ref="trendChartRef" class="nx-chart"></div>
    </div>

    <!-- 底部两栏 -->
    <div class="nx-chart-row">
      <div class="nx-panel nx-chart-panel">
        <div class="nx-panel-header">模型分布</div>
        <div ref="modelChartRef" class="nx-chart"></div>
      </div>
      <div class="nx-panel nx-chart-panel">
        <div class="nx-panel-header">Key 用量排行</div>
        <div ref="keyChartRef" class="nx-chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, inject } from 'vue'
import * as echarts from 'echarts'
import { getSummary, getDailyUsage, getModelStats, getKeyStats } from '@/api/usage'

function getEchartsTheme() {
  return document.documentElement.getAttribute('data-theme') === 'light' ? 'nexus-light' : 'nexus'
}

const stats = ref([
  { label: '今日调用', value: '-', color: 'var(--nx-accent-amber)' },
  { label: '今日 Token', value: '-', color: 'var(--nx-signal-blue)' },
  { label: '错误数', value: '-', color: 'var(--nx-accent-rose)' },
  { label: '平均耗时', value: '-', color: 'var(--nx-accent-teal)' },
])

const apiKeyInvalid = ref(false)
const trendChartRef = ref(null)
const modelChartRef = ref(null)
const keyChartRef = ref(null)
let charts = []
// 存储 chart options 以便主题切换时重建
let chartConfigs = []

function getLast7Days() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  return {
    start: start.toISOString().split('T')[0],
    end: end.toISOString().split('T')[0]
  }
}

function isAuthError(e) {
  return e?.response?.status === 401
}

function buildCharts() {
  charts.forEach(c => c.dispose())
  charts = []
  const theme = getEchartsTheme()
  for (const { ref: domRef, option } of chartConfigs) {
    if (!domRef.value) continue
    const c = echarts.init(domRef.value, theme)
    c.setOption(option)
    charts.push(c)
  }
}

function onThemeChange() {
  buildCharts()
}

onMounted(async () => {
  try {
    const summary = await getSummary()
    stats.value[0].value = summary.callCount || 0
    stats.value[1].value = (summary.totalTokens || 0).toLocaleString()
    stats.value[2].value = summary.errorCount || 0
    stats.value[3].value = Math.round(summary.avgDuration || 0) + 'ms'
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true; return }
  }

  const { start, end } = getLast7Days()

  // 趋势图
  try {
    const daily = await getDailyUsage(start, end)
    chartConfigs.push({
      ref: trendChartRef,
      option: {
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: daily.map(d => d.date) },
        yAxis: { type: 'value', name: 'Token' },
        series: [{
          type: 'line', data: daily.map(d => d.totalTokens),
          smooth: true,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(245, 158, 11, 0.25)' },
              { offset: 1, color: 'rgba(245, 158, 11, 0.02)' }
            ])
          },
          itemStyle: { color: '#f59e0b' },
          lineStyle: { width: 2 }
        }],
        animationDuration: 800,
        animationEasing: 'cubicOut'
      }
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true; return }
  }

  // 模型分布
  try {
    const models = await getModelStats(start, end)
    chartConfigs.push({
      ref: modelChartRef,
      option: {
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['45%', '70%'],
          data: models.map(m => ({ name: m.model, value: m.totalTokens })),
          label: { fontSize: 11, fontFamily: "'IBM Plex Mono', monospace" },
          emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
        }],
        animationDuration: 800
      }
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true; return }
  }

  // Key 排行
  try {
    const keys = await getKeyStats(start, end)
    chartConfigs.push({
      ref: keyChartRef,
      option: {
        tooltip: {},
        xAxis: { type: 'value', name: 'Token' },
        yAxis: { type: 'category', data: keys.map(k => k.keyId?.substring(0, 10)), inverse: true },
        series: [{
          type: 'bar', data: keys.map(k => k.totalTokens),
          itemStyle: { color: '#14b8a6', borderRadius: [0, 2, 2, 0] },
          barMaxWidth: 20
        }],
        animationDuration: 800
      }
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true }
  }

  buildCharts()
  window.addEventListener('nx-theme-change', onThemeChange)
})

onUnmounted(() => {
  charts.forEach(c => c.dispose())
  window.removeEventListener('nx-theme-change', onThemeChange)
})
</script>

<style scoped>
.nx-dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.nx-stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.nx-stat-card {
  padding: 20px;
  position: relative;
  overflow: hidden;
}
.nx-stat-card .nx-stat-value {
  font-family: var(--nx-font-mono);
  font-size: 32px;
  font-weight: 600;
  color: var(--nx-accent-amber);
  line-height: 1.1;
}
.nx-stat-card .nx-stat-label {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  color: var(--nx-text-secondary);
  margin-top: 8px;
}
.nx-stat-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  opacity: 0.5;
}

.nx-chart-panel {
  padding: 0;
}
.nx-panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--nx-border);
  font-family: var(--nx-font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  color: var(--nx-text-secondary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nx-panel-sub {
  font-size: 10px;
  color: var(--nx-text-muted);
}
.nx-chart {
  height: 280px;
  padding: 8px 12px;
}

.nx-chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
</style>
