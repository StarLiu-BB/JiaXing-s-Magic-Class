<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>文件列表</span>
          <el-upload
            v-hasPermi="['course:edit', 'course:publish']"
            :show-file-list="false"
            :http-request="handleUpload"
            :disabled="uploading"
          >
            <el-button type="primary" :loading="uploading">上传文件</el-button>
          </el-upload>
        </div>
      </template>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="tip"
        title="此处为单文件直传，适合封面、配图等小文件。文件地址会直接暴露对象存储路径，请勿把付费课时视频的地址分享到课时以外的地方。"
      />

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="文件名">
          <el-input v-model="queryParams.fileName" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="(item, value) in STATUS" :key="value" :label="item.text" :value="Number(value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="200" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="130" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).tag">{{ statusOf(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.fileUrl">
              <el-button type="primary" link @click="copyUrl(row)">复制地址</el-button>
              <el-link type="primary" :href="row.fileUrl" target="_blank" :underline="false">打开</el-link>
            </template>
            <span v-else class="muted">无地址</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="handleQuery"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMediaPage, uploadFile } from '@/api/media/upload'

// 与 media_schema.sql 一致
const STATUS = {
  0: { text: '上传中', tag: 'info' },
  1: { text: '合并完成', tag: 'info' },
  2: { text: '转码中', tag: 'warning' },
  3: { text: '成功', tag: 'success' },
  4: { text: '失败', tag: 'danger' }
}

const loading = ref(false)
const uploading = ref(false)
const total = ref(0)
const tableData = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, fileName: '', status: undefined })

const statusOf = (row) => STATUS[Number(row.status)] || { text: '未知', tag: 'info' }
const formatTime = (value) => (value ? String(value).replace('T', ' ').split('.')[0] : '-')

function formatSize(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(Math.floor(Math.log(n) / Math.log(1024)), units.length - 1)
  return `${(n / 1024 ** i).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

async function fetchList() {
  loading.value = true
  try {
    const query = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
    if (queryParams.fileName) query.fileName = queryParams.fileName.trim()
    if (queryParams.status !== undefined && queryParams.status !== null && queryParams.status !== '') {
      query.status = queryParams.status
    }
    const res = await listMediaPage(query)
    tableData.value = res.list
    total.value = res.total
  } catch (error) {
    tableData.value = []
    total.value = 0
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}

function resetQuery() {
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, fileName: '', status: undefined })
  fetchList()
}

async function handleUpload({ file }) {
  const formData = new FormData()
  formData.append('file', file)
  uploading.value = true
  try {
    await uploadFile(formData)
    ElMessage.success(`「${file.name}」上传成功`)
    handleQuery()
  } catch (error) {
    console.error(error)
  } finally {
    uploading.value = false
  }
}

async function copyUrl(row) {
  try {
    await navigator.clipboard.writeText(row.fileUrl)
    ElMessage.success('地址已复制')
  } catch {
    ElMessage.warning('浏览器不允许访问剪贴板，请点击「打开」后从地址栏复制')
  }
}

fetchList()
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tip,
.search-form {
  margin-bottom: 16px;
}

.muted {
  color: #909399;
  font-size: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
