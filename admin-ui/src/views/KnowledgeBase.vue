<template>
  <div class="kb-page">
    <!-- 文档管理区 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>知识库文档</span>
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pdf,.docx,.txt,.md,.csv">
            <el-button type="primary" :loading="uploading">
              <el-icon><Upload /></el-icon> 上传文档
            </el-button>
          </el-upload>
        </div>
      </template>

      <el-table :data="documents" v-loading="loadingDocs" empty-text="暂无文档，请上传" size="small">
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="字符数" width="90" prop="charCount" />
        <el-table-column label="切片数" width="90" prop="chunkCount" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').substring(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- RAG 对话区 -->
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>知识库问答</span>
          <el-select v-model="model" size="small" style="width: 160px">
            <el-option label="DeepSeek Chat" value="deepseek-chat" />
            <el-option label="通义千问" value="qwen-plus" />
            <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
            <el-option label="Claude Sonnet" value="claude-sonnet" />
          </el-select>
        </div>
      </template>

      <!-- 消息列表 -->
      <div class="messages" ref="messagesRef">
        <div v-if="messages.length === 0" style="text-align: center; color: #999; padding: 40px 0">
          上传文档后，在这里提问
        </div>
        <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
          <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="bubble">
            <pre style="white-space: pre-wrap; margin: 0; font-family: inherit">{{ msg.content }}</pre>
            <span v-if="msg.role === 'assistant' && i === messages.length - 1 && isStreaming" class="cursor">|</span>
            <!-- 来源引用 -->
            <div v-if="msg.sources && msg.sources.length" class="sources">
              <div class="sources-title">引用来源：</div>
              <div v-for="(s, si) in msg.sources" :key="si" class="source-item">
                {{ s.fileName }} (相似度: {{ (s.score * 100).toFixed(0) }}%)
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入栏 -->
      <div class="input-bar">
        <el-input v-model="inputMsg" placeholder="基于知识库提问... (Enter 发送)"
          @keyup.enter="send" :disabled="isStreaming" />
        <el-button type="primary" @click="send" :loading="isStreaming" style="margin-left: 12px">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocuments, uploadDocument, deleteDocument } from '@/api/rag'

// ========== 文档管理 ==========
const documents = ref([])
const loadingDocs = ref(false)
const uploading = ref(false)

async function loadDocuments() {
  loadingDocs.value = true
  try {
    documents.value = await getDocuments()
  } catch (e) { /* interceptor handles */ }
  loadingDocs.value = false
}

async function handleUpload(file) {
  uploading.value = true
  try {
    await uploadDocument(file)
    ElMessage.success('上传成功，正在处理...')
    // 轮询刷新状态
    pollDocuments()
  } catch (e) { /* interceptor handles */ }
  uploading.value = false
  return false // 阻止 el-upload 默认行为
}

function pollDocuments() {
  loadDocuments()
  // 每 3 秒刷新一次，直到没有处理中的文档
  const timer = setInterval(async () => {
    await loadDocuments()
    const processing = documents.value.some(d => d.status === 0 || d.status === 1)
    if (!processing) clearInterval(timer)
  }, 3000)
  // 最多轮询 2 分钟
  setTimeout(() => clearInterval(timer), 120000)
}

async function handleDelete(id) {
  try {
    await deleteDocument(id)
    ElMessage.success('已删除')
    loadDocuments()
  } catch (e) { /* interceptor handles */ }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

function statusText(s) {
  const n = Number(s)
  if (n === 0) return '处理中'
  if (n === 1) return '解析完成'
  if (n === 2) return '已就绪'
  if (n === -1) return '失败'
  return '未知'
}

function statusType(s) {
  const n = Number(s)
  if (n === 0 || n === 1) return 'warning'
  if (n === 2) return 'success'
  if (n === -1) return 'danger'
  return 'info'
}

// ========== RAG 对话 ==========
const messages = ref([])
const inputMsg = ref('')
const model = ref('deepseek-chat')
const isStreaming = ref(false)
const messagesRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return

  const question = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: question })
  messages.value.push({ role: 'assistant', content: '', sources: [] })
  scrollToBottom()

  isStreaming.value = true
  const apiKey = localStorage.getItem('apiKey') || ''

  try {
    const url = `/api/rag/stream?q=${encodeURIComponent(question)}&model=${model.value}`
    const resp = await fetch(url, {
      headers: { 'Authorization': `Bearer ${apiKey}` }
    })

    if (!resp.ok) {
      const last = messages.value[messages.value.length - 1]
      last.content = resp.status === 401 ? '请在左下角输入有效的 API Key' : `请求失败 (${resp.status})`
      isStreaming.value = false
      return
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

      let nextIsSources = false
      for (const line of lines) {
        const trimmed = line.trim()
        // SSE event 类型标记
        if (trimmed.startsWith('event:')) {
          nextIsSources = trimmed.replace('event:', '').trim() === 'sources'
          continue
        }
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          if (nextIsSources && Array.isArray(parsed)) {
            last.sources = parsed
            nextIsSources = false
          } else if (parsed.content) {
            last.content += parsed.content
          }
          scrollToBottom()
        } catch (e) { /* ignore non-JSON lines */ }
      }
    }

    isStreaming.value = false
  } catch (e) {
    isStreaming.value = false
    const last = messages.value[messages.value.length - 1]
    if (!last.content) last.content = '请求失败: ' + (e.message || '网络错误')
  }
}

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.kb-page { display: flex; flex-direction: column; height: calc(100vh - 80px); }
.messages { height: 300px; overflow-y: auto; padding: 12px; background: #fafafa; border-radius: 6px; margin-bottom: 12px; }
.message { display: flex; margin-bottom: 14px; gap: 10px; }
.message.user { flex-direction: row-reverse; }
.avatar { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: bold; flex-shrink: 0; }
.message.user .avatar { background: #1890ff; color: #fff; }
.message.assistant .avatar { background: #52c41a; color: #fff; }
.bubble { max-width: 75%; padding: 8px 12px; border-radius: 8px; line-height: 1.6; font-size: 14px; }
.message.user .bubble { background: #e6f7ff; }
.message.assistant .bubble { background: #f6ffed; }
.input-bar { display: flex; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.sources { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #d9d9d9; }
.sources-title { font-size: 12px; color: #999; margin-bottom: 4px; }
.source-item { font-size: 12px; color: #666; padding: 2px 0; }
</style>
