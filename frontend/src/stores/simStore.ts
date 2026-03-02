import { defineStore } from 'pinia';
// 导入了 getEvents
import { getSnapshot, stepNextEvent, resetSimulation, getEvents, getErrors, getAllErrors, getSuspendedChains } from '../api/simulation';

export const useSimStore = defineStore('simulation', {
    state: () => ({
        simTime: 0,
        devices: [] as any[],
        fences: [] as any[],
        chargingStations: [] as any[],
        workInstructions: [] as any[],
        events: [] as any[],
        errors: [] as any[],
        lastEventSimTime: 0,
        lastErrorSimTime: 0,
        isPlaying: false,
        playInterval: null as any,
        selectedDevice: null as any,
    }),

    actions: {
        async updateSnapshot() {
            try {
                const data: any = await getSnapshot();
                if (data) {
                    this.simTime = data.simTime;
                    this.devices = data.devices || [];
                    this.fences = data.fences || [];
                    this.chargingStations = data.chargingStations || [];
                    this.workInstructions = data.workInstructions || [];
                }
                // 每次拿完快照，顺便去拿一下最新的日志
                await this.fetchLogs();
                await this.fetchErrors();
            } catch (error) {
                console.error("更新快照数据失败", error);
            }
        },

        // 拉取并处理事件日志
        async fetchLogs() {
            try {
                const logs: any = await getEvents(this.lastEventSimTime);
                if (logs && logs.length > 0) {
                    // 简单的去重逻辑
                    const existingIds = new Set(this.events.map(e => e.eventId));
                    const newLogs = logs.filter((e: any) => !existingIds.has(e.eventId));

                    this.events.push(...newLogs);

                    // 为了防止浏览器卡顿，最多只保留最新的 100 条日志
                    if (this.events.length > 100) {
                        this.events = this.events.slice(this.events.length - 100);
                    }

                    // 更新查询时间戳，下次只查这之后的
                    const maxTime = Math.max(...logs.map((e: any) => e.simTime));
                    this.lastEventSimTime = maxTime;
                }
            } catch (e) {
                console.error("获取日志失败", e);
            }
        },

        // 拉取错误日志
        async fetchErrors() {
            try {
                const errors: any = await getErrors(this.lastErrorSimTime);
                if (errors && errors.length > 0) {
                    this.errors.push(...errors);
                    const maxTime = Math.max(...errors.map((e: any) => e.simTime));
                    this.lastErrorSimTime = maxTime;
                }
            } catch (e) {
                console.error("获取错误日志失败", e);
            }
        },

        // 获取所有错误日志
        async fetchAllErrors() {
            try {
                const errors: any = await getAllErrors();
                this.errors = errors || [];
                return errors;
            } catch (e) {
                console.error("获取所有错误日志失败", e);
                return [];
            }
        },

        // 获取暂停的事件链
        async fetchSuspendedChains() {
            try {
                return await getSuspendedChains();
            } catch (e) {
                console.error("获取暂停事件链失败", e);
                return null;
            }
        },

        async doStepNext() {
            try {
                await stepNextEvent();
                await this.updateSnapshot();
            } catch (error) {
                console.error("单步执行失败", error);
            }
        },

        async doReset() {
            try {
                await resetSimulation();
                this.stopAutoPlay();
                // 重置时清空日志
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
                    this.doStepNext();
                }, 500);
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

        // 设置选中的设备
        setSelectedDevice(device: any) {
            this.selectedDevice = device;
        },

        // 清除选中的设备
        clearSelectedDevice() {
            this.selectedDevice = null;
        }
    }
});