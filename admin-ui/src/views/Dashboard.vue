<template>
  <div>
    <!-- API Key 无效提示 -->
    <el-alert v-if="apiKeyInvalid" title="请在左下角输入有效的 API Key 后查看数据"
      type="warning" show-icon :closable="false" style="margin-bottom: 20px" />

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover">
          <div style="font-size: 28px; font-weight: bold; color: #1890ff">{{ stat.value }}</div>
          <div style="color: #999; margin-top: 8px">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用量趋势 -->
    <el-card style="margin-bottom: 20px">
      <template #header>Token 用量趋势（最近 7 天）</template>
      <div ref="trendChartRef" style="height: 300px"></div>
    </el-card>

    <!-- 底部两栏 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>模型使用分布</template>
          <div ref="modelChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>Key 用量排行</template>
          <div ref="keyChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getSummary, getDailyUsage, getModelStats, getKeyStats } from '@/api/usage'

const stats = ref([
  { label: '今日调用', value: '-' },
  { label: '今日 Token', value: '-' },
  { label: '错误数', value: '-' },
  { label: '平均耗时', value: '-' },
])

const apiKeyInvalid = ref(false)
const trendChartRef = ref(null)
const modelChartRef = ref(null)
const keyChartRef = ref(null)
let charts = []

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
    const chart1 = echarts.init(trendChartRef.value)
    charts.push(chart1)
    chart1.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: daily.map(d => d.date) },
      yAxis: { type: 'value', name: 'Token' },
      series: [{
        type: 'line', data: daily.map(d => d.totalTokens),
        smooth: true, areaStyle: { opacity: 0.3 }, itemStyle: { color: '#1890ff' }
      }]
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true; return }
  }

  // 模型分布
  try {
    const models = await getModelStats(start, end)
    const chart2 = echarts.init(modelChartRef.value)
    charts.push(chart2)
    chart2.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['40%', '70%'],
        data: models.map(m => ({ name: m.model, value: m.totalTokens }))
      }]
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true; return }
  }

  // Key 排行
  try {
    const keys = await getKeyStats(start, end)
    const chart3 = echarts.init(keyChartRef.value)
    charts.push(chart3)
    chart3.setOption({
      tooltip: {},
      xAxis: { type: 'category', data: keys.map(k => k.keyId?.substring(0, 10)) },
      yAxis: { type: 'value', name: 'Token' },
      series: [{ type: 'bar', data: keys.map(k => k.totalTokens), itemStyle: { color: '#52c41a' } }]
    })
  } catch (e) {
    if (isAuthError(e)) { apiKeyInvalid.value = true }
  }
})

onUnmounted(() => { charts.forEach(c => c.dispose()) })
</script>
