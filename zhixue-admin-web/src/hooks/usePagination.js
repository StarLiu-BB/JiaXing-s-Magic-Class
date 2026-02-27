import { ref } from 'vue'

/**
 * 通用分页 Hook
 * 用法：
 * const { pageNum, pageSize, total, handleSizeChange, handleCurrentChange } = usePagination(fetchList)
 * @param {Function} onChange 当分页变化时触发的回调函数 (pageNum, pageSize)
 */
export function usePagination(onChange) {
  const pageNum = ref(1)
  const pageSize = ref(10)
  const total = ref(0)

  const emitChange = () => {
    if (typeof onChange === 'function') {
      onChange(pageNum.value, pageSize.value)
    }
  }

  const handleSizeChange = (size) => {
    pageSize.value = size
    pageNum.value = 1
    emitChange()
  }

  const handleCurrentChange = (page) => {
    pageNum.value = page
    emitChange()
  }

  return {
    pageNum,
    pageSize,
    total,
    handleSizeChange,
    handleCurrentChange
  }
}


