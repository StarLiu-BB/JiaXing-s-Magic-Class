<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <el-button v-hasPermi="['system:menu:edit']" type="primary" @click="openCreate()">新增菜单</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="state-alert"
        title="管理端侧边栏由前端路由定义并按「权限标识」过滤；这里新增的权限标识需在后端接口与前端按钮上使用才会生效。"
      />
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="state-alert" />

      <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all>
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="TYPES[row.menuType]?.tag || 'info'" size="small">{{ TYPES[row.menuType]?.text || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="110" />
        <el-table-column prop="orderNum" label="排序" width="70" />
        <el-table-column prop="path" label="路由地址" min-width="120" />
        <el-table-column prop="perms" label="权限标识" min-width="180" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 0 ? 'success' : 'danger'" size="small">
              {{ Number(row.status) === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <span v-hasPermi="['system:menu:edit']">
              <el-button v-if="Number(row.menuType) !== 2" type="primary" link @click="openCreate(row)">新增</el-button>
              <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            </span>
            <el-button v-hasPermi="['system:menu:remove']" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0 && !errorMessage" description="暂无菜单数据" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜单' : '新增菜单'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children', disabled: 'disabled' }"
            node-key="id"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio v-for="(item, value) in TYPES" :key="value" :value="Number(value)">{{ item.text }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="form.menuName" maxlength="30" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :step="1" />
        </el-form-item>
        <template v-if="form.menuType !== 2">
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting" />
          </el-form-item>
          <el-form-item label="路由地址" prop="path">
            <el-input v-model="form.path" :placeholder="form.menuType === 0 ? '如 /system' : '如 user'" />
          </el-form-item>
        </template>
        <el-form-item v-if="form.menuType === 1" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如 system/user/index" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 0" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 2" label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
          <div class="form-tip">停用后该菜单的权限标识对所有角色失效</div>
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
import { addMenu, deleteMenu, listMenu, updateMenu } from '@/api/system/menu'

// 与种子数据一致：0 目录（component=Layout）、1 菜单（页面）、2 按钮（仅权限标识）
const TYPES = {
  0: { text: '目录', tag: 'warning' },
  1: { text: '菜单', tag: 'success' },
  2: { text: '按钮', tag: 'info' }
}
const ROOT = '0'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const tableData = ref([])
const errorMessage = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  id: undefined,
  parentId: ROOT,
  menuType: 1,
  menuName: '',
  orderNum: 1,
  icon: '',
  path: '',
  component: '',
  perms: '',
  visible: 1,
  status: 0
})
const form = reactive(emptyForm())

const rules = {
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入路由地址', trigger: 'blur' }],
  component: [{ required: true, message: '请输入组件路径', trigger: 'blur' }],
  perms: [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
}

/** 后端雪花 ID 超出 JS 安全整数时以字符串下发，统一按字符串比较 */
const idOf = (value) => (value === null || value === undefined ? ROOT : String(value))

const buildTree = (items) => {
  const map = new Map()
  const roots = []
  items.forEach((item) => map.set(idOf(item.id), { ...item, id: idOf(item.id), menuType: Number(item.menuType), children: [] }))
  map.forEach((item) => {
    const parentId = idOf(item.parentId)
    if (parentId === ROOT || !map.has(parentId)) roots.push(item)
    else map.get(parentId).children.push(item)
  })
  return roots
}

const collectIds = (node, set = new Set()) => {
  set.add(node.id)
  ;(node.children || []).forEach((child) => collectIds(child, set))
  return set
}

/** 上级候选：按钮不能有子级；编辑时禁用自身及后代，避免形成环 */
const parentOptions = computed(() => {
  const blocked = new Set()
  if (form.id) {
    const find = (nodes) => nodes.forEach((n) => (n.id === form.id ? collectIds(n, blocked) : find(n.children || [])))
    find(tableData.value)
  }
  const mark = (nodes) => nodes
    .filter((n) => n.menuType !== 2)
    .map((n) => ({ ...n, disabled: blocked.has(n.id), children: mark(n.children || []) }))
  return [{ id: ROOT, menuName: '顶级', children: mark(tableData.value) }]
})

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listMenu({})
    tableData.value = buildTree(Array.isArray(response?.data) ? response.data : [])
  } catch (error) {
    console.error('获取菜单列表失败:', error)
    tableData.value = []
    errorMessage.value = '菜单列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const openCreate = (parent) => {
  Object.assign(form, emptyForm(), {
    parentId: parent ? parent.id : ROOT,
    // 目录下默认建菜单，菜单下默认建按钮
    menuType: !parent ? 0 : parent.menuType === 0 ? 1 : 2,
    orderNum: (parent?.children?.length ?? tableData.value.length) + 1
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const openEdit = (row) => {
  Object.assign(form, emptyForm(), {
    id: row.id,
    parentId: idOf(row.parentId),
    menuType: Number(row.menuType),
    menuName: row.menuName,
    orderNum: Number(row.orderNum ?? 1),
    icon: row.icon || '',
    path: row.path || '',
    component: row.component || '',
    perms: row.perms || '',
    visible: Number(row.visible ?? 1),
    status: Number(row.status ?? 0)
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const type = form.menuType
  const payload = {
    id: form.id,
    parentId: form.parentId,
    menuType: type,
    menuName: form.menuName.trim(),
    orderNum: form.orderNum,
    icon: type === 2 ? '' : form.icon.trim(),
    path: type === 2 ? '' : form.path.trim(),
    component: type === 0 ? 'Layout' : type === 1 ? form.component.trim() : '',
    perms: type === 0 ? null : form.perms.trim(),
    visible: type === 2 ? 1 : form.visible,
    status: form.status
  }
  submitLoading.value = true
  try {
    if (form.id) {
      await updateMenu(payload)
      ElMessage.success('菜单已更新')
    } else {
      await addMenu(payload)
      ElMessage.success('菜单已创建')
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
    await ElMessageBox.confirm(`删除「${row.menuName}」将同时从所有角色中移除该授权，确定吗？`, '删除确认', { type: 'warning' })
    // 存在子菜单时后端会拒绝并给出原因
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch (error) {
    if (error !== 'cancel') console.error('删除菜单失败:', error)
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

.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
</style>
