<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import '@/assets/css/login.css'

// ⚠ 密码登录页仅作演示保留:后端 UserServiceImpl.login 只校验短信验证码,
// 本页提交 password 必然失败(返回"功能未完成"或错误),详见 README §10.2。
const router = useRouter()
const radio = ref('')
const form = ref({})

function login() {
  if (!radio.value) {
    ElMessage.error('请先确认阅读用户协议！')
    return
  }
  userApi.login(form.value)
    .then(({ data }) => {
      if (data) {
        // 保存用户信息到 session
        sessionStorage.setItem('token', data)
      }
      // 跳转到首页
      router.push('/info')
    })
    .catch(err => ElMessage.error(err))
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="login-container">
    <div class="header">
      <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
      <div class="header-title">密码登录&nbsp;&nbsp;&nbsp;</div>
    </div>
    <div class="content">
      <div class="login-form">
        <el-input placeholder="请输入手机号" v-model="form.phone">
        </el-input>
        <div style="height: 5px"></div>
        <el-input placeholder="请输入密码" v-model="form.password">
        </el-input>
        <div style="text-align: center; color: #8c939d;margin: 5px 0"><a href="javascript:void(0)">忘记密码</a></div>
        <el-button @click="login" style="width: 100%; background-color:#f63; color: #fff;">登录</el-button>
        <div style="text-align: right; color:#333333; margin: 5px 0"><router-link to="/login">验证码登录</router-link></div>
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
.el-radio__input.is-checked .el-radio__inner {
  border-color: #F63;
  background: #F63;
}
.el-input__inner:focus {
  border: 1px solid #F63;
}
</style>
