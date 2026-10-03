<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { shopApi, blogApi, uploadApi, userApi } from '@/api'
import '@/assets/css/blog-edit.css'

const router = useRouter()
const fileInput = ref(null)
const fileList = ref([]) // 已上传图片的 /imgs 路径列表
const params = ref({})
const showDialog = ref(false)
const shops = ref([]) // 商户信息
const shopName = ref('')
const selectedShop = ref({}) // 选中的商户

function queryShops() {
  shopApi.ofName(shopName.value)
    .then(({ data }) => shops.value = data)
    .catch(err => ElMessage.error(err))
}

function selectShop(s) {
  selectedShop.value = s
  showDialog.value = false
}

function submitBlog() {
  const data = {
    ...params.value,
    images: fileList.value.join(','),
    shopId: selectedShop.value.id
  }
  blogApi.create(data)
    .then(() => router.push('/info'))
    .catch(err => ElMessage.error(err))
}

function openFileDialog() {
  fileInput.value.click()
}

function fileSelected() {
  const file = fileInput.value.files[0]
  // FormData 的 multipart 头(含 boundary)由 axios 自动生成,无需手写
  uploadApi.uploadBlog(file)
    .then(({ data }) => fileList.value.push('/imgs' + data))
    .catch(err => ElMessage.error(err))
}

function deletePic(i) {
  // 保持原调用方式(后端 /upload/blog/delete 的路径拼接有双 imgs bug,属后端问题,README 有记录)
  uploadApi.deleteBlogImg('/imgs' + fileList.value[i])
    .then(() => fileList.value.splice(i, 1))
    .catch(err => ElMessage.error(err))
}

function checkLogin() {
  // 获取 token
  const token = sessionStorage.getItem('token')
  if (!token) {
    router.push('/login')
    return
  }
  // 查询用户信息
  userApi.me()
    .then(() => {})
    .catch(err => {
      ElMessage.error(err)
      setTimeout(() => router.push('/login'), 200)
    })
}

function goBack() {
  router.back()
}

checkLogin()
queryShops()
</script>

<template>
  <div class="header">
    <div class="header-cancel-btn" @click="goBack">取消</div>
    <div class="header-title">&nbsp;&nbsp;发笔记<el-icon><InfoFilled /></el-icon></div>
    <div class="header-commit">
      <div class="header-commit-btn" @click="submitBlog">发布</div>
    </div>
  </div>
  <div class="upload-box">
    <input type="file" @change="fileSelected" name="file" ref="fileInput" style="display: none">
    <div class="upload-btn" @click="openFileDialog">
      <el-icon :size="30"><Camera /></el-icon>
      <div style="font-size: 12px;line-height: 12px">上传照片</div>
    </div>
    <div class="pic-list">
      <div class="pic-box" v-for="(f,i) in fileList" :key="i">
        <img :src="f" alt="">
        <el-icon class="pic-del" @click="deletePic(i)"><Close /></el-icon>
      </div>
    </div>
  </div>
  <div class="blog-title">
    <input v-model="params.title" type="text" placeholder="填写标题更容易上首页哦~">
  </div>
  <div class="blog-content">
    <textarea v-model="params.content" placeholder="最近打卡了什么地方，有什么新奇体验呢？"></textarea>
  </div>
  <div class="divider"></div>
  <div class="blog-shop" @click="showDialog=true">
    <div class="shop-left">关联商户</div>
    <div v-if="selectedShop.name">{{selectedShop.name}}</div>
    <div v-else>去选择&nbsp;<el-icon><ArrowRight /></el-icon></div>
  </div>
  <div class="mask" v-show="showDialog" @click="showDialog=false"></div>

  <transition name="el-zoom-in-bottom">
    <div class="shop-dialog" v-show="showDialog">
      <div class="blog-shop">
        <div class="shop-left">关联商户</div>
      </div>
      <div class="search-bar">
        <div class="city-select">杭州 <el-icon><ArrowDown /></el-icon></div>
        <div class="search-input">
          <el-icon @click="queryShops"><Search /></el-icon>
          <input v-model="shopName" type="text" placeholder="搜索商户名称">
        </div>
      </div>
      <div class="shop-list">
        <div v-for="s in shops" class="shop-item" @click="selectShop(s)">
          <div class="shop-name">{{s.name}}</div>
          <div>{{s.area}}</div>
        </div>
      </div>
    </div>
  </transition>
</template>

<style>
.el-input__inner {
  border-radius: 20px;
}
</style>
