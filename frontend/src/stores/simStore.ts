import { defineStore } from 'pinia';
// 导入了 getEvents
import { getSnapshot, stepNextEvent, resetSimulation, getEvents } from '../api/simulation';

export const useSimStore = defineStore('simulation', {
    state: () => ({
        simTime: 0,
        devices: [] as any[],
        events: [] as any[],     // 存放事件日志的数组
        lastEventSimTime: 0,     // 记录上次查到了哪个时间的日志
        isPlaying: false,
        playInterval: null as any
    }),

    actions: {
        async updateSnapshot() {
            try {
                const data: any = await getSnapshot();
                if (data) {
                    this.simTime = data.simTime;
                    this.devices = data.devices || [];
                }
                // 每次拿完快照，顺便去拿一下最新的日志
                await this.fetchLogs();
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
                // 【新增】重置时清空日志
                this.events = [];
                this.lastEventSimTime = 0;
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
        }
    }
});