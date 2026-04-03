import api from './index'

export const listKeys = (tenantId) => api.get('/keys', { params: { tenantId } })

export const createApiKey = (tenantId, name) =>
  api.post('/keys', null, { params: { tenantId, name } })

export const disableKey = (keyId) => api.put(`/keys/${keyId}/disable`)

export const enableKey = (keyId) => api.put(`/keys/${keyId}/enable`)

export const removeKey = (keyId) => api.delete(`/keys/${keyId}`)
