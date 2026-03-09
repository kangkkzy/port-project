package engine.websocket;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.context.GlobalContext;
import model.dto.snapshot.DeviceSnapshotDto;
import model.entity.BaseDevice;
import model.entity.Truck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

        // ARRIVAL 事件主体 key 是 "TRUCK" 或 "CRANE"，不是 "DEVICE"，需逐级回退
        String deviceId = event.getPrimarySubject("DEVICE");
        if (deviceId == null) deviceId = event.getPrimarySubject("TRUCK");
        if (deviceId == null) deviceId = event.getPrimarySubject("CRANE");
        if (deviceId == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("eventType", event.getType().name());
        payload.put("deviceId", deviceId);
        payload.put("simTime", event.getTriggerTime());

        // 使用插值坐标 getRealTimePosX/getRealTimePosY 保证动画平滑
        GlobalContext ctx = GlobalContext.getInstance();
        long currentSimTime = ctx.getSimTime();
        BaseDevice device = ctx.getDevice(deviceId);
        if (device != null) {
            payload.put("currentPosX", device.getRealTimePosX(currentSimTime));
            payload.put("currentPosY", device.getRealTimePosY(currentSimTime));

            // 推送终点坐标（前端 lerp 动画的目标）：优先用集卡的剩余路径首点，其次用 targetX/targetY
            model.entity.Point targetPos = null;
            if (device instanceof Truck) {
                Truck truck = (Truck) device;
                if (truck.getRemainingMoveTargets() != null && !truck.getRemainingMoveTargets().isEmpty()) {
                    targetPos = truck.getRemainingMoveTargets().get(0);
                }
            }
            if (targetPos == null) {
                if (device.getTargetX() != null && device.getTargetY() != null) {
                    targetPos = new model.entity.Point(device.getTargetX(), device.getTargetY());
                }
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

    /**
     * 广播状态快照（包含所有设备的完整状态，用于前端动画插值）
     * 后端只在事件发生时广播，前端自行根据 moveStartTime/expectedArrivalTime 进行线性插值
     *
     * @param context 全局上下文
     */
    public void broadcastState(GlobalContext context) {
        if (context == null) return;

        long currentSimTime = context.getSimTime();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STATE_SNAPSHOT");
        payload.put("simTime", currentSimTime);

        // 构建所有设备的完整快照（包含前端插值所需字段）
        List<DeviceSnapshotDto> deviceSnapshots = new ArrayList<>();

        // 收集集卡快照
        for (Truck truck : context.getTruckMap().values()) {
            deviceSnapshots.add(buildDeviceSnapshot(truck, currentSimTime));
        }

        // 收集起重机快照（岸桥 QC）
        for (BaseDevice crane : context.getQcMap().values()) {
            deviceSnapshots.add(buildDeviceSnapshot(crane, currentSimTime));
        }

        // 收集起重机快照（龙门吊 ASC）
        for (BaseDevice crane : context.getAscMap().values()) {
            deviceSnapshots.add(buildDeviceSnapshot(crane, currentSimTime));
        }

        payload.put("devices", deviceSnapshots);

        try {
            messagingTemplate.convertAndSend("/topic/sim-state", payload);
        } catch (Exception e) {
            log.warn("广播状态快照失败: {}", e.getMessage());
        }
    }

    /**
     * 构建单个设备快照，包含前端插值所需的完整运动信息
     *
     * @param device 设备实体
     * @param currentSimTime 当前仿真时间
     * @return 设备快照 DTO
     */
    private DeviceSnapshotDto buildDeviceSnapshot(BaseDevice device, long currentSimTime) {
        DeviceSnapshotDto dto = new DeviceSnapshotDto();

        // 基础属性
        dto.setId(device.getId());
        dto.setType(device.getType());
        dto.setState(device.getState());
        dto.setCurrWiRefNo(device.getCurrWiRefNo());

        // 当前位置（离散事件触发时的真实位置）
        dto.setPosX(device.getPosX());
        dto.setPosY(device.getPosY());

        // 特有属性 (仅集卡有电量)
        if (device instanceof Truck) {
            Truck truck = (Truck) device;
            dto.setPowerLevel(truck.getPowerLevel());
            dto.setNeedCharge(truck.isNeedCharge());
        }

        //  前端插值所需字段
        // 直接映射原始字段，让前端自行计算插值
        dto.setMoveStartTime(device.getMoveStartTime());
        dto.setMoveStartPosX(device.getMoveStartPosX());
        dto.setMoveStartPosY(device.getMoveStartPosY());
        dto.setTargetX(device.getTargetX());
        dto.setTargetY(device.getTargetY());
        dto.setExpectedArrivalTime(device.getExpectedArrivalTime());

        return dto;
    }
}