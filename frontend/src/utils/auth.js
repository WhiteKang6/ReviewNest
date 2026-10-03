// 登录态存取:token 存 sessionStorage(与旧 common.js 约定一致)
export function getToken() {
  return sessionStorage.getItem('token')
}

export function isLogin() {
  return !!getToken()
}
