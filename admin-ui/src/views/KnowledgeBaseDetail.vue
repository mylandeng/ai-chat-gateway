<template>
  <div class="nx-kb-detail" v-loading="loading">
    <div class="nx-detail-header">
      <div class="nx-detail-nav">
        <el-button link @click="$router.push('/knowledge-list')" style="color: var(--nx-accent-amber)">
          &lt; 返回
        </el-button>
        <span class="nx-section-title" style="margin-left: 12px">{{ kb?.name || '...' }}</span>
      </div>
      <div class="nx-detail-actions">
        <el-button size="small" @click="$router.push(`/knowledge/${kbId}/chat`)">对话</el-button>
        <el-button size="small" @click="$router.push(`/knowledge/${kbId}/debug`)">调试</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 文档管理 -->
      <el-tab-pane label="文档管理" name="docs">
        <div style="margin-bottom: 12px; display: flex; justify-content: flex-end">
          <el-upload :show-file-list="false" :before-upload="handleUpload"
                     accept=".pdf,.docx,.xlsx,.txt,.md,.csv" multiple>
            <el-button type="primary" size="small" :loading="uploading">上传</el-button>
          </el-upload>
        </div>

        <el-table :data="documents" v-loading="loadingDocs" empty-text="暂无文档" size="small">
          <el-table-column prop="fileName" label="文件名" min-width="200" />
          <el-table-column label="大小" width="100">
            <template #default="{ row }"><span class="nx-mono">{{ formatSize(row.fileSize) }}</span></template>
          </el-table-column>
          <el-table-column label="字符数" width="90">
            <template #default="{ row }"><span class="nx-mono">{{ row.charCount }}</span></template>
          </el-table-column>
          <el-table-column label="分块数" width="90">
            <template #default="{ row }"><span class="nx-mono">{{ row.chunkCount }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="nx-mono" style="font-size: 12px; color: var(--nx-text-muted)">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-popconfirm title="确定删除？" @confirm="handleDeleteDoc(row.id)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 分享设置 -->
      <el-tab-pane label="分享设置" name="share">
        <div class="nx-share-panel nx-panel" style="max-width: 520px; padding: 20px">
          <el-form label-width="100px">
            <el-form-item label="公开访问">
              <el-switch v-model="shareEnabled" @change="handleToggleShare" />
            </el-form-item>
            <el-form-item label="分享链接" v-if="shareEnabled && shareUrl">
              <el-input :model-value="fullShareUrl" readonly class="nx-mono">
                <template #append>
                  <el-button @click="copyShareUrl">复制</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <div style="color: var(--nx-text-muted); font-size: 12px; font-family: var(--nx-font-mono)">
                开启后，任何人可通过分享链接匿名访问
              </div>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="info">
        <div class="nx-panel" style="max-width: 520px; padding: 20px">
          <el-form label-width="100px" :model="editForm">
            <el-form-item label="名称">
              <el-input v-model="editForm.name" maxlength="50" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="200" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" @click="handleUpdateInfo" :loading="saving">保存</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKb, updateKb, listKbDocuments, uploadKbDocument, deleteKbDocument, toggleShare } from '@/api/rag'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => route.params.id)

const kb = ref(null)
const loading = ref(false)
const activeTab = ref('docs')

const documents = ref([])
const loadingDocs = ref(false)
const uploading = ref(false)
const shareEnabled = ref(false)
const shareUrl = ref('')
const editForm = ref({ name: '', description: '' })
const saving = ref(false)

async function loadKb() {
  loading.value = true
  try {
    kb.value = await getKb(kbId.value)
    shareEnabled.value = kb.value.visibility === 'shared'
    shareUrl.value = kb.value.shareToken || ''
    editForm.value = { name: kb.value.name, description: kb.value.description || '' }
  } catch (e) { router.push('/knowledge-list') }
  loading.value = false
}

async function loadDocs() {
  loadingDocs.value = true
  try { documents.value = await listKbDocuments(kbId.value) } catch (e) {}
  loadingDocs.value = false
}

async function handleUpload(file) {
  uploading.value = true
  try {
    await uploadKbDocument(kbId.value, file)
    ElMessage.success('上传成功，正在处理...')
    pollDocuments()
  } catch (e) {}
  uploading.value = false
  return false
}

function pollDocuments() {
  loadDocs()
  const timer = setInterval(async () => {
    await loadDocs()
    if (!documents.value.some(d => d.status === 0 || d.status === 1)) clearInterval(timer)
  }, 3000)
  setTimeout(() => clearInterval(timer), 120000)
}

async function handleDeleteDoc(docId) {
  try { await deleteKbDocument(kbId.value, docId); ElMessage.success('已删除'); loadDocs() } catch (e) {}
}

async function handleToggleShare(val) {
  try {
    const res = await toggleShare(kbId.value, val)
    shareUrl.value = res.shareToken || ''
    ElMessage.success(val ? '已开启分享' : '已关闭分享')
  } catch (e) { shareEnabled.value = !val }
}

const fullShareUrl = computed(() => {
  if (!shareUrl.value) return ''
  return `${window.location.origin}/share/${shareUrl.value}`
})

function copyShareUrl() {
  navigator.clipboard.writeText(fullShareUrl.value)
  ElMessage.success('已复制')
}

async function handleUpdateInfo() {
  if (!editForm.value.name.trim()) return ElMessage.warning('名称不能为空')
  saving.value = true
  try { await updateKb(kbId.value, editForm.value.name, editForm.value.description); ElMessage.success('已保存'); loadKb() }
  catch (e) {}
  saving.value = false
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'K'
  return (bytes / 1024 / 1024).toFixed(1) + 'M'
}
function statusText(s) { return { 0: '索引中', 1: '已解析', 2: '就绪', '-1': '失败' }[s] || '?' }
function statusType(s) { return { 0: 'warning', 1: 'warning', 2: 'success', '-1': 'danger' }[s] || 'info' }
function formatTime(t) { return t ? t.replace('T', ' ').substring(0, 19) : '' }

onMounted(() => { loadKb(); loadDocs() })
</script>

<style scoped>
.nx-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.nx-detail-nav { display: flex; align-items: center; }
.nx-detail-actions { display: flex; gap: 8px; }
</style>
