package engine;

import common.config.PhysicsConfig;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.consts.WiStatusEnum;
import common.exception.BusinessException;
import common.exception.SimulationDeadLoopException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import service.algorithm.impl.SimulationEventLog;
import service.algorithm.impl.SimulationErrorLog;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {
    // 数据注入
    private final PhysicsConfig physicsConfig;
    private final SimulationEventLog eventLog;
    private final SimulationErrorLog errorLog;
    private final GlobalContext context = GlobalContext.getInstance();
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);
    private final List<SimEventHandler> handlerBeans;
    // 事件ID到事件的映射，用于取消事件
    private final Map<String, SimEvent> eventIdMap = new ConcurrentHashMap<>();
    // 被暂停的事件链：记录所有被暂停的事件ID（包括该事件及其所有子事件）
    private final java.util.Set<String> suspendedEventChainIds = ConcurrentHashMap.newKeySet();

    @Override
    public void afterPropertiesSet() {
        // 通过 Spring 注入的处理器列表进行注册
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }
    // 注入新事件
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        eventIdMap.put(event.getEventId(), event);
        return event;
    }

    /**
     * 取消指定事件
     * @param eventId 事件ID
     * @return 是否成功取消（事件存在且未被处理）
     */
    public boolean cancelEvent(String eventId) {
        SimEvent event = eventIdMap.get(eventId);
        if (event == null) {
            return false;
        }
        // 标记为已取消
        event.setCancelled(true);
        return true;
    }

    /**
     * 检查事件链是否被暂停
     * 递归检查当前事件及其父事件链，如果任何事件被暂停，则整个事件链被暂停
     * @param eventId 事件ID
     * @return 如果事件链被暂停返回true
     */
    private boolean isEventChainSuspended(String eventId) {
        if (eventId == null) {
            return false;
        }
        // 如果当前事件被暂停 返回true
        if (suspendedEventChainIds.contains(eventId)) {
            return true;
        }
        // 检查父事件链（递归检查所有父事件）
        SimEvent event = eventIdMap.get(eventId);
        if (event != null && event.getParentEventId() != null) {
            // 递归检查父事件链
            if (isEventChainSuspended(event.getParentEventId())) {
                // 如果父事件链被暂停 也将当前事件标记为暂停（优化后续检查）
                suspendedEventChainIds.add(eventId);
                return true;
            }
        }
        return false;
    }

    /**
     * 暂停事件链：将指定事件及其所有子事件标记为暂停
     * @param eventId 导致暂停的事件ID
     */
    private void suspendEventChain(String eventId) {
        if (eventId == null) {
            return;
        }
        suspendedEventChainIds.add(eventId);
        log.warn("事件链已暂停: EventId={}, 该事件及其所有子事件将被跳过", eventId);
    }

    /**
     * 获取所有被暂停的事件ID（供外部查询）
     * @return 被暂停的事件ID集合
     */
    public java.util.Set<String> getSuspendedEventChainIds() {
        return new java.util.HashSet<>(suspendedEventChainIds);
    }
    // 时间
    public void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;

        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp();

        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) break;

            eventQueue.poll();

            // 从映射中移除已处理的事件
            eventIdMap.remove(nextEvent.getEventId());

            if (nextEvent.isCancelled()) {
                log.info("事件已取消，跳过处理: EventId={}, Type={}, Time={}",
                        nextEvent.getEventId(), nextEvent.getType(), nextEvent.getTriggerTime());
                continue;
            }

            // 检查事件链是否被暂停（检查当前事件及其父事件链）
            if (isEventChainSuspended(nextEvent.getEventId())) {
                log.warn("事件链已暂停，跳过处理: EventId={}, Type={}, Time={}, ParentEventId={}",
                        nextEvent.getEventId(), nextEvent.getType(), nextEvent.getTriggerTime(), nextEvent.getParentEventId());
                continue;
            }

            // 改进的死循环检测逻辑
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > maxEventsPerTimestamp) {
                    // 记录死循环错误
                    String errorMsg = String.format("仿真死循环检测: 时间戳 %d 发生死循环，已处理 %d 个事件，超过阈值 %d",
                            lastProcessedTime, sameTimeEventCount, maxEventsPerTimestamp);
                    errorLog.recordDeadLoopError(lastProcessedTime, sameTimeEventCount, maxEventsPerTimestamp, errorMsg);

                    // 抛出异常，通知外部算法
                    throw new SimulationDeadLoopException(errorMsg, lastProcessedTime, sameTimeEventCount);
                }
            } else {
                // 新的时间戳，重置计数器
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 1;
            }

            context.setSimTime(nextEvent.getTriggerTime());

            // 记录事件日志
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
                    // 记录异常并暂停该事件链
                    String errorMsg = String.format("事件处理异常: Type=%s, Id=%s, Time=%d, 事件链已暂停",
                            nextEvent.getType(), nextEvent.getEventId(), nextEvent.getTriggerTime());
                    errorLog.recordEventProcessingError(nextEvent.getEventId(), nextEvent.getType(),
                            nextEvent.getTriggerTime(), errorMsg, e);
                    log.error(errorMsg, e);

                    // 暂停该事件链，该事件及其所有子事件将被跳过
                    suspendEventChain(nextEvent.getEventId());

                    // 不中断仿真，继续处理其他独立的事件链
                }
            }
        }
        context.setSimTime(targetSimTime);
    }

    /**
     *  栅栏控制处理器
     * 仅更新状态 如果是开启操作 清空等待列表（数据维护） 但不发送任何唤醒事件
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

                // 如果栅栏变更为通行 物理上不再阻挡任何车辆
                if (FenceStateEnum.PASSABLE.equals(status)) {
                    fence.getWaitingTrucks().clear();
                }

                log.info("栅栏 {} 状态已更新为: {}", fenceId, status.getDesc());
            }
        }
    }
    /**
     * 任务指派处理
     */
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
    /**
     * 任务确认处理
     */
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
    /**
     * 移动指令处理
     */
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
    /**
     * 开始移动处理
     */
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
            if (device != null) {
                device.onMoveStart(context.getSimTime(), engine, event.getEventId());
            }
        }
    }
    /**
     * 到达处理
     */
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
    /**
     * 空闲上报处理  记录日志
     */
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
    /**
     * 充电指令处理
     */
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
    /**
     * 充电开始处理
     */
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
    /**
     * 充电完成处理
     */
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

    /**
     * 栅栏指令转换处理
     */
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

    /**
     * 桥吊/龙门吊 移动指令处理
     */
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

    /**
     * 桥吊/龙门吊 操作处理
     */
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

    // 抓箱完成处理
    @org.springframework.stereotype.Component
    public static class FetchDoneHandler implements SimEventHandler {

        @Override
        public EventTypeEnum getType() {
            return EventTypeEnum.FETCH_DONE;
        }

        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            //  获取执行抓箱的设备
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);

            if (device != null) {
                //  获取当前绑定的作业指令
                String wiRefNo = device.getCurrWiRefNo();
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);

                if (wi != null && wi.getContainerId() != null) {
                    //  获取对应的集装箱
                    Container container = context.getContainerMap().get(wi.getContainerId());

                    if (container != null) {
                        //  更新箱子位置为当前设备ID 随着设备移动
                        String oldPos = container.getCurrentPos();
                        container.setCurrentPos(device.getId());

                        log.info("事件[FETCH_DONE]: 设备 [{}] 完成抓箱。集装箱 [{}] 位置已从 [{}] 更新为设备上的 [{}]",
                                deviceId, container.getContainerId(), oldPos, device.getId());
                    } else {
                        log.warn("事件[FETCH_DONE]: 指令 [{}] 引用的集装箱 [{}] 在系统中未找到", wiRefNo, wi.getContainerId());
                    }
                } else {
                    log.warn("事件[FETCH_DONE]: 设备 [{}] 完成抓箱动作，但未绑定有效指令或指令无箱号", deviceId);
                }
            }
        }
    }

    /**
     * 放箱完成处理 作业完成处理
     */
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

    /**
     * 指令完成处理
     */
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