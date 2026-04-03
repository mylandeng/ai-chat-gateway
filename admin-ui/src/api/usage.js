import api from './index'

export const getSummary = () => api.get('/usage/summary')

export const getDailyUsage = (start, end) =>
  api.get('/usage/daily', { params: { start, end } })

export const getModelStats = (start, end) =>
  api.get('/usage/by-model', { params: { start, end } })

export const getKeyStats = (start, end) =>
  api.get('/usage/by-key', { params: { start, end } })
