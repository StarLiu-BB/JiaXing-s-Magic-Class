/**
 * 媒体上传 API
 */
import request from '@/utils/request'

/**
 * 普通文件上传
 * @param {FormData} formData 表单数据
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
  formData.append('type', 'image')
  
  return request({
    url: '/media/upload/image',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

/**
 * 视频上传
 * @param {File} file 视频文件
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadVideo(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', 'video')
  
  return request({
    url: '/media/upload/video',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

/**
 * 初始化分片上传
 * @param {Object} params 参数
 * @param {string} params.fileName 文件名
 * @param {number} params.fileSize 文件大小
 * @param {string} params.fileType 文件类型
 * @param {string} params.fileMd5 文件MD5值
 */
export function initChunkUpload(params) {
  return request({
    url: '/media/upload/chunk/init',
    method: 'post',
    data: params
  })
}

/**
 * 上传分片
 * @param {Object} params 参数
 * @param {string} params.uploadId 上传ID
 * @param {number} params.chunkNumber 分片序号
 * @param {number} params.chunkSize 分片大小
 * @param {string} params.chunkMd5 分片MD5值
 * @param {Blob} params.chunk 分片数据
 * @param {Function} onUploadProgress 上传进度回调
 */
export function uploadChunk(params, onUploadProgress) {
  const formData = new FormData()
  formData.append('uploadId', params.uploadId)
  formData.append('chunkNumber', params.chunkNumber)
  formData.append('chunkSize', params.chunkSize)
  formData.append('chunkMd5', params.chunkMd5)
  formData.append('chunk', params.chunk)
  
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
 * 完成分片上传
 * @param {Object} params 参数
 * @param {string} params.uploadId 上传ID
 * @param {number} params.totalChunks 总分片数
 * @param {string} params.fileMd5 文件MD5值
 */
export function completeChunkUpload(params) {
  return request({
    url: '/media/upload/chunk/complete',
    method: 'post',
    data: params
  })
}

/**
 * 取消分片上传
 * @param {string} uploadId 上传ID
 */
export function cancelChunkUpload(uploadId) {
  return request({
    url: `/media/upload/chunk/${uploadId}/cancel`,
    method: 'post'
  })
}

/**
 * 检查分片上传状态
 * @param {string} uploadId 上传ID
 */
export function checkChunkUploadStatus(uploadId) {
  return request({
    url: `/media/upload/chunk/${uploadId}/status`,
    method: 'get'
  })
}

/**
 * 分片上传工具类
 */
export class ChunkUploader {
  constructor(file, options = {}) {
    this.file = file
    this.chunkSize = options.chunkSize || 5 * 1024 * 1024 // 默认5MB
    this.uploadId = null
    this.totalChunks = Math.ceil(file.size / this.chunkSize)
    this.uploadedChunks = new Set()
    this.onProgress = options.onProgress || (() => {})
    this.onSuccess = options.onSuccess || (() => {})
    this.onError = options.onError || (() => {})
  }

  /**
   * 计算文件MD5（简化版，实际应使用spark-md5等库）
   */
  async calculateFileMd5() {
    // 这里应该使用实际的MD5计算库
    // 示例：使用文件名+大小作为临时标识
    return `${this.file.name}_${this.file.size}`
  }

  /**
   * 开始上传
   */
  async start() {
    try {
      // 1. 初始化上传
      const fileMd5 = await this.calculateFileMd5()
      const initResult = await initChunkUpload({
        fileName: this.file.name,
        fileSize: this.file.size,
        fileType: this.file.type,
        fileMd5
      })
      
      this.uploadId = initResult.uploadId
      
      // 检查是否已上传过
      if (initResult.uploadedChunks) {
        initResult.uploadedChunks.forEach(chunk => {
          this.uploadedChunks.add(chunk)
        })
      }
      
      // 2. 上传分片
      for (let i = 0; i < this.totalChunks; i++) {
        if (this.uploadedChunks.has(i + 1)) {
          continue // 跳过已上传的分片
        }
        
        const start = i * this.chunkSize
        const end = Math.min(start + this.chunkSize, this.file.size)
        const chunk = this.file.slice(start, end)
        
        await uploadChunk({
          uploadId: this.uploadId,
          chunkNumber: i + 1,
          chunkSize: chunk.size,
          chunkMd5: '', // 实际应计算chunk的MD5
          chunk
        }, (progressEvent) => {
          const chunkProgress = (progressEvent.loaded / progressEvent.total) * 100
          const totalProgress = ((i + chunkProgress / 100) / this.totalChunks) * 100
          this.onProgress(totalProgress)
        })
        
        this.uploadedChunks.add(i + 1)
      }
      
      // 3. 完成上传
      const result = await completeChunkUpload({
        uploadId: this.uploadId,
        totalChunks: this.totalChunks,
        fileMd5
      })
      
      this.onSuccess(result)
      return result
    } catch (error) {
      this.onError(error)
      throw error
    }
  }

  /**
   * 取消上传
   */
  async cancel() {
    if (this.uploadId) {
      await cancelChunkUpload(this.uploadId)
    }
  }
}

