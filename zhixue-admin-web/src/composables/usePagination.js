import { ref, reactive } from 'vue'

/**
 * 分页 Hook
 * @param {Function} fetchData 获取数据的函数
 * @param {Object} initialQuery 初始查询参数
 */
export function usePagination(fetchData, initialQuery = {}) {
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const queryParams = reactive({
    ...initialQuery
  })

  /**
   * 获取数据
   */
  const getList = async () => {
    loading.value = true
    try {
      const params = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...queryParams
      }
      const response = await fetchData(params)
      total.value = response.total || 0
      return response.records || response.data || []
    } catch (error) {
      console.error('获取数据失败:', error)
      return []
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
   * 重置
   */
  const handleReset = () => {
    Object.keys(queryParams).forEach(key => {
      if (initialQuery[key] !== undefined) {
        queryParams[key] = initialQuery[key]
      } else {
        queryParams[key] = ''
      }
    })
    handleSearch()
  }

  /**
   * 页码改变
   */
  const handlePageChange = (page) => {
    pageNum.value = page
    getList()
  }

  /**
   * 每页数量改变
   */
  const handleSizeChange = (size) => {
    pageSize.value = size
    pageNum.value = 1
    getList()
  }

  return {
    loading,
    total,
    pageNum,
    pageSize,
    queryParams,
    getList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange
  }
}

