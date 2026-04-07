import request from '@/utils/request'

export function listDanmakuPage(query) {
  return request({
    url: '/interaction/danmaku/page',
    method: 'get',
    params: query
  }).then((res) => {
    const payload = res?.data ?? res ?? {}
    return {
      list: payload.list || payload.records || [],
      total: payload.total || 0,
      pageNum: payload.pageNum || query?.pageNum || 1,
      pageSize: payload.pageSize || query?.pageSize || 10
    }
  })
}

export function listDanmakuHistory(roomId, limit = 50) {
  return request({
    url: '/interaction/danmaku/history',
    method: 'get',
    params: { roomId, limit }
  })
}
