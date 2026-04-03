<template>
  <el-container class="layout">
    <el-aside width="200px" class="sidebar">
      <div class="logo">AI Chat Gateway</div>
      <el-menu :default-active="route.path" router class="sidebar-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/keys">
          <el-icon><Key /></el-icon>
          <span>Key 管理</span>
        </el-menu-item>
        <el-menu-item index="/templates">
          <el-icon><Document /></el-icon>
          <span>模板管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话测试</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </el-menu-item>
      </el-menu>

      <div class="api-key-setting">
        <el-input v-model="apiKey" placeholder="输入 API Key" size="small"
          @change="saveApiKey" type="password" show-password />
      </div>
    </el-aside>
    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const apiKey = ref(localStorage.getItem('apiKey') || '')

function saveApiKey() {
  localStorage.setItem('apiKey', apiKey.value)
}
</script>

<style>
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
.layout { height: 100vh; }
.sidebar { background: #304156; overflow: hidden; display: flex; flex-direction: column; }
.sidebar-menu { border-right: none; background: #304156; flex: 1; }
.sidebar-menu .el-menu-item { color: #bfcbd9; }
.sidebar-menu .el-menu-item:hover { background: #263445; }
.sidebar-menu .el-menu-item.is-active { background: #1890ff !important; color: #fff; }
.logo { height: 60px; line-height: 60px; text-align: center; color: #fff; font-size: 16px; font-weight: bold; background: #263445; }
.api-key-setting { padding: 12px; }
.main-content { background: #f0f2f5; padding: 20px; overflow: auto; }
</style>
