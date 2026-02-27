import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTagsViewStore = defineStore('tagsView', () => {
  // State
  const visitedViews = ref([]) // 已访问的视图
  const cachedViews = ref([]) // 缓存的视图

  // Actions
  /**
   * 添加访问的视图
   */
  function addVisitedView(view) {
    if (visitedViews.value.some(v => v.path === view.path)) return
    visitedViews.value.push(
      Object.assign({}, view, {
        title: view.meta?.title || 'no-name'
      })
    )
  }

  /**
   * 删除访问的视图
   */
  function delVisitedView(view) {
    return new Promise(resolve => {
      for (const [i, v] of visitedViews.value.entries()) {
        if (v.path === view.path) {
          visitedViews.value.splice(i, 1)
          break
        }
      }
      resolve([...visitedViews.value])
    })
  }

  /**
   * 删除其他访问的视图
   */
  function delOthersVisitedViews(view) {
    return new Promise(resolve => {
      visitedViews.value = visitedViews.value.filter(v => {
        return v.meta?.affix || v.path === view.path
      })
      resolve([...visitedViews.value])
    })
  }

  /**
   * 删除所有访问的视图
   */
  function delAllVisitedViews() {
    return new Promise(resolve => {
      const affixTags = visitedViews.value.filter(tag => tag.meta?.affix)
      visitedViews.value = affixTags
      resolve([...visitedViews.value])
    })
  }

  /**
   * 添加缓存的视图
   */
  function addCachedView(view) {
    if (cachedViews.value.includes(view.name)) return
    if (view.meta?.keepAlive) {
      cachedViews.value.push(view.name)
    }
  }

  /**
   * 删除缓存的视图
   */
  function delCachedView(view) {
    const index = cachedViews.value.indexOf(view.name)
    index > -1 && cachedViews.value.splice(index, 1)
  }

  /**
   * 删除其他缓存的视图
   */
  function delOthersCachedViews(view) {
    const index = cachedViews.value.indexOf(view.name)
    if (index > -1) {
      cachedViews.value = cachedViews.value.slice(index, index + 1)
    } else {
      cachedViews.value = []
    }
  }

  /**
   * 删除所有缓存的视图
   */
  function delAllCachedViews() {
    cachedViews.value = []
  }

  /**
   * 刷新视图
   */
  function refreshView(view) {
    // 先删除缓存
    delCachedView(view)
    // 再添加缓存（触发重新渲染）
    return new Promise(resolve => {
      if (view.meta?.keepAlive) {
        cachedViews.value.push(view.name)
      }
      resolve()
    })
  }

  return {
    // State
    visitedViews,
    cachedViews,
    // Actions
    addVisitedView,
    delVisitedView,
    delOthersVisitedViews,
    delAllVisitedViews,
    addCachedView,
    delCachedView,
    delOthersCachedViews,
    delAllCachedViews,
    refreshView
  }
})

