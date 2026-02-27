/**
 * 验证工具类
 */

/**
 * 验证手机号
 * @param {string} phone 手机号
 * @returns {boolean} 验证结果
 */
export function validPhone(phone) {
  const reg = /^1[3-9]\d{9}$/
  return reg.test(phone)
}

/**
 * 验证邮箱
 * @param {string} email 邮箱
 * @returns {boolean} 验证结果
 */
export function validEmail(email) {
  const reg = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  return reg.test(email)
}

/**
 * 验证URL
 * @param {string} url URL地址
 * @returns {boolean} 验证结果
 */
export function validURL(url) {
  const reg = /^(https?|ftp):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/
  return reg.test(url)
}

/**
 * 判断是否为外链
 * @param {string} path 路径
 * @returns {boolean} 是否为外链
 */
export function isExternal(path) {
  return /^(https?:|mailto:|tel:)/.test(path)
}

/**
 * 验证用户名（4-20位字母、数字、下划线）
 * @param {string} username 用户名
 * @returns {boolean} 验证结果
 */
export function validUsername(username) {
  const reg = /^[a-zA-Z0-9_]{4,20}$/
  return reg.test(username)
}

/**
 * 验证密码（6-20位，至少包含字母和数字）
 * @param {string} password 密码
 * @returns {boolean} 验证结果
 */
export function validPassword(password) {
  const reg = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,20}$/
  return reg.test(password)
}

/**
 * 验证身份证号
 * @param {string} idCard 身份证号
 * @returns {boolean} 验证结果
 */
export function validIdCard(idCard) {
  const reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/
  return reg.test(idCard)
}

/**
 * 验证IP地址
 * @param {string} ip IP地址
 * @returns {boolean} 验证结果
 */
export function validIP(ip) {
  const reg = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/
  return reg.test(ip)
}

/**
 * 验证中文
 * @param {string} str 字符串
 * @returns {boolean} 验证结果
 */
export function validChinese(str) {
  const reg = /^[\u4e00-\u9fa5]+$/
  return reg.test(str)
}

/**
 * 验证数字
 * @param {string} num 数字字符串
 * @returns {boolean} 验证结果
 */
export function validNumber(num) {
  const reg = /^-?\d+\.?\d*$/
  return reg.test(num)
}

/**
 * 验证正整数
 * @param {string} num 数字字符串
 * @returns {boolean} 验证结果
 */
export function validPositiveInteger(num) {
  const reg = /^[1-9]\d*$/
  return reg.test(num)
}

