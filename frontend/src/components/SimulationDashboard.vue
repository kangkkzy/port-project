<template>
  <div class="dashboard">
    <div class="toolbar">
      <h2>港口离散仿真系统 </h2>
      <div class="buttons">
        <el-button type="warning" @click="handleLoadMockData">一键加载测试场景</el-button>
        <el-button type="primary" @click="handleStep">单步执行 (Next)</el-button>
        <el-button type="success" @click="handleTogglePlay">
          {{ simStore.isPlaying ? '暂停播放' : '自动播放' }}
        </el-button>
        <el-button type="danger" @click="handleReset">重置系统</el-button>
      </div>
      <div class="time-display">
        当前仿真时钟: <strong>{{ simStore.simTime }}</strong> ms
      </div>
    </div>

    <div class="main-content">
      <div class="left-section">
        <div class="map-container">
          <v-stage :config="stageConfig" @click="handleStageClick">
            <v-layer>
              <v-rect :config="{ x: 0, y: 0, width: 800, height: 100, fill: '#bbdefb', name: 'bg' }" />
              <v-text :config="{ x: 20, y: 40, text: '海域 / 船泊区 (VESSEL)', fontSize: 24, fill: '#1565c0', opacity: 0.5, name: 'bg' }" />
              <v-rect :config="{ x: 0, y: 100, width: 800, height: 80, fill: '#e0e0e0', name: 'bg' }" />
              <v-line :config="{ points: [0, 140, 800, 140], stroke: '#e53935', strokeWidth: 4, dash: [15, 10], name: 'bg' }" />
              <v-rect :config="{ x: 100, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c', strokeWidth: 2, name: 'bg' }" />
              <v-rect :config="{ x: 350, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c', strokeWidth: 2, name: 'bg' }" />
              <v-rect :config="{ x: 600, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c', strokeWidth: 2, name: 'bg' }" />
              <v-line :config="{ points: [175, 230, 175, 520], stroke: '#8e24aa', strokeWidth: 4, dash: [15, 10], name: 'bg' }" />
              <v-line :config="{ points: [425, 230, 425, 520], stroke: '#8e24aa', strokeWidth: 4, dash: [15, 10], name: 'bg' }" />
              <v-line :config="{ points: [675, 230, 675, 520], stroke: '#8e24aa', strokeWidth: 4, dash: [15, 10], name: 'bg' }" />
              <v-line :config="{ points: [50, 200, 750, 200], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
              <v-line :config="{ points: [50, 550, 750, 550], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
              <v-line :config="{ points: [50, 200, 50, 550], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
              <v-line :config="{ points: [300, 200, 300, 550], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
              <v-line :config="{ points: [550, 200, 550, 550], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
              <v-line :config="{ points: [750, 200, 750, 550], stroke: '#ffb300', strokeWidth: 10, opacity: 0.5, name: 'bg' }" />
            </v-layer>

            <v-layer>
              <v-group v-for="dev in displayDevices" :key="dev.id" :config="{ x: dev.posX, y: dev.posY }">
                <v-rect :config="{
                  x: -15, y: -15, width: 30, height: 30,
                  fill: getDeviceColor(dev.type),
                  stroke: selectedDeviceId === dev.id ? '#000' : 'transparent',
                  strokeWidth: 4, shadowBlur: 5
                }" @click="selectDevice(dev.id, dev.type)" />
                <v-text :config="{ x: -25, y: 20, text: dev.id, fontSize: 13, fill: '#000', fontStyle: 'bold' }" />
              </v-group>
            </v-layer>
          </v-stage>
        </div>

        <div class="terminal-panel">
          <div class="terminal-header">
            <span>> 离散事件引擎内核日志 (Discrete Event Engine Logs)</span>
          </div>
          <ul class="log-list" ref="logContainer">
            <li v-for="log in simStore.events" :key="log.eventId">
              <span class="log-time">[{{ log.simTime }}ms]</span>
              <span class="log-type">{{ log.type }}</span>
              <span class="log-subject">{{ formatSubjects(log.subjects) }}</span>
            </li>
            <li v-if="simStore.events.length === 0" style="color: #666;">系统初始化就绪，等待事件触发...</li>
          </ul>
        </div>
      </div>

      <div class="info-panel">
        <h3>实时设备状态 (共 {{ simStore.devices.length }} 台)</h3>
        <ul class="device-list">
          <li v-for="dev in simStore.devices" :key="dev.id" @click="selectDevice(dev.id, dev.type)" :class="{ active: selectedDeviceId === dev.id }">
            <span class="dev-id" :style="{ color: getDeviceColor(dev.type) }">{{ dev.id }}</span>
            <span class="dev-state">[{{ dev.state }}]</span>
            <span class="dev-pos">目标坐标: ({{ dev.posX.toFixed(0) }}, {{ dev.posY.toFixed(0) }})</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useSimStore } from '../stores/simStore'
import { loadScenario, moveTruck, moveCrane } from '../api/simulation'
import { ElMessage } from 'element-plus'

const simStore = useSimStore()
let timer: any = null
let animFrameId: number;

const stageConfig = ref({ width: 800, height: 600 })
const selectedDeviceId = ref('')
const selectedDeviceType = ref('')
const logContainer = ref<HTMLElement | null>(null)

// 专门用于动画渲染的设备数组
const displayDevices = ref<Record<string, any>>({})

// 日志自动滚动到底部
watch(() => simStore.events.length, async () => {
  await nextTick()
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
})

// 核心渲染循环 (60FPS 平滑补间算法)
const animateLoop = () => {
  simStore.devices.forEach(target => {
    if (!displayDevices.value[target.id]) {
      // 第一次出现，直接放到位置上
      displayDevices.value[target.id] = { ...target };
    } else {
      const curr = displayDevices.value[target.id];
      const dx = target.posX - curr.posX;
      const dy = target.posY - curr.posY;

      // 如果距离极小，直接吸附到位（消除抖动）
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
        curr.posX = target.posX;
        curr.posY = target.posY;
      } else {
        // 线性插值 (Lerp)，0.08 控制动画的弹簧阻尼感
        curr.posX += dx * 0.08;
        curr.posY += dy * 0.08;
      }
      curr.state = target.state;
      curr.type = target.type;
    }
  });
  // 保持循环调用
  animFrameId = requestAnimationFrame(animateLoop);
};

onMounted(() => {
  simStore.updateSnapshot()
  // 启动轮询
  timer = setInterval(() => { if (!simStore.isPlaying) simStore.updateSnapshot() }, 1000)
  // 启动动画引擎
  animateLoop()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (animFrameId) cancelAnimationFrame(animFrameId)
})

const getDeviceColor = (type: string) => {
  if (type === 'ELECTRIC_TRUCK' || type === 'INTERNAL_TRUCK') return '#2196f3';
  if (type === 'QC') return '#ff9800';
  if (type === 'ASC') return '#4caf50';
  return '#909399';
}

const formatSubjects = (subjects: any) => {
  if (!subjects) return '';
  return Object.entries(subjects).map(([k, v]) => `${k}:${v}`).join(' | ');
}

const selectDevice = (id: string, type: string) => {
  selectedDeviceId.value = id
  selectedDeviceType.value = type
}

// 约束寻路算法
const handleStageClick = async (e: any) => {
  if (e.target.name() !== 'bg' || !selectedDeviceId.value) return;
  const pos = e.target.getStage().getPointerPosition();
  const device = simStore.devices.find(d => d.id === selectedDeviceId.value);
  if (!device) return;

  try {
    if (selectedDeviceType.value === 'QC') {
      const distance = pos.x - device.posX;
      await moveCrane({ craneId: device.id, moveType: "MOVE_HORIZONTAL", distance: distance, speed: 10.0 });
    } else if (selectedDeviceType.value === 'ASC') {
      const distance = pos.y - device.posY;
      await moveCrane({ craneId: device.id, moveType: "MOVE_VERTICAL", distance: distance, speed: 10.0 });
    } else {
      const hRoads = [200, 550]; const vRoads = [50, 300, 550, 750];
      const nearestH = hRoads.reduce((prev, curr) => Math.abs(curr - pos.y) < Math.abs(prev - pos.y) ? curr : prev);
      const nearestV = vRoads.reduce((prev, curr) => Math.abs(curr - pos.x) < Math.abs(prev - pos.x) ? curr : prev);
      let targetX = pos.x; let targetY = pos.y;
      if (Math.abs(nearestH - pos.y) < Math.abs(nearestV - pos.x)) { targetY = nearestH; } else { targetX = nearestV; }
      await moveTruck({ truckId: device.id, targetPoint: { x: targetX, y: targetY }, speed: 10.0 });
    }
  } catch (err) {}
}

const handleStep = () => { simStore.doStepNext() }
const handleTogglePlay = () => { simStore.togglePlay() }
const handleReset = () => { simStore.doReset() }

const handleLoadMockData = async () => {
  const mockData = {
    trucks: [
      { id: "TRUCK_01", type: "ELECTRIC_TRUCK", state: "IDLE", posX: 50, posY: 200, powerLevel: 100 },
      { id: "TRUCK_02", type: "ELECTRIC_TRUCK", state: "IDLE", posX: 550, posY: 550, powerLevel: 80 }
    ],
    qcDevices: [
      { id: "QC_01", type: "QC", state: "IDLE", posX: 400, posY: 140 }
    ],
    ascDevices: [
      { id: "ASC_01", type: "ASC", state: "IDLE", posX: 175, posY: 300 },
      { id: "ASC_02", type: "ASC", state: "IDLE", posX: 675, posY: 450 }
    ]
  }
  await loadScenario(mockData)
  await simStore.updateSnapshot()
}
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; height: 100vh; padding: 20px; box-sizing: border-box; background: #f0f2f5;}
.toolbar { display: flex; align-items: center; justify-content: space-between; background: white; padding: 15px 20px; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); margin-bottom: 20px; }
.toolbar h2 { margin: 0; color: #303133; }
.time-display { font-size: 18px; color: #409EFF; }
.main-content { display: flex; gap: 20px; flex: 1; min-height: 0; }
.left-section { display: flex; flex-direction: column; gap: 15px; flex: 1; }
.map-container { background: #fff; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); display: flex; justify-content: center; padding: 10px; }

/* 终端样式 */
.terminal-panel { height: 180px; background: #1e1e1e; color: #00ff00; border-radius: 8px; padding: 15px; font-family: 'Consolas', monospace; display: flex; flex-direction: column; box-shadow: 0 4px 15px rgba(0,0,0,0.3); }
.terminal-header { color: #fff; border-bottom: 1px solid #444; padding-bottom: 8px; margin-bottom: 10px; font-weight: bold; font-size: 14px; }
.log-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.log-list li { margin-bottom: 6px; font-size: 13px; line-height: 1.4; }
.log-time { color: #888; margin-right: 12px; }
.log-type { color: #569cd6; font-weight: bold; margin-right: 12px; display: inline-block; min-width: 120px; }
.log-subject { color: #ce9178; }

.info-panel { width: 350px; background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 20px; display: flex; flex-direction: column; }
.device-list { list-style: none; padding: 0; overflow-y: auto; flex: 1; }
.device-list li { padding: 12px; border-bottom: 1px solid #ebeef5; display: flex; flex-direction: column; gap: 5px; cursor: pointer; transition: background 0.2s; border-radius: 4px; }
.device-list li:hover { background: #f5f7fa; }
.device-list li.active { background: #ecf5ff; border: 1px solid #c6e2ff; }
.dev-id { font-weight: bold; font-size: 15px;}
.dev-state { color: #E6A23C; font-size: 14px; }
.dev-pos { color: #909399; font-size: 13px; }

/* 自定义滚动条 */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #c0c4cc; border-radius: 3px; }
.terminal-panel ::-webkit-scrollbar-thumb { background: #555; }
</style>