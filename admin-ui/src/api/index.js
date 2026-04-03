import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const apiKey = localStorage.getItem('apiKey')
  if (apiKey) {
    config.headers.Authorization = `Bearer ${apiKey}`
  }
  return config
})

// 防止短时间内重复弹出相同错误
let lastMsg = ''
let lastMsgTime = 0

api.interceptors.response.use(
  response => response.data,
  error => {
    // 401 由页面自行处理，不弹全局错误
    if (error.response?.status === 401) {
      return Promise.reject(error)
    }
    const msg = error.response?.data?.error?.message || error.message || '请求失败'
    const now = Date.now()
    if (msg !== lastMsg || now - lastMsgTime > 2000) {
      ElMessage.error(msg)
      lastMsg = msg
      lastMsgTime = now
    }
    return Promise.reject(error)
  }
)

export default api
