import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    // 侧边栏
    sidebar: {
      opened: true, // 侧边栏是否展开
      withoutAnimation: false // 是否无动画
    },
    // 设备类型
    device: 'desktop', // desktop | mobile
    // 标签视图
    visitedViews: [], // 已访问的视图
    cachedViews: [] // 缓存的视图
  }),
  getters: {
    // 侧边栏是否折叠
    isCollapse: (state) => !state.sidebar.opened
  },
  actions: {
    // 切换侧边栏
    toggleSidebar(withoutAnimation = false) {
      this.sidebar.opened = !this.sidebar.opened
      this.sidebar.withoutAnimation = withoutAnimation
    },
    // 关闭侧边栏
    closeSidebar(withoutAnimation = false) {
      this.sidebar.opened = false
      this.sidebar.withoutAnimation = withoutAnimation
    },
    // 打开侧边栏
    openSidebar(withoutAnimation = false) {
      this.sidebar.opened = true
      this.sidebar.withoutAnimation = withoutAnimation
    },
    // 设置设备类型
    setDevice(device) {
      this.device = device
    },
    // 添加访问的视图
    addVisitedView(view) {
      if (this.visitedViews.some(v => v.path === view.path)) return
      this.visitedViews.push(
        Object.assign({}, view, {
          title: view.meta?.title || 'no-name'
        })
      )
    },
    // 删除访问的视图
    delVisitedView(view) {
      return new Promise(resolve => {
        for (const [i, v] of this.visitedViews.entries()) {
          if (v.path === view.path) {
            this.visitedViews.splice(i, 1)
            break
          }
        }
        resolve([...this.visitedViews])
      })
    },
    // 删除其他访问的视图
    delOthersVisitedViews(view) {
      return new Promise(resolve => {
        this.visitedViews = this.visitedViews.filter(v => {
          return v.meta?.affix || v.path === view.path
        })
        resolve([...this.visitedViews])
      })
    },
    // 删除所有访问的视图
    delAllVisitedViews() {
      return new Promise(resolve => {
        const affixTags = this.visitedViews.filter(tag => tag.meta?.affix)
        this.visitedViews = affixTags
        resolve([...this.visitedViews])
      })
    },
    // 添加缓存的视图
    addCachedView(view) {
      if (this.cachedViews.includes(view.name)) return
      if (view.meta?.keepAlive) {
        this.cachedViews.push(view.name)
      }
    },
    // 删除缓存的视图
    delCachedView(view) {
      const index = this.cachedViews.indexOf(view.name)
      index > -1 && this.cachedViews.splice(index, 1)
    },
    // 删除其他缓存的视图
    delOthersCachedViews(view) {
      const index = this.cachedViews.indexOf(view.name)
      if (index > -1) {
        this.cachedViews = this.cachedViews.slice(index, index + 1)
      } else {
        this.cachedViews = []
      }
    },
    // 删除所有缓存的视图
    delAllCachedViews() {
      this.cachedViews = []
    }
  }
})

