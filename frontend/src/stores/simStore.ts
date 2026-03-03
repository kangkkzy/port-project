import { defineStore } from 'pinia';
import {
    getSnapshot,
    stepNextEvent,
    tick,
    resetSimulation,
    initSimulation,
    getEvents,
    getErrors,
    getAllErrors,
    getSuspendedChains,
    getMapPaths
} from '../api/simulation';

export interface MapPath {
    pathType: string;
    direction: string;
    position: number;
    startPoint: number;
    endPoint: number;
    keyPoints?: number[];
}

export const useSimStore = defineStore('simulation', {
    state: () => ({
        simTime: 0,
        devices: [] as any[],
        fences: [] as any[],
        chargingStations: [] as any[],
        vessels: [] as any[],
        workInstructions: [] as any[],
        containers: [] as any[],
        events: [] as any[],
        errors: [] as any[],
        lastEventSimTime: 0,
        lastErrorSimTime: 0,
        isPlaying: false,
        playInterval: null as any,
        isPolling: false,
        pollIntervalMs: 500, // 增加到 500ms 保证网络不阻塞
        pollTimer: null as any,
        selectedDevice: null as any,
        mapPaths: [] as MapPath[],
    }),

    actions: {
        async initScene() {
            try {
                await initSimulation();
                this.stopAutoPlay();
                this.stopSnapshotPolling();
                this.events = [];
                this.errors = [];
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                await this.loadMapPaths();
                await this.updateSnapshot();
                // 【修复】：初始化后必须重新启动轮询，否则画面彻底卡死不更新
                this.startSnapshotPolling();
            } catch (error) {
                console.error("初始化默认场景失败", error);
                throw error;
            }
        },

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
                }
            } catch (error) {
                console.error("获取快照失败", error);
            }
        },

        // 【修复】：使用递归 setTimeout 替代 setInterval，防止并发请求堆积卡死 UI
        async pollTick() {
            if (!this.isPolling) return;
            if (!this.isPlaying) {
                try {
                    await this.updateSnapshot();
                    await this.fetchLogs();
                } catch (e) {
                    console.error("轮询异常", e);
                }
            }
            if (this.isPolling) {
                this.pollTimer = setTimeout(() => this.pollTick(), this.pollIntervalMs);
            }
        },

        startSnapshotPolling(intervalMs?: number) {
            if (this.isPolling) return;
            this.isPolling = true;
            if (typeof intervalMs === 'number' && intervalMs > 0) {
                this.pollIntervalMs = intervalMs;
            }
            this.pollTick();
        },

        stopSnapshotPolling() {
            this.isPolling = false;
            if (this.pollTimer) {
                clearTimeout(this.pollTimer);
                this.pollTimer = null;
            }
        },

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
            } catch (error) {
                console.error("获取日志失败", error);
            }
        },

        async doStep() {
            try {
                await stepNextEvent();
                await this.updateSnapshot();
                await this.fetchLogs();
            } catch (error) {
                console.error("单步执行失败", error);
            }
        },

        async doStepNext() {
            return this.doStep();
        },

        async doReset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                this.stopSnapshotPolling();
                this.events = [];
                this.errors = [];
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                await this.updateSnapshot();
                // 【修复】：重置后重启轮询
                this.startSnapshotPolling();
            } catch (error) {
                console.error("重置系统失败", error);
                throw error;
            }
        },

        // 【修复】：安全递归处理高频滴答动画
        async playTick() {
            if (!this.isPlaying) return;
            try {
                await tick(100);
                await this.updateSnapshot();
                await this.fetchLogs();
            } catch (e) {
                console.error("自动播放异常", e);
            }
            if (this.isPlaying) {
                this.playInterval = setTimeout(() => this.playTick(), 100);
            }
        },

        togglePlay() {
            this.isPlaying = !this.isPlaying;
            if (this.isPlaying) {
                this.playTick();
            } else {
                this.stopAutoPlay();
            }
        },

        stopAutoPlay() {
            this.isPlaying = false;
            if (this.playInterval) {
                clearTimeout(this.playInterval);
                this.playInterval = null;
            }
        },

        setSelectedDevice(device: any) {
            this.selectedDevice = device;
        },

        clearSelectedDevice() {
            this.selectedDevice = null;
        },

        async loadMapPaths() {
            try {
                const res: any = await getMapPaths();
                this.mapPaths = res.data || res || [];
            } catch (error) {
                console.error("加载地图配置失败", error);
            }
        }
    }
});