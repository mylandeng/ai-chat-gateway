import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板' } },
  { path: '/keys', component: () => import('../views/KeyManage.vue'), meta: { title: 'Key 管理' } },
  { path: '/templates', component: () => import('../views/TemplateManage.vue'), meta: { title: '模板管理' } },
  { path: '/chat', component: () => import('../views/ChatTest.vue'), meta: { title: '对话测试' } },
  // W3 原知识库页面（向后兼容）
  { path: '/knowledge', component: () => import('../views/KnowledgeBase.vue'), meta: { title: '知识库(旧版)' } },
  // W4 知识库管理
  { path: '/knowledge-list', component: () => import('../views/KnowledgeBaseList.vue'), meta: { title: '知识库管理' } },
  { path: '/knowledge/:id', component: () => import('../views/KnowledgeBaseDetail.vue'), meta: { title: '知识库详情' } },
  { path: '/knowledge/:id/chat', component: () => import('../views/RagChat.vue'), meta: { title: 'RAG 问答' } },
  { path: '/knowledge/:id/debug', component: () => import('../views/RagDebug.vue'), meta: { title: 'RAG Debug' } },
  // W4 分享链接（独立页面，无侧边栏）
  { path: '/share/:token', component: () => import('../views/SharedChat.vue'), meta: { title: '知识库问答', layout: 'blank' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = (to.meta.title || 'AI Chat Gateway') + ' - 管理后台'
})

export default router
