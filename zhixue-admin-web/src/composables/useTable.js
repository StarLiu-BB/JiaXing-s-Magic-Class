import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 表格通用逻辑 Hook
 * @param {Object} options 配置选项
 */
export function useTable(options = {}) {
  const {
    deleteApi,
    onRefresh,
    onDeleteSuccess,
    confirmText = '确定要删除吗？',
    successText = '删除成功'
  } = options

  const tableRef = ref(null)
  const selectedRows = ref([])

  /**
   * 刷新表格
   */
  const refreshTable = () => {
    if (onRefresh) {
      onRefresh()
    } else if (tableRef.value) {
      tableRef.value.refresh()
    }
  }

  /**
   * 删除单个
   */
  const handleDelete = async (row, deleteFn) => {
    try {
      await ElMessageBox.confirm(confirmText, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })

      const deleteFunction = deleteFn || deleteApi
      if (deleteFunction) {
        await deleteFunction(row.id)
        ElMessage.success(successText)
        if (onDeleteSuccess) {
          onDeleteSuccess()
        } else {
          refreshTable()
        }
      }
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除失败:', error)
      }
    }
  }

  /**
   * 批量删除
   */
  const handleBatchDelete = async (deleteFn) => {
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

      const deleteFunction = deleteFn || deleteApi
      if (deleteFunction) {
        const ids = selectedRows.value.map(row => row.id)
        await deleteFunction(ids)
        ElMessage.success(successText)
        selectedRows.value = []
        if (onDeleteSuccess) {
          onDeleteSuccess()
        } else {
          refreshTable()
        }
      }
    } catch (error) {
      if (error !== 'cancel') {
        console.error('批量删除失败:', error)
      }
    }
  }

  /**
   * 选择改变
   */
  const handleSelectionChange = (selection) => {
    selectedRows.value = selection
  }

  /**
   * 清空选择
   */
  const clearSelection = () => {
    if (tableRef.value) {
      tableRef.value.clearSelection()
    }
    selectedRows.value = []
  }

  return {
    tableRef,
    selectedRows,
    refreshTable,
    handleDelete,
    handleBatchDelete,
    handleSelectionChange,
    clearSelection
  }
}

