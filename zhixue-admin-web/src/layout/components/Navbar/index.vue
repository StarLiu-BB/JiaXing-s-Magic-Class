<template>
  <div class="navbar">
    <div class="navbar-content">
      <!-- 左侧：折叠按钮、面包屑 -->
      <div class="navbar-left">
        <!-- 折叠按钮 -->
        <div class="hamburger-container" @click="toggleSideBar">
          <el-icon :class="isCollapse ? 'is-active' : ''">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
        
        <!-- 面包屑 -->
        <breadcrumb class="breadcrumb-container" />
      </div>
      
      <!-- 右侧：工具栏 -->
      <div class="navbar-right">
        <!-- 全屏按钮 -->
        <screenfull class="right-menu-item hover-effect" />
        
        <!-- 设置按钮 -->
        <div class="right-menu-item hover-effect" @click="openSettings">
          <el-icon><Setting /></el-icon>
        </div>
        
        <!-- 用户头像下拉菜单 -->
        <el-dropdown class="avatar-container right-menu-item hover-effect" trigger="click">
          <div class="avatar-wrapper">
            <img :src="avatar" class="user-avatar" />
            <el-icon><CaretBottom /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <router-link to="/profile/index">
                <el-dropdown-item>个人中心</el-dropdown-item>
              </router-link>
              <el-dropdown-item divided @click="logout">
                <span style="display:block;">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Fold, Expand, Setting, CaretBottom } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import Breadcrumb from './Breadcrumb.vue'
import Screenfull from './Screenfull.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const isCollapse = computed(() => appStore.isCollapse)
const avatar = computed(() => userStore.avatar || 'https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif')

// 切换侧边栏
const toggleSideBar = () => {
  appStore.toggleSidebar()
}

// 打开设置
const openSettings = () => {
  // TODO: 打开设置面板
  ElMessage.info('设置功能开发中')
}

// 退出登录
const logout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
    ElMessage.success('退出登录成功')
  }).catch(() => {})
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 50px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  
  .navbar-content {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 15px;
  }
  
  .navbar-left {
    display: flex;
    align-items: center;
    
    .hamburger-container {
      line-height: 46px;
      height: 100%;
      float: left;
      cursor: pointer;
      transition: background 0.3s;
      -webkit-tap-highlight-color: transparent;
      
      &:hover {
        background: rgba(0, 0, 0, 0.025);
      }
      
      .el-icon {
        font-size: 20px;
        vertical-align: middle;
      }
    }
    
    .breadcrumb-container {
      margin-left: 10px;
    }
  }
  
  .navbar-right {
    display: flex;
    align-items: center;
    
    .right-menu-item {
      display: inline-block;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: #5a5e66;
      vertical-align: text-bottom;
      
      &.hover-effect {
        cursor: pointer;
        transition: background 0.3s;
        
        &:hover {
          background: rgba(0, 0, 0, 0.025);
        }
      }
    }
    
    .avatar-container {
      margin-right: 30px;
      
      .avatar-wrapper {
        display: flex;
        align-items: center;
        cursor: pointer;
        
        .user-avatar {
          cursor: pointer;
          width: 40px;
          height: 40px;
          border-radius: 50%;
          margin-right: 5px;
        }
        
        .el-icon {
          font-size: 12px;
        }
      }
    }
  }
}
</style>

