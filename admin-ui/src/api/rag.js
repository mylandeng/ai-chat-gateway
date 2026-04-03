import api from './index'

// 文档管理
export const uploadDocument = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/rag/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export const getDocuments = () => api.get('/rag/documents')

export const deleteDocument = (id) => api.delete(`/rag/documents/${id}`)

// RAG 问答（非流式）
export const ragChat = (q, model) => api.get('/rag/chat', { params: { q, model } })
