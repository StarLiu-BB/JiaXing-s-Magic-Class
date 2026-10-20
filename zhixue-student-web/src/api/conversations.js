import request from '@/utils/request'

export const listConversations = (pageNum = 1, pageSize = 50) =>
  request({ url: '/ai/conversations', method: 'get', params: { pageNum, pageSize } })

export const getConversation = (id) => request({ url: `/ai/conversations/${id}`, method: 'get' })

export const getConversationMessages = (id) => request({ url: `/ai/conversations/${id}/messages`, method: 'get' })

export const renameConversation = (id, title) => request({ url: `/ai/conversations/${id}`, method: 'put', data: { title } })

export const deleteConversation = (id) => request({ url: `/ai/conversations/${id}`, method: 'delete' })
