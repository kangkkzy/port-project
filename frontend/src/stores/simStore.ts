// simStore.ts
// 仿真核心状态管理（Pinia store），负责与后端 API 交互、轮询快照、处理事件和错误、管理设备动画状态等。
// 采用选项式 API 风格（state + actions），便于组织大量状态和方法。

import { defineStore } from 'pinia';
import {
    getSnapshot, stepNextEvent, tick, resetSimulation, loadScenarioFromJson, getEvents, getErrors, getMapPaths, getTransferZones
} from '../api/simulation';

export const useSimStore = defineStore('simulation', {
    state: () => ({
        // ---------- 仿真核心数据 ----------
        simTime: 0,                     // 当前仿真时间（毫秒）
        devices: [] as any[],            // 所有设备（卡车、起重机等）
        fences: [] as any[],              // 围栏设备
        chargingStations: [] as any[],    // 充电桩
        vessels: [] as any[],             // 船舶
        workInstructions: [] as any[],    // 作业指令
        containers: [] as any[],          // 集装箱

        // ---------- 日志与事件 ----------
        events: [] as any[],               // 仿真事件列表
        errors: [] as any[],               // 错误事件列表
        lastEventSimTime: 0,               // 上次拉取事件的仿真时间戳（用于增量获取）
        lastErrorSimTime: 0,               // 上次拉取错误的仿真时间戳

        // ---------- 播放控制 ----------
        isPlaying: false,                   // 是否正在自动步进
        playInterval: null as any,          // 自动步进的定时器句柄

        // ---------- 轮询快照 ----------
        isPolling: false,                   // 是否正在轮询快照
        pollIntervalMs: 500,                 // 轮询间隔（毫秒）
        pollTimer: null as any,              // 轮询定时器句柄

        // ---------- UI 交互 ----------
        selectedDevice: null as any,         // 当前选中的设备

        // ---------- 地图数据 ----------
        mapPaths: [] as any[],                // 地图路径（轨道/道路）
        transferZones: [] as any[],           // 转运区信息

        // ---------- 动画与告警 ----------
        deviceAnimations: new Map<string, any>(),   // 设备动画状态（预留）
        deviceAlerts: new Map<string, number>(),    // 设备告警过期时间戳
        eventLogs: [] as any[],                      // 前端业务拦截日志（用于右侧拦截台）

        // 【新增】追踪设备的 Z 轴（起升/下降）工作状态，用于显示进度条动画
        activeZOperations: new Map<string, number>()
    }),

    actions: {
        /**
         * 初始化场景：从后端加载预设的 JSON 场景文件，并重置状态。
         */
        async initScene() {
            try {
                await loadScenarioFromJson('scenario-demo.json');
                this.stopAutoPlay();
                this.stopSnapshotPolling();
                this.events = [];
                this.errors = [];
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                this.activeZOperations.clear();
                await this.loadMapPaths();          // 加载地图路径
                await this.updateSnapshot();        // 获取一次快照
                this.startSnapshotPolling();        // 开始轮询
            } catch (error) { throw error; }
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
         * 轮询单次执行：若不在播放模式，则更新快照并拉取日志；然后设置下一次轮询。
         */
        async pollTick() {
            if (!this.isPolling) return;
            if (!this.isPlaying) {
                try { await this.updateSnapshot(); await this.fetchLogs(); } catch (e) {}
            }
            if (this.isPolling) { this.pollTimer = setTimeout(() => this.pollTick(), this.pollIntervalMs); }
        },

        /**
         * 启动轮询快照。
         * @param intervalMs 可选，覆盖默认轮询间隔
         */
        startSnapshotPolling(intervalMs?: number) {
            if (this.isPolling) return;
            this.isPolling = true;
            if (intervalMs) this.pollIntervalMs = intervalMs;
            this.pollTick();
        },

        /**
         * 停止轮询快照。
         */
        stopSnapshotPolling() {
            this.isPolling = false;
            if (this.pollTimer) { clearTimeout(this.pollTimer); this.pollTimer = null; }
        },

        /**
         * 增量拉取事件和错误日志，更新 events 和 errors 列表。
         */
        async fetchLogs() {
            try {
                const eventRes: any = await getEvents(this.lastEventSimTime);
                const eventData = eventRes.data || eventRes || [];
                if (eventData.length > 0) {
                    this.events.push(...eventData);
                    this.lastEventSimTime = eventData[eventData.length - 1].simTime;
                }
                const errorRes: any = await getErrors(this.lastErrorSimTime);
                const errorData = errorRes.data || errorRes || [];
                if (errorData.length > 0) {
                    this.errors.push(...errorData);
                    this.lastErrorSimTime = errorData[errorData.length - 1].simTime;
                }
            } catch (error) {}
        },

        /**
         * 执行单步步进（下一个事件）。
         */
        async doStepNext() {
            try {
                await stepNextEvent();
                await this.updateSnapshot();
                await this.fetchLogs();
            } catch (error) {}
        },

        /**
         * 重置仿真引擎。
         */
        async doReset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                this.stopSnapshotPolling();
                this.events = [];
                this.errors = [];
                this.activeZOperations.clear();
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                await this.updateSnapshot();
                this.startSnapshotPolling();
            } catch (error) { throw error; }
        },

        /**
         * 自动步进循环（播放模式）。
         * 每 100ms 调用一次 tick 接口。
         */
        async playTick() {
            if (!this.isPlaying) return;
            try { await tick(100); await this.updateSnapshot(); await this.fetchLogs(); } catch (e) {}
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

        /**
         * 加载地图路径数据。
         */
        async loadMapPaths() { try { const res: any = await getMapPaths(); this.mapPaths = res.data || res || []; } catch (error) {} },

        /**
         * 加载转运区数据。
         */
        async loadTransferZones() { try { const res: any = await getTransferZones(); this.transferZones = res.data || res || []; } catch (error) {} },

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