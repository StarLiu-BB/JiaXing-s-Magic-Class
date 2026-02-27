import { ref, onMounted } from 'vue'
import request from '@/utils/request'

/**
 * 通用字典 Hook
 *
 * 用法：
 * const { loading, refresh, sys_normal_disable, sys_user_sex } = useDict('sys_normal_disable', 'sys_user_sex')
 *
 * 返回的每个字典都是一个 ref([])，元素结构根据后端约定，一般为：
 * { dictLabel, dictValue, listClass, cssClass, default }
 */
export function useDict(...dictTypes) {
  const loading = ref(false)

  // 为每个字典类型创建一个 ref([])，返回对象中直接挂载同名属性
  const dictMap = {}
  dictTypes.forEach((type) => {
    dictMap[type] = ref([])
  })

  const fetchDict = async () => {
    if (!dictTypes.length) return
    loading.value = true
    try {
      await Promise.all(
        dictTypes.map(async (type) => {
          try {
            const res = await request({
              url: `/system/dict/data/type/${type}`,
              method: 'get'
            })
            // 兼容多种返回结构
            const list = res.data || res.rows || res || []
            dictMap[type].value = Array.isArray(list) ? list : []
          } catch (err) {
            console.error(`[useDict] 获取字典 ${type} 失败:`, err)
            dictMap[type].value = []
          }
        })
      )
    } finally {
      loading.value = false
    }
  }

  // 暴露刷新方法
  const refresh = () => fetchDict()

  onMounted(() => {
    fetchDict()
  })

  return {
    loading,
    refresh,
    ...dictMap
  }
}


