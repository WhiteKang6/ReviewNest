import request from './request'

// 所有接口按域收敛。返回值是响应拦截器处理后的 Result 对象
// ({ success, errorMsg, data, total }),调用处按旧约定 ({data}) 解构。
// 接口路径与后端 Controller 一一对应,无 /api 前缀(dev 由 vite proxy、prod 由 nginx rewrite 剥离)。

export const userApi = {
  sendCode(phone) { return request.post(`/user/code?phone=${phone}`) },
  login(form) { return request.post('/user/login', form) },
  logout() { return request.post('/user/logout') },
  me() { return request.get('/user/me') },
  info(id) { return request.get(`/user/info/${id}`) },
  getById(id) { return request.get(`/user/${id}`) }
}

export const shopApi = {
  listTypes() { return request.get('/shop-type/list') },
  ofType(params) { return request.get('/shop/of/type', { params }) },
  ofName(name) { return request.get('/shop/of/name?name=' + name) },
  detail(id) { return request.get(`/shop/${id}`) }
}

export const blogApi = {
  hot(current) { return request.get('/blog/hot?current=' + current) },
  detail(id) { return request.get(`/blog/${id}`) },
  like(id) { return request.put(`/blog/like/${id}`) },
  likes(id) { return request.get(`/blog/likes/${id}`) },
  ofMe() { return request.get('/blog/of/me') },
  ofFollow(params) { return request.get('/blog/of/follow', { params }) },
  ofUser(id, current) { return request.get('/blog/of/user', { params: { id, current } }) },
  create(data) { return request.post('/blog', data) }
}

export const followApi = {
  isFollowed(userId) { return request.get(`/follow/or/not/${userId}`) },
  follow(userId, isFollow) { return request.put(`/follow/${userId}/${isFollow}`) },
  common(userId) { return request.get(`/follow/common/${userId}`) }
}

export const voucherApi = {
  listByShop(shopId) { return request.get(`/voucher/list/${shopId}`) },
  seckill(id) { return request.post(`/voucher-order/seckill/${id}`) }
}

export const uploadApi = {
  // FormData 由 axios 自动生成 multipart 头(带 boundary),无需手写 Content-Type
  uploadBlog(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/upload/blog', formData)
  },
  deleteBlogImg(name) { return request.get('/upload/blog/delete?name=' + name) }
}
