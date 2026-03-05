<template>
  <div class="dashboard">
    <div class="toolbar">
      <h2>港口离散仿真内核验证台</h2>
      <div class="buttons">
        <!-- 三个测试按钮：加载场景、正常流程、异常拦截 -->
        <el-button type="info" @click="handleMockInit">1. 外部加载: 地图与作业场景</el-button>
        <el-button type="success" @click="handleMockNormal">2. 外部调度: 绑定任务与下发流程</el-button>
        <el-button type="danger" @click="handleMockError">3. 异常拦截: 下发非法位置越界</el-button>
        <!-- 核心控制：单步推演和重置 -->
        <el-button type="primary" @click="handleStep" style="margin-left: 20px; font-weight: bold;">单步推演 (Next Event)</el-button>
        <el-button type="warning" @click="handleReset" style="margin-left: 10px;">清空重置</el-button>
      </div>
      <div class="time-display">
        当前时钟: <strong>{{ simStore.simTime }}</strong> ms
      </div>
    </div>

    <div class="main-content">
      <div class="left-section">
        <!-- 画布区域：使用 Konva 渲染地图和设备 -->
        <div class="map-container">
          <v-stage :config="stageConfig">
            <v-layer name="background">
              <!-- 简单的背景色和文字示意海侧区域 -->
              <v-rect :config="{ x: 0, y: 0, width: 800, height: 100, fill: '#bbdefb' }" />
              <v-text :config="{ x: 20, y: 40, text: '海侧区域', fontSize: 24, fill: '#1565c0', opacity: 0.5 }" />
            </v-layer>

            <v-layer name="rails">
              <!-- 动态渲染地图路径（轨道/道路） -->
              <template v-for="(path, idx) in simStore.mapPaths" :key="'path-'+idx">
                <v-line :config="{
                  points: path.direction === 'HORIZONTAL'
                          ? [path.startPoint, path.position, path.endPoint, path.position]
                          : [path.position, path.startPoint, path.position, path.endPoint],
                  stroke: getPathColor(path.pathType),
                  strokeWidth: path.pathType === 'TRUCK_ROAD' ? 8 : 3,
                  dash: path.pathType === 'TRUCK_ROAD' ? [] : [15, 10],
                  opacity: 0.7
                }" />
                <v-text :config="{
                  x: path.direction === 'HORIZONTAL' ? 30 : path.position + 10,
                  y: path.direction === 'HORIZONTAL' ? path.position - 15 : 255,
                  text: `${path.name}(${path.direction === 'HORIZONTAL'?'Y':'X'}=${path.position})`,
                  fontSize: 10, fill: getPathColor(path.pathType)
                }" />
              </template>
            </v-layer>

            <v-layer name="devices">
              <!-- 渲染所有设备，并根据状态显示不同样式 -->
              <v-group v-for="dev in displayDevices" :key="dev.id" :config="{ x: dev.posX, y: dev.posY }">

                <!-- 如果是起重机且正在 Z 轴作业，显示动画进度条 -->
                <template v-if="(dev.type === 'QC' || dev.type === 'ASC') && simStore.activeZOperations.has(dev.id)">
                  <v-text :config="{ x: -30, y: -45, text: '↕ Z轴作业中', fill: '#00bcd4', fontSize: 12, fontStyle: 'bold' }" />
                  <v-rect :config="{ x: -20, y: -30, width: 40, height: 5, fill: '#e0e0e0', cornerRadius: 2 }" />
                  <v-rect :config="{ x: -20, y: -30, width: (Date.now() % 2000) / 2000 * 40, height: 5, fill: '#00bcd4', cornerRadius: 2 }" />
                </template>

                <!-- 根据设备类型绘制不同形状：QC、ASC、卡车等 -->
                <template v-if="dev.type === 'QC' || dev.type === 'CRANE_QC'">
                  <v-rect :config="{ x: -20, y: -20, width: 40, height: 40, fill: '#fff', stroke: dev.isAlerting ? '#ff0000' : '#ff9800', strokeWidth: 4 }" />
                  <v-circle :config="{ x: 0, y: 0, radius: 5, fill: dev.isAlerting ? '#ff0000' : '#ff9800' }" />
                </template>
                <template v-else-if="dev.type === 'ASC' || dev.type === 'CRANE_ASC'">
                  <v-rect :config="{ x: -15, y: -15, width: 30, height: 30, fill: '#fff', stroke: dev.isAlerting ? '#ff0000' : '#4caf50', strokeWidth: 4 }" />
                </template>
                <template v-else-if="dev.type === 'ELECTRIC_TRUCK' || dev.type === 'INTERNAL_TRUCK'">
                  <v-rect :config="{ x: -12, y: -6, width: 24, height: 12, fill: dev.isAlerting ? '#ff0000' : '#2196f3', cornerRadius: 2 }" />
                </template>

                <!-- 显示设备 ID -->
                <v-text :config="{ x: -25, y: 25, text: dev.id, fontSize: 12, fill: dev.isAlerting ? '#ff0000' : '#333' }" />
              </v-group>
            </v-layer>
          </v-stage>
        </div>

        <!-- 终端日志面板：显示仿真内核事件 -->
        <div class="terminal-panel">
          <div class="terminal-header">> 离散事件引擎内核日志 (Discrete Event Engine Logs)</div>
          <ul class="log-list" ref="logContainer">
            <li v-for="log in simStore.events" :key="log.eventId">
              <span class="log-time">[{{ log.simTime }}ms]</span>
              <span class="log-type">{{ log.type }}</span>
              <span class="log-subject">{{ formatSubjects(log.subjects) }}</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 右侧拦截台：显示业务逻辑错误和警告 -->
      <div class="log-console" style="position: absolute; right: 20px; top: 120px; width: 320px; background: rgba(0,0,0,0.85); color: #fff; padding: 15px; border-radius: 8px; z-index: 999; max-height: 500px; overflow-y: auto;">
        <h3 style="margin-top: 0; color: #ffeb3b; border-bottom: 1px solid #666; padding-bottom: 8px;">⚠️ 业务逻辑拦截台</h3>
        <div v-if="simStore.eventLogs.length === 0" style="color: #aaa; font-size: 12px; margin-top: 10px;">暂无拦截记录...</div>
        <div v-for="(log, index) in simStore.eventLogs" :key="index" style="margin-top: 10px; font-size: 13px; line-height: 1.5; border-bottom: 1px dashed #444; padding-bottom: 8px;">
          <span style="color: #ff5722; font-family: monospace;">[{{ log.simTime }}ms]</span>
          <span style="color: #03a9f4; font-weight: bold; margin-left: 5px;">{{ log.deviceId }}</span>
          <div style="color: #ff8a80; margin-top: 4px;">✖ {{ log.message }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// @ts-nocheck
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useSimStore } from '../stores/simStore'
import { mockFlowNormal, mockFlowError } from '../api/simulation'
import { ElMessage } from 'element-plus'

const simStore = useSimStore()
let animFrameId: number;  // 动画帧句柄
const stageConfig = ref({ width: 800, height: 600 })
const logContainer = ref<HTMLElement | null>(null)
// 用于平滑动画的中间状态设备数据，避免直接突变导致闪烁
const displayDevices = ref<Record<string, any>>({})

// 监听事件列表长度，自动滚动日志到底部
watch(() => simStore.events.length, async () => {
  await nextTick()
  if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight
})

// 根据路径类型返回对应颜色（用于渲染）
const getPathColor = (type: string) => {
  if (type === 'QC_RAIL') return '#e53935';
  if (type === 'ASC_RAIL') return '#8e24aa';
  if (type === 'TRUCK_ROAD') return '#ffb300';
  return '#999';
}

// 格式化事件 subjects 对象为字符串
const formatSubjects = (subjects: any) => {
  if (!subjects) return '';
  return Object.entries(subjects).map(([k, v]) => `${k}:${v}`).join(' | ');
}

/**
 * 动画循环：平滑地将设备位置从当前位置移动到目标位置，并处理闪烁告警。
 * 每帧更新 displayDevices 中的坐标，实现缓动效果。
 */
const animateLoop = () => {
  const now = Date.now();
  simStore.devices.forEach(target => {
    if (!displayDevices.value[target.id]) {
      // 首次出现，直接拷贝
      displayDevices.value[target.id] = { ...target };
    } else {
      const curr = displayDevices.value[target.id];
      const dx = target.posX - curr.posX;
      const dy = target.posY - curr.posY;
      // 距离足够近则直接对齐，否则按比例移动（缓动）
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
        curr.posX = target.posX;
        curr.posY = target.posY;
      } else {
        curr.posX += dx * 0.1;
        curr.posY += dy * 0.1;
      }
      curr.state = target.state;
      curr.type = target.type;

      // 告警闪烁：如果设备在告警集合中且未超时，则根据时间奇偶控制显示
      const alertExpiry = simStore.deviceAlerts.get(target.id);
      curr.isAlerting = alertExpiry && now < alertExpiry && (Math.floor(now / 250) % 2 === 0);
    }
  });
  simStore.animateTick();  // 让 store 也执行一次动画相关更新（如清除过期告警）
  animFrameId = requestAnimationFrame(animateLoop);
};

onMounted(() => {
  // 启动轮询获取快照，并开始动画
  simStore.startSnapshotPolling(500)
  animateLoop()
})

onBeforeUnmount(() => {
  // 清理资源
  simStore.stopSnapshotPolling()
  if (animFrameId) cancelAnimationFrame(animFrameId)
})

// 点击按钮：加载初始场景（调用 store 的 initScene）
const handleMockInit = async () => {
  try {
    await simStore.initScene();
    ElMessage.success('外部 JSON 地图与作业场景加载成功');
  } catch (e: any) {
    ElMessage.error('加载失败');
  }
}

// 点击按钮：模拟正常流程（调用 mockFlowNormal）
const handleMockNormal = async () => {
  try {
    const res: any = await mockFlowNormal();
    ElMessage.success(res.data?.message || res.message || '调度任务与流程序列已下发');
  } catch (e: any) { ElMessage.error(e.message || '下发失败'); }
}

// 点击按钮：模拟错误流程（调用 mockFlowError）
const handleMockError = async () => {
  try {
    const res: any = await mockFlowError();
    ElMessage.warning('引擎校验拦截: ' + (res.data?.message || res.message || res));
  } catch (e: any) { ElMessage.warning('严格拦截: ' + e.message); }
}

const handleStep = () => { simStore.doStepNext() }
const handleReset = async () => { await simStore.doReset() }
</script>

<style scoped>
/* 样式略，保持原有 */
</style>