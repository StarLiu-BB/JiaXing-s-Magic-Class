<template>
  <div class="upload-image">
    <el-upload
      class="upload-image-uploader"
      list-type="picture-card"
      :http-request="handleCustomUpload"
      :file-list="fileList"
      :limit="limit"
      :multiple="multiple"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
      :before-upload="beforeUpload"
      :on-exceed="handleExceed"
      :show-file-list="true"
    >
      <el-icon><Plus /></el-icon>
      <template #tip>
        <div class="el-upload__tip">
          {{ tipText }}
        </div>
      </template>
    </el-upload>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" width="600px" append-to-body>
      <img :src="previewImage" alt="预览图片" style="width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { uploadImage } from '@/api/media/upload'

const props = defineProps({
  // v-model 绑定的值：单图为字符串，多图为字符串数组
  modelValue: {
    type: [String, Array],
    default: ''
  },
  // 是否多图上传
  multiple: {
    type: Boolean,
    default: false
  },
  // 最大上传数量（多图时生效）
  limit: {
    type: Number,
    default: 1
  },
  // 单个文件大小限制（MB）
  maxSize: {
    type: Number,
    default: 2
  },
  // 允许的文件类型
  accept: {
    type: Array,
    default: () => ['image/jpeg', 'image/png', 'image/jpg']
  },
  // 提示文案
  tip: {
    type: String,
    default: '支持 JPG/PNG，大小不超过 2MB'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const fileList = ref([])
const previewVisible = ref(false)
const previewImage = ref('')

const tipText = computed(() => props.tip || `支持图片格式，大小不超过 ${props.maxSize}MB`)

// 初始化 / 监听外部 v-model
const initFileList = (val) => {
  if (!val) {
    fileList.value = []
    return
  }

  const urls = Array.isArray(val) ? val : [val]
  fileList.value = urls.map((url, index) => ({
    name: `image_${index}`,
    url,
    status: 'success'
  }))
}

watch(
  () => props.modelValue,
  (val) => {
    initFileList(val)
  },
  { immediate: true }
)

// 自定义上传逻辑
const handleCustomUpload = async (options) => {
  const { file, onProgress, onSuccess, onError } = options

  try {
    const response = await uploadImage(file, (event) => {
      if (onProgress && event.total) {
        onProgress({ percent: (event.loaded / event.total) * 100 })
      }
    })

    const url =
      response.url ||
      response.data?.url ||
      response.data ||
      response.path ||
      response.img

    if (!url) {
      throw new Error('上传返回数据中未找到图片地址')
    }

    const item = {
      name: file.name,
      url,
      status: 'success'
    }
    fileList.value.push(item)

    syncModelValue()

    if (onSuccess) onSuccess(response)
    ElMessage.success('图片上传成功')
  } catch (error) {
    console.error('图片上传失败:', error)
    ElMessage.error('图片上传失败')
    if (onError) onError(error)
  }
}

// 删除
const handleRemove = (file, list) => {
  fileList.value = list
  syncModelValue()
}

// 预览
const handlePreview = (file) => {
  previewImage.value = file.url
  previewVisible.value = true
}

// 上传前校验
const beforeUpload = (file) => {
  const isAllowedType =
    !props.accept || props.accept.length === 0 || props.accept.includes(file.type)
  const isLtSize = file.size / 1024 / 1024 < props.maxSize

  if (!isAllowedType) {
    ElMessage.error('不支持的图片格式')
    return false
  }
  if (!isLtSize) {
    ElMessage.error(`图片大小不能超过 ${props.maxSize}MB!`)
    return false
  }

  return true
}

// 超出限制
const handleExceed = () => {
  ElMessage.warning(`最多上传 ${props.limit} 张图片`)
}

// 同步 v-model
const syncModelValue = () => {
  const urls = fileList.value.map((item) => item.url)
  const value = props.multiple ? urls : urls[0] || ''
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped lang="scss">
.upload-image {
  .upload-image-uploader {
    :deep(.el-upload--picture-card) {
      width: 120px;
      height: 120px;
    }

    :deep(.el-upload-list__item) {
      width: 120px;
      height: 120px;
    }
  }
}
</style>