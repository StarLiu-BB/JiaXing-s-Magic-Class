import { onBeforeUnmount, ref } from 'vue'

/** 响应式的媒体查询结果，组件卸载时自动解除监听 */
export function useMediaQuery(query) {
  const mql = window.matchMedia(query)
  const matches = ref(mql.matches)
  const update = (event) => {
    matches.value = event.matches
  }
  mql.addEventListener('change', update)
  onBeforeUnmount(() => mql.removeEventListener('change', update))
  return matches
}
