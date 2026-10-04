<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-button v-hasPermi="['system:role:edit']" type="primary" @click="openCreate">新增角色</el-button>
        </div>
      </template>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="state-alert" />

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="角色标识" min-width="140" />
        <el-table-column prop="roleSort" label="排序" width="80" />
        <el-table-column label="授权菜单数" width="110">
          <template #default="{ row }">{{ (row.menuIds || []).length }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.status) === 0"
              :disabled="!canEdit || isSuperAdmin(row)"
              @change="(val) => onStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-hasPermi="['system:role:edit']" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-tooltip v-if="isSuperAdmin(row)" content="超级管理员角色不可删除">
              <span v-hasPermi="['system:role:remove']"><el-button type="danger" link disabled>删除</el-button></span>
            </el-tooltip>
            <el-button v-else v-hasPermi="['system:role:remove']" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0 && !errorMessage" description="暂无角色数据" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="620px" @opened="syncTreeChecked">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" maxlength="30" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" maxlength="30" :disabled="isSuperAdmin(form)" placeholder="如 TEACHER，登录令牌中的角色即此值" />
        </el-form-item>
        <el-form-item label="排序" prop="roleSort">
          <el-input-number v-model="form.roleSort" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="tree-box">
            <el-tree
              ref="treeRef"
              :data="menuTree"
              :props="{ label: 'menuName', children: 'children' }"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              @check="onTreeCheck"
            />
          </div>
          <div v-if="isSuperAdmin(form)" class="form-tip">超级管理员必须保留「角色管理」的查看与编辑权限，否则将无法再修改任何授权</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { addRole, changeRoleStatus, deleteRole, getRole, listRole, updateRole } from '@/api/system/role'
import { listMenu } from '@/api/system/menu'

const userStore = useUserStore()
const canEdit = computed(() => (userStore.permissions || []).includes('system:role:edit'))

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const tableData = ref([])
const errorMessage = ref('')
const formRef = ref(null)
const treeRef = ref(null)
const menuTree = ref([])
const parentOf = ref(new Map())

const emptyForm = () => ({ id: undefined, roleName: '', roleKey: '', roleSort: 1, status: 0, remark: '', menuIds: [] })
const form = reactive(emptyForm())

const rules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]
}

const isSuperAdmin = (role) => String(role?.roleKey || '').toUpperCase() === 'ADMIN'

/** 后端雪花 ID 超出 JS 安全整数时以字符串下发，统一按字符串比较 */
const key = (id) => String(id)

const buildTree = (items) => {
  const map = new Map()
  const roots = []
  items.forEach((item) => map.set(key(item.id), { ...item, id: key(item.id), children: [] }))
  map.forEach((item) => {
    const parent = map.get(key(item.parentId))
    if (parent) parent.children.push(item)
    else roots.push(item)
  })
  const sort = (nodes) => nodes.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0)).forEach((n) => sort(n.children))
  sort(roots)
  const parents = new Map()
  map.forEach((item) => map.has(key(item.parentId)) && parents.set(item.id, key(item.parentId)))
  return { roots, parents }
}

const loadMenus = async () => {
  const response = await listMenu({})
  const { roots, parents } = buildTree(Array.isArray(response?.data) ? response.data : [])
  menuTree.value = roots
  parentOf.value = parents
}

/**
 * 树按节点独立勾选（check-strictly），再手动联动：
 * 勾选时连同下级与全部上级（页面可达）；取消时连同下级（页面都没有，按钮无意义）。
 * 不用 el-tree 自带的父子联动：那种模式下取消某页面的全部按钮会连页面本身一起取消，
 * 无法表达"能看订单列表、不能退款"这类授权。
 */
const onTreeCheck = (data, { checkedKeys }) => {
  const tree = treeRef.value
  const checked = checkedKeys.includes(data.id)
  const walkDown = (node) => (node.children || []).forEach((child) => {
    tree.setChecked(child.id, checked, false)
    walkDown(child)
  })
  walkDown(data)
  if (checked) {
    for (let p = parentOf.value.get(data.id); p; p = parentOf.value.get(p)) {
      tree.setChecked(p, true, false)
    }
  }
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listRole({})
    tableData.value = Array.isArray(response?.data) ? response.data : []
  } catch (error) {
    console.error('获取角色列表失败:', error)
    tableData.value = []
    errorMessage.value = '角色列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const syncTreeChecked = async () => {
  await nextTick()
  treeRef.value?.setCheckedKeys((form.menuIds || []).map(key))
}

const openCreate = async () => {
  Object.assign(form, emptyForm())
  try {
    await loadMenus()
  } catch (error) {
    console.error(error)
    return
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const openEdit = async (row) => {
  try {
    const [res] = await Promise.all([getRole(row.id), loadMenus()])
    const detail = res?.data || row
    Object.assign(form, emptyForm(), {
      id: detail.id,
      roleName: detail.roleName,
      roleKey: detail.roleKey,
      roleSort: Number(detail.roleSort ?? 1),
      status: Number(detail.status ?? 0),
      remark: detail.remark || '',
      menuIds: detail.menuIds || []
    })
    dialogVisible.value = true
    formRef.value?.clearValidate()
  } catch (error) {
    console.error(error)
  }
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const menuIds = treeRef.value?.getCheckedKeys() || []
  const payload = {
    id: form.id,
    roleName: form.roleName.trim(),
    roleKey: form.roleKey.trim(),
    roleSort: form.roleSort,
    status: form.status,
    remark: form.remark,
    menuIds
  }
  submitLoading.value = true
  try {
    if (form.id) {
      await updateRole(payload)
      ElMessage.success('角色已更新，相关用户重新登录后生效')
    } else {
      await addRole(payload)
      ElMessage.success('角色已创建')
    }
    dialogVisible.value = false
    await getList()
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

const onStatusChange = async (row, enabled) => {
  try {
    if (!enabled) {
      await ElMessageBox.confirm(`停用后拥有「${row.roleName}」角色的用户将失去其全部权限，确定吗？`, '停用确认', { type: 'warning' })
    }
    await changeRoleStatus(row.id, enabled ? 0 : 1)
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  } finally {
    await getList()
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`删除角色「${row.roleName}」将同时解除它与所有用户、菜单的关联，确定吗？`, '删除确认', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch (error) {
    if (error !== 'cancel') console.error('删除角色失败:', error)
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

.tree-box {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px 0;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
