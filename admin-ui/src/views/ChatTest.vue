<template>
  <div class="chat-page">
    <!-- 工具栏 -->
    <el-card class="toolbar" style="margin-bottom: 16px">
      <el-select v-model="model" style="width: 180px; margin-right: 12px">
        <el-option label="DeepSeek Chat" value="deepseek-chat" />
        <el-option label="通义千问" value="qwen-plus" />
        <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
        <el-option label="Claude Sonnet" value="claude-sonnet" />
      </el-select>
      <el-button @click="clearChat">清空对话</el-button>
    </el-card>

    <!-- 消息列表 -->
    <div class="messages" ref="messagesRef">
      <div v-if="messages.length === 0" style="text-align: center; color: #999; padding: 60px 0">
        选择模型，开始对话
      </div>
      <div v-for="(msg, i) in messages" :key="i"
           :class="['message', msg.role]">
        <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
        <div class="bubble">
          <pre style="white-space: pre-wrap; margin: 0; font-family: inherit">{{ msg.content }}</pre>
          <span v-if="msg.role === 'assistant' && i === messages.length - 1 && isStreaming"
            class="cursor">|</span>
        </div>
      </div>
    </div>

    <!-- 输入栏 -->
    <div class="input-bar">
      <el-input v-model="inputMsg" placeholder="输入消息... (Enter 发送)"
        @keyup.enter="send" :disabled="isStreaming" size="large" />
      <el-button type="primary" @click="send" :loading="isStreaming" size="large"
        style="margin-left: 12px">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const messages = ref([])
const inputMsg = ref('')
const model = ref('deepseek-chat')
const isStreaming = ref(false)
const messagesRef = ref(null)

function clearChat() {
  messages.value = []
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function send() {
  if (!inputMsg.value.trim() || isStreaming.value) return

  const userMsg = inputMsg.value
  inputMsg.value = ''
  messages.value.push({ role: 'user', content: userMsg })
  messages.value.push({ role: 'assistant', content: '' })
  scrollToBottom()

  isStreaming.value = true
  const apiKey = localStorage.getItem('apiKey') || ''

  try {
    const url = `/api/chat/stream?message=${encodeURIComponent(userMsg)}&model=${model.value}`
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
      buffer = lines.pop() // 保留未完整的行

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()
        if (data === '[DONE]' || data === '"[DONE]"') continue
        try {
          const parsed = JSON.parse(data)
          const last = messages.value[messages.value.length - 1]
          last.content += parsed.content || ''
          scrollToBottom()
        } catch (e) { /* ignore parse errors */ }
      }
    }

    isStreaming.value = false
  } catch (e) {
    isStreaming.value = false
  }
}
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 100px); }
.messages { flex: 1; overflow-y: auto; padding: 16px; background: #fff; border-radius: 8px; margin-bottom: 16px; }
.message { display: flex; margin-bottom: 16px; gap: 12px; }
.message.user { flex-direction: row-reverse; }
.avatar { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: bold; flex-shrink: 0; }
.message.user .avatar { background: #1890ff; color: #fff; }
.message.assistant .avatar { background: #52c41a; color: #fff; }
.bubble { max-width: 70%; padding: 10px 14px; border-radius: 8px; line-height: 1.6; }
.message.user .bubble { background: #e6f7ff; }
.message.assistant .bubble { background: #f6ffed; }
.input-bar { display: flex; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
</style>
