<template>
  <div class="rich-editor">
    <Toolbar
      v-if="editorRef"
      class="editor-toolbar"
      :editor="editorRef"
      :default-config="toolbarConfig"
      :mode="mode"
    />
    <Editor
      class="editor-content"
      v-model="innerValue"
      :default-config="editorConfig"
      :mode="mode"
      @on-created="handleCreated"
      @on-change="handleChange"
    />
  </div>
</template>

<script setup>
import { shallowRef, ref, watch, onBeforeUnmount } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { ElMessage } from 'element-plus'
import { uploadImage, uploadVideo } from '@/api/media/upload'

const props = defineProps({
  /* v-model 绑定的内容（HTML 字符串） */
  modelValue: {
    type: String,
    default: ''
  },
  /* 编辑器模式：'default' | 'simple' */
  mode: {
    type: String,
    default: 'default'
  },
  /* 占位提示 */
  placeholder: {
    type: String,
    default: '请输入内容...'
  },
  /* 是否只读 */
  readonly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

// wangEditor 实例
const editorRef = shallowRef(null)

// 内部绑定值
const innerValue = ref(props.modelValue)

watch(
  () => props.modelValue,
  (val) => {
    if (val !== innerValue.value) {
      innerValue.value = val || ''
    }
  }
)

// 工具栏配置（可按需裁剪）
const toolbarConfig = {
  toolbarKeys: [
    'headerSelect',
    'bold',
    'italic',
    'underline',
    'through',
    'color',
    'bgColor',
    'fontSize',
    'fontFamily',
    'lineHeight',
    '|',
    'bulletedList',
    'numberedList',
    'todo',
    'justifyLeft',
    'justifyCenter',
    'justifyRight',
    'insertLink',
    'blockquote',
    'insertTable',
    '|',
    'insertImage',
    'insertVideo',
    'codeBlock',
    'undo',
    'redo',
    'fullScreen'
  ]
}

// 编辑器配置
const editorConfig = {
  placeholder: props.placeholder,
  readOnly: props.readonly,
  MENU_CONF: {}
}

// 图片上传配置
editorConfig.MENU_CONF.uploadImage = {
  async customUpload(file, insertFn) {
    try {
      const res = await uploadImage(file, () => {})
      const url =
        res.url ||
        res.data?.url ||
        res.data ||
        res.path ||
        res.img
      if (!url) {
        throw new Error('上传返回数据中未找到图片地址')
      }
      insertFn(url, file.name, url)
    } catch (e) {
      console.error('图片上传失败:', e)
      ElMessage.error('图片上传失败')
    }
  }
}

// 视频上传配置
editorConfig.MENU_CONF.uploadVideo = {
  async customUpload(file, insertFn) {
    try {
      const res = await uploadVideo(file, () => {})
      const url =
        res.url ||
        res.data?.url ||
        res.data ||
        res.path
      if (!url) {
        throw new Error('上传返回数据中未找到视频地址')
      }
      insertFn(url)
    } catch (e) {
      console.error('视频上传失败:', e)
      ElMessage.error('视频上传失败')
    }
  }
}

// 编辑器创建完成
const handleCreated = (editor) => {
  editorRef.value = editor
}

// 内容变更
const handleChange = (editor) => {
  const html = editor.getHtml()
  emit('update:modelValue', html)
  emit('change', html)
}

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor == null) return
  editor.destroy()
})
</script>

<style scoped lang="scss">
.rich-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;

  .editor-toolbar {
    border-bottom: 1px solid #dcdfe6;
  }

  .editor-content {
    min-height: 260px;
    padding: 10px 12px;
    line-height: 1.6;
  }
}
</style>