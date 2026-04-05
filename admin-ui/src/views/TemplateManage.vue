<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="display: flex; align-items: center; gap: 16px">
            <span>提示词模板</span>
            <el-radio-group v-model="tab" size="small">
              <el-radio-button value="my">我的模板</el-radio-button>
              <el-radio-button value="market">模板市场</el-radio-button>
            </el-radio-group>
          </div>
          <el-button type="primary" size="small" @click="openEditor()">新建模板</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="8" v-for="tpl in templates" :key="tpl.id">
          <div class="nx-tpl-card nx-panel" @click="openEditor(tpl)">
            <div class="nx-tpl-header">
              <span class="nx-tpl-name">{{ tpl.name }}</span>
              <el-tag size="small">{{ tpl.category }}</el-tag>
            </div>
            <p class="nx-tpl-desc">{{ tpl.description }}</p>
            <div class="nx-tpl-meta">
              <span class="nx-mono">v{{ tpl.version }}</span>
              <span>{{ tpl.isPublic ? '公开' : '私有' }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="showEditor" :title="editing ? '编辑模板' : '新建模板'" width="650px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="模板内容">
          <el-input v-model="form.content" type="textarea" :rows="6"
            placeholder="使用 {{变量名}} 作为占位符" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="form.isPublic" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="editing" type="danger" @click="del" style="float: left">删除</el-button>
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { listTemplates, createTemplate, updateTemplate, deleteTemplate, listMarket } from '@/api/templates'
import { ElMessage } from 'element-plus'

const templates = ref([])
const tab = ref('my')
const showEditor = ref(false)
const editing = ref(null)
const categories = ['translation', 'coding', 'writing', 'analysis', 'customer-service', 'education', 'creative', 'other']

const form = ref({ name: '', description: '', category: '', content: '', isPublic: false })

onMounted(() => load())
watch(tab, () => load())

async function load() {
  try {
    if (tab.value === 'my') {
      templates.value = await listTemplates()
    } else {
      const res = await listMarket({})
      templates.value = res.content || []
    }
  } catch (e) {}
}

function openEditor(tpl) {
  if (tpl) {
    editing.value = tpl.id
    form.value = { name: tpl.name, description: tpl.description, category: tpl.category,
      content: tpl.content, isPublic: tpl.isPublic }
  } else {
    editing.value = null
    form.value = { name: '', description: '', category: '', content: '', isPublic: false }
  }
  showEditor.value = true
}

async function save() {
  if (editing.value) {
    await updateTemplate(editing.value, form.value)
    ElMessage.success('已更新')
  } else {
    await createTemplate(form.value)
    ElMessage.success('已创建')
  }
  showEditor.value = false
  load()
}

async function del() {
  await deleteTemplate(editing.value)
  ElMessage.success('已删除')
  showEditor.value = false
  load()
}
</script>

<style scoped>
.nx-tpl-card {
  padding: 16px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: border-color 150ms;
}
.nx-tpl-card:hover {
  border-color: var(--nx-accent-amber);
}
.nx-tpl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.nx-tpl-name {
  font-weight: 600;
  color: var(--nx-text-primary);
}
.nx-tpl-desc {
  color: var(--nx-text-secondary);
  font-size: 13px;
  margin: 0 0 8px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.nx-tpl-meta {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  color: var(--nx-text-muted);
  display: flex;
  gap: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
