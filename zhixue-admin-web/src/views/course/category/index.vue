<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分类管理</span>
          <el-button v-hasPermi="['course:category:edit']" type="primary" @click="openCreate()">新增分类</el-button>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="state-alert"
      />

      <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all>
        <el-table-column prop="name" label="分类名称" min-width="220" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <span v-hasPermi="['course:category:edit']">
              <el-button type="primary" link @click="openCreate(row)">新增子分类</el-button>
              <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0 && !errorMessage" description="暂无分类数据" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级分类" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            node-key="id"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addCategory, deleteCategory, getCategoryList, updateCategory } from '@/api/course/course'

const ROOT = '0'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const tableData = ref([])
const errorMessage = ref('')
const formRef = ref(null)
const form = reactive({ id: undefined, parentId: ROOT, name: '', sort: 1 })

const rules = {
  parentId: [{ required: true, message: '请选择上级分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

/** 后端雪花 ID 以字符串下发，统一按字符串比较 */
const idOf = (value) => (value === null || value === undefined ? ROOT : String(value))

const buildTree = (items) => {
  const map = new Map()
  const roots = []
  items.forEach((item) => map.set(idOf(item.id), { ...item, id: idOf(item.id), children: [] }))
  map.forEach((item) => {
    const parentId = idOf(item.parentId)
    if (parentId === ROOT || !map.has(parentId)) {
      roots.push(item)
    } else {
      map.get(parentId).children.push(item)
    }
  })
  return roots
}

const collectIds = (node, set = new Set()) => {
  set.add(node.id)
  ;(node.children || []).forEach((child) => collectIds(child, set))
  return set
}

/** 上级分类候选：编辑时禁用自身及其后代，避免形成环 */
const parentOptions = computed(() => {
  const blocked = new Set()
  if (form.id) {
    const find = (nodes) => nodes.forEach((n) => (n.id === form.id ? collectIds(n, blocked) : find(n.children || [])))
    find(tableData.value)
  }
  const mark = (nodes) => nodes.map((n) => ({ ...n, disabled: blocked.has(n.id), children: mark(n.children || []) }))
  return [{ id: ROOT, name: '顶级分类', children: mark(tableData.value) }]
})

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getCategoryList()
    tableData.value = buildTree(Array.isArray(response?.data) ? response.data : [])
  } catch (error) {
    console.error('获取分类列表失败:', error)
    tableData.value = []
    errorMessage.value = '分类列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const openCreate = (parent) => {
  Object.assign(form, { id: undefined, parentId: parent ? parent.id : ROOT, name: '', sort: 1 })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const openEdit = (row) => {
  Object.assign(form, { id: row.id, parentId: idOf(row.parentId), name: row.name, sort: Number(row.sort ?? 1) })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const payload = { id: form.id, parentId: form.parentId, name: form.name.trim(), sort: form.sort }
    if (form.id) {
      await updateCategory(payload)
      ElMessage.success('分类已更新')
    } else {
      await addCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await getList()
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除确认', { type: 'warning' })
    // 存在子分类或被课程引用时后端会拒绝并给出原因
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch (error) {
    if (error !== 'cancel') console.error('删除分类失败:', error)
  }
}

onMounted(getList)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.state-alert {
  margin-bottom: 12px;
}
</style>
