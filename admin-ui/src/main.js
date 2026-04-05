import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/nexus-theme.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { nexusChartTheme, nexusLightChartTheme } from './styles/nexus-echarts'
import router from './router'
import App from './App.vue'

// 注册 NEXUS ECharts 主题（暗色 + 亮色）
echarts.registerTheme('nexus', nexusChartTheme)
echarts.registerTheme('nexus-light', nexusLightChartTheme)

// 初始化主题：读取 localStorage 偏好
const savedTheme = localStorage.getItem('nx-theme') || 'dark'
document.documentElement.setAttribute('data-theme', savedTheme)

const app = createApp(App)
app.use(ElementPlus)
app.use(router)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
