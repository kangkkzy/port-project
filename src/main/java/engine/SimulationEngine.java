package engine;

import common.config.PhysicsConfig;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.consts.WiStatusEnum;
import common.exception.BusinessException;
import common.util.GisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.snapshot.EventLogEntryDto;
import model.entity.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

import service.algorithm.impl.SimulationEventLog;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {

    private final PhysicsConfig physicsConfig;
    private final SimulationEventLog eventLog;
    private final GlobalContext context = GlobalContext.getInstance();
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);
    private final List<SimEventHandler> handlerBeans;

    @Override
    public void afterPropertiesSet() {
        // 通过 Spring 注入的处理器列表进行注册，符合开闭原则
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        return event;
    }

    public void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;

        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp();

        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) break;

            eventQueue.poll();
            if (nextEvent.isCancelled()) continue;

            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > maxEventsPerTimestamp) {
                    log.error("仿真异常: 时间戳 {} 发生死循环，强制熔断", lastProcessedTime);
                    break;
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 0;
            }

            context.setSimTime(nextEvent.getTriggerTime());

            // 记录事件日志（在执行前记录当前上下文）
            EventLogEntryDto logEntry = new EventLogEntryDto();
            logEntry.setSimTime(nextEvent.getTriggerTime());
            logEntry.setType(nextEvent.getType());
            logEntry.setEventId(nextEvent.getEventId());
            logEntry.setParentEventId(nextEvent.getParentEventId());
            logEntry.setSubjects(nextEvent.getSubjects());
            eventLog.append(logEntry);

            SimEventHandler handler = handlerMap.get(nextEvent.getType());
            if (handler != null) {
                try {
                    handler.handle(nextEvent, this, context);
                } catch (Exception e) {
                    log.error("事件处理异常: Type={}, Id={}", nextEvent.getType(), nextEvent.getEventId(), e);
                }
            }
        }
        context.setSimTime(targetSimTime);
    }

    // --- 处理器实现 ---

    /**
     * [重点修改] 栅栏控制处理器
     * 逻辑：仅更新状态。如果是开启操作，清空等待列表（数据维护），但不发送任何唤醒事件。
     */
    @org.springframework.stereotype.Component
    public static class FenceControlHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.FENCE_CONTROL;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = event.getPrimarySubject("FENCE");
            Fence fence = context.getFenceMap().get(fenceId);
            if (fence != null) {
                FenceStateEnum status = (FenceStateEnum) event.getData();
                fence.setStatus(status.getCode());

                // 状态维护：如果栅栏变更为通行，物理上不再阻挡任何车辆。
                // 清空列表是为了保持状态数据的一致性 (SimStateController获取的数据更准确)。
                // 注意：这里没有 scheduleEvent(MOVE_START)，唤醒完全依赖外部算法。
                if (FenceStateEnum.PASSABLE.equals(status)) {
                    fence.getWaitingTrucks().clear();
                }

                log.info("栅栏 {} 状态已更新为: {}", fenceId, status.getDesc());
            }
        }
    }

    // --- 以下处理器保持之前的无逻辑/纯执行状态 ---

    @org.springframework.stereotype.Component
    public static class CmdAssignTaskHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_ASSIGN_TASK;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("DEVICE");
            if (deviceId == null) deviceId = event.getPrimarySubject("TRUCK");
            BaseDevice device = context.getDevice(deviceId);
            if (device == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            SimEvent ackEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CMD_TASK_ACK, payload);
            ackEvent.addSubject("DEVICE", deviceId);
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdTaskAckHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_TASK_ACK;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("DEVICE");
            BaseDevice device = context.getDevice(deviceId);
            if (device == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            device.setCurrWiRefNo((String) payload.get("wiRefNo"));
            if (device.getType() == DeviceTypeEnum.ASC || device.getType() == DeviceTypeEnum.QC) {
                device.setState(DeviceStateEnum.WORKING);
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdMoveHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_MOVE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            BaseDevice device = context.getDevice(truckId);
            if (device == null) throw new BusinessException("移动指令异常: 设备不存在");
            Map<String, Object> payload = (Map<String, Object>) event.getData();

            Double speed = (Double) payload.get("speed");
            Point target = (Point) payload.get("target");
            device.setSpeed(speed);
            device.setCurrentTargetPos(target);

            SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
            moveStart.addSubject("TRUCK", truckId);
        }
    }

    @org.springframework.stereotype.Component
    public static class MoveStartHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.MOVE_START;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("TRUCK");
            if (deviceId == null) deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            // BaseDevice.onMoveStart 会检测 Fence 状态。
            // 若 Fence 为 BLOCKED，device 状态变为 WAITING 并停止，等待外部再次发送 CMD_MOVE。
            if (device != null) {
                device.onMoveStart(context.getSimTime(), engine, event.getEventId());
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class ArrivalHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.ARRIVAL;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String id = event.getPrimarySubject("TRUCK");
            if(id==null) id = event.getPrimarySubject("CRANE");
            BaseDevice d = context.getDevice(id);
            if(d != null) {
                d.onArrival((Point)event.getData(), context.getSimTime(), engine, event.getEventId());
                SimEvent reportEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                reportEvent.addSubject(d.getType() == DeviceTypeEnum.ASC || d.getType() == DeviceTypeEnum.QC ? "CRANE" : "TRUCK", id);
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class ReportIdleHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.REPORT_IDLE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String id = event.getPrimarySubject("TRUCK");
            if(id==null) id = event.getPrimarySubject("CRANE");
            log.info("设备 {} 动作结束，当前空闲", id);
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdChargeHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_CHARGE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            if (truck == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String stationId = (String) payload.get("stationId");
            ChargingStation station = context.getChargingStationMap().get(stationId);
            if (station == null) throw new BusinessException("充电桩不存在");

            Point truckPos = new Point(truck.getPosX(), truck.getPosY());
            Point stationPos = new Point(station.getPosX(), station.getPosY());
            // 使用 GlobalContext 中注入的 PhysicsConfig，避免在静态内部类中直接访问外部非静态字段
            double alignThreshold = context.getPhysicsConfig().getChargeAlignThreshold();
            if (GisUtil.getDistance(truckPos, stationPos) > alignThreshold) {
                throw new BusinessException("充电失败: 设备未对准充电桩");
            }

            station.setTruckId(truckId);
            station.setStatus(DeviceStateEnum.WORKING.getCode());
            truck.setTargetStationId(stationId);
            SimEvent chargeStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CHARGING_START, null);
            chargeStart.addSubject("TRUCK", truckId);
            chargeStart.addSubject("STATION", stationId);
        }
    }

    @org.springframework.stereotype.Component
    public static class ChargingStartHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CHARGING_START;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            String stationId = event.getPrimarySubject("STATION");
            ChargingStation station = context.getChargingStationMap().get(stationId);
            if (truck != null && station != null) {
                Double rate = station.getChargeRate();
                truck.setState(DeviceStateEnum.CHARGING);
                double powerNeeded = Truck.MAX_POWER_LEVEL - truck.getPowerLevel();
                long chargeDurationMS = (long) (powerNeeded / rate);
                SimEvent fullEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                fullEvent.addSubject("TRUCK", truckId);
                fullEvent.addSubject("STATION", stationId);
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class ChargeFullHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CHARGE_FULL;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            String stationId = event.getPrimarySubject("STATION");
            ChargingStation station = context.getChargingStationMap().get(stationId);
            if (truck != null) {
                truck.setPowerLevel(Truck.MAX_POWER_LEVEL);
                truck.setNeedCharge(false);
                truck.setState(DeviceStateEnum.IDLE);
                truck.setTargetStationId(null);
            }
            if (station != null) {
                station.setTruckId(null);
                station.setStatus(DeviceStateEnum.IDLE.getCode());
            }
            SimEvent idleEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
            idleEvent.addSubject("TRUCK", truckId);
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdFenceHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_FENCE_TOGGLE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = event.getPrimarySubject("FENCE");
            Fence fence = context.getFenceMap().get(fenceId);
            if (fence != null) {
                FenceStateEnum status = (FenceStateEnum) event.getData();
                SimEvent ctrlEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.FENCE_CONTROL, status);
                ctrlEvent.addSubject("FENCE", fenceId);
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdCraneMoveHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_CRANE_MOVE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String craneId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(craneId);
            if (device == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            CraneMoveReq req = (CraneMoveReq) payload.get("req");
            Double speed = (Double) payload.get("speed");
            if (speed == null || speed <= 0) throw new BusinessException("speed无效");
            long travelTimeMS = (long) ((req.getDistance() / speed) * 1000);
            device.setState(req.getMoveType());
            SimEvent arrEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, null);
            arrEvent.addSubject("CRANE", device.getId());
        }
    }

    @org.springframework.stereotype.Component
    public static class CmdCraneOpHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.CMD_CRANE_OP;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            CraneOperationReq req = (CraneOperationReq) event.getData();
            SimEvent opEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + req.getDurationMS(), req.getAction(), null);
            opEvent.addSubject("CRANE", req.getCraneId());
        }
    }

    @org.springframework.stereotype.Component
    public static class FetchDoneHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.FETCH_DONE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        }
    }

    @org.springframework.stereotype.Component
    public static class PutDoneHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.PUT_DONE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                device.setState(DeviceStateEnum.IDLE);
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null && device.getId().equals(wi.getPutCheId())) {
                    SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
                    completeEvent.addSubject("WI", device.getCurrWiRefNo());
                }
            }
        }
    }

    @org.springframework.stereotype.Component
    public static class WiCompleteHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.WI_COMPLETE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String wiRefNo = event.getPrimarySubject("WI");
            WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
            if (doneWi != null) doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
        }
    }
}