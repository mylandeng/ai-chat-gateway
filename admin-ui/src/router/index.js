import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板' } },
  { path: '/keys', component: () => import('../views/KeyManage.vue'), meta: { title: 'Key 管理' } },
  { path: '/templates', component: () => import('../views/TemplateManage.vue'), meta: { title: '模板管理' } },
  { path: '/chat', component: () => import('../views/ChatTest.vue'), meta: { title: '对话测试' } },
  { path: '/knowledge', component: () => import('../views/KnowledgeBase.vue'), meta: { title: '知识库' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = (to.meta.title || 'AI Chat Gateway') + ' - 管理后台'
})

export default router
