import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  // State
  const sidebar = ref({
    opened: true, // 侧边栏是否展开
    withoutAnimation: false // 是否无动画
  })
  const device = ref('desktop') // desktop | mobile

  // Getters
  const isCollapse = computed(() => !sidebar.value.opened)

  // Actions
  /**
   * 切换侧边栏
   */
  function toggleSidebar(withoutAnimation = false) {
    sidebar.value.opened = !sidebar.value.opened
    sidebar.value.withoutAnimation = withoutAnimation
  }

  /**
   * 关闭侧边栏
   */
  function closeSidebar(withoutAnimation = false) {
    sidebar.value.opened = false
    sidebar.value.withoutAnimation = withoutAnimation
  }

  /**
   * 打开侧边栏
   */
  function openSidebar(withoutAnimation = false) {
    sidebar.value.opened = true
    sidebar.value.withoutAnimation = withoutAnimation
  }

  /**
   * 设置设备类型
   */
  function setDevice(newDevice) {
    device.value = newDevice
  }

  return {
    // State
    sidebar,
    device,
    // Getters
    isCollapse,
    // Actions
    toggleSidebar,
    closeSidebar,
    openSidebar,
    setDevice
  }
})

