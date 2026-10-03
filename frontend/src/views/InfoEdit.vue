<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import FootBar from '@/components/FootBar.vue'
import '@/assets/css/info.css'

const router = useRouter()
const user = ref('')
const info = ref({})

function checkLogin() {
  // 查询用户信息
  userApi.me()
    .then(({ data }) => {
      user.value = data
      // 用户详情存在 sessionStorage(登录后由 info 页写入)
      info.value = JSON.parse(sessionStorage.getItem('userInfo') || '{}')
    })
    .catch(err => {
      ElMessage.error(err)
      setTimeout(() => router.push('/login'), 1000)
    })
}

function goBack() {
  router.back()
}

checkLogin()
</script>

<template>
  <div class="header">
    <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
    <div class="header-title">资料编辑&nbsp;&nbsp;&nbsp;</div>
  </div>
  <div class="edit-container">
    <div class="info-box">
      <div class="info-item">
        <div class="info-label">头像</div>
        <div class="info-btn">
          <img width="35" :src="user.icon || '/imgs/icons/default-icon.png'" alt="">
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="info-item">
        <div class="info-label">昵称</div>
        <div class="info-btn">
          <div>{{user.nickName}}</div>
          <div>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="info-item">
        <div class="info-label">个人介绍</div>
        <div class="info-btn">
          <div style="overflow: hidden; width: 150px;text-align: right">{{info.introduce || '介绍一下自己'}}</div>
          <div>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>
    <div class="info-box">
      <div class="info-item">
        <div class="info-label">性别</div>
        <div class="info-btn">
          <div>{{info.gender || '选择'}}</div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="info-item">
        <div class="info-label">城市</div>
        <div class="info-btn">
          <div>{{info.city || '选择'}}</div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="info-item">
        <div class="info-label">生日</div>
        <div class="info-btn">
          <div>{{info.birthday || '添加'}}</div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
    </div>

    <div class="info-box">
      <div class="info-item">
        <div class="info-label">我的积分</div>
        <div class="info-btn">
          <div>查看积分</div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
      <div class="divider"></div>
      <div class="info-item">
        <div class="info-label">会员等级</div>
        <div class="info-btn">
          <div><a href="javascript:void(0)">成为VIP尊享特权</a></div>
          <div><el-icon><ArrowRight /></el-icon></div>
        </div>
      </div>
    </div>
  </div>
  <foot-bar :active-btn="4"></foot-bar>
</template>
