<template>
  <div class="nx-chat-page">
    <!-- 工具栏 -->
    <div class="nx-toolbar nx-panel">
      <div class="nx-toolbar-left">
        <span class="nx-section-title">MCP 工具测试</span>
      </div>
      <div class="nx-toolbar-right">
        <el-select v-model="selectedPreset" size="small" style="width: 200px" @change="onPresetChange">
          <el-option v-for="p in presets" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
        <el-input v-model="serverUrl" size="small" placeholder="MCP 服务器地址 (SSE)" style="width: 360px" clearable />
        <el-button size="small" type="primary" @click="fetchTools">获取工具</el-button>
        <el-button size="small" @click="clearLog">清空</el-button>
      </div>
    </div>

    <!-- 工具列表 -->
    <div v-if="tools.length > 0" class="nx-tool-list">
      <div
        v-for="t in tools" :key="t.name"
        :class="['nx-tool-card', { active: activeTool === t.name }]"
        @click="selectTool(t)"
      >
        <span class="nx-tool-name">{{ t.name }}</span>
        <span class="nx-tool-desc">{{ t.description }}</span>
      </div>
    </div>

    <!-- 调用表单 -->
    <div v-if="activeTool" class="nx-call-panel nx-panel">
      <div class="nx-call-header">
        <span class="nx-call-title">调用 {{ activeTool }}</span>
        <el-button size="small" type="success" @click="callTool" :loading="isCalling">执行</el-button>
      </div>
      <div class="nx-params">
        <div v-for="p in activeParams" :key="p.name" class="nx-param-row">
          <label>{{ p.name }} <span v-if="p.required" class="nx-required">*</span></label>
          <el-input v-model="paramValues[p.name]" size="small" :placeholder="p.description || p.type" />
        </div>
      </div>
    </div>

    <!-- 日志区 -->
    <div class="nx-log-area" ref="logRef">
      <div v-if="logEntries.length === 0" class="nx-empty">
        <div class="nx-empty-icon">_</div>
        <div>连接 MCP 服务器 // 选择工具 // 开始测试</div>
      </div>
      <div v-for="(entry, i) in logEntries" :key="i" class="nx-log-entry">
        <div class="nx-log-header">
          <span :class="['nx-log-role', entry.type]">
            {{ entry.type === 'system' ? '> 系统' : entry.type === 'result' ? '  输出' : '  错误' }}
          </span>
          <span class="nx-log-time">{{ entry.time }}</span>
          <span class="nx-log-rule"></span>
        </div>
        <div class="nx-log-body">
          <pre v-if="entry.type !== 'system'" class="nx-log-json">{{ entry.content }}</pre>
          <span v-else>{{ entry.content }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { listMcpPresets, listMcpTools, callMcpTool } from '@/api/mcp'

const serverUrl = ref('')
const selectedPreset = ref('')
const presets = ref([])
const tools = ref([])
const activeTool = ref('')
const activeParams = ref([])
const paramValues = ref({})
const isCalling = ref(false)
const logEntries = ref([])
const logRef = ref(null)

const now = () => new Date().toLocaleTimeString('en-GB', { hour12: false })

function addLog(type, content) {
  logEntries.value.push({ type, content, time: now() })
  nextTick(() => {
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  })
}

onMounted(async () => {
  try {
    presets.value = await listMcpPresets()
    if (presets.value.length > 0) {
      selectedPreset.value = presets.value[0].name
      serverUrl.value = presets.value[0].serverUrl
      activeParams.value = presets.value[0].tools
    }
  } catch (e) {
    addLog('error', '加载预置 MCP 服务失败: ' + e.message)
  }
})

function onPresetChange(name) {
  const p = presets.value.find(x => x.name === name)
  if (p) {
    serverUrl.value = p.serverUrl
    tools.value = []
    activeTool.value = ''
    activeParams.value = p.tools || []
    addLog('system', '切换预置: ' + p.name + ' (' + p.description + ')')
  }
}

async function fetchTools() {
  if (!serverUrl.value.trim()) return
  addLog('system', '连接 MCP 服务器: ' + serverUrl.value)
  try {
    const result = await listMcpTools(serverUrl.value)
    tools.value = result.tools || []
    addLog('system', '获取到 ' + tools.value.length + ' 个工具')
  } catch (e) {
    addLog('error', '获取工具失败: ' + e.message)
  }
}

function selectTool(tool) {
  activeTool.value = tool.name
  // 从预置参数或工具 inputSchema 解析参数
  const preset = presets.value.find(p => p.name === selectedPreset.value)
  const presetParams = preset?.tools?.find(t => t.name === tool.name)?.parameters
  if (presetParams) {
    activeParams.value = presetParams
  } else if (tool.inputSchema?.properties) {
    const required = tool.inputSchema.required || []
    activeParams.value = Object.entries(tool.inputSchema.properties).map(([k, v]) => ({
      name: k,
      type: v.type || 'string',
      description: v.description || '',
      required: required.includes(k)
    }))
  }
  paramValues.value = {}
}

async function callTool() {
  if (!activeTool.value) return
  isCalling.value = true

  // 构建有效的参数对象
  const args = {}
  for (const p of activeParams.value) {
    if (paramValues.value[p.name]) args[p.name] = paramValues.value[p.name]
  }

  addLog('system', '调用 ' + activeTool.value + ' → ' + JSON.stringify(args))

  try {
    const reader = await callMcpTool(serverUrl.value, activeTool.value, args)
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()
        try {
          const parsed = JSON.parse(data)
          if (parsed.type === 'log') {
            addLog('system', parsed.message)
          } else if (parsed.type === 'result') {
            addLog('result', JSON.stringify(parsed.data, null, 2))
          } else if (parsed.type === 'error') {
            addLog('error', parsed.message)
          }
        } catch (e) { /* ignore */ }
      }
    }
  } catch (e) {
    addLog('error', '调用失败: ' + e.message)
  } finally {
    isCalling.value = false
  }
}

function clearLog() {
  logEntries.value = []
}
</script>

<style scoped>
.nx-chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  gap: 0;
}
.nx-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  flex-shrink: 0;
}
.nx-toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.nx-tool-list {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.nx-tool-card {
  padding: 8px 14px;
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
  transition: border-color .2s;
}
.nx-tool-card:hover, .nx-tool-card.active {
  border-color: var(--nx-accent-amber);
}
.nx-tool-name {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-accent-amber);
}
.nx-tool-desc {
  font-size: 11px;
  color: var(--nx-text-muted);
}

.nx-call-panel {
  padding: 14px 16px;
  margin: 0 0 12px 0;
  flex-shrink: 0;
}
.nx-call-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.nx-call-title {
  font-family: var(--nx-font-mono);
  font-size: 13px;
  color: var(--nx-accent-amber);
}
.nx-params {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.nx-param-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nx-param-row label {
  font-size: 11px;
  font-family: var(--nx-font-mono);
  color: var(--nx-text-secondary);
  white-space: nowrap;
  min-width: 100px;
  text-align: right;
}
.nx-required {
  color: #e74c3c;
}

.nx-log-area {
  flex: 1;
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  padding: 20px;
  overflow-y: auto;
  margin: 12px 0;
}
.nx-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--nx-text-muted);
  font-family: var(--nx-font-mono);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 2px;
  gap: 12px;
}
.nx-empty-icon {
  font-size: 32px;
  color: var(--nx-accent-amber);
  animation: nx-pulse 2s infinite;
}
@keyframes nx-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.nx-log-entry {
  margin-bottom: 16px;
}
.nx-log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.nx-log-role {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}
.nx-log-role.system { color: var(--nx-text-secondary); }
.nx-log-role.result { color: #27ae60; }
.nx-log-role.error { color: #e74c3c; }
.nx-log-time {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  color: var(--nx-text-muted);
}
.nx-log-rule {
  flex: 1;
  border-bottom: 1px dashed var(--nx-border);
}
.nx-log-body {
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-text-main);
  line-height: 1.5;
}
.nx-log-json {
  margin: 0;
  padding: 10px;
  background: rgba(0,0,0,.2);
  border-radius: 2px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 11px;
}
</style>
