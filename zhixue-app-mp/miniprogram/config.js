/**
 * 小程序环境配置。
 *
 * 微信小程序没有构建期环境变量注入，因此用显式配置表 + Storage 覆盖：
 *  - 开发联调：默认走本地网关；也可在开发者工具 Storage 里设 BASE_URL 临时切换。
 *  - 正式发布：必须把 ENV 改为 'prod' 并填入 HTTPS 域名。
 *    微信正式版强制校验域名且只允许 https/wss，http 与 IP 一律无法通过。
 */

const ENV = 'dev'

const CONFIGS = {
  dev: {
    baseUrl: 'http://127.0.0.1:19001',
    wsUrl: 'ws://127.0.0.1:19999/ws'
  },
  prod: {
    // TODO(发布前必填)：换成已在微信后台配置的 https/wss 域名
    baseUrl: '',
    wsUrl: ''
  }
}

/** HTTP 基址：Storage 覆盖优先，便于真机调试 */
function getBaseUrl() {
  const override = wx.getStorageSync('BASE_URL')
  if (override) {
    return override
  }
  const url = CONFIGS[ENV].baseUrl
  if (!url) {
    // 不返回一个能"看起来正常"的默认值，避免请求静默打到错误地址
    throw new Error(`[config] ENV=${ENV} 的 baseUrl 未配置，请在 config.js 中填写`)
  }
  return url
}

/** WebSocket 基址：Storage 覆盖优先 */
function getWsUrl() {
  const override = wx.getStorageSync('WS_URL')
  if (override) {
    return override
  }
  const url = CONFIGS[ENV].wsUrl
  if (!url) {
    throw new Error(`[config] ENV=${ENV} 的 wsUrl 未配置，请在 config.js 中填写`)
  }
  return url
}

module.exports = {
  ENV,
  getBaseUrl,
  getWsUrl
}
