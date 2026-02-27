<template>
  <div class="home">
    <h1>欢迎使用 {{ appStore.title }}</h1>
    <p>Vue 3 + Vite + Element Plus</p>
  </div>
</template>

<script setup>
import { useAppStore } from '@/stores'

const appStore = useAppStore()
</script>

<style lang="scss" scoped>
.home {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  
  h1 {
    font-size: 32px;
    margin-bottom: 20px;
    color: #409eff;
  }
  
  p {
    font-size: 16px;
    color: #666;
  }
}
</style>

