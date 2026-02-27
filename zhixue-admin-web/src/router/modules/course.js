/**
 * 课程管理路由模块
 */
export default {
  path: '/course',
  component: () => import('@/layout/index.vue'),
  redirect: '/course/list',
  name: 'Course',
  meta: {
    title: '课程管理',
    icon: 'VideoPlay'
  },
  children: [
    {
      path: 'list',
      name: 'CourseList',
      component: () => import('@/views/course/list/index.vue'),
      meta: {
        title: '课程列表',
        icon: 'List',
        permission: ['course:list'],
        keepAlive: true
      }
    },
    {
      path: 'publish',
      name: 'CoursePublish',
      component: () => import('@/views/course/publish/index.vue'),
      meta: {
        title: '课程发布',
        icon: 'Plus',
        permission: ['course:publish'],
        keepAlive: false
      }
    },
    {
      path: 'chapter',
      name: 'CourseChapter',
      component: () => import('@/views/course/chapter/index.vue'),
      meta: {
        title: '章节管理',
        icon: 'Folder',
        permission: ['course:chapter:list'],
        keepAlive: true
      }
    },
    {
      path: 'category',
      name: 'CourseCategory',
      component: () => import('@/views/course/category/index.vue'),
      meta: {
        title: '分类管理',
        icon: 'Grid',
        permission: ['course:category:list'],
        keepAlive: true
      }
    }
  ]
}

