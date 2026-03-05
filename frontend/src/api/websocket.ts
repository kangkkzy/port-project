// @ts-nocheck
import { ref } from 'vue';
import { useSimStore } from '../stores/simStore';
// [关键修改]：直接引用包名，由 Vite 处理模块查找
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

/**
 * WebSocket 服务：订阅 /topic/sim-events，接收后端推送的实时仿真事件。
 * 前端根据事件类型更新设备状态，并驱动动画。
 */
class SimulationWebSocketService {
    private client: Client | null = null;
    public connected = ref(false);
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 3000;

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
            // 后端 WebSocket 端点地址
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-sim'),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            debug: (msg) => console.log('[WS Debug]', msg)
        });

        this.client.onConnect = () => {
            console.log('✅ [WS] 连接成功');
            this.connected.value = true;
            this.reconnectAttempts = 0;

            // 订阅仿真事件主题
            this.client!.subscribe('/topic/sim-events', (message) => {
                this.handleMessage(message);
            });
        };

        this.client.onStompError = (frame) => {
            console.error('❌ [WS] STOMP 协议错误:', frame.headers['message']);
        };

        this.client.onWebSocketClose = () => {
            this.handleDisconnect();
        };

        this.client.activate();
    }

    /**
     * 解析并处理后端推送的仿真事件
     */
    private handleMessage(message: any) {
        const simStore = useSimStore();
        try {
            const eventData = JSON.parse(message.body);
            // 将事件存入 Store，驱动视图更新
            simStore.handleSimEvent(eventData);

            console.log('[WS] 收到事件:', eventData.eventType, eventData.deviceId);
        } catch (e) {
            console.error('[WS] 消息解析异常:', e);
        }
    }

    private handleDisconnect() {
        this.connected.value = false;
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`[WS] 连接断开，尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => this.connect(), this.reconnectDelay);
        }
    }

    public disconnect() {
        if (this.client) {
            this.client.deactivate();
            this.connected.value = false;
        }
    }
}

export const wsService = new SimulationWebSocketService();