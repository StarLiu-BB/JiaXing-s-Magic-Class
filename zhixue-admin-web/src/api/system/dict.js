/**
 * 字典管理 API
 *
 * 对应后端 zhixue-system / SysDictController。
 * 此前该控制器是占位实现（只打日志便返回成功），本页也只展示硬编码假数据；
 * 现已接到真实的 sys_dict_type / sys_dict_data 表。
 */
import request from '@/utils/request'

/** 字典类型分页 */
export function listDictTypes(params) {
  return request({
    url: '/system/dict/type/list',
    method: 'get',
    params
  })
}

/** 全部启用的字典类型（下拉用） */
export function listAllDictTypes() {
  return request({
    url: '/system/dict/type/all',
    method: 'get'
  })
}

/** 新增或更新字典类型（带 id 即更新） */
export function saveDictType(data) {
  return request({
    url: '/system/dict/type/save',
    method: 'post',
    data
  })
}

export function deleteDictType(id) {
  return request({
    url: `/system/dict/type/${id}`,
    method: 'delete'
  })
}

/** 按类型编码取字典数据项 */
export function listDictDataByType(dictType) {
  return request({
    url: `/system/dict/data/type/${dictType}`,
    method: 'get'
  })
}

/** 新增或更新字典数据项（带 id 即更新） */
export function saveDictData(data) {
  return request({
    url: '/system/dict/data/save',
    method: 'post',
    data
  })
}

export function deleteDictData(id) {
  return request({
    url: `/system/dict/data/${id}`,
    method: 'delete'
  })
}
