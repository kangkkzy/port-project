package engine.websocket;

import common.consts.EventTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import engine.SimEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 仿真事件 WebSocket 广播服务。
 * 当核心物理事件（MOVE_START, ARRIVAL, FETCH_DONE, PUT_DONE）成功执行时，
 * 向所有连入的 WebSocket 客户端推送轻量级 JSON 消息。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationEventWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /** 广播目标主题 */
    private static final String TOPIC = "/topic/sim-events";

    /** 需要广播的事件类型 */
    private static final java.util.Set<EventTypeEnum> BROADCAST_EVENTS = java.util.Set.of(
            EventTypeEnum.MOVE_START,
            EventTypeEnum.ARRIVAL,
            EventTypeEnum.FETCH_DONE,
            EventTypeEnum.PUT_DONE
    );

    /**
     * 广播仿真事件。
     * 若事件类型在 BROADCAST_EVENTS 中，则向 /topic/sim-events 推送消息。
     *
     * @param event 已处理完成的事件
     * @param durationMs 事件耗时（毫秒），前端用于动画时长
     */
    public void broadcast(SimEvent event, long durationMs) {
        if (event == null) return;

        EventTypeEnum type = event.getType();
        if (!BROADCAST_EVENTS.contains(type)) {
            return;
        }

        String deviceId = event.getPrimaryDeviceId();
        if (deviceId == null) {
            deviceId = event.getPrimarySubject("DEVICE");
        }

        // 构建轻量级消息
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", type.name());
        payload.put("deviceId", deviceId);
        payload.put("simTime", event.getTriggerTime());
        payload.put("durationMs", durationMs);

        // 提取目标坐标（如果有）
        Object data = event.getData();
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object targetXObj = dataMap.get("targetX");
            Object targetYObj = dataMap.get("targetY");
            // 兼容 Point 对象序列化后的 x/y 字段
            if (targetXObj == null && dataMap.containsKey("targetPoint")) {
                Object targetPoint = dataMap.get("targetPoint");
                if (targetPoint instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pointMap = (Map<String, Object>) targetPoint;
                    targetXObj = pointMap.get("x");
                    targetYObj = pointMap.get("y");
                }
            }
            if (targetXObj != null) payload.put("targetPosX", targetXObj);
            if (targetYObj != null) payload.put("targetPosY", targetYObj);
        }

        try {
            messagingTemplate.convertAndSend(TOPIC, payload);
            log.debug("广播仿真事件: {} - {} -> device={}", type.name(), event.getEventId(), deviceId);
        } catch (Exception e) {
            log.warn("广播仿真事件失败: {}", e.getMessage());
        }
    }
}

