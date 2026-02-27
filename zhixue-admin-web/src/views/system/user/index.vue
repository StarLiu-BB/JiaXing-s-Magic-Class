<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
      class="search-form"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="queryParams.username"
          placeholder="请输入用户名"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="queryParams.phone"
          placeholder="请输入手机号"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择状态"
          clearable
          style="width: 200px"
        >
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 350px"
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
          v-hasPermi="['system:user:add']"
          @click="handleAdd"
        >
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Edit"
          :disabled="selectedRows.length !== 1"
          v-hasPermi="['system:user:edit']"
          @click="handleUpdate"
        >
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="selectedRows.length === 0"
          v-hasPermi="['system:user:remove']"
          @click="handleBatchDelete"
        >
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          v-hasPermi="['system:user:export']"
          @click="handleExport"
        >
          导出
        </el-button>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="userList"
      @selection-change="handleSelectionChange"
      ref="tableRef"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="用户编号" prop="id" width="100" align="center" />
      <el-table-column label="用户名" prop="username" width="120" />
      <el-table-column label="昵称" prop="nickname" width="120" />
      <el-table-column label="部门" prop="deptName" width="120" />
      <el-table-column label="手机号" prop="phone" width="120" />
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="0"
            :inactive-value="1"
            v-hasPermi="['system:user:edit']"
            @change="handleStatusChange(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="180" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            v-hasPermi="['system:user:edit']"
            @click="handleUpdate(scope.row)"
          >
            修改
          </el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            v-hasPermi="['system:user:remove']"
            @click="handleDelete(scope.row)"
          >
            删除
          </el-button>
          <el-button
            link
            type="warning"
            icon="Key"
            v-hasPermi="['system:user:resetPwd']"
            @click="handleResetPwd(scope.row)"
          >
            重置密码
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

    <!-- 新增/修改弹窗 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="600px"
      append-to-body
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="form.id !== undefined"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!form.id">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">正常</el-radio>
            <el-radio :label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select
            v-model="form.roleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入备注"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog
      title="重置密码"
      v-model="resetPwdDialogVisible"
      width="400px"
      append-to-body
    >
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetPwdForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="resetPwdDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitResetPwd">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { useTable } from '@/composables/useTable'
import { listUser, getUser, addUser, updateUser, deleteUser, resetUserPwd, changeUserStatus } from '@/api/system/user'
import { listRole } from '@/api/system/role'
import { parseTime } from '@/utils/ruoyi'
import { validPhone, validEmail } from '@/utils/validate'
import Pagination from '@/components/Pagination/index.vue'

// 使用 Hooks
const { loading, total, pageNum, pageSize, queryParams } = usePagination(listUser)
const { tableRef, selectedRows, handleSelectionChange } = useTable({
  deleteApi: deleteUser
})

// 数据
const userList = ref([])
const dateRange = ref([])
const roleOptions = ref([])

// 表单引用
const queryFormRef = ref(null)
const formRef = ref(null)
const resetPwdFormRef = ref(null)

// 弹窗状态
const dialogVisible = ref(false)
const resetPwdDialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

// 表单数据
const form = reactive({
  id: undefined,
  username: '',
  nickname: '',
  phone: '',
  email: '',
  password: '',
  status: 0,
  roleIds: [],
  remark: ''
})

const resetPwdForm = reactive({
  userId: undefined,
  password: '',
  confirmPassword: ''
})

// 表单验证规则
const validatePhone = (rule, value, callback) => {
  if (value && !validPhone(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const validateEmail = (rule, value, callback) => {
  if (value && !validEmail(value)) {
    callback(new Error('请输入正确的邮箱'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (!form.id && !value) {
    callback(new Error('请输入密码'))
  } else if (value && value.length < 6) {
    callback(new Error('密码不能少于6位'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== resetPwdForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = reactive({
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  password: [
    { validator: validatePassword, trigger: 'blur' }
  ],
  status: [
    { required: true, message: '状态不能为空', trigger: 'change' }
  ]
})

const resetPwdRules = reactive({
  password: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

// 获取角色列表
const getRoleList = async () => {
  try {
    const response = await listRole({ pageNum: 1, pageSize: 1000 })
    roleOptions.value = response.data?.records || response.data || response.records || []
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

// 获取用户列表
const getList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...queryParams
    }
    // 处理时间范围
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const response = await listUser(params)
    userList.value = response.data?.records || response.records || response.data || []
    total.value = response.data?.total || response.total || 0
  } catch (error) {
    console.error('获取用户列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pageNum.value = 1
  getList()
}

// 重置
const handleReset = () => {
  dateRange.value = []
  queryParams.username = ''
  queryParams.phone = ''
  queryParams.status = ''
  handleSearch()
}

// 新增
const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
}

// 修改
const handleUpdate = async (row) => {
  const userId = row.id || selectedRows.value[0]?.id
  if (!userId) {
    ElMessage.warning('请选择要修改的数据')
    return
  }

  try {
    const response = await getUser(userId)
    Object.assign(form, response)
    form.roleIds = response.roleIds || []
    dialogTitle.value = '修改用户'
    dialogVisible.value = true
  } catch (error) {
    console.error('获取用户详情失败:', error)
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(row.id)
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
    await deleteUser(ids)
    ElMessage.success('删除成功')
    selectedRows.value = []
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
    }
  }
}

// 状态改变
const handleStatusChange = async (row) => {
  const text = row.status === 0 ? '启用' : '停用'
  try {
    await changeUserStatus(row.id, row.status)
    ElMessage.success(`${text}成功`)
  } catch (error) {
    row.status = row.status === 0 ? 1 : 0
    console.error(`${text}失败:`, error)
  }
}

// 重置密码
const handleResetPwd = (row) => {
  resetPwdForm.userId = row.id
  resetPwdForm.password = ''
  resetPwdForm.confirmPassword = ''
  resetPwdDialogVisible.value = true
}

// 提交重置密码
const submitResetPwd = async () => {
  resetPwdFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await resetUserPwd(resetPwdForm.userId, resetPwdForm.password)
        ElMessage.success('重置密码成功')
        resetPwdDialogVisible.value = false
      } catch (error) {
        console.error('重置密码失败:', error)
      }
    }
  })
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 提交表单
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await updateUser(form)
          ElMessage.success('修改成功')
        } else {
          await addUser(form)
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        getList()
      } catch (error) {
        console.error('提交失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  form.id = undefined
  form.username = ''
  form.nickname = ''
  form.phone = ''
  form.email = ''
  form.password = ''
  form.status = 0
  form.roleIds = []
  form.remark = ''
  formRef.value?.resetFields()
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
  getRoleList()
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
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

.dialog-footer {
  text-align: right;
}
</style>

