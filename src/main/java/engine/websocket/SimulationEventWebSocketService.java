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

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationEventWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(SimEvent event) {
        if (event == null) return;

        java.util.Set<EventTypeEnum> BROADCAST_EVENTS = new java.util.HashSet<>();
        BROADCAST_EVENTS.add(EventTypeEnum.MOVE_START);
        BROADCAST_EVENTS.add(EventTypeEnum.ARRIVAL);
        BROADCAST_EVENTS.add(EventTypeEnum.FETCH_DONE);
        BROADCAST_EVENTS.add(EventTypeEnum.PUT_DONE);
        if (!BROADCAST_EVENTS.contains(event.getType())) return;

        String deviceId = event.getPrimarySubject("DEVICE");
        if (deviceId == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getType().name());
        payload.put("deviceId", deviceId);
        payload.put("simTime", event.getTriggerTime());

        // 核心修复：从上下文中主动获取坐标，防止 event.getData() 为 null 时前端无数据
        GlobalContext ctx = GlobalContext.getInstance();
        BaseDevice device = ctx.getDevice(deviceId);
        if (device != null) {
            payload.put("currentPosX", device.getPosX());
            payload.put("currentPosY", device.getPosY());

            if (device instanceof Truck) {
                Truck truck = (Truck) device;
                if (truck.getRemainingMoveTargets() != null && !truck.getRemainingMoveTargets().isEmpty()) {
                    payload.put("targetPosX", truck.getRemainingMoveTargets().get(0).getX());
                    payload.put("targetPosY", truck.getRemainingMoveTargets().get(0).getY());
                }
            }
        }

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

    // 第三阶段新增：专门用于广播业务异常事件
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
