<template>
  <div ref="scrollContainerRef" class="scroll-container" @wheel.prevent="handleScroll">
    <div ref="scrollWrapperRef" :style="{ left: left + 'px' }" class="scroll-wrapper">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const scrollContainerRef = ref(null)
const scrollWrapperRef = ref(null)
const left = ref(0)

const handleScroll = (e) => {
  const eventDelta = e.wheelDelta || -e.deltaY * 40
  const $scrollWrapper = scrollWrapperRef.value
  $scrollWrapper.scrollLeft = $scrollWrapper.scrollLeft + eventDelta / 4
}

const moveToTarget = (target) => {
  const $container = scrollContainerRef.value
  const $containerWidth = $container.offsetWidth
  const $scrollWrapper = scrollWrapperRef.value
  const targetLeft = target.offsetLeft
  const targetWidth = target.offsetWidth

  if (targetLeft < -left.value) {
    // 标签在可视区域左侧
    left.value = -targetLeft + 20
  } else if (targetLeft + targetWidth > -left.value + $containerWidth) {
    // 标签在可视区域右侧
    left.value = -(targetLeft - $containerWidth + targetWidth + 20)
  }
}

defineExpose({
  moveToTarget
})
</script>

<style lang="scss" scoped>
.scroll-container {
  white-space: nowrap;
  position: relative;
  overflow: hidden;
  width: 100%;
  
  .scroll-wrapper {
    position: absolute;
    transition: left 0.3s ease;
  }
}
</style>

