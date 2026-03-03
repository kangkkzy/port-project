<template>
  <div class="dashboard">
    <div class="toolbar">
      <h2>港口离散仿真系统</h2>
      <div class="buttons">
        <el-button type="info" @click="handleInitScene" :loading="isInitLoading">一键加载场景</el-button>
        <el-button type="primary" @click="handleStep">单步执行 (Next)</el-button>
        <el-button type="success" @click="handleTogglePlay">
          {{ simStore.isPlaying ? '暂停播放' : '自动播放' }}
        </el-button>
        <el-button type="danger" @click="handleReset">重置系统</el-button>
        <el-divider direction="vertical" />

        <el-dropdown @command="handleTestScenario">
          <el-button type="warning" :loading="isTestLoading">
            仿真业务自动演示 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
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
            <!-- 第1层：背景层 - 海域和堆场底图 -->
            <v-layer name="background">
              <v-rect :config="{ x: 0, y: 0, width: 800, height: 120, fill: '#bbdefb', name: 'bg' }" />
              <v-text :config="{ x: 20, y: 50, text: '海域 / 船泊区 (VESSEL)', fontSize: 24, fill: '#1565c0', opacity: 0.5, name: 'bg' }" />
              <!-- 堆场背景 -->
              <v-rect v-for="(yard, idx) in yardAreas" :key="'yard-bg-'+idx" :config="{ x: yard.x, y: yard.y, width: yard.width, height: yard.height, fill: yard.fill, stroke: yard.stroke, strokeWidth: 2, name: 'bg' }" />
            </v-layer>

            <!-- 第2层：轨道层 - QC轨道(红色虚线)和ASC轨道(紫色虚线) -->
            <v-layer name="rails">
              <v-line
                  v-for="(path, idx) in qcRails"
                  :key="'qc-rail-'+idx"
                  :config="{
                  points: [path.startPoint, path.position, path.endPoint, path.position],
                  stroke: '#e53935',
                  strokeWidth: 3,
                  dash: [15, 10],
                  opacity: 0.8,
                  name: 'rail'
                }"
              />
              <v-line
                  v-for="(path, idx) in ascRails"
                  :key="'asc-rail-'+idx"
                  :config="{
                  points: [path.position, path.startPoint, path.position, path.endPoint],
                  stroke: '#8e24aa',
                  strokeWidth: 3,
                  dash: [15, 10],
                  opacity: 0.8,
                  name: 'rail'
                }"
              />
              <!-- 轨道标签 -->
              <v-text :config="{ x: 30, y: 115, text: 'QC轨道(红色)', fontSize: 10, fill: '#e53935', name: 'rail' }" />
              <v-text :config="{ x: 120, y: 255, text: 'ASC轨道(紫色)', fontSize: 10, fill: '#8e24aa', name: 'rail' }" />
            </v-layer>

            <!-- 第3层：道路层 - 集卡道路网(黄色实线) -->
            <v-layer name="roads">
              <v-line
                  v-for="(path, idx) in truckRoads"
                  :key="'truck-road-'+idx"
                  :config="{
                  points: path.direction === 'HORIZONTAL'
                          ? [path.startPoint, path.position, path.endPoint, path.position]
                          : [path.position, path.startPoint, path.position, path.endPoint],
                  stroke: '#ffb300',
                  strokeWidth: 8,
                  opacity: 0.7,
                  lineCap: 'round',
                  name: 'road'
                }"
              />
              <v-text :config="{ x: 30, y: 145, text: '集卡道路(黄色)', fontSize: 10, fill: '#ffb300', name: 'road' }" />
            </v-layer>

            <!-- 第4层：交接区域层 - QC交接区(橙色)和ASC交接区(绿色) -->
            <v-layer name="transfer-zones">
              <v-rect
                  v-for="zone in simStore.transferZones"
                  :key="'zone-'+zone.zoneId"
                  :config="{
                  x: zone.xRange[0],
                  y: zone.yRange[0],
                  width: zone.xRange[1] - zone.xRange[0],
                  height: zone.yRange[1] - zone.yRange[0],
                  fill: zone.zoneId.startsWith('QC') ? 'rgba(255, 152, 0, 0.25)' : 'rgba(76, 175, 80, 0.25)',
                  stroke: zone.zoneId.startsWith('QC') ? '#ff9800' : '#4caf50',
                  strokeWidth: 2,
                  dash: [8, 4],
                  name: 'transfer'
                }"
              />
              <v-text
                  v-for="zone in simStore.transferZones"
                  :key="'zone-label-'+zone.zoneId"
                  :config="{
                  x: zone.xRange[0] + 5,
                  y: zone.yRange[0] + 3,
                  text: zone.name,
                  fontSize: 9,
                  fontStyle: 'bold',
                  fill: zone.zoneId.startsWith('QC') ? '#e65100' : '#1b5e20',
                  name: 'transfer'
                }"
              />
            </v-layer>

            <!-- 第5层：设备层 - 围栏、充电桩、集装箱、船舶 -->
            <v-layer name="facilities">
              <v-circle v-for="fence in simStore.fences" :key="fence.nodeId" :config="{ x: fence.posX, y: fence.posY, radius: fence.radius || 20, fill: fence.status === '02' ? '#4caf50' : '#f44336', opacity: 0.5, stroke: '#333', strokeWidth: 2 }" />
              <v-rect v-for="station in simStore.chargingStations" :key="station.stationCode" :config="{ x: station.posX - 8, y: station.posY - 8, width: 16, height: 16, fill: '#ffeb3b', stroke: '#f57f17', strokeWidth: 2, cornerRadius: 3 }" />
              <v-rect v-for="c in simStore.containers" :key="c.containerId" :config="{ x: (c.posX || 0) - 6, y: (c.posY || 0) - 6, width: 12, height: 12, fill: '#ff7043', stroke: '#bf360c', strokeWidth: 1, cornerRadius: 2 }" />
              <v-rect v-for="v in simStore.vessels" :key="v.vesselId" :config="{ x: (v.berthLocation || 0) - (v.length || 50) / 2, y: 20, width: v.length || 50, height: 40, fill: '#90caf9', stroke: '#0d47a1', strokeWidth: 2, cornerRadius: 5 }" />
              <v-text v-for="v in simStore.vessels" :key="v.vesselId + '-label'" :config="{ x: (v.berthLocation || 0) - 30, y: 65, text: v.vesselId, fontSize: 12, fill: '#0d47a1', fontStyle: 'bold' }" />
            </v-layer>

            <!-- 第6层：设备动态层 - QC、ASC、集卡 -->
            <v-layer name="devices">
              <v-group v-for="dev in displayDevices" :key="dev.id" :config="{ x: dev.posX, y: dev.posY }">
                <template v-if="dev.type === 'QC' || dev.type === 'CRANE_QC'">
                  <v-rect :config="{ x: -20, y: -25, width: 40, height: 50, fill: 'transparent', stroke: '#ff9800', strokeWidth: 4 }" />
                  <v-line :config="{ points: [-20, 0, 20, 0], stroke: '#ff9800', strokeWidth: 4 }" />
                  <v-circle :config="{ x: 0, y: 0, radius: 5, fill: selectedDeviceId === dev.id ? '#f5222d' : '#fff' }" />
                </template>
                <template v-else-if="dev.type === 'ASC' || dev.type === 'CRANE_ASC'">
                  <v-rect :config="{ x: -15, y: -20, width: 30, height: 40, fill: 'transparent', stroke: '#4caf50', strokeWidth: 4 }" />
                  <v-line :config="{ points: [0, -20, 0, 20], stroke: '#4caf50', strokeWidth: 4 }" />
                  <v-circle :config="{ x: 0, y: 0, radius: 4, fill: selectedDeviceId === dev.id ? '#f5222d' : '#fff' }" />
                </template>
                <template v-else-if="dev.type === 'ELECTRIC_TRUCK' || dev.type === 'INTERNAL_TRUCK'">
                  <v-rect :config="{ x: -12, y: -6, width: 24, height: 12, fill: selectedDeviceId === dev.id ? '#ff4d4f' : '#2196f3', cornerRadius: 2 }" />
                  <v-rect :config="{ x: 12, y: -5, width: 6, height: 10, fill: '#1565c0', cornerRadius: 1 }" />
                </template>
                <template v-else>
                  <v-rect :config="{ x: -10, y: -10, width: 20, height: 20, fill: '#909399' }" />
                </template>

                <v-text :config="{ x: -25, y: 25, text: dev.id, fontSize: 12, fill: '#000', fontStyle: 'bold' }" />
                <v-rect :config="{ x: -25, y: -25, width: 50, height: 50, fill: 'transparent' }" @click="selectDevice(dev.id, dev.type)" />
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
            <el-button v-if="selectedDeviceType === 'QC' || selectedDeviceType === 'ASC'" type="primary" size="small" @click="showCraneMoveDialog = true">移动</el-button>
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
        <el-form-item label="目标X"><el-input-number v-model="truckMoveTarget.x" :min="0" :max="800" /></el-form-item>
        <el-form-item label="目标Y"><el-input-number v-model="truckMoveTarget.y" :min="0" :max="600" /></el-form-item>
        <el-form-item label="速度"><el-input-number v-model="truckMoveTarget.speed" :min="1" :max="50" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showTruckMoveDialog = false">取消</el-button><el-button type="primary" @click="handleTruckMove">确认移动</el-button></template>
    </el-dialog>

    <el-dialog v-model="showCraneMoveDialog" title="起重机移动控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="移动类型">
          <el-select v-model="craneMoveType">
            <el-option label="水平移动" value="MOVE_HORIZONTAL" />
            <el-option label="垂直移动" value="MOVE_VERTICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="距离(米)"><el-input-number v-model="craneMoveDistance" :min="-500" :max="500" /></el-form-item>
        <el-form-item label="速度(m/s)"><el-input-number v-model="craneMoveSpeed" :min="1" :max="50" :step="0.5" :precision="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCraneMoveDialog = false">取消</el-button><el-button type="primary" @click="handleCraneMove">确认移动</el-button></template>
    </el-dialog>

    <el-dialog v-model="showCraneOpDialog" title="起重机操作控制" width="400px">
      <el-form label-width="80px">
        <el-form-item label="操作类型">
          <el-select v-model="craneOpType">
            <el-option label="抓箱" value="FETCH_DONE" />
            <el-option label="放箱" value="PUT_DONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="耗时(ms)"><el-input-number v-model="craneOpDuration" :min="100" :max="60000" :step="100" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCraneOpDialog = false">取消</el-button><el-button type="primary" @click="handleCraneOperate">确认操作</el-button></template>
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
      <template #footer><el-button @click="showChargeDialog = false">取消</el-button><el-button type="primary" @click="handleCharge" :disabled="!chargeStationId">确认充电</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { useSimStore } from '../stores/simStore'
import { moveTruck, moveCrane, operateCrane, controlFence, chargeTruck, testTruckDelivery, testQcLoading, testAscUnloading, testFullLoading, testYardShift, testRecv, testDirectIn, testDirectOut, testQcHorizontalVertical, testAscHorizontalVertical } from '../api/simulation'
import { ElMessage } from 'element-plus'

const simStore = useSimStore()
let animFrameId: number;

const stageConfig = ref({ width: 800, height: 600 })
const selectedDeviceId = ref('')
const selectedDeviceType = ref('')
const selectedDeviceState = ref('')
const selectedDevicePower = ref<number | null>(null)
const logContainer = ref<HTMLElement | null>(null)

// 计算属性：按类型分组路径
const qcRails = computed(() => simStore.mapPaths.filter((p: any) => p.pathType === 'QC_RAIL'))
const ascRails = computed(() => simStore.mapPaths.filter((p: any) => p.pathType === 'ASC_RAIL'))
const truckRoads = computed(() => simStore.mapPaths.filter((p: any) => p.pathType === 'TRUCK_ROAD'))

// 堆场区域配置
const yardAreas = computed(() => [
  { x: 100, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c' },
  { x: 350, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c' },
  { x: 600, y: 250, width: 150, height: 250, fill: '#c8e6c9', stroke: '#388e3c' }
])

const isInitLoading = ref(false)
const isTestLoading = ref(false)

const showTruckMoveDialog = ref(false)
const showCraneMoveDialog = ref(false)
const showCraneOpDialog = ref(false)
const showChargeDialog = ref(false)
const truckMoveTarget = ref({ x: 0, y: 0, speed: 10 })
const craneMoveType = ref('MOVE_HORIZONTAL')
const craneMoveDistance = ref(0)
const craneMoveSpeed = ref(10)
const craneOpType = ref('FETCH_DONE')
const craneOpDuration = ref(5000)
const chargeStationId = ref('')

const displayDevices = ref<Record<string, any>>({})

watch(() => simStore.events.length, async () => {
  await nextTick()
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
})

const animateLoop = () => {
  simStore.devices.forEach(target => {
    if (!displayDevices.value[target.id]) {
      displayDevices.value[target.id] = { ...target };
    } else {
      const curr = displayDevices.value[target.id];
      const dx = target.posX - curr.posX;
      const dy = target.posY - curr.posY;
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
        curr.posX = target.posX;
        curr.posY = target.posY;
      } else {
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
  simStore.loadTransferZones()
  simStore.startSnapshotPolling(500)
  animateLoop()
})

onBeforeUnmount(() => {
  simStore.stopAutoPlay()
  simStore.stopSnapshotPolling()
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

const handleTruckMove = async () => {
  try {
    await moveTruck({ truckId: selectedDeviceId.value, targetPoint: { x: truckMoveTarget.value.x, y: truckMoveTarget.value.y }, speed: truckMoveTarget.value.speed })
    ElMessage.success('移动指令已下发')
    showTruckMoveDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) { ElMessage.error('移动指令失败') }
}

const handleCraneMove = async () => {
  try {
    let moveType = craneMoveType.value
    // ASC 严格限制在紫色轨道上，只允许垂直移动
    if (selectedDeviceType.value === 'ASC' && moveType === 'MOVE_HORIZONTAL') {
      moveType = 'MOVE_VERTICAL'
    }
    await moveCrane({ craneId: selectedDeviceId.value, moveType, distance: craneMoveDistance.value, speed: craneMoveSpeed.value })
    ElMessage.success('移动指令已下发')
    showCraneMoveDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) { ElMessage.error('移动指令失败') }
}

const handleCraneOperate = async () => {
  try {
    await operateCrane({ craneId: selectedDeviceId.value, action: craneOpType.value, durationMS: craneOpDuration.value })
    ElMessage.success('操作指令已下发')
    showCraneOpDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) { ElMessage.error('操作指令失败') }
}

const handleCharge = async () => {
  try {
    await chargeTruck({ truckId: selectedDeviceId.value, stationId: chargeStationId.value })
    ElMessage.success('充电指令已下发')
    showChargeDialog.value = false
    await simStore.updateSnapshot()
  } catch (err: any) { ElMessage.error('充电指令失败') }
}

const toggleFence = async (fence: any) => {
  try {
    const newStatus = fence.status === '01' ? '02' : '01'
    await controlFence({ fenceId: fence.nodeId, status: newStatus })
    ElMessage.success(`围栏已${newStatus === '02' ? '开启' : '关闭'}`)
    await simStore.updateSnapshot()
  } catch (err: any) { ElMessage.error('围栏控制失败') }
}

const handleInitScene = async () => {
  isInitLoading.value = true;
  try {
    await simStore.initScene()
    ElMessage.success('默认场景已加载')
  } catch (e) {
    ElMessage.error('场景加载失败，请检查后端状态')
  } finally {
    isInitLoading.value = false;
  }
}

const handleStep = () => { simStore.doStepNext() }
const handleTogglePlay = () => { simStore.togglePlay() }
const handleReset = async () => { await simStore.doReset() }

const handleTestScenario = async (command: string) => {
  isTestLoading.value = true;
  try {
    await simStore.doReset();
    simStore.stopAutoPlay();

    let res: any;
    switch (command) {
      case 'task-chain': res = await testFullLoading(); break;
      case 'dsch': res = await testTruckDelivery(); break;
      case 'load': res = await testQcLoading(); break;
      case 'dlvr': res = await testAscUnloading(); break;
      case 'yard-shift': res = await testYardShift(); break;
      case 'recv': res = await testRecv(); break;
      case 'direct-in': res = await testDirectIn(); break;
      case 'direct-out': res = await testDirectOut(); break;
      case 'qc-hv': res = await testQcHorizontalVertical(); break;
      case 'asc-hv': res = await testAscHorizontalVertical(); break;
      default: ElMessage.warning(`命令 ${command} 暂未实现`); return;
    }

    const msg = typeof res === 'string' ? res : (res?.data?.msg || res?.msg || `[${command}] 测试指令注入成功`);
    ElMessage.success({ message: msg, duration: 4000 });

    await simStore.updateSnapshot();
    if(!simStore.isPlaying) {
      simStore.togglePlay();
    }
  } catch (err: any) {
    ElMessage.error(err.message || '测试调度失败，请检查控制台或引擎异常');
  } finally {
    isTestLoading.value = false;
  }
}

const getPathsByType = (pathType: string) => simStore.mapPaths.filter(p => p.pathType === pathType);

// 将任意点击点投影到最近的集卡道路上，确保集卡只能在路网上运行
const projectToTruckRoad = (x: number, y: number) => {
  const roads = getPathsByType('TRUCK_ROAD');
  let bestPoint: { x: number; y: number } | null = null;
  let minDist = Infinity;

  roads.forEach((path: any) => {
    if (path.direction === 'HORIZONTAL') {
      if (x < path.startPoint || x > path.endPoint) return;
      const py = path.position;
      const dist = Math.abs(y - py);
      if (dist < minDist) {
        minDist = dist;
        bestPoint = { x, y: py };
      }
    } else if (path.direction === 'VERTICAL') {
      if (y < path.startPoint || y > path.endPoint) return;
      const px = path.position;
      const dist = Math.abs(x - px);
      if (dist < minDist) {
        minDist = dist;
        bestPoint = { x: px, y };
      }
    }
  });

  // 距离道路太远则认为点击无效，避免产生“穿越堆场”的指令
  if (!bestPoint || minDist > 40) {
    return null;
  }
  return bestPoint;
};

const getQcRail = () => {
  const rails = getPathsByType('QC_RAIL');
  return rails.length > 0 ? rails[0] : null;
};

const getAscRailForDevice = (deviceX: number) => {
  const rails = getPathsByType('ASC_RAIL');
  if (rails.length === 0) return null;
  let bestRail: any = rails[0];
  let minDx = Math.abs(deviceX - bestRail.position);
  rails.forEach((r: any) => {
    const dx = Math.abs(deviceX - r.position);
    if (dx < minDx) {
      minDx = dx;
      bestRail = r;
    }
  });
  return bestRail;
};

const handleStageClick = async (e: any) => {
  if (e.target.name() !== 'bg' || !selectedDeviceId.value) return;
  const pos = e.target.getStage().getPointerPosition();
  const device = simStore.devices.find(d => d.id === selectedDeviceId.value);
  if (!device) return;

  try {
    if (selectedDeviceType.value === 'QC') {
      // QC 只能沿岸边轨道水平移动
      const rail = getQcRail();
      if (!rail) {
        ElMessage.error('当前地图未配置QC轨道');
        return;
      }
      const targetX = Math.max(rail.startPoint, Math.min(rail.endPoint, pos.x));
      const distance = targetX - device.posX;
      if (Math.abs(distance) > 1) {
        await moveCrane({
          craneId: device.id,
          moveType: 'MOVE_HORIZONTAL',
          distance,
          speed: 10.0
        });
      }
    } else if (selectedDeviceType.value === 'ASC') {
      // ASC 严格限制在紫色虚线轨道上，只允许沿轨道做垂直移动
      const rail = getAscRailForDevice(device.posX);
      if (!rail) {
        ElMessage.error('当前地图未配置ASC轨道');
        return;
      }
      // 将点击点投影到该 ASC 轨道的 Y 范围内
      const targetY = Math.max(rail.startPoint, Math.min(rail.endPoint, pos.y));
      const distance = targetY - device.posY;
      if (Math.abs(distance) > 1) {
        await moveCrane({
          craneId: device.id,
          moveType: 'MOVE_VERTICAL',
          distance,
          speed: 10.0
        });
      }
    } else {
      // 集卡：点击位置先投影到最近的道路，再下发移动指令
      const projected = projectToTruckRoad(pos.x, pos.y);
      if (!projected) {
        ElMessage.warning('请点击靠近道路的位置，集卡只能在路网上运行');
        return;
      }
      await moveTruck({
        truckId: device.id,
        targetPoint: { x: projected.x, y: projected.y },
        speed: 10.0
      });
    }
    await simStore.updateSnapshot();
  } catch (err: any) {
    ElMessage.error(err.message || '移动失败');
  }
}
</script>

<style scoped>
/* 保持你的原版CSS不变 */
.dashboard { display: flex; flex-direction: column; height: 100vh; padding: 20px; box-sizing: border-box; background: #f0f2f5;}
.toolbar { display: flex; align-items: center; justify-content: space-between; background: white; padding: 15px 20px; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); margin-bottom: 20px; }
.toolbar h2 { margin: 0; color: #303133; }
.time-display { font-size: 18px; color: #409EFF; }
.main-content { display: flex; gap: 20px; flex: 1; min-height: 0; }
.left-section { display: flex; flex-direction: column; gap: 15px; flex: 1; }
.map-container { background: #fff; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); display: flex; justify-content: center; padding: 10px; }
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
.control-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; }
.control-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.control-info { margin-bottom: 10px; font-size: 13px; color: #606266; }
.control-info p { margin: 5px 0; }
.control-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
.fence-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; }
.fence-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.fence-list { list-style: none; padding: 0; margin: 0; }
.fence-list li { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #ebeef5; font-size: 13px; }
.fence-id { font-weight: bold; }
.fence-status { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.fence-status.open { background: #e7f7e7; color: #67c23a; }
.fence-status.closed { background: #fdecea; color: #f56c6c; }
.wi-panel { background: white; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; max-height: 200px; display: flex; flex-direction: column; }
.wi-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #303133; }
.wi-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.wi-list li { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px solid #ebeef5; font-size: 12px; }
.wi-ref { font-weight: bold; color: #409EFF; }
.wi-move { color: #E6A23C; }
.wi-status { color: #67c23a; }
.error-panel { background: #fef0f0; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 15px; max-height: 150px; display: flex; flex-direction: column; border: 1px solid #fbc4c4; }
.error-panel h3 { margin: 0 0 10px 0; font-size: 14px; color: #f56c6c; }
.error-list { list-style: none; padding: 0; margin: 0; overflow-y: auto; flex: 1; }
.error-list li { padding: 6px 0; font-size: 12px; border-bottom: 1px solid #fbc4c4; }
.err-time { color: #909399; margin-right: 8px; }
.err-msg { color: #f56c6c; }
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #c0c4cc; border-radius: 3px; }
.terminal-panel ::-webkit-scrollbar-thumb { background: #555; }
</style>