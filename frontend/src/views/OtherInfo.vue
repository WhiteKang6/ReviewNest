<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, blogApi, followApi } from '@/api'
import FootBar from '@/components/FootBar.vue'
import '@/assets/css/info.css'

const router = useRouter()
const route = useRoute()
const user = ref('') // 被查看的用户
const loginUser = ref({}) // 登录用户
const activeName = ref('1')
const info = ref({})
const blogs = ref([])
const followed = ref(false) // 是否已关注
const commonFollows = ref([]) // 共同关注

function queryBlogs() {
  blogApi.ofUser(user.value.id, 1)
    .then(({ data }) => blogs.value = data)
    .catch(err => ElMessage.error(err))
}

function queryLoginUser() {
  // 查询登录用户
  userApi.me()
    .then(({ data }) => {
      loginUser.value = data
    })
    .catch(err => console.log(err))
}

function queryUser() {
  const id = route.query.id
  userApi.getById(id)
    .then(({ data }) => {
      user.value = data
      queryUserInfo()
      queryBlogs()
      isFollowed()
    })
    .catch(err => console.log(err))
}

function goBack() {
  router.back()
}

function queryUserInfo() {
  userApi.info(user.value.id)
    .then(({ data }) => {
      if (!data) return
      info.value = data
      // 保存到本地
      sessionStorage.setItem('userInfo', JSON.stringify(data))
    })
    .catch(err => ElMessage.error(err))
}

function isFollowed() {
  followApi.isFollowed(user.value.id)
    .then(({ data }) => followed.value = data)
    .catch(err => ElMessage.error(err))
}

function queryCommonFollow() {
  followApi.common(user.value.id)
    .then(({ data }) => commonFollows.value = data)
    .catch(err => ElMessage.error(err))
}

function follow() {
  followApi.follow(user.value.id, !followed.value)
    .then(() => {
      ElMessage.success(followed.value ? '已取消关注' : '已关注')
      followed.value = !followed.value
    })
    .catch(err => ElMessage.error(err))
}

// Element Plus 的 tab-click 回调参数是 TabsPaneContext(不是 Vue 2 的 tab.name)
function handleClick(tab) {
  const name = tab?.props?.name || tab?.paneName?.value || tab?.paneName
  if (name === '2') {
    queryCommonFollow()
  }
}

function toOtherInfo(id) {
  router.push('/other-info?id=' + id)
}

queryUser()
queryLoginUser()
</script>

<template>
  <div class="header">
    <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
    <div class="header-title">&nbsp;&nbsp;&nbsp;</div>
  </div>
  <div class="basic">
    <div class="basic-icon">
      <img :src="user.icon || '/imgs/icons/default-icon.png'" alt="">
    </div>
    <div class="basic-info">
      <div class="name">{{user.nickName}}</div>
      <span>杭州</span>
    </div>
    <div class="logout-btn" @click="follow" style="text-align: center">
      {{followed ? '取消关注' : '关注'}}
    </div>
  </div>
  <div class="introduce">
    <span v-if="info.introduce"></span>
    <span v-else>这个人很懒，什么都没有留下</span>
  </div>
  <div class="content">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-tab-pane label="笔记" name="1">
        <div v-for="b in blogs" :key="b.id" class="blog-item">
          <div class="blog-img"><img :src="b.images.split(',')[0]" alt=""></div>
          <div class="blog-info">
            <!-- v-html 渲染标题,学习项目可接受;生产环境需过滤 -->
            <div class="blog-title" v-html="b.title"></div>
            <div class="blog-liked"><img src="/imgs/thumbup.png" alt=""> {{b.liked}}</div>
            <div class="blog-comments"><el-icon><ChatDotRound /></el-icon> {{b.comments}}</div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="共同关注" name="2">
        <div>你们都关注了：</div>
        <div class="follow-info" v-for="u in commonFollows" :key="u.id">
          <div class="follow-info-icon" @click="toOtherInfo(u.id)">
            <img :src="u.icon || '/imgs/icons/default-icon.png'" alt="">
          </div>
          <div class="follow-info-name">
            <div class="name">{{u.nickName}}</div>
          </div>
          <div class="follow-info-btn" @click="toOtherInfo(u.id)">
              去主页看看
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
  <foot-bar :active-btn="0"></foot-bar>
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
