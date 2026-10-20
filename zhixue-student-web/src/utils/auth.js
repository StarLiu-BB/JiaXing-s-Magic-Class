const TOKEN_KEY = 'zx_student_token'

/** 输入框草稿（sessionStorage）：登录失效跳去登录、回来后问题仍在输入框里 */
export const CHAT_DRAFT_KEY = 'zx_chat_draft'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}
