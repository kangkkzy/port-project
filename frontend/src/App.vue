<template>
  <div class="app-container">
    <SimulationDashboard />
  </div>
</template>

<script setup lang="ts">
// 引入我们即将创建的控制台组件
import SimulationDashboard from './components/SimulationDashboard.vue'
</script>

<style>
/* 简单的全局样式，去掉默认的边距 */
body {
  margin: 0;
  padding: 0;
  background-color: #f5f7fa;
  font-family: Arial, sans-serif;
}
.app-container {
  width: 100vw;
  height: 100vh;
}
</style>