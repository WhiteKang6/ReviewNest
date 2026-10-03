<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { shopApi } from '@/api'
import '@/assets/css/shop-list.css'

const router = useRouter()
const route = useRoute()
const isReachBottom = ref(false)
const types = ref([]) // 类型列表
const shops = ref([]) // 商铺列表
const typeName = ref(route.query.name || '')
const params = reactive({
  typeId: Number(route.query.type || 0),
  current: 1,
  sortBy: '',
  x: 120.149993, // 经度
  y: 30.334229 // 纬度
})

// 查询商铺类型
function queryTypes() {
  shopApi.listTypes()
    .then(({ data }) => {
      types.value = data
    })
    .catch(err => {
      console.log(err)
      ElMessage.error(err)
    })
}

function queryShops() {
  shopApi.ofType(params)
    .then(({ data }) => {
      if (!data) return
      data.forEach(s => s.images = s.images.split(',')[0])
      shops.value = shops.value.concat(data)
    })
    .catch(err => {
      console.log(err)
      ElMessage.error(err)
    })
}

// 切换类型:同步 URL(原 location.href 语义),重置列表后查询
function handleCommand(t) {
  typeName.value = t.name
  params.typeId = t.id
  resetAndQuery()
  router.replace({ path: '/shop-list', query: { type: t.id, name: t.name } })
}

// 排序:修复原 bug——原 sortAndQuery 不重置 current 也不清空 shops,
// 反复点排序会把列表无限叠加;触底处还调用了带参 queryShops(current,5)(参数被忽略)
function sortAndQuery(sortBy) {
  params.sortBy = sortBy
  resetAndQuery()
}

function resetAndQuery() {
  params.current = 1
  shops.value = []
  queryShops()
}

function goBack() {
  router.back()
}

function toDetail(id) {
  router.push('/shop-detail?id=' + id)
}

function onScroll(e) {
  const scrollTop = e.target.scrollTop
  const offsetHeight = e.target.offsetHeight
  const scrollHeight = e.target.scrollHeight
  if (scrollTop + offsetHeight + 1 > scrollHeight && !isReachBottom.value) {
    isReachBottom.value = true
    params.current++
    queryShops()
  } else {
    isReachBottom.value = false
  }
}

queryTypes()
queryShops()
</script>

<template>
  <div class="header">
    <div class="header-back-btn" @click="goBack"><el-icon><ArrowLeft /></el-icon></div>
    <div class="header-title">{{typeName}}</div>
    <div class="header-search">
      <el-icon><Search /></el-icon>
    </div>
  </div>
  <div class="sort-bar">
    <div class="sort-item">
      <el-dropdown trigger="click" @command="handleCommand">
      <span class="el-dropdown-link">
        {{typeName}}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="t in types" :key="t.id" :command="t">
              {{t.name}}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div class="sort-item" @click="sortAndQuery('')">
      距离 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </div>
    <div class="sort-item" @click="sortAndQuery('comments')">
      人气 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </div>
    <div class="sort-item" @click="sortAndQuery('score')">
      评分 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </div>
  </div>
  <div class="shop-list" @scroll="onScroll">
    <div class="shop-box" v-for="s in shops" :key="s.id" @click="toDetail(s.id)">
      <div class="shop-img"><img :src="s.images" alt=""></div>
      <div class="shop-info">
        <div class="shop-title shop-item">{{s.name}}</div>
        <div class="shop-rate shop-item">
          <el-rate
              disabled :model-value="(s.score || 0) / 10"
              text-color="#F63"
              show-score
          ></el-rate>
          <span>{{s.comments}}条</span>
        </div>
        <div class="shop-area shop-item">
          <span>{{s.area}}</span>
          <span v-if="s.distance">{{s.distance < 1000 ? s.distance.toFixed(1) + 'm' : (s.distance/1000).toFixed(1) + 'km'}}</span>
        </div>
        <div class="shop-avg shop-item">￥{{s.avgPrice}}/人</div>
        <div class="shop-address shop-item">
          <el-icon><MapLocation /></el-icon>
          <span>{{s.address}}</span>
        </div>
      </div>
    </div>
  </div>
</template>
