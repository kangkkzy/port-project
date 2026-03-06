package engine.websocket;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Truck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 消息推送服务，负责将仿真事件广播给前端。
 * 通过 STOMP 协议向 /topic/sim-events 主题发送消息。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationEventWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 需要广播的事件类型集合（只有这些事件才推给前端，避免刷屏）
     */
    private static final Set<EventTypeEnum> BROADCAST_EVENTS = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList(
                    EventTypeEnum.MOVE_START,
                    EventTypeEnum.ARRIVAL,
                    EventTypeEnum.FETCH_DONE,
                    EventTypeEnum.PUT_DONE
            ))
    );

    /**
     * 广播仿真事件（仅限特定类型的事件才会推送，避免刷屏）
     * @param event 仿真事件对象
     */
    public void broadcast(SimEvent event) {
        if (event == null) return;

        // 检查事件类型是否在广播列表中
        if (!BROADCAST_EVENTS.contains(event.getType())) return;

        // MOVE_START/ARRIVAL 事件主体 key 是 "TRUCK" 或 "CRANE"，不是 "DEVICE"，需逐级回退
        String deviceId = event.getPrimarySubject("DEVICE");
        if (deviceId == null) deviceId = event.getPrimarySubject("TRUCK");
        if (deviceId == null) deviceId = event.getPrimarySubject("CRANE");
        if (deviceId == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getType().name());
        payload.put("deviceId", deviceId);
        payload.put("simTime", event.getTriggerTime());

        // 核心修复：从上下文中主动获取设备坐标，防止 event.getData() 为 null 时前端缺少位置信息
        GlobalContext ctx = GlobalContext.getInstance();
        BaseDevice device = ctx.getDevice(deviceId);
        if (device != null) {
            payload.put("currentPosX", device.getPosX());
            payload.put("currentPosY", device.getPosY());

            // 推送终点坐标（前端 lerp 动画的目标）：优先用集卡的剩余路径首点，其次用 currentTargetPos
            model.entity.Point targetPos = null;
            if (device instanceof Truck) {
                Truck truck = (Truck) device;
                if (truck.getRemainingMoveTargets() != null && !truck.getRemainingMoveTargets().isEmpty()) {
                    targetPos = truck.getRemainingMoveTargets().get(0);
                }
            }
            if (targetPos == null) {
                targetPos = device.getCurrentTargetPos(); // 覆盖 QC/ASC 等起重机
            }
            if (targetPos != null) {
                payload.put("targetPosX", targetPos.getX());
                payload.put("targetPosY", targetPos.getY());
            }
        }

        // 合并事件自带的附加数据（如果有）
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) event.getData();
            payload.putAll(dataMap);
        }

        try {
            messagingTemplate.convertAndSend("/topic/sim-events", payload);
        } catch (Exception e) {
            log.warn("广播仿真事件失败: {}", e.getMessage());
        }
    }

    /**
     * 专门用于广播业务异常事件（如位置校验失败、任务冲突等）
     * @param deviceId     关联的设备ID（可为null，表示系统级错误）
     * @param errorMessage 错误描述
     * @param simTime      当前仿真时间
     */
    public void broadcastError(String deviceId, String errorMessage, long simTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "ERR_" + System.currentTimeMillis());
        payload.put("eventType", "SIM_ERROR_EVENT");
        payload.put("deviceId", deviceId != null ? deviceId : "SYSTEM");
        payload.put("message", errorMessage);
        payload.put("simTime", simTime);

        try {
            messagingTemplate.convertAndSend("/topic/sim-events", payload);
            log.info("已推送业务告警事件到前端 -> {}: {}", deviceId, errorMessage);
        } catch (Exception e) {
            log.warn("广播异常事件失败: {}", e.getMessage());
        }
    }
}