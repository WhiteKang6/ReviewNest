<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import '@/assets/css/login.css'

const router = useRouter()
const radio = ref('')
const disabled = ref(false) // 发送短信按钮
const codeBtnMsg = ref('发送验证码') // 发送短信按钮提示
const form = ref({})
let countdown = null

onUnmounted(() => {
  if (countdown) clearInterval(countdown)
})

function login() {
  if (!radio.value) {
    ElMessage.error('请先确认阅读用户协议！')
    return
  }
  if (!form.value.phone || !form.value.code) {
    ElMessage.error('手机号和验证码不能为空！')
    return
  }
  userApi.login(form.value)
    .then(({ data }) => {
      if (data) {
        // 保存 token 到 sessionStorage
        sessionStorage.setItem('token', data)
      }
      // 跳转到个人主页
      router.push('/info')
    })
    .catch(err => ElMessage.error(err))
}

function goBack() {
  router.back()
}

function sendCode() {
  if (!form.value.phone) {
    ElMessage.error('手机号不能为空')
    return
  }
  // 发送验证码(后端不真发短信,验证码打印在服务端日志,学习用)
  userApi.sendCode(form.value.phone)
    .then(() => {})
    .catch(err => ElMessage.error(err))
  // 禁用按钮 + 倒计时
  disabled.value = true
  let i = 60
  codeBtnMsg.value = (i--) + '秒后可重发'
  countdown = setInterval(() => codeBtnMsg.value = (i--) + '秒后可重发', 1000)
  setTimeout(() => {
    disabled.value = false
    clearInterval(countdown)
    codeBtnMsg.value = '发送验证码'
  }, 59000)
}
</script>

<template>
  <div class="login-container">
    <div class="header">
      <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
      <div class="header-title">手机号码快捷登录&nbsp;&nbsp;&nbsp;</div>
    </div>
    <div class="content">
      <div class="login-form">
        <div style="display: flex; justify-content: space-between">
          <el-input style="width: 60%" placeholder="请输入手机号" v-model="form.phone">
          </el-input>
          <el-button style="width: 38%" @click="sendCode" type="success" :disabled="disabled">{{codeBtnMsg}}</el-button>
        </div>

        <div style="height: 5px"></div>
        <el-input placeholder="请输入验证码" v-model="form.code">
        </el-input>
        <div style="text-align: center; color: #8c939d;margin: 5px 0">未注册的手机号码验证后自动创建账户</div>
        <el-button @click="login" style="width: 100%; background-color:#f63; color: #fff;">登录</el-button>
        <div style="text-align: right; color:#333333; margin: 5px 0"><router-link to="/login2">密码登录</router-link></div>
      </div>
      <div class="login-radio">
        <div>
          <input type="radio" name="readed" v-model="radio" value="1">
          <label for="readed"></label>
        </div>
        <div>我已阅读并同意
          <a href="javascript:void(0)">
          《黑马点评用户服务协议》</a>、
          <a href="javascript:void(0)">《隐私政策》</a>
          等，接受免除或者限制责任、诉讼管辖约定等粗体标示条款
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.el-input__inner {
  border-radius: 20px;
}
</style>
