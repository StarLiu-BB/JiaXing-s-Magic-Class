<template>
  <div class="upload-video">
    <div class="upload-video-main">
      <el-upload
        :show-file-list="false"
        :http-request="handleCustomUpload"
        :before-upload="beforeUpload"
      >
        <el-button type="primary" :loading="uploading">
          {{ uploading ? '上传中...' : (modelValue ? '重新上传视频' : '选择视频上传') }}
        </el-button>
        <span class="upload-tip">{{ tipText }}</span>
      </el-upload>
    </div>

    <div v-if="progress > 0" class="upload-progress">
      <el-progress
        :percentage="Math.floor(progress)"
        :status="progress === 100 ? 'success' : undefined"
      />
    </div>

    <div v-if="modelValue" class="video-preview">
      <video :src="modelValue" controls class="video-player" />
      <div class="video-actions">
        <el-button type="danger" link @click="handleRemove">删除视频</el-button>
        <el-button v-if="uploading" type="warning" link @click="handleCancel">
          取消上传
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ChunkUploader } from '@/api/media/upload'

const props = defineProps({
  // v-model 绑定的视频地址
  modelValue: {
    type: String,
    default: ''
  },
  // 单个文件大小限制（MB）
  maxSize: {
    type: Number,
    default: 1024 // 默认 1GB
  },
  // 允许的文件类型
  accept: {
    type: Array,
    default: () => ['video/mp4', 'video/ogg', 'video/webm']
  },
  // 分片大小（MB）
  chunkSize: {
    type: Number,
    default: 5
  },
  // 提示文案
  tip: {
    type: String,
    default: '支持 MP4 等常见视频格式，大小不超过 1GB，分片上传 5MB/片'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const uploading = ref(false)
const progress = ref(0)
let uploader = null

const tipText = computed(() => props.tip)

// 上传前校验
const beforeUpload = (file) => {
  const isAllowedType =
    !props.accept || props.accept.length === 0 || props.accept.includes(file.type)
  const isLtSize = file.size / 1024 / 1024 < props.maxSize

  if (!isAllowedType) {
    ElMessage.error('不支持的视频格式')
    return false
  }
  if (!isLtSize) {
    ElMessage.error(`视频大小不能超过 ${props.maxSize}MB!`)
    return false
  }

  return true
}

// 自定义上传（分片）
const handleCustomUpload = async (options) => {
  const { file } = options

  if (!beforeUpload(file)) return

  uploading.value = true
  progress.value = 0

  uploader = new ChunkUploader(file, {
    chunkSize: props.chunkSize * 1024 * 1024,
    onProgress: (p) => {
      progress.value = p
    },
    onSuccess: (result) => {
      const url = result.url || result.data?.url || result.data || result.path
      if (!url) {
        ElMessage.warning('上传成功，但未返回视频地址，请检查后端响应')
        return
      }
      emit('update:modelValue', url)
      emit('change', url)
      ElMessage.success('视频上传成功')
    },
    onError: (error) => {
      console.error('视频上传失败:', error)
      ElMessage.error('视频上传失败，请重试')
    }
  })

  try {
    await uploader.start()
  } catch (e) {
    // 错误已在 onError 中处理
  } finally {
    uploading.value = false
  }
}

// 删除视频
const handleRemove = () => {
  emit('update:modelValue', '')
  emit('change', '')
  progress.value = 0
}

// 取消上传
const handleCancel = async () => {
  if (uploader) {
    try {
      await uploader.cancel()
      ElMessage.success('已取消上传')
    } catch (error) {
      console.error('取消上传失败:', error)
    }
  }
  uploading.value = false
}
</script>

<style scoped lang="scss">
.upload-video {
  .upload-video-main {
    display: flex;
    align-items: center;
    gap: 12px;

    .upload-tip {
      font-size: 12px;
      color: #999;
    }
  }

  .upload-progress {
    margin-top: 10px;
    max-width: 400px;
  }

  .video-preview {
    margin-top: 16px;

    .video-player {
      width: 480px;
      max-width: 100%;
      border-radius: 4px;
      background: #000;
    }

    .video-actions {
      margin-top: 8px;
      display: flex;
      gap: 12px;
    }
  }
}
</style>