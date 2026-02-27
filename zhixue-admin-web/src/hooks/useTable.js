import { ref, reactive, unref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 通用表格 Hook
 *
 * 用法示例：
 * const {
 *   loading,
 *   dataList,
 *   selectedRows,
 *   queryParams,
 *   pageNum,
 *   pageSize,
 *   total,
 *   getList,
 *   handleSearch,
 *   handleReset,
 *   handleSelectionChange,
 *   handleDelete,
 *   handleBatchDelete,
 *   handleExport
 * } = useTable({
 *   api: listApi,
 *   deleteApi,
 *   exportApi,
 *   immediate: true,
 *   params: { title: '', status: undefined },
 *   transformResponse: (res) => ({ records: res.records, total: res.total })
 * })
 */
export function useTable(options = {}) {
  const {
    api, // 列表接口 (必传)
    deleteApi, // 删除接口 (可选)
    exportApi, // 导出接口 (可选)
    params = {}, // 初始查询参数
    immediate = true, // 是否立即加载
    transformResponse, // 自定义响应转换
    onSuccess // 加载成功回调
  } = options

  if (typeof api !== 'function') {
    console.warn('[useTable] options.api 必须是一个函数')
  }

  const loading = ref(false)
  const dataList = ref([])
  const selectedRows = ref([])

  const pageNum = ref(1)
  const pageSize = ref(10)
  const total = ref(0)

  const queryParams = reactive({
    ...params
  })

  /**
   * 获取列表
   */
  const getList = async () => {
    if (typeof api !== 'function') return

    loading.value = true
    try {
      const query = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...unref(queryParams)
      }
      const res = await api(query)

      let records
      let totalCount

      if (typeof transformResponse === 'function') {
        const t = transformResponse(res)
        records = t.records
        totalCount = t.total
      } else {
        // 默认兼容多种返回结构
        records = res.records || res.rows || res.data || res.list || []
        totalCount =
          res.total ||
          (res.page && res.page.total) ||
          (Array.isArray(records) ? records.length : 0)
      }

      dataList.value = records || []
      total.value = totalCount || 0

      if (typeof onSuccess === 'function') {
        onSuccess(res)
      }
    } catch (error) {
      console.error('[useTable] 获取列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 搜索
   */
  const handleSearch = () => {
    pageNum.value = 1
    getList()
  }

  /**
   * 重置查询参数
   */
  const handleReset = () => {
    Object.keys(queryParams).forEach((key) => {
      const value = params[key]
      // 简单重置为初始值（null/undefined/''）
      queryParams[key] =
        value === undefined || value === null ? (typeof value === 'number' ? undefined : '') : value
    })
    handleSearch()
  }

  /**
   * 选择变化
   */
  const handleSelectionChange = (rows) => {
    selectedRows.value = rows || []
  }

  /**
   * 删除单条数据
   * @param {Object|number|string} rowOrId 行数据或主键
   */
  const handleDelete = async (rowOrId) => {
    if (typeof deleteApi !== 'function') {
      console.warn('[useTable] 未配置 deleteApi，无法删除')
      return
    }

    const id = typeof rowOrId === 'object' ? rowOrId.id : rowOrId
    if (!id) {
      console.warn('[useTable] 删除失败：未找到有效的 id')
      return
    }

    try {
      await deleteApi(id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
      console.error('[useTable] 删除失败:', error)
    }
  }

  /**
   * 批量删除
   * @param {Array} rows 可选，自定义要删除的行数组，不传则使用 selectedRows
   */
  const handleBatchDelete = async (rows) => {
    if (typeof deleteApi !== 'function') {
      console.warn('[useTable] 未配置 deleteApi，无法删除')
      return
    }

    const targets = rows && rows.length ? rows : selectedRows.value
    if (!targets.length) {
      ElMessage.warning('请选择要删除的数据')
      return
    }

    const ids = targets.map((item) => item.id).filter(Boolean)
    if (!ids.length) {
      console.warn('[useTable] 批量删除失败：未找到有效的 id 集合')
      return
    }

    try {
      await deleteApi(ids.length === 1 ? ids[0] : ids)
      ElMessage.success('删除成功')
      selectedRows.value = []
      getList()
    } catch (error) {
      console.error('[useTable] 批量删除失败:', error)
    }
  }

  /**
   * 导出
   * @param {Object} extraParams 额外参数
   */
  const handleExport = async (extraParams = {}) => {
    if (typeof exportApi !== 'function') {
      console.warn('[useTable] 未配置 exportApi，无法导出')
      return
    }

    try {
      const query = {
        ...unref(queryParams),
        ...extraParams
      }
      await exportApi(query)
    } catch (error) {
      console.error('[useTable] 导出失败:', error)
    }
  }

  // 初始加载
  if (immediate) {
    getList()
  }

  return {
    loading,
    dataList,
    selectedRows,
    queryParams,
    pageNum,
    pageSize,
    total,
    getList,
    handleSearch,
    handleReset,
    handleSelectionChange,
    handleDelete,
    handleBatchDelete,
    handleExport
  }
}


