<template>
  <!-- 任务 5 把本页整体替换为完整的对话页；这里先提供登录后的落地页 -->
  <div class="chat-view">
    <h1>有什么想学的？</h1>
    <p>问编程问题，AI 课程助教会结合课程资料回答，并推荐相关的视频课</p>
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  height: 100%;
  overflow-y: auto;
  padding: 14vh 24px 24px;
  text-align: center;

  h1 {
    font-size: 28px;
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 8px;
    color: var(--el-text-color-secondary);
  }
}
</style>
