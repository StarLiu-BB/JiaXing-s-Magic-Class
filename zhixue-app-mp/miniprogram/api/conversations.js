const { get, put, del } = require('./request')
const options = { showLoading: false }

function listConversations(pageNum = 1) {
  return get('/ai/conversations', { pageNum, pageSize: 20 }, options).then(res => res.data)
}
function getConversation(id) { return get(`/ai/conversations/${id}`, {}, options).then(res => res.data) }
function getMessages(id) { return get(`/ai/conversations/${id}/messages`, {}, options).then(res => res.data) }
function renameConversation(id, title) { return put(`/ai/conversations/${id}`, { title }, options) }
function deleteConversation(id) { return del(`/ai/conversations/${id}`, {}, options) }

module.exports = { listConversations, getConversation, getMessages, renameConversation, deleteConversation }
