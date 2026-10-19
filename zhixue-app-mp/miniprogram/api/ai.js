/**
 * AI 课程答疑 API（模型由服务端本机部署的开源模型提供）
 */
const { get } = require('./request')

/** 本地模型首次加载约 6s，加上生成远超默认 10s；与后端答疑超时对齐 */
const CHAT_TIMEOUT = 100000

/**
 * 课程答疑
 * @param {string} question 问题（≤500 字）
 * @param {number|string} [courseId] 课程 ID；传入时只检索该课程资料与通用资料
 * @returns {Promise<string>} 回答
 */
function askCourse(question, courseId) {
  const params = courseId ? { question, courseId } : { question }
  return get('/ai/chat/course', params, {
    timeout: CHAT_TIMEOUT,
    // 页面自己展示"思考中"，不用全屏遮罩挡住对话
    showLoading: false
  }).then((res) => res.data || '')
}

module.exports = {
  askCourse
}
