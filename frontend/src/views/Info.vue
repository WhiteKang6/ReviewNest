<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, blogApi } from '@/api'
import FootBar from '@/components/FootBar.vue'
import '@/assets/css/info.css'

const router = useRouter()
const user = ref('')
const activeName = ref('1')
const info = ref({})
const blogs = ref([]) // 我的笔记
const blogs2 = ref([]) // 关注的人的博客(feed)
const params = ref({
  minTime: 0, // 上一次拉取到的时间戳
  offset: 0 // 偏移量
})
const isReachBottom = ref(false)

function queryBlogs() {
  blogApi.ofMe()
    .then(({ data }) => blogs.value = data)
    .catch(err => ElMessage.error(err))
}

function queryBlogsOfFollow(clear) {
  if (clear) {
    params.value.offset = 0
    params.value.minTime = new Date().getTime() + 1
  }
  const { minTime, offset: os } = params.value
  blogApi.ofFollow({ offset: os, lastId: minTime || new Date().getTime() + 1 })
    .then(({ data }) => {
      if (!data) return
      const { list, ...rest } = data
      list.forEach(b => b.img = b.images.split(',')[0])
      blogs2.value = clear ? list : blogs2.value.concat(list)
      params.value = rest
    })
    .catch(e => console.log(e))
}

function queryUser() {
  // 查询用户信息
  userApi.me()
    .then(({ data }) => {
      user.value = data
      // 查询用户详情
      queryUserInfo()
      // 查询用户笔记
      queryBlogs()
    })
    .catch(err => {
      router.push('/login')
    })
}

function goBack() {
  router.back()
}

function queryUserInfo() {
  userApi.info(user.value.id)
    .then(({ data }) => {
      if (!data) return
      // 保存用户详情
      info.value = data
      // 保存到本地(info-edit 页读取)
      sessionStorage.setItem('userInfo', JSON.stringify(data))
    })
    .catch(err => ElMessage.error(err))
}

function toEdit() {
  router.push('/info-edit')
}

// ⚠ 后端 POST /user/logout 返回 fail("功能未完成")(README §5.7),该接口实际不会成功,
// 原页面"退出登录"点了永远弹错误、不跳转。此处保持该行为,仅做跳转兜底。
function logout() {
  userApi.logout()
    .then(() => {
      // 清理 session
      sessionStorage.removeItem('token')
      router.push('/')
    })
    .catch(err => ElMessage.error(err))
}

// Element Plus 的 tab-click 回调参数是 TabsPaneContext(不是 Vue 2 的 tab.name),
// name 可能在 props.name 或 paneName(computed ref)里,统一兼容
function handleClick(tab) {
  const name = tab?.props?.name || tab?.paneName?.value || tab?.paneName
  if (name === '4') {
    queryBlogsOfFollow(true)
  }
}

function addLike(b) {
  blogApi.like(b.id)
    .then(() => {
      queryBlogById(b)
    })
    .catch(err => ElMessage.error(err))
}

function queryBlogById(b) {
  blogApi.detail(b.id)
    .then(({ data }) => {
      b.liked = data.liked
      b.isLike = data.isLike
    })
    .catch(() => {
      ElMessage.error('点赞失败')
      b.liked++
    })
}

function onScroll(e) {
  const scrollTop = e.target.scrollTop
  const offsetHeight = e.target.offsetHeight
  const scrollHeight = e.target.scrollHeight
  if (scrollTop === 0) {
    // 到顶部了,重新查询一次
    queryBlogsOfFollow(true)
  } else if (scrollTop + offsetHeight + 1 > scrollHeight && !isReachBottom.value) {
    isReachBottom.value = true
    // 再次查询下一页数据
    queryBlogsOfFollow()
  } else {
    isReachBottom.value = false
  }
}

queryUser()
</script>

<template>
  <div class="header">
    <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
    <div class="header-title">个人主页&nbsp;&nbsp;&nbsp;</div>
  </div>
  <div class="basic">
    <div class="basic-icon">
      <img :src="user.icon || '/imgs/icons/default-icon.png'" alt="">
    </div>
    <div class="basic-info">
      <div class="name">{{user.nickName}}</div>
      <span>杭州</span>
      <div class="edit-btn" @click="toEdit">
        编辑资料
      </div>
    </div>
    <div class="logout-btn" @click="logout">
      退出登录
    </div>
  </div>
  <div class="introduce">
    <span v-if="info.introduce"></span>
    <span v-else>添加个人简介，让大家更好的认识你 <el-icon><Edit /></el-icon></span>
  </div>
  <div class="content">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-tab-pane label="笔记" name="1">
        <div v-for="b in blogs" :key="b.id" class="blog-item">
          <div class="blog-img"><img :src="b.images.split(',')[0]" alt=""></div>
          <div class="blog-info">
            <div class="blog-title">{{b.title}}</div>
            <div class="blog-liked"><img src="/imgs/thumbup.png" alt=""> {{b.liked}}</div>
            <div class="blog-comments"><el-icon><ChatDotRound /></el-icon> {{b.comments}}</div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="评价" name="2">评价</el-tab-pane>
      <el-tab-pane label="粉丝(0)" name="3">粉丝(0)</el-tab-pane>
      <el-tab-pane label="关注(0)" name="4">
        <div class="blog-list" @scroll="onScroll">
          <div class="blog-box" v-for="b in blogs2" :key="b.id">
            <div class="blog-img2" @click="toBlogDetail(b)"><img :src="b.img" alt=""></div>
            <div class="blog-title">{{b.title}}</div>
            <div class="blog-foot">
              <div class="blog-user-icon"><img :src="b.icon || '/imgs/icons/default-icon.png'" alt=""></div>
              <div class="blog-user-name">{{b.name}}</div>
              <div class="blog-liked" @click="addLike(b)">
                <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1"
                     xmlns="http://www.w3.org/2000/svg" p-id="2187" width="14" height="14">
                  <path
                      d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z"
                      p-id="2188" :fill="b.isLike ? '#ff6633' : '#82848a'"></path>
                </svg>
                {{b.liked}}
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
  <foot-bar :active-btn="4"></foot-bar>
</template>

<style>
.el-tabs--bottom .el-tabs__item.is-bottom:nth-child(2), .el-tabs--bottom .el-tabs__item.is-top:nth-child(2), .el-tabs--top .el-tabs__item.is-bottom:nth-child(2), .el-tabs--top .el-tabs__item.is-top:nth-child(2) {
  padding-left: 15px;
}
.el-tabs, .el-tab-pane {
  height: 100%;
}
.el-tabs__header {
  height: 10%;
}
.el-tabs__content {
  height: 90%;
}
</style>
