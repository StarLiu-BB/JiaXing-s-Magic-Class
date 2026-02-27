<template>
  <div class="app-container">
    <!-- 状态标签页 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="status-tabs">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="草稿" name="0" />
      <el-tab-pane label="待审核" name="2" />
      <el-tab-pane label="已发布" name="1" />
      <el-tab-pane label="已下架" name="3" />
    </el-tabs>

    <!-- 搜索区域 -->
    <el-form
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
      class="search-form"
    >
      <el-form-item label="课程标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入课程标题"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="分类" prop="categoryId">
        <el-select
          v-model="queryParams.categoryId"
          placeholder="请选择分类"
          clearable
          style="width: 200px"
        >
          <el-option
            v-for="category in categoryOptions"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="讲师" prop="teacherName">
        <el-input
          v-model="queryParams.teacherName"
          placeholder="请输入讲师名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="toolbar">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          v-hasPermi="['course:publish']"
          @click="handleAdd"
        >
          发布课程
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="selectedRows.length === 0"
          v-hasPermi="['course:delete']"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="courseList"
      @selection-change="handleSelectionChange"
      ref="tableRef"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="封面" width="120" align="center">
        <template #default="scope">
          <el-image
            :src="scope.row.cover"
            :preview-src-list="[scope.row.cover]"
            fit="cover"
            style="width: 80px; height: 60px; border-radius: 4px"
            :preview-teleported="true"
          />
        </template>
      </el-table-column>
      <el-table-column label="课程标题" prop="title" min-width="200" show-overflow-tooltip />
      <el-table-column label="分类" prop="categoryName" width="100" />
      <el-table-column label="讲师" prop="teacherName" width="120" />
      <el-table-column label="价格" width="150" align="center">
        <template #default="scope">
          <span class="price-current">¥{{ scope.row.price }}</span>
          <span v-if="scope.row.originalPrice" class="price-original">
            ¥{{ scope.row.originalPrice }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="销量" prop="sales" width="100" align="center" />
      <el-table-column label="学习人数" prop="studentCount" width="120" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="180" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="250" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="View"
            @click="handleView(scope.row)"
          >
            查看
          </el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            v-hasPermi="['course:edit']"
            @click="handleEdit(scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="scope.row.status === 0"
            link
            type="success"
            icon="Promotion"
            v-hasPermi="['course:publish']"
            @click="handlePublish(scope.row)"
          >
            发布
          </el-button>
          <el-button
            v-if="scope.row.status === 1"
            link
            type="warning"
            icon="Remove"
            v-hasPermi="['course:offline']"
            @click="handleOffline(scope.row)"
          >
            下架
          </el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            v-hasPermi="['course:delete']"
            @click="handleDelete(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="pageNum"
      v-model:limit="pageSize"
      @pagination="handlePagination"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCourse, deleteCourse, publishCourse, offlineCourse } from '@/api/course/course'
import { getCategoryList } from '@/api/course/course'
import { parseTime } from '@/utils/ruoyi'
import Pagination from '@/components/Pagination/index.vue'

const router = useRouter()

// 数据
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const courseList = ref([])
const categoryOptions = ref([])
const activeTab = ref('all')
const selectedRows = ref([])
const tableRef = ref(null)

const queryParams = reactive({
  title: '',
  categoryId: undefined,
  teacherName: '',
  status: undefined
})

// 获取状态类型
const getStatusType = (status) => {
  const statusMap = {
    0: 'info',    // 草稿
    1: 'success', // 已发布
    2: 'warning', // 待审核
    3: 'danger'   // 已下架
  }
  return statusMap[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    0: '草稿',
    1: '已发布',
    2: '待审核',
    3: '已下架'
  }
  return statusMap[status] || '未知'
}

// 获取课程列表
const getList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...queryParams
    }
    const response = await listCourse(params)
    // 后端返回格式: { code: 200, data: { records: [], total: 0 } }
    const data = response.data || response
    courseList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取课程列表失败:', error)
    courseList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 获取分类列表
const getCategoryOptions = async () => {
  try {
    const response = await getCategoryList()
    // 后端返回格式: { code: 200, data: [...] }
    categoryOptions.value = response.data || response || []
  } catch (error) {
    console.error('获取分类列表失败:', error)
    categoryOptions.value = []
  }
}

// 标签页切换
const handleTabChange = (tabName) => {
  if (tabName === 'all') {
    queryParams.status = undefined
  } else {
    queryParams.status = parseInt(tabName)
  }
  pageNum.value = 1
  getList()
}

// 搜索
const handleSearch = () => {
  pageNum.value = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.title = ''
  queryParams.categoryId = undefined
  queryParams.teacherName = ''
  handleSearch()
}

// 新增
const handleAdd = () => {
  router.push('/course/publish')
}

// 查看
const handleView = (row) => {
  router.push(`/course/detail?id=${row.id}`)
}

// 编辑
const handleEdit = (row) => {
  router.push(`/course/publish?id=${row.id}`)
}

// 发布
const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm('确定要发布该课程吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await publishCourse(row.id)
    ElMessage.success('发布成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('发布失败:', error)
    }
  }
}

// 下架
const handleOffline = async (row) => {
  try {
    await ElMessageBox.confirm('确定要下架该课程吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await offlineCourse(row.id)
    ElMessage.success('下架成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('下架失败:', error)
    }
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该课程吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const ids = Array.isArray(row.id) ? row.id : [row.id]
    await deleteCourse(ids.length === 1 ? ids[0] : ids)
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请选择要删除的数据')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条数据吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const ids = selectedRows.value.map(row => row.id)
    await deleteCourse(ids.length === 1 ? ids[0] : ids)
    ElMessage.success('删除成功')
    selectedRows.value = []
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
    }
  }
}

// 选择改变
const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

// 分页改变
const handlePagination = (pagination) => {
  pageNum.value = pagination.page
  pageSize.value = pagination.limit
  getList()
}

// 组件挂载
onMounted(() => {
  getList()
  getCategoryOptions()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
}

.status-tabs {
  background: #fff;
  padding: 0 20px;
  margin-bottom: 20px;
  border-radius: 4px;
}

.search-form {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
  margin-bottom: 20px;
}

.toolbar {
  margin-bottom: 20px;
}

:deep(.el-table) {
  background: #fff;
  border-radius: 4px;
}

.price-current {
  color: #ff4d4f;
  font-weight: bold;
  font-size: 16px;
}

.price-original {
  color: #999;
  text-decoration: line-through;
  font-size: 12px;
  margin-left: 8px;
}
</style>

