<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>字典管理</span>
          <el-button type="primary" @click="handleAddType">新增字典</el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-bar">
        <el-form-item label="字典名称">
          <el-input v-model="query.dictName" placeholder="请输入" clearable @keyup.enter="loadTypes" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTypes">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="dictName" label="字典名称" />
        <el-table-column prop="dictType" label="字典类型" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEditType(row)">编辑</el-button>
            <el-button type="warning" link @click="handleViewData(row)">字典数据</el-button>
            <el-button type="danger" link @click="handleDeleteType(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @size-change="loadTypes"
        @current-change="loadTypes"
      />
    </el-card>

    <!-- 字典类型编辑 -->
    <el-dialog v-model="typeDialog.visible" :title="typeDialog.title" width="480px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="如：用户状态" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="typeForm.dictType" placeholder="如：sys_user_status" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="typeDialog.saving" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据 -->
    <el-dialog v-model="dataDialog.visible" :title="`字典数据 - ${dataDialog.dictType}`" width="720px">
      <div class="data-toolbar">
        <el-button type="primary" size="small" @click="handleAddData">新增数据项</el-button>
      </div>
      <el-table :data="dataList" v-loading="dataDialog.loading" border size="small">
        <el-table-column prop="dictLabel" label="标签" />
        <el-table-column prop="dictValue" label="取值" />
        <el-table-column prop="dictSort" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditData(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteData(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider v-if="dataForm.visible" />
      <el-form v-if="dataForm.visible" :model="dataForm" label-width="80px" size="small">
        <el-form-item label="标签">
          <el-input v-model="dataForm.dictLabel" placeholder="展示文本" />
        </el-form-item>
        <el-form-item label="取值">
          <el-input v-model="dataForm.dictValue" placeholder="实际取值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.dictSort" :min="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dataForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="dataForm.saving" @click="submitData">保存</el-button>
          <el-button @click="dataForm.visible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictTypes,
  saveDictType,
  deleteDictType,
  listDictDataByType,
  saveDictData,
  deleteDictData
} from '@/api/system/dict'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, dictName: '', status: null })

const typeFormRef = ref()
const typeDialog = reactive({ visible: false, title: '', saving: false })
const typeForm = reactive({ id: null, dictName: '', dictType: '', status: 0, remark: '' })
const typeRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [
    { required: true, message: '请输入字典类型', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '只能包含小写字母、数字和下划线', trigger: 'blur' }
  ]
}

const dataList = ref([])
const dataDialog = reactive({ visible: false, loading: false, dictType: '' })
const dataForm = reactive({
  visible: false, saving: false, id: null,
  dictType: '', dictLabel: '', dictValue: '', dictSort: 1, status: 0
})

async function loadTypes() {
  loading.value = true
  try {
    const res = await listDictTypes(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 失败即提示，不再退回假数据掩盖故障
    ElMessage.error('加载字典列表失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNum = 1
  query.dictName = ''
  query.status = null
  loadTypes()
}

function handleAddType() {
  Object.assign(typeForm, { id: null, dictName: '', dictType: '', status: 0, remark: '' })
  typeDialog.title = '新增字典'
  typeDialog.visible = true
}

function handleEditType(row) {
  Object.assign(typeForm, { ...row })
  typeDialog.title = '编辑字典'
  typeDialog.visible = true
}

async function submitType() {
  await typeFormRef.value?.validate()
  typeDialog.saving = true
  try {
    await saveDictType({ ...typeForm })
    ElMessage.success('保存成功')
    typeDialog.visible = false
    loadTypes()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    typeDialog.saving = false
  }
}

async function handleDeleteType(row) {
  await ElMessageBox.confirm(
    `确认删除字典「${row.dictName}」？其下所有数据项将一并删除。`, '提示', { type: 'warning' })
  try {
    await deleteDictType(row.id)
    ElMessage.success('删除成功')
    loadTypes()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function handleViewData(row) {
  dataDialog.dictType = row.dictType
  dataDialog.visible = true
  dataForm.visible = false
  await loadData()
}

async function loadData() {
  dataDialog.loading = true
  try {
    const res = await listDictDataByType(dataDialog.dictType)
    dataList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载字典数据失败')
    dataList.value = []
  } finally {
    dataDialog.loading = false
  }
}

function handleAddData() {
  Object.assign(dataForm, {
    visible: true, saving: false, id: null,
    dictType: dataDialog.dictType, dictLabel: '', dictValue: '', dictSort: 1, status: 0
  })
}

function handleEditData(row) {
  Object.assign(dataForm, { ...row, visible: true, saving: false })
}

async function submitData() {
  if (!dataForm.dictLabel || !dataForm.dictValue) {
    ElMessage.warning('标签与取值不能为空')
    return
  }
  dataForm.saving = true
  try {
    await saveDictData({
      id: dataForm.id,
      dictType: dataForm.dictType,
      dictLabel: dataForm.dictLabel,
      dictValue: dataForm.dictValue,
      dictSort: dataForm.dictSort,
      status: dataForm.status
    })
    ElMessage.success('保存成功')
    dataForm.visible = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    dataForm.saving = false
  }
}

async function handleDeleteData(row) {
  await ElMessageBox.confirm(`确认删除数据项「${row.dictLabel}」？`, '提示', { type: 'warning' })
  try {
    await deleteDictData(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(loadTypes)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-bar {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.data-toolbar {
  margin-bottom: 12px;
}
</style>
