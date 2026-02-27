<template>
  <div class="error-container">
    <div class="error-content">
      <el-result
        icon="warning"
        title="401"
        sub-title="抱歉，您没有权限访问该页面"
      >
        <template #extra>
          <el-button type="primary" @click="goHome">返回首页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style lang="scss" scoped>
.error-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: #f5f5f5;
  
  .error-content {
    width: 500px;
  }
}
</style>

