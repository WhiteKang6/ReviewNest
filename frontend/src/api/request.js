import axios from 'axios'

// 401 处理回调由 main.js 注入(router.push('/login')),
// 避免本文件静态 import router 造成 router → views → api → router 循环依赖
let unauthorizedHandler = null
export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn
}

const request = axios.create({
  baseURL: '/api',
  timeout: 2000
})

// 请求拦截器:把用户 token 放入请求头。
// 注意:每次请求现读 sessionStorage,修复旧 common.js"模块加载时读一次 token、
// 登录后同页请求拿到过期 token"的隐患。
request.interceptors.request.use(
  config => {
    const token = sessionStorage.getItem('token')
    if (token) config.headers['authorization'] = token
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器:后端 Result 无 code 字段,只靠 success 判断成败
request.interceptors.response.use(
  res => {
    if (!res.data.success) {
      return Promise.reject(res.data.errorMsg)
    }
    return res.data
  },
  error => {
    if (error.response && error.response.status === 401) {
      // 未登录,跳转登录页
      setTimeout(() => {
        if (unauthorizedHandler) unauthorizedHandler()
      }, 200)
      return Promise.reject('请先登录')
    }
    return Promise.reject('服务器异常')
  }
)

export default request
