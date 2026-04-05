<template>
  <div class="nx-kb-list">
    <div class="nx-page-header">
      <span class="nx-section-title">知识库管理</span>
      <el-button type="primary" size="small" @click="showCreateDialog = true">+ 新建知识库</el-button>
    </div>

    <div class="nx-kb-grid" v-loading="loading">
      <div v-if="kbs.length === 0 && !loading" class="nx-empty-state">
        <div class="nx-empty-icon">_</div>
        <div>暂无知识库 // 点击上方创建</div>
      </div>

      <div v-for="kb in kbs" :key="kb.id" class="nx-kb-card nx-panel"
           @click="$router.push(`/knowledge/${kb.id}`)">
        <div class="nx-kb-card-content">
          <div class="nx-kb-name">{{ kb.name }}</div>
          <div class="nx-kb-desc">{{ kb.description || '暂无描述' }}</div>
          <div class="nx-kb-meta">
            <span class="nx-mono">{{ kb.docCount || 0 }} 篇文档</span>
            <el-tag v-if="kb.visibility === 'shared'" type="success" size="small">已共享</el-tag>
            <span class="nx-kb-time nx-mono">{{ formatTime(kb.createdAt) }}</span>
          </div>
        </div>
        <div class="nx-kb-actions" @click.stop>
          <el-button link size="small" @click="openEdit(kb)" style="color: var(--nx-text-secondary)">编辑</el-button>
          <el-popconfirm title="删除后不可恢复，确定？" @confirm="handleDelete(kb.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog :title="editKb ? '编辑知识库' : '新建知识库'" v-model="showCreateDialog" width="450px" @closed="resetForm">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="简要描述用途" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ editKb ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listKbs, createKb, updateKb, deleteKb } from '@/api/rag'

const kbs = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const saving = ref(false)
const editKb = ref(null)
const form = ref({ name: '', description: '' })

async function loadKbs() {
  loading.value = true
  try { kbs.value = await listKbs() } catch (e) {}
  loading.value = false
}

function openEdit(kb) {
  editKb.value = kb
  form.value = { name: kb.name, description: kb.description || '' }
  showCreateDialog.value = true
}

function resetForm() {
  editKb.value = null
  form.value = { name: '', description: '' }
}

async function handleSave() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入名称')
  saving.value = true
  try {
    if (editKb.value) {
      await updateKb(editKb.value.id, form.value.name, form.value.description)
      ElMessage.success('已更新')
    } else {
      await createKb(form.value.name, form.value.description)
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    loadKbs()
  } catch (e) {}
  saving.value = false
}

async function handleDelete(id) {
  try { await deleteKb(id); ElMessage.success('已删除'); loadKbs() } catch (e) {}
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

onMounted(loadKbs)
</script>

<style scoped>
.nx-kb-list { }
.nx-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.nx-kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.nx-empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--nx-text-muted);
  font-family: var(--nx-font-mono);
  font-size: 12px;
  letter-spacing: 2px;
}
.nx-empty-icon {
  font-size: 32px;
  color: var(--nx-accent-amber);
  animation: nx-pulse 2s infinite;
}

.nx-kb-card {
  padding: 18px;
  cursor: pointer;
  transition: all 150ms;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.nx-kb-card:hover {
  border-color: var(--nx-accent-amber);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(245, 158, 11, 0.06);
}

.nx-kb-card-content { min-height: 80px; }

.nx-kb-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--nx-text-primary);
  margin-bottom: 6px;
}

.nx-kb-desc {
  color: var(--nx-text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.nx-kb-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: var(--nx-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.nx-kb-time { margin-left: auto; }

.nx-kb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--nx-border);
}
</style>
