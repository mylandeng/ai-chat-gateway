<template>
  <div class="nx-debug">
    <div class="nx-debug-header">
      <el-button link @click="$router.push(`/knowledge/${kbId}`)" style="color: var(--nx-accent-amber)">
        &lt; 返回
      </el-button>
      <span class="nx-section-title" style="margin-left: 12px">RAG 调试控制台</span>
    </div>

    <!-- 查询输入 -->
    <div class="nx-panel nx-query-bar">
      <div class="nx-query-inner">
        <span class="nx-input-prompt">></span>
        <el-input v-model="query" placeholder="输入调试查询..." @keyup.enter="runDebug" />
        <el-select v-model="model" size="default" style="width: 160px; flex-shrink: 0">
          <el-option label="DeepSeek Chat" value="deepseek-chat" />
          <el-option label="通义千问" value="qwen-plus" />
          <el-option label="GPT-4o Mini" value="gpt-4o-mini" />
        </el-select>
        <el-button type="primary" @click="runDebug" :loading="loading">分析</el-button>
      </div>
      <div v-if="loading" class="nx-scanline-bar"></div>
    </div>

    <template v-if="result">
      <!-- 指标概览 -->
      <div class="nx-metrics-row">
        <div class="nx-metric nx-panel">
          <div class="nx-metric-value nx-mono">{{ result.retrievalMode.toUpperCase() }}</div>
          <div class="nx-stat-label">检索模式</div>
        </div>
        <div class="nx-metric nx-panel">
          <div class="nx-metric-value nx-mono" style="color: var(--nx-accent-amber)">{{ result.timing.retrievalMs }}<span class="nx-metric-unit">ms</span></div>
          <div class="nx-stat-label">检索耗时</div>
        </div>
        <div class="nx-metric nx-panel">
          <div class="nx-metric-value nx-mono" style="color: var(--nx-signal-blue)">{{ result.timing.rewriteMs }}<span class="nx-metric-unit">ms</span></div>
          <div class="nx-stat-label">改写耗时</div>
        </div>
        <div class="nx-metric nx-panel">
          <div class="nx-metric-value nx-mono" style="color: var(--nx-accent-teal)">{{ result.timing.rerankMs }}<span class="nx-metric-unit">ms</span></div>
          <div class="nx-stat-label">重排耗时</div>
        </div>
        <div class="nx-metric nx-panel">
          <div class="nx-metric-value nx-mono" style="color: var(--nx-text-primary)">{{ result.timing.totalMs }}<span class="nx-metric-unit">ms</span></div>
          <div class="nx-stat-label">总耗时</div>
        </div>
      </div>

      <!-- 配置参数面板 -->
      <div class="nx-panel nx-config-panel" v-if="result.config">
        <div class="nx-panel-header-inline">配置参数</div>
        <div class="nx-config-grid">
          <div class="nx-config-item">
            <span class="nx-config-key">检索模式</span>
            <span class="nx-config-val">
              {{ result.config.hybridEnabled
                ? `混合检索 (向量:${result.config.vectorWeight} + BM25:${result.config.bm25Weight})`
                : '向量检索' }}
            </span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">Rerank</span>
            <span class="nx-config-val">
              <template v-if="result.config.rerankEnabled">
                <span class="nx-tag-on">已启用</span> {{ result.config.rerankProvider }} / {{ result.config.rerankModel }}
              </template>
              <span v-else class="nx-tag-off">未启用</span>
            </span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">Query 改写</span>
            <span class="nx-config-val">
              <span :class="result.config.rewriteEnabled ? 'nx-tag-on' : 'nx-tag-off'">
                {{ result.config.rewriteEnabled ? '已启用' : '未启用' }}
              </span>
            </span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">最大结果数</span>
            <span class="nx-config-val nx-mono">{{ result.config.maxResults }}</span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">最低分数阈值</span>
            <span class="nx-config-val nx-mono">{{ result.config.minScore }}</span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">分块参数</span>
            <span class="nx-config-val nx-mono">{{ result.config.chunkSize }} 字 / {{ result.config.chunkOverlap }} 重叠</span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">Embedding 模型</span>
            <span class="nx-config-val nx-mono">{{ result.config.embeddingModel }} ({{ result.config.embeddingDimension }}维)</span>
          </div>
          <div class="nx-config-item">
            <span class="nx-config-key">知识库片段数</span>
            <span class="nx-config-val nx-mono">{{ result.totalChunks }}</span>
          </div>
        </div>
      </div>

      <!-- Query 改写 -->
      <div class="nx-panel nx-rewrite" v-if="result.rewrittenQuery !== result.originalQuery">
        <div class="nx-panel-header-inline">查询改写</div>
        <div class="nx-rewrite-body">
          <div class="nx-rewrite-item">
            <span class="nx-rewrite-label">原始查询</span>
            <span>{{ result.originalQuery }}</span>
          </div>
          <div class="nx-rewrite-arrow">→</div>
          <div class="nx-rewrite-item">
            <span class="nx-rewrite-label" style="color: var(--nx-accent-amber)">改写结果</span>
            <span>{{ result.rewrittenQuery }}</span>
          </div>
        </div>
      </div>

      <!-- 检索结果 -->
      <div class="nx-panel nx-results-panel">
        <div class="nx-panel-header-inline">
          检索结果 — {{ filteredCount }}/{{ result.totalChunks }} 个片段（共匹配 {{ result.matches.length }} 条）
        </div>
        <el-table :data="result.matches" size="small" border :row-class-name="rowClassName">
          <el-table-column label="#" width="50">
            <template #default="{ row }"><span class="nx-mono">{{ row.rank }}</span></template>
          </el-table-column>
          <el-table-column label="文件" width="150" prop="fileName" />
          <el-table-column label="向量分" width="100">
            <template #default="{ row }">
              <span :class="['nx-mono', scoreClass(row.vectorScore)]">{{ row.vectorScore.toFixed(4) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="BM25" width="90" v-if="result.retrievalMode.includes('hybrid')">
            <template #default="{ row }">
              <span class="nx-mono">{{ row.bm25Score.toFixed(4) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总分" width="100">
            <template #default="{ row }">
              <span :class="['nx-mono', scoreClass(row.totalScore)]">{{ row.totalScore.toFixed(4) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="重排分" width="100" v-if="result.rerankEnabled">
            <template #default="{ row }">
              <span class="nx-mono">{{ row.rerankScore >= 0 ? row.rerankScore.toFixed(4) : '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="isBelowThreshold(row)" type="info" size="small">已过滤</el-tag>
              <el-tag v-else type="success" size="small">有效</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文本片段" min-width="300">
            <template #default="{ row }">
              <div class="nx-chunk-text">{{ row.text.substring(0, 200) }}{{ row.text.length > 200 ? '...' : '' }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 完整 Prompt（可折叠） -->
      <div class="nx-panel nx-prompt-panel">
        <div class="nx-panel-header-inline">
          <span style="cursor: pointer" @click="promptExpanded = !promptExpanded">
            {{ promptExpanded ? '▾' : '▸' }} 完整 Prompt
          </span>
          <el-button size="small" @click="copyPrompt">复制</el-button>
        </div>
        <pre v-show="promptExpanded" class="nx-prompt-code">{{ result.fullPrompt }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ragDebug } from '@/api/rag'

const route = useRoute()
const kbId = computed(() => route.params.id)

const query = ref('')
const model = ref('deepseek-chat')
const loading = ref(false)
const result = ref(null)
const promptExpanded = ref(false)

const filteredCount = computed(() => {
  if (!result.value || !result.value.config) return 0
  const threshold = result.value.config.minScore
  return result.value.matches.filter(m => m.totalScore >= threshold).length
})

function isBelowThreshold(row) {
  if (!result.value || !result.value.config) return false
  return row.totalScore < result.value.config.minScore
}

function rowClassName({ row }) {
  return isBelowThreshold(row) ? 'nx-row-filtered' : ''
}

async function runDebug() {
  if (!query.value.trim()) return ElMessage.warning('请输入查询')
  loading.value = true
  try { result.value = await ragDebug(kbId.value, query.value, model.value) } catch (e) {}
  loading.value = false
}

function copyPrompt() {
  if (!result.value) return
  navigator.clipboard.writeText(result.value.fullPrompt)
  ElMessage.success('已复制')
}

function scoreClass(score) {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.nx-debug { display: flex; flex-direction: column; gap: 16px; }
.nx-debug-header { display: flex; align-items: center; }

.nx-query-bar { padding: 14px 16px; position: relative; overflow: hidden; }
.nx-query-inner { display: flex; align-items: center; gap: 10px; }
.nx-input-prompt {
  font-family: var(--nx-font-mono); font-size: 16px;
  color: var(--nx-accent-amber); font-weight: 600; flex-shrink: 0;
}

.nx-scanline-bar {
  position: absolute;
  top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--nx-accent-amber), transparent);
  animation: nx-scanline 1.5s ease-in-out infinite;
}

/* Metrics */
.nx-metrics-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.nx-metric { padding: 16px; text-align: center; }
.nx-metric-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--nx-text-primary);
  line-height: 1.2;
}
.nx-metric-unit { font-size: 12px; color: var(--nx-text-muted); margin-left: 2px; }

/* Config Panel */
.nx-config-panel { padding: 16px; }
.nx-config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 24px;
}
.nx-config-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--nx-border);
}
.nx-config-key {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--nx-text-muted);
  white-space: nowrap;
  min-width: 100px;
}
.nx-config-val {
  font-size: 13px;
  color: var(--nx-text-primary);
}
.nx-tag-on {
  display: inline-block;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 2px;
  background: rgba(45, 212, 191, 0.15);
  color: var(--nx-accent-teal);
  margin-right: 6px;
}
.nx-tag-off {
  display: inline-block;
  font-family: var(--nx-font-mono);
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 2px;
  background: rgba(148, 163, 184, 0.15);
  color: var(--nx-text-muted);
}

/* Rewrite */
.nx-rewrite { padding: 16px; }
.nx-panel-header-inline {
  font-family: var(--nx-font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  color: var(--nx-text-secondary);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nx-rewrite-body { display: flex; align-items: center; gap: 16px; }
.nx-rewrite-item { flex: 1; }
.nx-rewrite-label {
  font-family: var(--nx-font-mono);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--nx-text-muted);
  display: block;
  margin-bottom: 4px;
}
.nx-rewrite-arrow {
  font-size: 18px;
  color: var(--nx-accent-amber);
  flex-shrink: 0;
}

/* Results */
.nx-results-panel { padding: 16px; }
.nx-chunk-text {
  font-size: 12px;
  color: var(--nx-text-secondary);
  line-height: 1.5;
  max-height: 60px;
  overflow: hidden;
}

/* Filtered rows */
:deep(.nx-row-filtered) {
  opacity: 0.45;
}
:deep(.nx-row-filtered td) {
  text-decoration: line-through;
  text-decoration-color: var(--nx-text-muted);
}

/* Score colors */
.score-high { color: var(--nx-accent-teal) !important; }
.score-mid { color: var(--nx-accent-amber) !important; }
.score-low { color: var(--nx-text-muted) !important; }

/* Prompt */
.nx-prompt-panel { padding: 16px; }
.nx-prompt-code {
  white-space: pre-wrap;
  font-family: var(--nx-font-mono);
  font-size: 12px;
  color: var(--nx-text-secondary);
  background: var(--nx-bg-deep);
  border: 1px solid var(--nx-border);
  border-radius: 2px;
  padding: 14px;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  position: relative;
}
.nx-prompt-code::before {
  content: '';
  position: absolute;
  top: 0; left: 0;
  width: 3px; height: 100%;
  background: var(--nx-accent-amber);
  opacity: 0.4;
}
</style>
