/**
 * API 接口示例
 * 展示如何使用封装的 request 工具
 */
import request from '@/utils/request'

// GET 请求示例
export function getExample(params) {
  return request({
    url: '/example/list',
    method: 'get',
    params
  })
}

// POST 请求示例
export function postExample(data) {
  return request({
    url: '/example/add',
    method: 'post',
    data
  })
}

// PUT 请求示例
export function putExample(id, data) {
  return request({
    url: `/example/${id}`,
    method: 'put',
    data
  })
}

// DELETE 请求示例
export function deleteExample(id) {
  return request({
    url: `/example/${id}`,
    method: 'delete'
  })
}

