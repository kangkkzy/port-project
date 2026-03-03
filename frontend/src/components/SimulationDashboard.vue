<template>
  <div class="dashboard">
    <div class="toolbar">
      <h2>港口离散仿真系统</h2>
      <div class="buttons">
        <el-button type="primary" @click="handleStep">单步执行 (Next)</el-button>
        <el-button type="success" @click="handleTogglePlay">
          {{ simStore.isPlaying ? '暂停播放' : '自动播放' }}
        </el-button>
        <el-button type="danger" @click="handleReset">重置系统</el-button>
        <el-divider direction="vertical" />

        <el-dropdown @command="handleTestScenario">
          <el-button type="warning">
            仿真业务自动演示 <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="task-chain" style="font-weight:bold; color:#E6A23C;">
                🌟 进出口核心任务链 (卸船->移箱->提货)
              </el-dropdown-item>
              <el-dropdown-item divided command="dsch">单环节：DSCH (卸船)</el-dropdown-item>
              <el-dropdown-item command="load">单环节：LOAD (装船)</el-dropdown-item>
              <el-dropdown-item command="dlvr">单环节：DLVR (外场提箱)</el-dropdown-item>
              <el-dropdown-item command="yard-shift">单环节：YARD_SHIFT (场内移箱)</el-dropdown-item>
              <el-dropdown-item command="recv">单环节：RECV (外场收箱)</el-dropdown-item>
              <el-dropdown-item command="direct-in">单环节：DIRECT_IN (直进船)</el-dropdown-item>
              <el-dropdown-item command="direct-out">单环节：DIRECT_OUT (直提)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

              <v-circle
                  v-for="fence in simStore.fences"
                  :key="fence.nodeId"
                  :config="{
                  x: fence.posX,
                  y: fence.posY,
                  radius: fence.radius || 20,
                  fill: fence.status === '02' ? '#4caf50' : '#f44336',
                  opacity: 0.5,
                  stroke: '#333',
                  strokeWidth: 2
                }"
              />

              <v-rect
                  v-for="station in simStore.chargingStations"
                  :key="station.stationCode"
                  :config="{
                  x: station.posX - 8,
                  y: station.posY - 8,
                  width: 16,
                  height: 16,
                  fill: '#ffeb3b',
                  stroke: '#f57f17',
                  strokeWidth: 2,
                  cornerRadius: 3
                }"
              />

              <!-- 集装箱（如果后端提供绝对坐标则可渲染） -->
              <v-rect
                  v-for="c in simStore.containers"
                  :key="c.containerId"
                  :config="{
                  x: (c.posX || 0) - 6,
                  y: (c.posY || 0) - 6,
                  width: 12,
                  height: 12,
                  fill: '#ff7043',
                  stroke: '#bf360c',
                  strokeWidth: 1,
                  cornerRadius: 2
                }"
              />

              <v-rect
                  v-for="v in simStore.vessels"
                  :key="v.vesselId"
                  :config="{
                  x: (v.berthLocation || 0) - (v.length || 50) / 2,
                  y: 20,
                  width: v.length || 50,
                  height: 40,
                  fill: '#90caf9',
                  stroke: '#0d47a1',
                  strokeWidth: 2,
                  cornerRadius: 5
                }"
              />
              <v-text
                  v-for="v in simStore.vessels"
                  :key="v.vesselId + '-label'"
                  :config="{
                  x: (v.berthLocation || 0) - 30,
                  y: 65,
                  text: v.vesselId,
                  fontSize: 12,
                  fill: '#0d47a1',
                  fontStyle: 'bold'
                }"
              />
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

      <div class="right-section">
        <div class="info-panel">
          <h3>实时设备状态 (共 {{ simStore.devices.length }} 台)</h3>
          <ul class="device-list">
            <li v-for="dev in simStore.devices" :key="dev.id" @click="selectDevice(dev.id, dev.type)" :class="{ active: selectedDeviceId === dev.id }">
              <span class="dev-id" :style="{ color: getDeviceColor(dev.type) }">{{ dev.id }}</span>
              <span class="dev-state">[{{ dev.state }}]</span>
              <span class="dev-pos">坐标: ({{ dev.posX.toFixed(0) }}, {{ dev.posY.toFixed(0) }})</span>
              <span v-if="dev.powerLevel" class="dev-power">电量: {{ dev.powerLevel }}%</span>
            </li>
          </ul>
        </div>

        <div class="control-panel" v-if="selectedDeviceId">
          <h3>设备控制 - {{ selectedDeviceId }}</h3>
          <div class="control-info">
            <p><strong>类型:</strong> {{ selectedDeviceType }}</p>
            <p><strong>状态:</strong> {{ selectedDeviceState }}</p>
            <p v-if="selectedDevicePower"><strong>电量:</strong> {{ selectedDevicePower }}%</p>
          </div>
          <div class="control-buttons">
            <el-button v-if="selectedDeviceType === 'ELECTRIC_TRUCK' || selectedDeviceType === 'INTERNAL_TRUCK'" type="primary" size="small" @click="showTruckMoveDialog = true">移动</el-button>
            <el-button v-if="selectedDeviceType === 'QC'" type="primary" size="small" @click="showCraneMoveDialog = true">移动</el-button>
            <el-button v-if="selectedDeviceType === 'ASC'" type="primary" size="small" @click="showCraneMoveDialog = true">移动</el-button>
            <el-button v-if="selectedDeviceType === 'ELECTRIC_TRUCK'" type="warning" size="small" @click="showChargeDialog = true">充电</el-button>
            <el-button v-if="selectedDeviceType === 'QC' || selectedDeviceType === 'ASC'" type="success" size="small" @click="showCraneOpDialog = true">操作</el-button>
          </div>
        </div>

        <div class="fence-panel" v-if="simStore.fences.length > 0">
          <h3>围栏状态 (共 {{ simStore.fences.length }} 个)</h3>
          <ul class="fence-list">
            <li v-for="fence in simStore.fences" :key="fence.nodeId">
              <span class="fence-id">{{ fence.nodeId }}</span>
              <span :class="['fence-status', fence.status === '02' ? 'open' : 'closed']">{{ fence.status === '02' ? '通行' : '禁止通行' }}</span>
              <el-button size="small" @click="toggleFence(fence)">{{ fence.status === '02' ? '关闭' : '开启' }}</el-button>
            </li>
          </ul>
        </div>

        <div class="wi-panel" v-if="simStore.workInstructions.length > 0">
          <h3>作业指令 (共 {{ simStore.workInstructions.length }} 条)</h3>
          <ul class="wi-list">
            <li v-for="wi in simStore.workInstructions" :key="wi.wiRefNo">
              <span class="wi-ref">{{ wi.wiRefNo }}</span>
              <span class="wi-move">{{ wi.moveKind }}</span>
              <span class="wi-status">{{ wi.wiStatus }}</span>
            </li>
          </ul>
        </div>

        <div class="error-panel" v-if="simStore.errors.length > 0">
          <h3>错误日志 (共 {{ simStore.errors.length }} 条)</h3>
          <ul class="error-list">
            <li v-for="(err, idx) in simStore.errors.slice(-10)" :key="idx">
              <span class="err-time">[{{ err.simTime }}ms]</span>
              <span class="err-msg">{{ err.message || err.error }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <el-dialog v-model="showTruckMoveDialog" title="集卡移动控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="目标X">
          <el-input-number v-model="truckMoveTarget.x" :min="0" :max="800" />
        </el-form-item>
        <el-form-item label="目标Y">
          <el-input-number v-model="truckMoveTarget.y" :min="0" :max="600" />
        </el-form-item>
        <el-form-item label="速度">
          <el-input-number v-model="truckMoveTarget.speed" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTruckMoveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleTruckMove">确认移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCraneMoveDialog" title="起重机移动控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="移动类型">
          <el-select v-model="craneMoveType">
            <el-option label="水平移动(MOVE_HORIZONTAL)" value="MOVE_HORIZONTAL" />
            <el-option label="垂直移动(MOVE_VERTICAL)" value="MOVE_VERTICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="距离(米)">
          <el-input-number v-model="craneMoveDistance" :min="-500" :max="500" />
        </el-form-item>
        <el-form-item label="速度(米/秒)">
          <el-input-number v-model="craneMoveSpeed" :min="1" :max="50" :step="0.5" :precision="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCraneMoveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCraneMove">确认移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCraneOpDialog" title="起重机操作控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="操作类型">
          <el-select v-model="craneOpType">
            <el-option label="抓箱(FETCH_DONE)" value="FETCH_DONE" />
            <el-option label="放箱(PUT_DONE)" value="PUT_DONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="耗时(毫秒)">
          <el-input-number v-model="craneOpDuration" :min="100" :max="60000" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCraneOpDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCraneOperate">确认操作</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showChargeDialog" title="集卡充电控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="充电桩">
          <el-select v-model="chargeStationId" placeholder="请选择充电桩" filterable>
            <el-option v-for="station in simStore.chargingStations" :key="station.stationCode" :label="station.stationCode" :value="station.stationCode">
              <span>{{ station.stationCode }}</span>
              <span style="float: right; color: #8492a6; font-size: 12px;">{{ station.status }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="无充电桩" v-if="simStore.chargingStations.length === 0">
          <span style="color: #909399;">当前场景无可用充电桩，请先加载包含充电桩的场景</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showChargeDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCharge" :disabled="!chargeStationId">确认充电</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { useSimStore } from '../stores/simStore'
import { moveTruck, moveCrane, operateCrane, controlFence, chargeTruck, testTruckDelivery, testQcLoading, testAscUnloading, testFullLoading, testYardShift, testRecv, testDirectIn, testDirectOut } from '../api/simulation'
import { ElMessage } from 'element-plus'

const simStore = useSimStore()
let timer: any = null
let animFrameId: number;

const stageConfig = ref({ width: 800, height: 600 })
const selectedDeviceId = ref('')
const selectedDeviceType = ref('')
const selectedDeviceState = ref('')
const selectedDevicePower = ref<number | null>(null)
const logContainer = ref<HTMLElement | null>(null)

// 对话框控制
const showTruckMoveDialog = ref(false)
const showCraneMoveDialog = ref(false)
const showCraneOpDialog = ref(false)
const showChargeDialog = ref(false)

// 集卡移动参数
const truckMoveTarget = ref({ x: 0, y: 0, speed: 10 })

// 起重机移动参数
const craneMoveType = ref('MOVE_HORIZONTAL')
const craneMoveDistance = ref(0)
const craneMoveSpeed = ref(10)

// 起重机操作参数
const craneOpType = ref('FETCH_DONE')
const craneOpDuration = ref(5000)

// 充电参数
const chargeStationId = ref('')

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
  animFrameId = requestAnimationFrame(animateLoop);
};

onMounted(() => {
  simStore.updateSnapshot()
  simStore.loadMapPaths()
  timer = setInterval(() => { if (!simStore.isPlaying) simStore.updateSnapshot() }, 1000)
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
  const device = simStore.devices.find(d => d.id === id)
  if (device) {
    selectedDeviceState.value = device.state
    selectedDevicePower.value = device.powerLevel || null
    if (type === 'ELECTRIC_TRUCK' || type === 'INTERNAL_TRUCK') {
      truckMoveTarget.value = { x: Math.round(device.posX), y: Math.round(device.posY), speed: 10 }
    }
    if (type === 'QC' || type === 'ASC') {
      craneMoveDistance.value = 0
      craneMoveSpeed.value = 10
    }
  }
  chargeStationId.value = ''
  simStore.setSelectedDevice(device)
}

// 集卡移动
const handleTruckMove = async () => {
  const dx = truckMoveTarget.value.x - (simStore.devices.find(d => d.id === selectedDeviceId.value)?.posX || 0)
  const dy = truckMoveTarget.value.y - (simStore.devices.find(d => d.id === selectedDeviceId.value)?.posY || 0)
  const distance = Math.sqrt(dx * dx + dy * dy)

  if (distance < 1) {
    ElMessage.warning('目标位置与当前位置相同，无需移动')
    return
  }

  if (truckMoveTarget.value.speed <= 0) {
    ElMessage.warning('请输入有效的速度')
    return
  }

  try {
    await moveTruck({
      truckId: selectedDeviceId.value,
      targetPoint: { x: truckMoveTarget.value.x, y: truckMoveTarget.value.y },
      speed: truckMoveTarget.value.speed
    })
    ElMessage.success('移动指令已下发')
    showTruckMoveDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '移动指令失败')
  }
}

// 起重机移动
const handleCraneMove = async () => {
  try {
    await moveCrane({
      craneId: selectedDeviceId.value,
      moveType: craneMoveType.value,
      distance: craneMoveDistance.value,
      speed: craneMoveSpeed.value
    })
    ElMessage.success('移动指令已下发')
    showCraneMoveDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '移动指令失败')
  }
}

// 起重机操作
const handleCraneOperate = async () => {
  try {
    await operateCrane({
      craneId: selectedDeviceId.value,
      action: craneOpType.value,
      durationMS: craneOpDuration.value
    })
    ElMessage.success('操作指令已下发')
    showCraneOpDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '操作指令失败')
  }
}

// 集卡充电
const handleCharge = async () => {
  try {
    await chargeTruck({
      truckId: selectedDeviceId.value,
      stationId: chargeStationId.value
    })
    ElMessage.success('充电指令已下发')
    showChargeDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '充电指令失败')
  }
}

// 围栏控制
const toggleFence = async (fence: any) => {
  try {
    const newStatus = fence.status === '01' ? '02' : '01'
    await controlFence({
      fenceId: fence.nodeId,
      status: newStatus
    })
    ElMessage.success(`围栏已${newStatus === '02' ? '开启' : '关闭'}`)
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '围栏控制失败')
  }
}

// ==================== 路径验证函数 ====================
const getPathsByType = (pathType: string) => {
  return simStore.mapPaths.filter(p => p.pathType === pathType);
};

const isValidQCPosition = (x: number, y: number): boolean => {
  const qcPaths = getPathsByType('QC_RAIL');
  if (qcPaths.length === 0) {
    return Math.abs(y - 140) < 5;
  }
  return qcPaths.some(path => {
    if (path.direction !== 'HORIZONTAL') return false;
    return Math.abs(y - path.position) < 5 && x >= path.startPoint && x <= path.endPoint;
  });
};

const isValidASCPosition = (x: number, y: number): boolean => {
  const ascPaths = getPathsByType('ASC_RAIL');
  if (ascPaths.length === 0) {
    return [175, 425, 675].some(rail => Math.abs(x - rail) < 5);
  }
  return ascPaths.some(path => {
    if (path.direction !== 'VERTICAL') return false;
    return Math.abs(x - path.position) < 5 && y >= path.startPoint && y <= path.endPoint;
  });
};

const isValidTruckPosition = (x: number, y: number): boolean => {
  const truckPaths = getPathsByType('TRUCK_ROAD');
  if (truckPaths.length === 0) {
    const hRoads = [200, 550];
    const vRoads = [50, 300, 550, 750];
    return hRoads.some(road => Math.abs(y - road) < 5) || vRoads.some(road => Math.abs(x - road) < 5);
  }
  return truckPaths.some(path => {
    if (path.direction === 'HORIZONTAL') {
      return Math.abs(y - path.position) < 5 && x >= path.startPoint && x <= path.endPoint;
    } else if (path.direction === 'VERTICAL') {
      return Math.abs(x - path.position) < 5 && y >= path.startPoint && y <= path.endPoint;
    }
    return false;
  });
};

const getTruckRoadBounds = () => {
  const truckPaths = getPathsByType('TRUCK_ROAD');
  let minX = 50, maxX = 750, minY = 200, maxY = 550;

  if (truckPaths.length === 0) {
    return { minX, maxX, minY, maxY };
  }

  truckPaths.forEach(p => {
    if (p.direction === 'HORIZONTAL') {
      minX = Math.min(minX, p.startPoint);
      maxX = Math.max(maxX, p.endPoint);
      minY = Math.min(minY, p.position);
      maxY = Math.max(maxY, p.position);
    } else if (p.direction === 'VERTICAL') {
      minX = Math.min(minX, p.position);
      maxX = Math.max(maxX, p.position);
      minY = Math.min(minY, p.startPoint);
      maxY = Math.max(maxY, p.endPoint);
    }
  });

  return { minX, maxX, minY, maxY };
};

const validateMoveTarget = (deviceType: string, x: number, y: number): string | null => {
  if (deviceType === 'QC' || deviceType === 'CRANE_QC') {
    if (!isValidQCPosition(x, y)) {
      const qcPaths = getPathsByType('QC_RAIL');
      if (qcPaths.length > 0) {
        const positions = qcPaths.map(p => `y=${p.position}`).join(', ');
        return `QC(桥吊)只能在水平轨道(${positions})上移动，当前位置(${Math.round(x)}, ${Math.round(y)})不在轨道上`;
      }
      return `QC(桥吊)移动位置(${Math.round(x)}, ${Math.round(y)})无效`;
    }
  } else if (deviceType === 'ASC' || deviceType === 'CRANE_ASC') {
    if (!isValidASCPosition(x, y)) {
      const ascPaths = getPathsByType('ASC_RAIL');
      if (ascPaths.length > 0) {
        const positions = ascPaths.map(p => `x=${p.position}`).join(', ');
        return `ASC(龙门吊)只能在垂直轨道(${positions})上移动，当前位置(${Math.round(x)}, ${Math.round(y)})不在轨道上`;
      }
      return `ASC(龙门吊)移动位置(${Math.round(x)}, ${Math.round(y)})无效`;
    }
  } else if (deviceType === 'ELECTRIC_TRUCK' || deviceType === 'INTERNAL_TRUCK' || deviceType === 'TRUCK') {
    if (!isValidTruckPosition(x, y)) {
      const truckPaths = getPathsByType('TRUCK_ROAD');
      if (truckPaths.length > 0) {
        const hPaths = truckPaths.filter(p => p.direction === 'HORIZONTAL').map(p => `y=${p.position}`);
        const vPaths = truckPaths.filter(p => p.direction === 'VERTICAL').map(p => `x=${p.position}`);
        const positionInfo = [...hPaths, ...vPaths].join(', ');
        return `集卡只能在道路网格(${positionInfo})上移动，当前位置(${Math.round(x)}, ${Math.round(y)})不在道路上`;
      }
      return `集卡移动位置(${Math.round(x)}, ${Math.round(y)})无效，不在道路上`;
    }
  }
  return null;
};

// ==================== 设备移动处理 ====================
const handleStageClick = async (e: any) => {
  if (e.target.name() !== 'bg' || !selectedDeviceId.value) return;
  const pos = e.target.getStage().getPointerPosition();
  const device = simStore.devices.find(d => d.id === selectedDeviceId.value);
  if (!device) return;

  try {
    if (selectedDeviceType.value === 'QC') {
      const targetX = Math.max(0, Math.min(800, pos.x));
      const targetY = 140;

      const error = validateMoveTarget('QC', targetX, targetY);
      if (error) { ElMessage.warning(error); return; }

      const distance = targetX - device.posX;
      if (Math.abs(distance) > 1) {
        await moveCrane({ craneId: device.id, moveType: "MOVE_HORIZONTAL", distance: distance, speed: 10.0 });
      }
    } else if (selectedDeviceType.value === 'ASC') {
      const ascRails = [175, 425, 675];
      const nearestRail = ascRails.reduce((prev, curr) => Math.abs(curr - device.posX) < Math.abs(prev - device.posX) ? curr : prev);
      const targetX = nearestRail;
      const targetY = Math.max(200, Math.min(550, pos.y));

      const error = validateMoveTarget('ASC', targetX, targetY);
      if (error) { ElMessage.warning(error); return; }

      const distance = targetY - device.posY;
      if (Math.abs(distance) > 1) {
        await moveCrane({ craneId: device.id, moveType: "MOVE_VERTICAL", distance: distance, speed: 10.0 });
      }
    } else {
      const hRoads = [200, 550];
      const vRoads = [50, 300, 550, 750];
      const nearestH = hRoads.reduce((prev, curr) => Math.abs(curr - pos.y) < Math.abs(prev - pos.y) ? curr : prev);
      const nearestV = vRoads.reduce((prev, curr) => Math.abs(curr - pos.x) < Math.abs(prev - pos.x) ? curr : prev);

      const { minX, maxX, minY, maxY } = getTruckRoadBounds();

      let targetX = pos.x;
      let targetY = pos.y;
      if (Math.abs(nearestH - pos.y) < Math.abs(nearestV - pos.x)) {
        targetY = nearestH;
        targetX = Math.max(minX, Math.min(maxX, pos.x));
      } else {
        targetX = nearestV;
        targetY = Math.max(minY, Math.min(maxY, pos.y));
      }

      const error = validateMoveTarget(selectedDeviceType.value, targetX, targetY);
      if (error) { ElMessage.warning(error); return; }

      await moveTruck({ truckId: device.id, targetPoint: { x: targetX, y: targetY }, speed: 10.0 });
    }
    await simStore.updateSnapshot()
  } catch (err: any) {
    ElMessage.error(err.message || '移动失败')
  }
}

const handleStep = () => { simStore.doStepNext() }
const handleTogglePlay = () => { simStore.togglePlay() }
const handleReset = () => { simStore.doReset() }

// ===================== 核心测试分发方法 =====================
const handleTestScenario = async (command: string) => {
  try {
    await simStore.doReset();
    simStore.stopAutoPlay();

    let res;
    switch (command) {
      case 'task-chain': res = await testFullLoading(); break;   // 多业务链路流转 -> full-loading
      case 'dsch': res = await testTruckDelivery(); break;        // DSCH卸船 -> truck-delivery
      case 'load': res = await testQcLoading(); break;            // LOAD装船 -> qc-loading
      case 'dlvr': res = await testAscUnloading(); break;         // DLVR提箱 -> asc-unloading
      case 'yard-shift': res = await testYardShift(); break;
      case 'recv': res = await testRecv(); break;
      case 'direct-in': res = await testDirectIn(); break;
      case 'direct-out': res = await testDirectOut(); break;
      default: ElMessage.warning(`命令 ${command} 暂未实现`); return;
    }

    ElMessage.success({
      message: typeof res === 'string' ? res : (res?.msg || `[${command}] 测试指令注入成功`),
      duration: 4000
    });

    await simStore.updateSnapshot();
    if(!simStore.isPlaying) {
      simStore.togglePlay();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '测试调度失败，请检查控制台或引擎异常');
  }
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

.right-section { width: 380px; display: flex; flex-direction: column; gap: 15px; overflow-y: auto; }

.info-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; max-height: 250px; display: flex; flex-direction: column; }
.info-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.device-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.device-list li { padding: 10px; border-bottom: 1px solid #ebeef5; display: flex; flex-direction: column; gap: 3px; cursor: pointer; transition: background 0.2s; border-radius: 4px; font-size: 13px; }
.device-list li:hover { background: #f5f7fa; }
.device-list li.active { background: #ecf5ff; border: 1px solid #c6e2ff; }
.dev-id { font-weight: bold; font-size: 14px;}
.dev-state { color: #E6A23C; font-size: 12px; }
.dev-pos { color: #909399; font-size: 11px; }
.dev-power { color: #67c23a; font-size: 11px; }

/* 控制面板 */
.control-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; }
.control-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.control-info { margin-bottom: 10px; font-size: 13px; color: #606266; }
.control-info p { margin: 5px 0; }
.control-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

/* 围栏面板 */
.fence-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; }
.fence-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.fence-list { list-style: none; padding: 0; margin: 0; }
.fence-list li { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #ebeef5; font-size: 13px; }
.fence-id { font-weight: bold; }
.fence-status { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.fence-status.open { background: #e7f7e7; color: #67c23a; }
.fence-status.closed { background: #fdecea; color: #f56c6c; }

/* 作业指令面板 */
.wi-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; max-height: 200px; display: flex; flex-direction: column; }
.wi-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.wi-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.wi-list li { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px solid #ebeef5; font-size: 12px; }
.wi-ref { font-weight: bold; color: #409EFF; }
.wi-move { color: #E6A23C; }
.wi-status { color: #67c23a; }

/* 错误日志面板 */
.error-panel { background: #fef0f0; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; max-height: 150px; display: flex; flex-direction: column; border: 1px solid #fbc4c4; }
.error-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #f56c6c; }
.error-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.error-list li { padding: 6px 0; font-size: 12px; border-bottom: 1px solid #fbc4c4; }
.err-time { color: #909399; margin-right: 8px; }
.err-msg { color: #f56c6c; }

/* 自定义滚动条 */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #c0c4cc; border-radius: 3px; }
.terminal-panel ::-webkit-scrollbar-thumb { background: #555; }
</style>