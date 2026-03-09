// simStore.ts
// 仿真核心状态管理（Pinia store），负责与后端 API 交互、轮询快照、处理事件和错误、管理设备动画状态等。
// 采用选项式 API 风格（state + actions），便于组织大量状态和方法。

import { defineStore } from 'pinia';
import {
    getSnapshot, stepNextEvent, tick, resetSimulation, loadScenarioFromJson, getMapPaths, getTransferZones, setPlaybackSpeed
} from '../api/simulation';

export const useSimStore = defineStore('simulation', {
    state: () => ({
        // ---------- 仿真核心数据 ----------
        simTime: 0,                      // 当前仿真时间（毫秒）
        devices: [] as any[],            // 所有设备（卡车、起重机等）
        fences: [] as any[],             // 围栏设备
        chargingStations: [] as any[],   // 充电桩
        vessels: [] as any[],            // 船舶
        workInstructions: [] as any[],   // 作业指令
        containers: [] as any[],         // 集装箱

        // ---------- 日志与事件 ----------
        events: [] as any[],             // 仿真事件列表
        errors: [] as any[],             // 错误事件列表

        // ---------- 播放控制 ----------
        isPlaying: false,                // 是否正在自动步进
        playInterval: null as any,       // 自动步进的定时器句柄
        playbackSpeed: 1.0,              // 播放速度倍率

        // ---------- UI 交互 ----------
        selectedDevice: null as any,     // 当前选中的设备
        selectedDeviceId: null as string | null,  // 当前选中的设备ID
        selectedTargetPos: null as { x: number, y: number } | null,  // 当前选中的目标位置
        pendingMoveCommand: null as { fromX: number, fromY: number, toX: number, toY: number } | null,  // 待执行的移动指令（用于显示连线）

        // ---------- 地图数据 ----------
        mapConfig: null as any,            // 地图配置（堆场、充电站、路径等）
        mapPaths: [] as any[],           // 地图路径（轨道/道路）
        transferZones: [] as any[],      // 转运区信息

        // ---------- 动画与告警 ----------
        deviceAnimations: new Map<string, any>(),   // 设备动画状态（预留）
        deviceAlerts: new Map<string, number>(),    // 设备告警过期时间戳
        eventLogs: [] as any[],                    // 前端业务拦截日志（用于右侧拦截台）

        // 追踪设备的 Z 轴（起升/下降）工作状态，用于显示进度条动画
        activeZOperations: new Map<string, number>(),

        // ---------- 引擎状态 ----------
        isSuspended: false,               // 引擎是否处于全局熔断状态
        suspendedBizTypes: [] as string[], // 导致熔断的业务类型
        suspendedEventIds: [] as string[]  // 导致熔断的事件ID
    }),

    actions: {
        /**
         * 初始化场景：从后端加载预设的 JSON 场景文件，并重置状态。
         * 仅获取一次全量快照用于铺底，不再启动轮询（完全依赖 WebSocket 推送）。
         */
        async initScene() {
            try {
                await loadScenarioFromJson('scenario-demo.json');
                this.stopAutoPlay();
                this.events = [];
                this.errors = [];
                this.activeZOperations.clear();
                // 先加载地图配置，再加载路径和转运区
                await this.loadMapConfig();
                await this.loadMapPaths();
                await this.loadTransferZones();
                await this.updateSnapshot();
                // 不再启动轮询 - 完全依赖 WebSocket 推送事件更新状态
            } catch (error) { throw error; }
        },

        /** 加载地图配置（堆场、充电站、路径等）- 从快照接口获取 */
        async loadMapConfig() {
            try {
                const res: any = await getSnapshot();
                // 兼容 axios 的返回包装结构
                const data = res.data?.data || res.data || res;
                if (data) {
                    this.mapConfig = {
                        yardBlocks: data.yardBlocks || [],
                        chargingStations: data.chargingStations || [],
                        paths: data.paths || []
                    };
                    console.log('[Store] 地图配置已从 Snapshot 加载完成', this.mapConfig);
                }
            } catch (error) {
                console.error('[Store] 加载地图配置失败', error);
            }
        },

        /**
         * 更新快照：调用 getSnapshot 接口，并用返回数据刷新 state。
         * 同时根据设备状态标记 Z 轴作业中的设备。
         */
        async updateSnapshot() {
            try {
                const res: any = await getSnapshot();
                const data = res.data || res;
                if (data) {
                    this.simTime = data.simTime || 0;
                    this.devices = data.devices || [];
                    this.fences = data.fences || [];
                    this.chargingStations = data.chargingStations || [];
                    this.vessels = data.vessels || [];
                    this.workInstructions = data.workInstructions || [];
                    this.containers = data.containers || [];

                    // 更新引擎熔断状态
                    this.isSuspended = data.globalSuspended || false;
                    this.suspendedBizTypes = data.suspendedBizTypes || [];
                    this.suspendedEventIds = data.suspendedEventIds || [];

                    // 遍历设备，如果设备处于 WORKING 状态且是起重机，则认为正在进行 Z 轴作业
                    this.devices.forEach(dev => {
                        if (dev.state === 'WORKING' && (dev.type === 'QC' || dev.type === 'ASC')) {
                            if (!this.activeZOperations.has(dev.id)) {
                                this.activeZOperations.set(dev.id, Date.now());
                            }
                        } else {
                            this.activeZOperations.delete(dev.id);
                        }
                    });
                }
            } catch (error) {}  // 静默失败，避免频繁报错
        },

        /**
         * 执行单步步进（下一个事件）。
         */
        async doStepNext() {
            try {
                // 直接调用后端 step，后端会通过 WebSocket 推送最新状态
                await stepNextEvent();
                // 无需额外 HTTP 拉取 - WebSocket 会推送事件更新
            } catch (error) {}
        },

        /**
         * 重置仿真引擎。
         * 仅获取一次全量快照用于铺底，不再启动轮询。
         */
        async doReset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                // 清空前端状态
                this.events = [];
                this.errors = [];
                this.eventLogs = [];
                this.activeZOperations.clear();
                this.deviceAlerts.clear();
                // 重置熔断状态
                this.isSuspended = false;
                this.suspendedBizTypes = [];
                this.suspendedEventIds = [];
                // 更新一次快照获取重置后的数据
                await this.updateSnapshot();
                // 不再启动轮询 - 完全依赖 WebSocket 推送事件更新状态
            } catch (error) { throw error; }
        },

        /**
         * 自动步进循环（播放模式）。
         * 每 100ms 调用一次 tick 接口。
         * 注意：不再调用 updateSnapshot，完全依赖 WebSocket 推送事件更新状态。
         */
        async playTick() {
            if (!this.isPlaying) return;
            try {
                // 倍速控制：每 100ms 真实时间推进 playbackSpeed 倍的仿真时间
                const stepMs = Math.round(100 * this.playbackSpeed);
                await tick(stepMs);
                // 不再调用 fetchLogs - WebSocket 已推送事件，避免重复
            } catch (e) {}
            if (this.isPlaying) { this.playInterval = setTimeout(() => this.playTick(), 100); }
        },

        /**
         * 动画帧回调（由组件驱动），用于检查告警过期等。
         */
        animateTick() { this.checkAlerts(); },

        /**
         * 切换播放/暂停状态。
         */
        togglePlay() {
            this.isPlaying = !this.isPlaying;
            if (this.isPlaying) this.playTick();
            else this.stopAutoPlay();
        },

        /**
         * 停止自动播放。
         */
        stopAutoPlay() {
            this.isPlaying = false;
            if (this.playInterval) { clearTimeout(this.playInterval); this.playInterval = null; }
        },

        // 设备选择相关
        setSelectedDevice(device: any) { this.selectedDevice = device; },
        clearSelectedDevice() { this.selectedDevice = null; },

        /** 选中指定设备 */
        selectDevice(deviceId: string) {
            // 尝试通过 id 或 deviceId 查找设备
            const device = this.devices.find(d => d.id === deviceId || d.deviceId === deviceId);
            this.selectedDeviceId = deviceId;
            this.selectedDevice = device || null;
            // 选中设备时清空目标位置
            this.selectedTargetPos = null;
        },

        /** 选中目标位置 */
        selectTarget(x: number, y: number) {
            this.selectedTargetPos = { x, y };
        },

        /** 仅清空目标位置（保留已选中设备） */
        clearTarget() {
            this.selectedTargetPos = null;
        },

        /** 清空选中状态 */
        clearSelection() {
            this.selectedDeviceId = null;
            this.selectedDevice = null;
            this.selectedTargetPos = null;
        },

        /**
         * 手动刷新快照（用于断线重连后获取全量数据铺底）。
         * 不启动轮询，仅单次获取。
         */
        async refreshSnapshot() {
            await this.updateSnapshot();
        },

        /**
         * 加载地图路径数据。
         */
        async loadMapPaths() { try { const res: any = await getMapPaths(); this.mapPaths = res.data || res || []; } catch (error) {} },

        /**
         * 加载转运区数据。
         */
        async loadTransferZones() { try { const res: any = await getTransferZones(); this.transferZones = res.data || res || []; } catch (error) {} },

        /**
         * 设置仿真播放速度
         */
        async setSpeed(speed: number) {
            try {
                await setPlaybackSpeed(speed);
                this.playbackSpeed = speed;
            } catch (error) {}
        },

        /**
         * 处理仿真错误（例如从 WebSocket 收到的错误事件）。
         * 将错误推入 eventLogs 和 errors，并触发设备告警闪烁。
         * @param deviceId 关联的设备 ID，可为 null
         * @param errorMessage 错误描述
         */
        onSimulationError(deviceId: string | null, errorMessage: string) {
            this.eventLogs.unshift({ eventId: 'ERR_' + Date.now(), simTime: this.simTime, deviceId: deviceId, message: errorMessage, eventType: 'SIM_ERROR_EVENT' });
            if (this.eventLogs.length > 50) this.eventLogs.pop();
            this.errors.push({ eventId: 'ERR_' + Date.now(), simTime: this.simTime, deviceId: deviceId, message: errorMessage, type: 'SIM_ERROR_EVENT' });
            if (deviceId) { this.deviceAlerts.set(deviceId, Date.now() + 3000); }  // 告警持续3秒
        },

        /**
         * 处理 WebSocket 推送的仿真事件。
         * 将事件推入 events 数组，并平滑更新设备位置以触发视图补间动画。
         * @param eventData WebSocket 推送的事件数据
         */
        handleSimEvent(eventData: any) {
            // 将事件推入 events 数组
            this.events.push({
                eventId: eventData.eventId,
                eventType: eventData.eventType,
                deviceId: eventData.deviceId,
                simTime: eventData.simTime,
                ...eventData
            });
            // 限制数组长度，防止内存泄漏
            if (this.events.length > 500) {
                this.events = this.events.slice(-500);
            }

            // 如果包含位置信息，更新 device.posX/posY 使 animateLoop 感知到坐标差，触发 lerp 动画
            if (eventData.deviceId && eventData.currentPosX !== undefined) {
                const device = this.devices.find(d => d.id === eventData.deviceId);
                if (device) {
                    // 优先使用终点坐标(targetPosX)作为视觉目标，让 lerp 从当前显示位置平滑过渡到终点
                    // MOVE_START 事件携带 targetPosX = 第一个路径节点（即单段移动的终点）
                    // ARRIVAL 事件携带 currentPosX = 实际到达坐标，两者都能正确驱动动画
                    if (eventData.targetPosX !== undefined) {
                        device.posX = eventData.targetPosX;
                        device.posY = eventData.targetPosY;
                    } else {
                        device.posX = eventData.currentPosX;
                        device.posY = eventData.currentPosY;
                    }
                    // 如果是当前选中的设备收到位置更新，清除待执行指令（表示已起步）
                    if (this.selectedDeviceId === eventData.deviceId && this.pendingMoveCommand) {
                        this.pendingMoveCommand = null;
                    }
                    // 同步选中设备的坐标显示
                    if (this.selectedDeviceId === eventData.deviceId && this.selectedDevice) {
                        this.selectedDevice.posX = device.posX;
                        this.selectedDevice.posY = device.posY;
                    }
                }
            }

            // 处理错误事件
            if (eventData.eventType === 'SIM_ERROR_EVENT') {
                // 强制设置熔断状态
                this.isSuspended = true;
                // 将引发错误的设备ID加入熔断列表
                if (eventData.deviceId && !this.suspendedEventIds.includes(eventData.deviceId)) {
                    this.suspendedEventIds.push(eventData.deviceId);
                }
                // 记录错误日志
                this.onSimulationError(eventData.deviceId, eventData.message);
            }
        },

        /**
         * 检查设备告警是否过期，若过期则从 Map 中移除。
         * 由 animateTick 每帧调用。
         */
        checkAlerts() {
            const now = Date.now();
            for (const [deviceId, expiryTime] of this.deviceAlerts.entries()) {
                if (now > expiryTime) { this.deviceAlerts.delete(deviceId); }
            }
        }
    }
});