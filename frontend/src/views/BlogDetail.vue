<script setup>
import { ref, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { blogApi, shopApi, followApi, userApi } from '@/api'
import { formatTime } from '@/utils/format'
import '@/assets/css/blog-detail.css'

const router = useRouter()
const route = useRoute()
const blog = ref({})
const shop = ref({})
const likes = ref([]) // 最早点赞的前 5 人
const user = ref({}) // 登录用户
const followed = ref(false) // 是否关注了博主

// ---- 手写 swiper(原页面自实现轮播,非组件,原样迁入) ----
const swiper = ref(null) // 图片容器
const _width = ref(0)
const duration = 300
const items = [] // .swiper-item 子节点
const active = ref(0)
const start = { x: 0, y: 0 }
const move = { x: 0, y: 0 }
const isMoving = ref(false)
const sensitivity = 60
const resistance = 0.3

// 博客关联商铺评分:el-rate 需要可赋值的 model,用 computed 兜住 undefined 初始值
const rate = computed({
  get: () => (shop.value.score || 0) / 10,
  set: v => { shop.value.score = v * 10 }
})

function init() {
  // 获得所有的子节点
  items.length = 0
  items.push(...swiper.value.querySelectorAll('.swiper-item'))
  updateItemWidth()
  setTransform()
  setTransition('none')
}

async function queryBlogById(id) {
  try {
    const { data } = await blogApi.detail(id)
    data.images = data.images.split(',')
    blog.value = data
    await nextTick()
    init()
    queryShopById(data.shopId)
    queryLikeList(id)
    queryLoginUser()
  } catch (err) {
    ElMessage.error(err)
  }
}

function queryShopById(shopId) {
  shopApi.detail(shopId)
    .then(({ data }) => {
      shop.value = { ...data, image: data.images.split(',')[0] }
    })
    .catch(err => ElMessage.error(err))
}

function queryLikeList(id) {
  blogApi.likes(id)
    .then(({ data }) => likes.value = data)
    .catch(err => ElMessage.error(err))
}

function addLike() {
  blogApi.like(blog.value.id)
    .then(() => {
      blogApi.detail(blog.value.id)
        .then(({ data }) => {
          data.images = data.images.split(',')
          blog.value = data
          queryLikeList(blog.value.id)
        })
        .catch(err => ElMessage.error(err))
    })
    .catch(err => ElMessage.error(err))
}

function isFollowed() {
  followApi.isFollowed(blog.value.userId)
    .then(({ data }) => followed.value = data)
    .catch(err => ElMessage.error(err))
}

function follow() {
  followApi.follow(blog.value.userId, !followed.value)
    .then(() => {
      ElMessage.success(followed.value ? '已取消关注' : '已关注')
      followed.value = !followed.value
    })
    .catch(err => ElMessage.error(err))
}

function queryLoginUser() {
  // 查询登录用户;匿名访问时该请求失败,保持 user 为空对象即可
  userApi.me()
    .then(({ data }) => {
      user.value = data
      if (user.value.id !== blog.value.userId) {
        isFollowed()
      }
    })
    .catch(err => console.log(err))
}

function toOtherInfo() {
  if (blog.value.userId === user.value.id) {
    router.push('/info')
  } else {
    router.push('/other-info?id=' + blog.value.userId)
  }
}

function goBack() {
  router.back()
}

// ---- swiper 的滑动逻辑 ----
function updateItemWidth() {
  _width.value = swiper.value.offsetWidth || document.documentElement.offsetWidth
}

// 根据当前活动子项的下标计算各个子项的 X 轴位置
// 计算公式:(子项的下标 - 当前活动下标) * 子项宽度 + 偏移(手指移动距离)
function setTransform(offset) {
  offset = offset || 0
  items.forEach((item, i) => {
    const distance = (i - active.value) * _width.value + offset
    const transform = `translate3d(${distance}px, 0, 0)`
    item.style.webkitTransform = transform
    item.style.transform = transform
  })
}

// 给每一个子项添加 transition 过渡动画
function setTransition(durationArg) {
  durationArg = durationArg || duration
  durationArg = typeof durationArg === 'number' ? (durationArg + 'ms') : durationArg
  items.forEach(item => {
    item.style.webkitTransition = durationArg
    item.style.transition = durationArg
  })
}

function moveStart(e) {
  start.x = e.changedTouches[0].pageX
  start.y = e.changedTouches[0].pageY
  setTransition('none')
}

function moving(e) {
  e.preventDefault()
  e.stopPropagation()
  const distanceX = e.changedTouches[0].pageX - start.x
  const distanceY = e.changedTouches[0].pageY - start.y
  if (Math.abs(distanceX) > Math.abs(distanceY)) {
    isMoving.value = true
    move.x = start.x + distanceX
    move.y = start.y + distanceY
    // 当活动子项为第一项且手指向右滑动、或者活动项为最后一项且向左滑动时,添加阻力,形成拉弹簧效果
    let d = distanceX
    if ((active.value === 0 && distanceX > 0) || (active.value === (items.length - 1) && distanceX < 0)) {
      d = distanceX * resistance
    }
    setTransform(d)
  }
}

function moveEnd(e) {
  if (isMoving.value) {
    e.preventDefault()
    e.stopPropagation()
    const distance = move.x - start.x
    if (Math.abs(distance) > sensitivity) {
      if (distance < 0) {
        next()
      } else {
        prev()
      }
    } else {
      back()
    }
    reset()
    isMoving.value = false
  }
}

function next() {
  go(active.value + 1)
}

function prev() {
  go(active.value - 1)
}

function reset() {
  start.x = 0
  start.y = 0
  move.x = 0
  move.y = 0
}

function back() {
  setTransition()
  setTransform()
}

// 运用动画切换到指定下标的子项
function go(index) {
  active.value = index
  if (active.value < 0) {
    active.value = 0
  } else if (active.value > items.length - 1) {
    active.value = items.length - 1
  }
  setTransition()
  setTransform()
}

queryBlogById(route.query.id)
</script>

<template>
  <div class="header">
    <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
    <div class="header-title"></div>
    <div class="header-share">...</div>
  </div>
  <div style="height: 85%; overflow-y: scroll; overflow-x: hidden">
    <div class="blog-info-box" ref="swiper"
         @touchstart="moveStart"
         @touchmove="moving"
         @touchend="moveEnd">
      <div class="swiper-item" v-for="(img, i) in blog.images" :key="i">
        <img :src="img" alt="" style="width: 100%" height="100%">
      </div>
    </div>
    <div class="basic">
      <div class="basic-icon" @click="toOtherInfo">
        <img :src="blog.icon || '/imgs/icons/default-icon.png'" alt="">
      </div>
      <div class="basic-info">
        <div class="name">{{blog.name}}</div>
        <span class="time">{{formatTime(new Date(blog.createTime))}}</span>
      </div>
      <div style="width: 20%">
        <div class="logout-btn" @click="follow" v-show="!user || user.id !== blog.userId ">
          {{followed ? '取消关注' : '关注'}}
        </div>
      </div>
    </div>
    <!-- v-html 渲染博客正文,学习项目可接受;生产环境正文需做 XSS 过滤 -->
    <div class="blog-text" v-html="blog.content">
    </div>
    <div class="shop-basic">
      <div class="shop-icon">
        <img :src="shop.image" alt="">
      </div>
      <div style="width: 80%">
        <div class="name">{{shop.name}}</div>
        <div>
          <el-rate v-model="rate"></el-rate>
        </div>
        <div class="shop-avg">￥{{shop.avgPrice}}/人</div>
      </div>
    </div>
    <div class="zan-box">
      <div>
        <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="2187" width="20" height="20">
          <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z" p-id="2188" :fill="blog.isLike ? '#ff6633' : '#82848a'"></path>
        </svg>
      </div>
      <div class="zan-list">
        <div class="user-icon-mini" v-for="u in likes" :key="u.id">
          <img :src="u.icon || '/imgs/icons/default-icon.png'" alt="">
        </div>
        <div style="margin-left:10px;text-align: center;line-height: 24px;">{{blog.liked}}人点赞</div>
      </div>
    </div>
    <div class="blog-divider"></div>
    <div class="blog-comments">
      <div class="comments-head">
        <div>网友评价 <span>（119）</span></div>
        <div><el-icon><ArrowRight /></el-icon></div>
      </div>
      <div class="comment-list">
        <div class="comment-box" v-for="i in 3" :key="i">
          <div class="comment-icon">
            <img
                src="https://p0.meituan.net/userheadpicbackend/57e44d6eba01aad0d8d711788f30a126549507.jpg%4048w_48h_1e_1c_1l%7Cwatermark%3D0"
                alt="">
          </div>
          <div class="comment-info">
            <div class="comment-user">叶小乙 <span>Lv5</span></div>
            <div style="display: flex;">
              打分
              <el-rate disabled :model-value="4.5"></el-rate>
            </div>
            <div style="padding: 5px 0; font-size: 14px">
              某平台上买的券，价格可以当工作餐吃，虽然价格便宜，但是这家店一点都没有...
            </div>
            <div class="comment-images">
              <img
                  src="https://qcloud.dpfile.com/pc/6T7MfXzx7USPIkSy7jzm40qZSmlHUF2jd-FZUL6WpjE9byagjLlrseWxnl1LcbuSGybIjx5eX6WNgCPvcASYAw.jpg"
                  alt="">
              <img
                  src="https://qcloud.dpfile.com/pc/sZ5q-zgglv4VXEWU71xCFjnLM_jUHq-ylq0GKivtrz3JksWQ1f7oBWZsxm1DWgcaGybIjx5eX6WNgCPvcASYAw.jpg"
                  alt="">
              <img
                  src="https://qcloud.dpfile.com/pc/xZy6W4NwuRFchlOi43DVLPFsx7KWWvPqifE1JTe_jreqdsBYA9CFkeSm2ZlF0OVmGybIjx5eX6WNgCPvcASYAw.jpg"
                  alt="">
              <img
                  src="https://qcloud.dpfile.com/pc/xZy6W4NwuRFchlOi43DVLPFsx7KWWvPqifE1JTe_jreqdsBYA9CFkeSm2ZlF0OVmGybIjx5eX6WNgCPvcASYAw.jpg"
                  alt="">
            </div>
            <div>
              浏览641 &nbsp;&nbsp;&nbsp;&nbsp;评论5
            </div>
          </div>
        </div>
        <div
            style="display: flex; justify-content: space-between;padding: 15px 0; border-top: 1px solid #f1f1f1; margin-top: 10px;">
          <div>查看全部119条评价</div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
    </div>
    <div class="blog-divider"></div>
  </div>
  <div class="foot">
    <div class="foot-box">
      <div class="foot-view" @click="addLike()">
        <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="2187" width="26" height="26">
          <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z" p-id="2188" :fill="blog.isLike ? '#ff6633' : '#82848a'"></path>
        </svg>
        <span :class="{liked: blog.isLike}">{{blog.liked}}</span>
      </div>
    </div>
    <div style="width: 40%">
    </div>
    <div class="foot-box">
      <div class="foot-view"><el-icon :size="26"><ChatSquare /></el-icon></div>
    </div>
  </div>
</template>

<style>
.header {
  position: relative;
}
.foot-view span {
  font-size: 12px;
}
</style>
