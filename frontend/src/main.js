import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'

// 全局基础样式(html/body/#app 定高、foot 导航、el-tabs 定制)
import './assets/css/main.css'

const app = createApp(App)

app.use(router)
// Element Plus 全量引入(学习项目不做按需优化)
app.use(ElementPlus)

// el-icon-* 字体图标在 Element Plus 已移除,改用图标组件;全部注册以便模板直接 <el-icon><Search/></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

// 全量引入不会自动挂载 $message,需显式注入(保持旧代码 this.$message 的用法)
app.config.globalProperties.$message = ElMessage

// 401 跳转:由 main.js 注入,避免 request.js 静态 import router 造成循环依赖
setUnauthorizedHandler(() => router.push('/login'))

app.mount('#app')
