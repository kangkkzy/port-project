import { defineStore } from 'pinia';
import {
    getSnapshot,
    stepNextEvent,
    tick,
    resetSimulation,
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
        selectedDevice: null as any,
        mapPaths: [] as MapPath[],
    }),

    actions: {
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

        async reset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                this.events = [];
                this.errors = [];
                this.lastEventSimTime = 0;
                this.lastErrorSimTime = 0;
                await this.updateSnapshot();
            } catch (error) {
                console.error("重置系统失败", error);
            }
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