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
        pollIntervalMs: 300,
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
            } catch (error) {
                console.error("初始化默认场景失败", error);
            }
        },

        async updateSnapshot() {
            try {
                // 使用 any 绕过 AxiosResponse 泛型限制
                const res: any = await getSnapshot();
                // 兼容拦截器：如果返回了后端 Result 对象，实际数据在 res.data 里
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

        startSnapshotPolling(intervalMs?: number) {
            if (this.isPolling) return;
            this.isPolling = true;
            if (typeof intervalMs === 'number' && intervalMs > 0) {
                this.pollIntervalMs = intervalMs;
            }
            this.pollTimer = setInterval(() => {
                // 离散仿真：播放中由 tick 驱动刷新；此处仅在非播放状态下轮询同步
                if (!this.isPlaying) {
                    this.updateSnapshot();
                    this.fetchLogs();
                }
            }, this.pollIntervalMs);
        },

        stopSnapshotPolling() {
            this.isPolling = false;
            if (this.pollTimer) {
                clearInterval(this.pollTimer);
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

        // 兼容旧调用名（组件里可能仍在使用）
        async doStepNext() {
            return this.doStep();
        },

        async reset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                this.stopSnapshotPolling();
                this.events = [];
                this.errors = [];
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                await this.updateSnapshot();
            } catch (error) {
                console.error("重置系统失败", error);
            }
        },

        // 兼容旧调用名（组件里可能仍在使用）
        async doReset() {
            return this.reset();
        },

        togglePlay() {
            this.isPlaying = !this.isPlaying;
            if (this.isPlaying) {
                this.playInterval = setInterval(() => {
                    // 以时间片推进，保证 MOVING/WORKING 状态能被前端采样到
                    tick(100).then(() => {
                        this.updateSnapshot();
                        this.fetchLogs();
                    });
                }, 100);
            } else {
                this.stopAutoPlay();
            }
        },

        stopAutoPlay() {
            this.isPlaying = false;
            if (this.playInterval) {
                clearInterval(this.playInterval);
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
                // 修复 TS2740: 强制转换为 any 解析，并安全地提取 .data 属性
                const res: any = await getMapPaths();
                const paths = res.data || res || [];
                this.mapPaths = paths;
                console.log('地图路径配置已加载:', this.mapPaths);
            } catch (error) {
                console.error("加载地图配置失败", error);
            }
        }
    }
});