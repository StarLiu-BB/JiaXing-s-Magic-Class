let expiredHandler = () => Promise.resolve()
let recovering = null

export function setSessionExpiredHandler(handler) {
  expiredHandler = handler
}

export function handleSessionExpired() {
  if (recovering) return recovering
  try {
    recovering = Promise.resolve(expiredHandler()).finally(() => { recovering = null })
  } catch (error) {
    recovering = Promise.reject(error)
    recovering.catch(() => { recovering = null })
  }
  return recovering
}

export function recoverFromExpiredLogin(store, router) {
  store.resetToken()
  if (router.currentRoute.value.path === '/login') return Promise.resolve()
  return router.replace('/login')
}
