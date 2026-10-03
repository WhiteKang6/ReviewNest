<script setup>
import { useRouter } from 'vue-router'

// 原 footer.js 的全局组件 footBar:底部导航。activeBtn 与旧页约定一致:
// 1=首页,4=我的,0=不发亮(其他页)
defineProps({
  activeBtn: { type: Number, default: 0 }
})

const router = useRouter()

function toPage(i) {
  if (i === 0) {
    router.push('/blog-edit')
  } else if (i === 4) {
    router.push('/info')
  } else if (i === 1) {
    router.push('/')
  }
  // 2(地图)、3(消息)暂无落地页,不做跳转(保持旧行为)
}
</script>

<template>
  <div class="foot">
    <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
      <div class="foot-view"><el-icon :size="26"><HomeFilled /></el-icon></div>
      <div class="foot-text">首页</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)">
      <div class="foot-view"><el-icon :size="26"><MapLocation /></el-icon></div>
      <div class="foot-text">地图</div>
    </div>
    <div class="foot-box" @click="toPage(0)">
      <img class="add-btn" src="/imgs/add.png" alt="">
    </div>
    <div class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)">
      <div class="foot-view"><el-icon :size="26"><ChatDotRound /></el-icon></div>
      <div class="foot-text">消息</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
      <div class="foot-view"><el-icon :size="26"><User /></el-icon></div>
      <div class="foot-text">我的</div>
    </div>
  </div>
</template>
