// @ts-nocheck
/**
 * websocket.ts
 *
 * WebSocket 服务模块，基于 SockJS 和 STOMP 实现与后端的实时通信。
 * 负责建立连接、订阅仿真事件主题，并将接收到的消息转发给 Vue 状态管理（Pinia store）。
 * 支持断线自动重连。
 */

import { ref } from 'vue';
import { useSimStore } from '../stores/simStore';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

/**
 * WebSocket 服务类
 * 单例模式，通过 wsService 实例对外提供服务。
 */
class SimulationWebSocketService {
    private client: Client | null = null;
    public connected = ref(false);           // 响应式连接状态，供 Vue 组件使用
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;        // 最大重连尝试次数
    private reconnectDelay = 3000;            // 重连延迟（毫秒）

    /**
     * 连接到 WebSocket 服务器
     */
    connect() {
        if (this.client && this.connected.value) {
            console.log('[WS] 已经处于连接状态');
            return;
        }

        // 配置 STOMP 客户端
        this.client = new Client({
            // 使用 SockJS 创建 WebSocket 连接
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-sim'),
            reconnectDelay: 5000,              // 自动重连延迟
            heartbeatIncoming: 4000,            // 接收心跳间隔
            heartbeatOutgoing: 4000,             // 发送心跳间隔
            debug: (msg) => console.log('[WS Debug]', msg)
        });

        // 连接成功回调
        this.client.onConnect = () => {
            console.log(' 连接成功');
            this.connected.value = true;
            this.reconnectAttempts = 0;

            // 订阅仿真事件主题（后端推送地址）
            this.client!.subscribe('/topic/sim-events', (message) => {
                this.handleMessage(message);
            });
        };

        // STOMP 协议错误回调
        this.client.onStompError = (frame) => {
            console.error(' STOMP 协议错误:', frame.headers['message']);
        };

        // WebSocket 关闭回调（可能由于网络问题或服务器断开）
        this.client.onWebSocketClose = () => {
            this.handleDisconnect();
        };

        // 激活客户端，发起连接
        this.client.activate();
    }

    /**
     * 处理接收到的 WebSocket 消息
     * @param message STOMP 消息对象，包含 body 等属性
     */
    private handleMessage(message: any) {
        const simStore = useSimStore();
        try {
            const eventData = JSON.parse(message.body);
            // 将事件数据存入 Store，由 Store 驱动视图更新或动画
            simStore.handleSimEvent(eventData);

            console.log('[WS] 收到事件:', eventData.eventType, eventData.deviceId);
        } catch (e) {
            console.error('[WS] 消息解析异常:', e);
        }
    }

    /**
     * 处理连接断开：尝试重连（不超过最大次数）
     */
    private handleDisconnect() {
        this.connected.value = false;
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`[WS] 连接断开，尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => this.connect(), this.reconnectDelay);
        }
    }

    /**
     * 主动断开 WebSocket 连接
     */
    public disconnect() {
        if (this.client) {
            this.client.deactivate();
            this.connected.value = false;
        }
    }
}

// 导出单例实例
export const wsService = new SimulationWebSocketService();