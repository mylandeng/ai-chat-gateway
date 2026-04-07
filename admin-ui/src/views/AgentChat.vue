<template>
  <div class="nx-agent-chat">
    <!-- 会话侧栏 -->
    <div class="nx-session-sidebar">
      <div class="nx-session-header">
        <el-button type="primary" size="small" @click="newSession" style="width: 100%">
          + 新建会话
        </el-button>
      </div>
      <div class="nx-session-list">
        <div v-for="s in sessions" :key="s.id"
             :class="['nx-session-item', { active: s.id === currentSessionId }]"
             @click="switchSession(s.id)">
          <span class="nx-session-title">{{ s.title || '新会话' }}</span>
          <span class="nx-session-del" @click.stop="handleDeleteSession(s.id)">&times;</span>
        </div>
        <div v-if="sessions.length === 0" class="nx-session-empty">暂无会话</div>
      </div>
      <div class="nx-session-footer">
        <el-button link size="small" @click="$router.push('/agents')"
          style="color: var(--nx-text-muted); font-family: var(--nx-font-mono); font-size: 11px">
          &lt; 返回 Agent 列表
        </el-button>
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="nx-chat-main">
      <div class="nx-chat-header">
        <div class="nx-chat-header-left">
          <span class="nx-agent-avatar-sm">{{ agent.avatar || '🤖' }}</span>
          <span class="nx-section-title">{{ agent.name || 'Agent' }} // 对话</span>
        </div>
        <el-select v-model="model" size="small" style="width: 160px">
          <el-option label="DeepSeek Chat" value="deepseek-chat" />
          <el-option label="通义千问" value="qwen-plus" />
          <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
          <el-option label="Claude Sonnet" value="claude-sonnet" />
        </el-select>
      </div>

      <div class="nx-log-area" ref="messagesRef">
        <div v-if="messages.length === 0" class="nx-empty">
          <div class="nx-empty-icon">{{ agent.avatar || '_' }}</div>
          <div>{{ agent.description || '开始对话' }}</div>
        </div>

        <template v-for="(msg, i) in messages" :key="i">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="nx-log-entry">
            <div class="nx-log-header">
              <span class="nx-log-role user">> 用户</span>
              <span class="nx-log-time">{{ msg.time || '' }}</span>
              <span class="nx-log-rule"></span>
            </div>
            <div class="nx-log-body user-text">{{ msg.content }}</div>
          </div>

          <!-- 工具调用卡片 -->
          <div v-else-if="msg.role === 'tool'" class="nx-tool-card"
               :class="{ expanded: msg.expanded }">
            <div class="nx-tool-header" @click="msg.expanded = !msg.expanded">
              <span class="nx-tool-icon">{{ msg.running ? '⟳' : '⚡' }}</span>
              <span class="nx-tool-name">{{ msg.toolName }}</span>
              <span v-if="msg.running" class="nx-tool-status running">执行中...</span>
              <span v-else class="nx-tool-status done">完成</span>
              <span class="nx-tool-expand">{{ msg.expanded ? '▾' : '▸' }}</span>
            </div>
            <div v-if="msg.expanded" class="nx-tool-detail">
              <div v-if="msg.toolArgs" class="nx-tool-section">
                <div class="nx-tool-label">输入</div>
                <pre class="nx-tool-pre">{{ msg.toolArgs }}</pre>
              </div>
              <div v-if="msg.result" class="nx-tool-section">
                <div class="nx-tool-label">输出</div>
                <pre class="nx-tool-pre">{{ msg.result }}</pre>
              </div>
            </div>
          </div>

          <!-- 助手回复 -->
          <div v-else-if="msg.role === 'assistant'" class="nx-log-entry">
            <div class="nx-log-header">
              <span class="nx-log-role assistant">  {{ agent.avatar || '🤖' }} {{ agent.name || '助手' }}</span>
              <span class="nx-log-time">{{ msg.time || '' }}</span>
              <span class="nx-log-rule"></span>
            </div>
            <div class="nx-log-body">
              <div class="nx-markdown" v-html="renderMd(msg.content)"></div>
              <span v-if="i === messages.length - 1 && isStreaming" class="nx-log-cursor"></span>
            </div>
          </div>
        </template>
      </div>

      <div class="nx-input-bar">
        <span class="nx-input-prompt">></span>
        <el-input v-model="inputMsg" placeholder="输入消息..."
                  @keyup.enter="send" :disabled="isStreaming" />
        <el-button type="primary" @click="send" :loading="isStreaming">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAgent, listAgentSessions, getAgentMessages, deleteAgentSession } from '@/api/agent'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const agentId = computed(() => route.params.id)

const agent = reactive({ name: '', avatar: '', description: '' })
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputMsg = ref('')
const model = ref('')
const isStreaming = ref(false)
const messagesRef = ref(null)

function renderMd(text) { return renderMarkdown(text) }
function now() { return new Date().toLocaleTimeString('en-GB', { hour12: false }) }

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function loadAgent() {
  try {
    const a = await getAgent(agentId.value)
    Object.assign(agent, a)
    if (!model.value) model.value = a.modelId || 'deepseek-chat'
  } catch (e) { router.push('/agents') }
}

async function loadSessions() {
  try { sessions.value = await listAgentSessions(agentId.value) } catch (e) {}
}

async function switchSession(sessionId) {
  currentSessionId.value = sessionId
  try {
    const msgs = await getAgentMessages(agentId.value, sessionId)
    messages.value = msgs.map(m => {
      if (m.role === 'tool') {
        return { role: 'tool', toolName: m.toolName, toolArgs: m.toolInput, result: m.toolOutput, expanded: false, running: false }
      }
      return { role: m.role, content: m.content, time: '' }
    })
    scrollToBottom()
  } catch (e) {}
}

function newSession() { currentSessionId.value = null; messages.value = [] }

async function handleDeleteSession(sessionId) {
  try {
    await deleteAgentSession(agentId.value, sessionId)
    ElMessage.success('已删除')
    if (currentSessionId.value === sessionId) { currentSessionId.value = null; messages.value = [] }
    loadSessions()
  } catch (e) {}
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return
  const question = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: question, time: now() })
  scrollToBottom()

  isStreaming.value = true
  const apiKey = localStorage.getItem('apiKey') || ''

  // Track current assistant message index (will be pushed when first token arrives)
  let assistantIdx = -1

  try {
    const params = new URLSearchParams({ q: question })
    if (model.value) params.set('model', model.value)
    if (currentSessionId.value) params.set('sessionId', currentSessionId.value)
    const url = `/api/agents/${agentId.value}/chat/stream?${params}`
    const resp = await fetch(url, { headers: { 'Authorization': `Bearer ${apiKey}` } })

    if (!resp.ok) {
      messages.value.push({ role: 'assistant', content: resp.status === 401 ? '[错误] 未授权' : `[错误] ${resp.status}`, time: now() })
      isStreaming.value = false; return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      let nextEvent = null
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) { nextEvent = trimmed.slice(6).trim(); continue }
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]') continue

        try {
          const parsed = JSON.parse(data)

          // session event
          if (nextEvent === 'session' && parsed.sessionId) {
            currentSessionId.value = parsed.sessionId
            nextEvent = null; continue
          }

          // tool_call event
          if (nextEvent === 'tool_call') {
            messages.value.push({
              role: 'tool',
              toolName: parsed.toolName || '工具',
              toolArgs: parsed.toolArgs || '',
              result: parsed.result || '',
              expanded: false,
              running: false
            })
            scrollToBottom()
            nextEvent = null; continue
          }

          // token data
          if (parsed.type === 'token' && parsed.content) {
            if (assistantIdx === -1) {
              messages.value.push({ role: 'assistant', content: '', time: now() })
              assistantIdx = messages.value.length - 1
            }
            messages.value[assistantIdx].content += parsed.content
            scrollToBottom()
          }

          // done
          if (parsed.type === 'done') {
            // stream finished
          }

          // error
          if (parsed.type === 'error') {
            if (assistantIdx === -1) {
              messages.value.push({ role: 'assistant', content: '', time: now() })
              assistantIdx = messages.value.length - 1
            }
            messages.value[assistantIdx].content += `\n\n[错误] ${parsed.message}`
          }

          nextEvent = null
        } catch (e) {}
      }
    }
    isStreaming.value = false
    loadSessions()
  } catch (e) {
    isStreaming.value = false
    messages.value.push({ role: 'assistant', content: '[错误] ' + (e.message || '网络故障'), time: now() })
  }
}

onMounted(() => { loadAgent(); loadSessions() })
</script>

<style scoped>
.nx-agent-chat { display: flex; height: calc(100vh - 100px); gap: 0; }

/* Session sidebar */
.nx-session-sidebar {
  width: 220px; background: var(--nx-bg-surface); border: 1px solid var(--nx-border);
  border-radius: 2px; display: flex; flex-direction: column; flex-shrink: 0; margin-right: 16px;
}
.nx-session-header { padding: 12px; border-bottom: 1px solid var(--nx-border); }
.nx-session-list { flex: 1; overflow-y: auto; padding: 6px; }
.nx-session-item {
  padding: 8px 10px; border-radius: 2px; cursor: pointer;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: var(--nx-text-secondary); margin-bottom: 2px;
  border-left: 3px solid transparent; transition: all 150ms;
}
.nx-session-item:hover { background: var(--nx-bg-raised); color: var(--nx-text-primary); }
.nx-session-item.active {
  background: var(--nx-bg-raised); color: var(--nx-accent-amber);
  border-left-color: var(--nx-accent-amber);
}
.nx-session-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.nx-session-del { opacity: 0; color: var(--nx-text-muted); cursor: pointer; font-size: 16px; }
.nx-session-item:hover .nx-session-del { opacity: 1; }
.nx-session-empty {
  text-align: center; color: var(--nx-text-muted); font-family: var(--nx-font-mono);
  font-size: 11px; padding: 20px 0; letter-spacing: 1px;
}
.nx-session-footer { padding: 10px 12px; border-top: 1px solid var(--nx-border); }

/* Chat main */
.nx-chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.nx-chat-header {
  padding: 10px 16px; background: var(--nx-bg-surface); border: 1px solid var(--nx-border);
  border-radius: 2px; display: flex; justify-content: space-between;
  align-items: center; margin-bottom: 12px; flex-shrink: 0;
}
.nx-chat-header-left { display: flex; align-items: center; gap: 8px; }
.nx-agent-avatar-sm { font-size: 20px; }

.nx-log-area { flex: 1; overflow-y: auto; margin-bottom: 12px; }

.nx-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 100%; color: var(--nx-text-muted); font-family: var(--nx-font-mono);
  font-size: 12px; text-transform: uppercase; letter-spacing: 2px; gap: 12px;
}
.nx-empty-icon { font-size: 32px; color: var(--nx-accent-amber); animation: nx-pulse 2s infinite; }

/* Tool call card */
.nx-tool-card {
  margin: 8px 0; border: 1px solid var(--nx-border); border-radius: 2px;
  background: var(--nx-bg-surface); font-size: 12px;
  border-left: 3px solid var(--nx-accent-teal);
}
.nx-tool-header {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  cursor: pointer; transition: background 150ms;
}
.nx-tool-header:hover { background: var(--nx-bg-raised); }
.nx-tool-icon { font-size: 14px; }
.nx-tool-name {
  font-family: var(--nx-font-mono); font-weight: 600; color: var(--nx-text-primary);
  letter-spacing: 0.5px;
}
.nx-tool-status {
  font-family: var(--nx-font-mono); font-size: 10px; text-transform: uppercase;
  letter-spacing: 1px; margin-left: auto;
}
.nx-tool-status.running { color: var(--nx-accent-amber); }
.nx-tool-status.done { color: var(--nx-accent-teal); }
.nx-tool-expand { color: var(--nx-text-muted); font-size: 10px; }

.nx-tool-detail {
  padding: 0 12px 10px; border-top: 1px solid var(--nx-border);
}
.nx-tool-section { margin-top: 8px; }
.nx-tool-label {
  font-family: var(--nx-font-mono); font-size: 10px; color: var(--nx-text-muted);
  text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px;
}
.nx-tool-pre {
  background: var(--nx-bg-deep); border: 1px solid var(--nx-border); border-radius: 2px;
  padding: 8px 10px; font-family: var(--nx-font-mono); font-size: 11px;
  color: var(--nx-text-secondary); white-space: pre-wrap; word-break: break-all;
  max-height: 200px; overflow-y: auto; margin: 0;
}

/* Messages */
.nx-log-entry { margin-bottom: 16px; }
.nx-log-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.nx-log-role {
  font-family: var(--nx-font-mono); font-size: 11px; text-transform: uppercase;
  letter-spacing: 1px; white-space: nowrap;
}
.nx-log-role.user { color: var(--nx-accent-amber); }
.nx-log-role.assistant { color: var(--nx-accent-teal); }
.nx-log-time { font-family: var(--nx-font-mono); font-size: 10px; color: var(--nx-text-muted); }
.nx-log-rule { flex: 1; height: 1px; background: var(--nx-border); margin-left: 8px; }
.nx-log-body { padding-left: 4px; color: var(--nx-text-primary); line-height: 1.7; font-size: 14px; }
.nx-log-body.user-text { color: var(--nx-text-secondary); }

.nx-log-cursor {
  display: inline-block; width: 8px; height: 16px; background: var(--nx-accent-amber);
  vertical-align: text-bottom; animation: nx-blink 1s step-start infinite;
}
@keyframes nx-blink { 50% { opacity: 0; } }

.nx-input-bar { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.nx-input-prompt {
  font-family: var(--nx-font-mono); font-size: 16px; color: var(--nx-accent-amber);
  font-weight: 600; flex-shrink: 0;
}
</style>
