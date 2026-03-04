package engine.websocket;

import common.consts.EventTypeEnum;
import engine.SimEvent;
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

        // 仅广播前端动画关心的核心物理事件
        java.util.Set<EventTypeEnum> BROADCAST_EVENTS = java.util.Set.of(
                EventTypeEnum.MOVE_START,
                EventTypeEnum.ARRIVAL,
                EventTypeEnum.FETCH_DONE,
                EventTypeEnum.PUT_DONE
        );
        if (!BROADCAST_EVENTS.contains(event.getType())) return;

        // 安全获取设备 ID：只能使用 getPrimarySubject("DEVICE")
        String deviceId = event.getPrimarySubject("DEVICE");
        if (deviceId == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getType().name());
        payload.put("deviceId", deviceId);
        payload.put("simTime", event.getTriggerTime());

        // 安全提取 data 中的负载参数（不要调用实体中不存在的方法）
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) event.getData();
            payload.putAll(dataMap); // 将 targetX, targetY, durationMS 等信息直接透传
        }

        try {
            messagingTemplate.convertAndSend("/topic/sim-events", payload);
            log.debug("广播事件到前端: {} -> {}", event.getType(), deviceId);
        } catch (Exception e) {
            log.warn("广播仿真事件失败: {}", e.getMessage());
        }
    }
}
