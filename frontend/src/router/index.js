import { createRouter, createWebHistory } from 'vue-router'

// 旧静态页 URL 去掉 .html,query 参数不变(如 /shop-list?type=1&name=美食)
const routes = [
  { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/login', component: () => import('@/views/Login.vue') },
  { path: '/login2', component: () => import('@/views/Login2.vue') },
  { path: '/shop-list', component: () => import('@/views/ShopList.vue') },
  { path: '/shop-detail', component: () => import('@/views/ShopDetail.vue') },
  { path: '/blog-edit', component: () => import('@/views/BlogEdit.vue') },
  { path: '/blog-detail', component: () => import('@/views/BlogDetail.vue') },
  { path: '/info', component: () => import('@/views/Info.vue') },
  { path: '/info-edit', component: () => import('@/views/InfoEdit.vue') },
  { path: '/other-info', component: () => import('@/views/OtherInfo.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router
