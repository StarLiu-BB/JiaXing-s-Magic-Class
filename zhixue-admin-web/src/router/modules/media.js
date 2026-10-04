/**
 * 媒资管理路由模块
 */
export default {
  path: '/media',
  component: () => import('@/layout/index.vue'),
  redirect: '/media/file',
  name: 'Media',
  meta: {
    title: '媒资管理',
    icon: 'Files'
  },
  children: [
    {
      path: 'file',
      name: 'MediaFile',
      component: () => import('@/views/media/file/index.vue'),
      meta: {
        title: '文件列表',
        icon: 'Document',
        permission: ['media:file:list'],
        keepAlive: true
      }
    }
  ]
}
