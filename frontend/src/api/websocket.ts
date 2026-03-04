import { ref } from 'vue';
import { useSimStore } from '../stores/simStore';
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';

/**
 * WebSocket 服务：订阅 /topic/sim-events，接收后端推送的实时仿真事件。
 * 前端根据事件类型更新设备状态，并驱动动画。
 */
class SimulationWebSocketService {
    private client: Client | null = null;
    private connected = ref(false);
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 3000;

    /**
     * 连接到 WebSocket 服务器
     * 使用 STOMP over SockJS
     */
    connect() {
        if (this.client && this.connected.value) {
            console.log('[WS] 已连接');
            return;
        }

        this.client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-sim'),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        this.client.onConnect = () => {
            console.log('[WS] 已连接');
            this.connected.value = true;
            this.reconnectAttempts = 0;

            // 订阅仿真事件主题
            this.client?.subscribe('/topic/sim-events', (message: any) => {
                this.handleMessage(message);
            });
        };

        this.client.onStompError = (frame: any) => {
            console.error('[WS] 连接失败:', frame);
            this.handleDisconnect();
        };

        this.client.activate();
    }

    /**
     * 处理收到的消息
     */
    handleMessage(message: any) {
        try {
            const payload = JSON.parse(message.body);
            console.log('[WS] 收到事件:', payload);

            const simStore = useSimStore();
            const { eventType, deviceId, targetPosX, targetPosY, durationMs, message: errorMessage } = payload;

            if (eventType === 'SIM_ERROR_EVENT') {
                // 业务约束错误事件，推送到控制台并触发视觉告警
                simStore.onSimulationError(deviceId, errorMessage);
                return;
            }

            if (eventType === 'MOVE_START' && deviceId) {
                // 更新设备的 targetPosX/Y 和动画时长，让前端组件执行 CSS 过渡或 Canvas 补间
                simStore.updateDeviceTarget(deviceId, targetPosX, targetPosY, durationMs);
            } else if (eventType === 'ARRIVAL' && deviceId) {
                // 到达目标，更新实际坐标
                simStore.updateDevicePosition(deviceId, targetPosX, targetPosY);
            } else if ((eventType === 'FETCH_DONE' || eventType === 'PUT_DONE') && deviceId) {
                // 抓/落箱完成，可能需要更新箱状态
                simStore.onContainerOperation(deviceId, eventType);
            }
        } catch (e) {
            console.error('[WS] 解析消息失败:', e);
        }
    }

    /**
     * 处理断开连接
     */
    handleDisconnect() {
        this.connected.value = false;
        this.client = null;

        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`[WS] ${this.reconnectDelay / 1000}s 后重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            setTimeout(() => this.connect(), this.reconnectDelay);
        } else {
            console.error('[WS] 达到最大重连次数，停止重连');
        }
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.client) {
            this.client.deactivate();
            this.client = null;
            this.connected.value = false;
            console.log('[WS] 已断开连接');
        }
    }

    /**
     * 是否已连接
     */
    isConnected() {
        return this.connected.value;
    }
}

// 导出单例
export const wsService = new SimulationWebSocketService();
