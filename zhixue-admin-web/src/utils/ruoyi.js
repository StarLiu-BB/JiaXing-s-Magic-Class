/**
 * 通用工具类（参考 RuoYi 框架）
 */

/**
 * 日期格式化
 * @param {Date|string|number} time 时间
 * @param {string} pattern 格式，默认 'yyyy-MM-dd HH:mm:ss'
 * @returns {string} 格式化后的时间字符串
 */
export function parseTime(time, pattern) {
  if (arguments.length === 0 || !time) {
    return null
  }
  const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
  let date
  if (typeof time === 'object') {
    date = time
  } else {
    if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
      time = parseInt(time)
    }
    if ((typeof time === 'number') && (time.toString().length === 10)) {
      time = time * 1000
    }
    date = new Date(time)
  }
  const formatObj = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  const time_str = format.replace(/{([ymdhis])+}/g, (result, key) => {
    const value = formatObj[key]
    // Note: getDay() returns 0 on Sunday
    if (key === 'a') {
      return ['日', '一', '二', '三', '四', '五', '六'][value]
    }
    return value.toString().padStart(2, '0')
  })
  return time_str
}

/**
 * 格式化时间（相对时间）
 * @param {Date|string|number} time 时间
 * @param {string} pattern 格式，可选
 * @returns {string} 相对时间字符串
 */
export function formatTime(time, pattern) {
  const d = new Date(time)
  const now = Date.now()
  const diff = (now - d) / 1000

  if (diff < 30) {
    return '刚刚'
  } else if (diff < 3600) {
    // less 1 hour
    return Math.ceil(diff / 60) + '分钟前'
  } else if (diff < 3600 * 24) {
    return Math.ceil(diff / 3600) + '小时前'
  } else if (diff < 3600 * 24 * 2) {
    return '1天前'
  }
  if (pattern) {
    return parseTime(time, pattern)
  } else {
    return (
      d.getMonth() +
      1 +
      '月' +
      d.getDate() +
      '日' +
      d.getHours() +
      '时' +
      d.getMinutes() +
      '分'
    )
  }
}

/**
 * 参数转换
 * @param {object} params 参数对象
 * @returns {string} URL参数字符串
 */
export function param(data) {
  let url = ''
  for (const k in data) {
    const value = data[k] !== undefined ? data[k] : ''
    url += '&' + k + '=' + encodeURIComponent(value)
  }
  return url ? url.substring(1) : ''
}

/**
 * 获取URL参数
 * @param {string} name 参数名
 * @returns {string|null} 参数值
 */
export function getQueryParam(name) {
  const reg = new RegExp('(^|&)' + name + '=([^&]*)(&|$)')
  const r = window.location.search.substr(1).match(reg)
  if (r != null) {
    return decodeURIComponent(r[2])
  }
  return null
}

/**
 * 树形数据处理
 * @param {Array} data 数据数组
 * @param {string} id ID字段名，默认 'id'
 * @param {string} pid 父ID字段名，默认 'parentId'
 * @param {string} children 子节点字段名，默认 'children'
 * @returns {Array} 树形数据
 */
export function handleTree(data, id, pid, children) {
  const config = {
    id: id || 'id',
    parentId: pid || 'parentId',
    childrenList: children || 'children'
  }

  const childrenListMap = {}
  const nodeIds = {}
  const tree = []

  for (const d of data) {
    const parentId = d[config.parentId]
    if (childrenListMap[parentId] == null) {
      childrenListMap[parentId] = []
    }
    nodeIds[d[config.id]] = d
    childrenListMap[parentId].push(d)
  }

  for (const d of data) {
    const parentId = d[config.parentId]
    if (nodeIds[parentId] == null) {
      tree.push(d)
    }
  }

  for (const t of tree) {
    adaptToChildrenList(t)
  }

  function adaptToChildrenList(o) {
    if (childrenListMap[o[config.id]] !== null) {
      o[config.childrenList] = childrenListMap[o[config.id]]
    }
    if (o[config.childrenList]) {
      for (const c of o[config.childrenList]) {
        adaptToChildrenList(c)
      }
    }
  }
  return tree
}

/**
 * 数组转树形结构（简化版）
 * @param {Array} list 数据数组
 * @param {string} idKey ID字段名
 * @param {string} pidKey 父ID字段名
 * @param {string} childrenKey 子节点字段名
 * @returns {Array} 树形数据
 */
export function arrayToTree(list, idKey = 'id', pidKey = 'parentId', childrenKey = 'children') {
  const map = {}
  const roots = []

  // 创建映射
  list.forEach(item => {
    map[item[idKey]] = { ...item, [childrenKey]: [] }
  })

  // 构建树
  list.forEach(item => {
    const node = map[item[idKey]]
    const parentId = item[pidKey]

    if (parentId && map[parentId]) {
      map[parentId][childrenKey].push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

/**
 * 树形数据转数组（扁平化）
 * @param {Array} tree 树形数据
 * @param {string} childrenKey 子节点字段名
 * @returns {Array} 扁平化数组
 */
export function treeToArray(tree, childrenKey = 'children') {
  const result = []

  function traverse(nodes) {
    nodes.forEach(node => {
      const { [childrenKey]: children, ...rest } = node
      result.push(rest)
      if (children && children.length > 0) {
        traverse(children)
      }
    })
  }

  traverse(tree)
  return result
}

/**
 * 深拷贝
 * @param {any} source 源对象
 * @returns {any} 拷贝后的对象
 */
export function deepClone(source) {
  if (!source && typeof source !== 'object') {
    throw new Error('error arguments', 'deepClone')
  }
  const targetObj = source.constructor === Array ? [] : {}
  Object.keys(source).forEach(keys => {
    if (source[keys] && typeof source[keys] === 'object') {
      targetObj[keys] = deepClone(source[keys])
    } else {
      targetObj[keys] = source[keys]
    }
  })
  return targetObj
}

/**
 * 防抖函数
 * @param {Function} func 函数
 * @param {number} wait 等待时间（毫秒）
 * @param {boolean} immediate 是否立即执行
 * @returns {Function} 防抖后的函数
 */
export function debounce(func, wait, immediate) {
  let timeout
  return function() {
    const context = this
    const args = arguments
    const later = function() {
      timeout = null
      if (!immediate) func.apply(context, args)
    }
    const callNow = immediate && !timeout
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
    if (callNow) func.apply(context, args)
  }
}

/**
 * 节流函数
 * @param {Function} func 函数
 * @param {number} wait 等待时间（毫秒）
 * @returns {Function} 节流后的函数
 */
export function throttle(func, wait) {
  let timeout
  return function() {
    const context = this
    const args = arguments
    if (!timeout) {
      timeout = setTimeout(function() {
        timeout = null
        func.apply(context, args)
      }, wait)
    }
  }
}

/**
 * 获取文件扩展名
 * @param {string} filename 文件名
 * @returns {string} 扩展名
 */
export function getFileExtension(filename) {
  return filename.slice((filename.lastIndexOf('.') - 1 >>> 0) + 2)
}

/**
 * 格式化文件大小
 * @param {number} bytes 字节数
 * @returns {string} 格式化后的文件大小
 */
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

