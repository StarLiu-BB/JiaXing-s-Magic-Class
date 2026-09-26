/**
 * 媒体上传 API
 *
 * 后端（zhixue-media / UploadController）真实提供的端点只有：
 *   POST /media/upload           单文件上传
 *   POST /media/upload/chunk     上传单个分片（ChunkUploadDTO）
 *   POST /media/upload/merge     合并分片（MergeChunkDTO）
 *   GET  /media/file/{id}
 *   POST /media/file/list
 *   GET  /media/file/page
 *
 * 此前本文件调用的 /upload/image、/upload/video、/upload/chunk/init、
 * /upload/chunk/complete、/upload/chunk/{id}/cancel、/upload/chunk/{id}/status
 * 在后端均不存在，导致封面上传、富文本配图、视频上传全部不可用。
 */
import request from '@/utils/request'

/**
 * 单文件上传（封面、配图等）
 * @param {FormData} formData 至少包含 file 字段
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadFile(formData, onUploadProgress) {
  return request({
    url: '/media/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

/**
 * 图片上传
 * @param {File} file 图片文件
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadImage(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadFile(formData, onUploadProgress)
}

/**
 * 视频上传（小文件直传；大文件应走分片流程）
 * @param {File} file 视频文件
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadVideo(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadFile(formData, onUploadProgress)
}

/**
 * 上传单个分片。字段名必须与后端 ChunkUploadDTO 一致。
 * @param {Object} params
 * @param {string} params.fileMd5 整个文件的 32 位 MD5（后端强校验格式）
 * @param {number} params.chunkIndex 分片序号，从 0 开始
 * @param {number} params.chunkTotal 分片总数
 * @param {string} params.fileName 原始文件名
 * @param {number} params.chunkSize 当前分片大小
 * @param {Blob}   params.chunkFile 分片数据
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadChunk(params, onUploadProgress) {
  const formData = new FormData()
  formData.append('fileMd5', params.fileMd5)
  formData.append('chunkIndex', params.chunkIndex)
  formData.append('chunkTotal', params.chunkTotal)
  formData.append('fileName', params.fileName)
  formData.append('chunkSize', params.chunkSize)
  formData.append('chunkFile', params.chunkFile)

  return request({
    url: '/media/upload/chunk',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

/**
 * 合并分片。字段名必须与后端 MergeChunkDTO 一致。
 * @param {Object} params
 * @param {string} params.fileMd5
 * @param {string} params.fileName
 * @param {string} params.fileType
 * @param {number} params.chunkTotal
 * @param {number} params.fileSize
 */
export function mergeChunks(params) {
  return request({
    url: '/media/upload/merge',
    method: 'post',
    data: params
  })
}

/**
 * 媒资分页查询
 */
export function listMediaPage(query) {
  return request({
    url: '/media/file/page',
    method: 'get',
    params: query
  })
}

/**
 * 分片上传工具类。
 *
 * 注意：后端以 fileMd5 作为分片目录键，并强校验为 32 位 MD5。
 * 浏览器 Web Crypto 不支持 MD5，必须引入 spark-md5 之类的库才能实现，
 * 这部分与"秒传/断点续传"一并作为后续工作。
 * 在真实 MD5 就绪前，这里显式抛错，绝不使用伪造哈希——
 * 伪造值会被后端拒绝，或写入不可预期的对象路径。
 */
export class ChunkUploader {
  constructor(file, options = {}) {
    this.file = file
    this.chunkSize = options.chunkSize || 5 * 1024 * 1024
    this.totalChunks = Math.ceil(file.size / this.chunkSize)
    this.onProgress = options.onProgress || (() => {})
    this.onSuccess = options.onSuccess || (() => {})
    this.onError = options.onError || (() => {})
  }

  async calculateFileMd5() {
    throw new Error(
      '分片上传需要真实的文件 MD5（后端强校验 32 位十六进制），请先接入 spark-md5'
    )
  }

  async start() {
    const error = new Error('分片上传尚未接入真实 MD5 计算，请改用 uploadFile 直传小文件')
    this.onError(error)
    throw error
  }
}
