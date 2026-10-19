/**
 * AI 助手接口（模型由本机 Ollama 提供）
 *
 * 后端接口（经网关 /ai 前缀）：
 *   GET  /ai/chat/course?question=&courseId=   课程答疑（需登录；courseId 可选，传入时只检索该课程资料与通用资料）
 *   POST /ai/kb/upload                         上传知识库文档（需 ai:kb:edit；仅 .txt/.md，≤1MB）
 */
import request from '@/utils/request'

/** 本地模型冷启动（首次加载约 6s）加生成，远超全局 10s 超时；与后端 ai.llm.chat-timeout-seconds 对齐 */
const CHAT_TIMEOUT = 100000

/**
 * 课程答疑
 * @param {string} question 问题（≤500 字）
 * @param {number|string} [courseId] 课程 ID
 * @returns {Promise<string>} 回答
 */
export function askCourse(question, courseId) {
  return request({
    url: '/ai/chat/course',
    method: 'get',
    params: courseId ? { question, courseId } : { question },
    timeout: CHAT_TIMEOUT
  }).then((res) => res?.data || '')
}

/**
 * 上传知识库文档
 * @param {{file: File, title?: string, courseId?: number|string, tags?: string}} form 表单
 */
export function uploadKnowledge({ file, title, courseId, tags }) {
  const data = new FormData()
  data.append('file', file)
  if (title) data.append('title', title)
  if (courseId) data.append('courseId', courseId)
  if (tags) data.append('tags', tags)
  return request({
    url: '/ai/kb/upload',
    method: 'post',
    data,
    timeout: 60000
  })
}
