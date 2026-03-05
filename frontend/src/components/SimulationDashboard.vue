<template>
  <div class="dashboard">
    <!-- 全局熔断遮罩 -->
    <div v-if="simStore.isSuspended" class="suspend-overlay">
      <div class="suspend-content">
        <h1>🚨 引擎触发全局熔断</h1>
        <p>仿真引擎因异常已暂停运行</p>
        <div class="suspend-detail">
          <div v-if="simStore.suspendedBizTypes.length > 0">
            <strong>受影响业务:</strong> {{ simStore.suspendedBizTypes.join(', ') }}
          </div>
          <div v-if="simStore.suspendedEventIds.length > 0">
            <strong>异常事件ID:</strong> {{ simStore.suspendedEventIds.slice(0, 3).join(', ') }}
          </div>
          <div v-if="simStore.errors.length > 0">
            <strong>最新错误:</strong> {{ simStore.errors[simStore.errors.length - 1]?.message || '未知' }}
          </div>
        </div>
        <el-button type="warning" size="large" @click="handleReset">点击重置引擎</el-button>
      </div>
    </div>

    <div class="toolbar">
      <h2>港口离散仿真内核验证台</h2>
      <div class="buttons">
        <!-- 外部算法测试：上传 JSON 测试用例 -->
        <el-button type="info" @click="triggerJsonUpload">1. 加载 JSON 测试用例</el-button>
        <input
            ref="jsonFileInput"
            type="file"
            accept=".json"
            style="display: none"
            @change="handleJsonFileUpload"
        />
        <!-- 核心控制：单步推演和重置 -->
        <el-button
            type="primary"
            @click="handleStep"
            :disabled="simStore.isSuspended"
            style="margin-left: 20px; font-weight: bold;"
        >单步推演 (Next Event)</el-button>
        <el-button
            type="warning"
            @click="handleReset"
            style="margin-left: 10px;"
        >清空重置</el-button>
        <el-button
            type="success"
            @click="handlePlay"
            :disabled="simStore.isSuspended"
            style="margin-left: 10px;"
        >播放</el-button>
        <el-button
            type="danger"
            @click="handlePause"
            :disabled="simStore.isSuspended"
            style="margin-left: 10px;"
        >暂停</el-button>
        <!-- 速度控制 -->
        <div class="speed-control" style="margin-left: 20px; display: flex; align-items: center; gap: 10px;">
          <span>倍速:</span>
          <el-slider
              v-model="playbackSpeed"
              :min="0.5"
              :max="5"
              :step="0.5"
              :format-tooltip="(val: number) => val + 'x'"
              @change="handleSpeedChange"
              style="width: 120px;"
          />
          <span>{{ playbackSpeed }}x</span>
        </div>
      </div>
      <div class="time-display">
        当前时钟: <strong>{{ simStore.simTime }}</strong> ms
        <span style="margin-left: 20px; color: #666;">(WS: {{ wsService.connected.value ? '已连接' : '未连接' }})</span>
        <span v-if="simStore.isSuspended" style="margin-left: 20px; color: #f00; font-weight: bold;">⚠️ 已熔断</span>
      </div>
    </div>

    <div class="main-content">
      <div class="left-section">
        <!-- 画布区域：使用 Konva 渲染地图和设备 -->
        <div class="map-container" ref="mapContainerRef">
          <v-stage :config="stageConfig">
            <v-layer name="background">
              <!-- 简单的背景色和文字示意海侧区域 -->
              <v-rect :config="{ x: 0, y: 0, width: stageConfig.width, height: 100, fill: '#bbdefb' }" />
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
                  <!-- 使用 simTime 计算进度，确保暂停时动画也能停止 -->
                  <v-rect :config="{ x: -20, y: -30, width: ((simStore.simTime % 2000) / 2000) * 40, height: 5, fill: '#00bcd4', cornerRadius: 2 }" />
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
          <!-- 日志过滤标签 -->
          <div class="log-filters">
            <span
                :class="['filter-tag', { active: logFilter === 'all' }]"
                @click="logFilter = 'all'"
            >[All]</span>
            <span
                :class="['filter-tag', { active: logFilter === 'movement' }]"
                @click="logFilter = 'movement'"
            >[Movements]</span>
            <span
                :class="['filter-tag', { active: logFilter === 'crane' }]"
                @click="logFilter = 'crane'"
            >[Crane Ops]</span>
          </div>
          <div class="terminal-header">> 离散事件引擎内核日志 (Discrete Event Engine Logs)</div>
          <ul class="log-list" ref="logContainer">
            <li v-for="log in filteredEvents" :key="log.eventId">
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
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useSimStore } from '../stores/simStore'
import { loadScenarioFromJson, stepNextEvent, tick, moveTruck, moveCrane, operateCrane, assignTask, controlFence, chargeTruck } from '../api/simulation'
import { wsService } from '../api/websocket'
import { ElMessage } from 'element-plus'

const simStore = useSimStore()
let animFrameId: number;  // 动画帧句柄
const stageConfig = ref({ width: 800, height: 600 })
const logContainer = ref<HTMLElement | null>(null)
const mapContainerRef = ref<HTMLElement | null>(null)
const jsonFileInput = ref<HTMLInputElement | null>(null)
const playbackSpeed = ref(1.0)
const lastFrameTime = ref(0)
const logFilter = ref<'all' | 'movement' | 'crane'>('all')
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

// 计算属性：根据过滤条件返回日志
const filteredEvents = computed(() => {
  const events = simStore.events;
  if (logFilter.value === 'all') return events;

  // Movement 类型事件
  const movementTypes = ['MOVE_START', 'ARRIVAL', 'MOVE_END'];
  // Crane 操作类型事件
  const craneTypes = ['CMD_CRANE_OP', 'FETCH_DONE', 'PUT_DONE', 'CRANE_MOVE'];

  return events.filter(e => {
    const type = e.type?.name || e.type || '';
    if (logFilter.value === 'movement') {
      return movementTypes.some(t => type.includes(t));
    }
    if (logFilter.value === 'crane') {
      return craneTypes.some(t => type.includes(t));
    }
    return true;
  });
});

// 格式化事件 subjects 对象为字符串
const formatSubjects = (subjects: any) => {
  if (!subjects) return '';
  return Object.entries(subjects).map(([k, v]) => `${k}:${v}`).join(' | ');
}

/**
 * 动画循环：平滑地将设备位置从当前位置移动到目标位置，并处理闪烁告警。
 * 每帧更新 displayDevices 中的坐标，实现缓动效果。
 */
const animateLoop = (timestamp?: number) => {
  // 计算 Delta Time 用于平滑动画
  const now = timestamp || performance.now();
  const deltaTime = lastFrameTime.value ? (now - lastFrameTime.value) : 16;
  lastFrameTime.value = now;

  // 计算基于 Delta Time 的插值因子 (假设 60fps 为基准)
  const lerpFactor = Math.min(deltaTime / 16.67, 2.0); // 限制最大插值防止跳跃

  simStore.devices.forEach(target => {
    if (!displayDevices.value[target.id]) {
      // 首次出现，直接拷贝
      displayDevices.value[target.id] = { ...target, zProgress: 0 };
    } else {
      const curr = displayDevices.value[target.id];
      const dx = target.posX - curr.posX;
      const dy = target.posY - curr.posY;
      // 距离足够近则直接对齐，否则按 Delta Time 比例移动
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
        curr.posX = target.posX;
        curr.posY = target.posY;
      } else {
        // 使用基于时间的平滑因子
        curr.posX += dx * lerpFactor * 0.15;
        curr.posY += dy * lerpFactor * 0.15;
      }
      curr.state = target.state;
      curr.type = target.type;

      // 告警闪烁：如果设备在告警集合中且未超时，则根据时间奇偶控制显示
      const alertExpiry = simStore.deviceAlerts.get(target.id);
      curr.isAlerting = alertExpiry && now < alertExpiry && (Math.floor(now / 250) % 2 === 0);

      // 记录当前 simTime 用于 Z 轴进度计算
      curr.simTime = simStore.simTime;
    }
  });
  simStore.animateTick();
  animFrameId = requestAnimationFrame(animateLoop);
};

onMounted(() => {
  // 初始化 WebSocket 连接（完全依赖 WS 推送事件，不再轮询快照）
  wsService.connect();

  // 加载地图数据
  simStore.loadMapPaths();
  simStore.loadTransferZones();

  // 初始化场景（会调用一次 snapshot 用于铺底）
  simStore.initScene();

  // 启动动画循环
  animateLoop();

  // 监听容器大小变化，实现画布自适应
  if (mapContainerRef.value) {
    const resizeObserver = new ResizeObserver(entries => {
      for (const entry of entries) {
        stageConfig.value.width = entry.contentRect.width;
        stageConfig.value.height = entry.contentRect.height;
      }
    });
    resizeObserver.observe(mapContainerRef.value);
    // 初始设置尺寸
    stageConfig.value.width = mapContainerRef.value.clientWidth;
    stageConfig.value.height = mapContainerRef.value.clientHeight;
  }
})

onBeforeUnmount(() => {
  // 清理资源：断开 WebSocket 连接，停止动画
  wsService.disconnect();
  simStore.stopSnapshotPolling();
  simStore.stopAutoPlay();
  if (animFrameId) cancelAnimationFrame(animFrameId);
})

// 点击按钮：加载初始场景（调用 store 的 initScene）
const triggerJsonUpload = () => {
  jsonFileInput.value?.click();
}

// 处理 JSON 文件上传，解析并按顺序执行 API 调用
const handleJsonFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  try {
    const text = await file.text();
    let testCase;

    // 支持两种格式：1. 对象格式 { scenario, commands }  2. 直接数组格式
    try {
      const parsed = JSON.parse(text);
      if (Array.isArray(parsed)) {
        testCase = { commands: parsed };
      } else {
        testCase = parsed;
      }
    } catch {
      throw new Error('JSON 格式错误：无法解析');
    }

    if (!testCase.scenario && !testCase.commands) {
      throw new Error('JSON 格式错误：缺少 scenario 或 commands 字段');
    }

    // 1. 加载场景（如果提供）
    if (testCase.scenario) {
      await loadScenarioFromJson(testCase.scenario);
      ElMessage.success(`场景 ${testCase.scenario} 加载成功`);
    }

    // 2. 按顺序执行命令序列（支持 timeOffset 延迟执行）
    if (testCase.commands && Array.isArray(testCase.commands)) {
      let lastSimTime = simStore.simTime;

      for (const cmd of testCase.commands) {
        // 计算相对时间延迟
        const timeOffset = cmd.timeOffset || 0;
        const targetSimTime = lastSimTime + timeOffset;

        // 如果需要等待到指定仿真时间，先推进仿真时钟
        if (timeOffset > 0 && targetSimTime > simStore.simTime) {
          await tick(targetSimTime - simStore.simTime);
        }

        await executeCommand(cmd);
        lastSimTime = simStore.simTime;
      }
      ElMessage.success(`共执行 ${testCase.commands.length} 条指令`);
    }
  } catch (e: any) {
    ElMessage.error('执行失败: ' + e.message);
  }

  // 清空文件输入，以便再次选择同一文件
  target.value = '';
}

// 根据命令类型调用对应的 API
// 支持两种格式：
// 1. 简化格式: { type: 'moveTruck', data: {...} }
// 2. 完整格式: { timeOffset: 100, api: '/sim/cmd/move', payload: {...} }
const executeCommand = async (cmd: any) => {
  // 完整格式处理
  if (cmd.api) {
    const apiPath = cmd.api;
    const payload = cmd.payload || {};
    // 根据 api 路径选择对应的请求方法
    if (apiPath.includes('/truck/move')) {
      return await moveTruck(payload);
    } else if (apiPath.includes('/crane/move')) {
      return await moveCrane(payload);
    } else if (apiPath.includes('/crane/operate')) {
      return await operateCrane(payload);
    } else if (apiPath.includes('/assign')) {
      return await assignTask(payload);
    } else if (apiPath.includes('/fence')) {
      return await controlFence(payload);
    } else if (apiPath.includes('/charge')) {
      return await chargeTruck(payload);
    }
    return;
  }

  // 简化格式处理
  const { type, data } = cmd;
  switch (type) {
    case 'moveTruck':
      return await moveTruck(data);
    case 'moveCrane':
      return await moveCrane(data);
    case 'operateCrane':
      return await operateCrane(data);
    case 'assignTask':
      return await assignTask(data);
    case 'controlFence':
      return await controlFence(data);
    case 'chargeTruck':
      return await chargeTruck(data);
    case 'step':
      return await stepNextEvent();
    case 'tick':
      return await tick(data?.deltaMs || 100);
    default:
      console.warn('未知命令类型:', type);
  }
}

const handleStep = () => { simStore.doStepNext() }
const handlePlay = () => { simStore.togglePlay() }
const handlePause = () => { simStore.stopAutoPlay() }

const handleSpeedChange = (val: number) => {
  simStore.setSpeed(val)
}

const handleReset = async () => { await simStore.doReset() }
</script>

<style scoped>
/* 全局熔断遮罩样式 */
.suspend-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(180, 20, 20, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.suspend-content {
  background: #fff;
  padding: 40px 60px;
  border-radius: 12px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.suspend-content h1 {
  color: #d32f2f;
  font-size: 32px;
  margin-bottom: 16px;
}

.suspend-content p {
  color: #666;
  font-size: 18px;
  margin-bottom: 24px;
}

.suspend-detail {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 24px;
  text-align: left;
  font-size: 14px;
  color: #333;
}

.suspend-detail div {
  margin-bottom: 8px;
}

.suspend-detail div:last-child {
  margin-bottom: 0;
}

/* 速度控制样式 */
.speed-control {
  background: #f0f0f0;
  padding: 8px 16px;
  border-radius: 4px;
}

/* 日志过滤样式 */
.log-filters {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #444;
}

.filter-tag {
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #888;
  transition: all 0.2s;
}

.filter-tag:hover {
  color: #fff;
}

.filter-tag.active {
  background: #409eff;
  color: #fff;
}
</style>