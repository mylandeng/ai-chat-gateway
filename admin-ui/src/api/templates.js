import api from './index'

export const listTemplates = () => api.get('/templates')

export const getTemplate = (id) => api.get(`/templates/${id}`)

export const createTemplate = (data) => api.post('/templates', data)

export const updateTemplate = (id, data) => api.put(`/templates/${id}`, data)

export const deleteTemplate = (id) => api.delete(`/templates/${id}`)

export const renderTemplate = (id, variables) =>
  api.post(`/templates/${id}/render`, variables)

export const listMarket = (params) => api.get('/templates/market', { params })

export const getVersions = (id) => api.get(`/templates/${id}/versions`)
