package engine;

import common.config.PhysicsConfig;
import common.consts.BizTypeEnum;
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

/**
 * 仿真引擎核心
 * 负责维护仿真时钟、管理事件优先队列、调度事件处理以及处理全局异常熔断。
 * 设计原则：单一时钟源、串行事件链。一旦发生未捕获异常，引擎将触发全局暂停以保护现场。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {

    private final PhysicsConfig physicsConfig;
    private final SimulationEventLog eventLog;
    private final SimulationErrorLog errorLog;
    private final GlobalContext context = GlobalContext.getInstance();

    // 数据结构

    /**
     * 事件优先队列
     * 按仿真时间戳排序 驱动仿真推进
     */
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();

    /** 事件处理器注册表 */
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);
    private final List<SimEventHandler> handlerBeans;

    /** 事件索引，用于快速查找/取消事件 */
    private final Map<String, SimEvent> eventIdMap = new ConcurrentHashMap<>();

    // 状态控制

    /**
     * 全局暂停标志
     * 出现异常时置为 true，阻断后续事件执行，直到人工 reset。
     */
    private volatile boolean globalSuspended = false;

    // 暂停现场记录
    private final java.util.Set<common.consts.BizTypeEnum> suspendedBizTypes = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> suspendedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public void afterPropertiesSet() {
        // 自动注册所有 Spring 管理的 Handler
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    /**
     * 调度新事件
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        eventIdMap.put(event.getEventId(), event);
        return event;
    }

    /**
     * 取消事件
     */
    public boolean cancelEvent(String eventId) {
        SimEvent event = eventIdMap.get(eventId);
        if (event == null) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    /**
     * 辅助方法：尝试回溯报错事件关联的业务类型
     */
    private common.consts.BizTypeEnum getBizTypeFromEvent(SimEvent event) {
        if (event == null) return null;

        // 1. 尝试从 payload 获取
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String wiRefNo = (String) payload.get("wiRefNo");
            if (wiRefNo != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) return wi.getMoveKind();
            }
        }
        // 尝试从关联的 WI Subject 获取
        String wiRefNoFromSubject = event.getPrimarySubject("WI");
        if (wiRefNoFromSubject != null) {
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNoFromSubject);
            if (wi != null) return wi.getMoveKind();
        }
        // 尝试从设备反查
        String deviceId = event.getPrimarySubject("DEVICE");
        if (deviceId == null) deviceId = event.getPrimarySubject("TRUCK");
        if (deviceId == null) deviceId = event.getPrimarySubject("CRANE");
        if (deviceId != null) {
            BaseDevice device = context.getDevice(deviceId);
            if (device != null && device.getCurrWiRefNo() != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null) return wi.getMoveKind();
            }
        }
        // 递归父事件
        String parentEventId = event.getParentEventId();
        if (parentEventId != null) {
            SimEvent parentEvent = eventIdMap.get(parentEventId);
            if (parentEvent != null) return getBizTypeFromEvent(parentEvent);
        }
        return null;
    }

    /**
     * 触发全局熔断
     * 记录错误上下文并锁死引擎。
     */
    private void triggerGlobalSuspend(SimEvent event) {
        this.globalSuspended = true;

        if (event != null) {
            log.error(">>> 仿真引擎触发全局暂停 <<< 错误源头: Id={}, Type={}", event.getEventId(), event.getType());
            suspendedEventIds.add(event.getEventId());
            common.consts.BizTypeEnum bizType = getBizTypeFromEvent(event);
            if (bizType != null) {
                suspendedBizTypes.add(bizType);
            }
        } else {
            log.error(">>> 仿真引擎触发全局暂停 <<< (未知事件源)");
        }
    }

    public java.util.Set<common.consts.BizTypeEnum> getSuspendedBizTypes() {
        return new java.util.HashSet<>(suspendedBizTypes);
    }

    public java.util.Set<String> getSuspendedEventIds() {
        return new java.util.HashSet<>(suspendedEventIds);
    }

    /**
     * 重置仿真状态
     * 清空队列、重置时钟锁、清除错误记录。
     */
    public synchronized void reset() {
        eventQueue.clear();
        eventIdMap.clear();
        suspendedBizTypes.clear();
        suspendedEventIds.clear();
        globalSuspended = false;
        log.info("仿真引擎已重置，系统恢复就绪。");
    }

    /**
     * 单步执行
     * 供前端单步调试或 runUntil 内部调用。
     */
    public synchronized SimEvent stepNextEvent() {
        if (globalSuspended) {
            log.warn("拒绝执行：引擎处于全局暂停状态。");
            return null;
        }

        if (eventQueue.isEmpty()) {
            return null;
        }

        SimEvent nextEvent = eventQueue.poll();
        if (nextEvent == null) {
            return null;
        }

        processEvent(nextEvent);
        return nextEvent;
    }

    /**
     * 事件处理核心逻辑
     * 包含完整的异常捕获 只要有异常就会中断
     */
    private void processEvent(SimEvent nextEvent) {
        // 跳过已取消或暂停状态
        if (nextEvent.isCancelled()) {
            eventIdMap.remove(nextEvent.getEventId());
            return;
        }
        if (globalSuspended) {
            return;
        }

        eventIdMap.remove(nextEvent.getEventId());

        // 推进时钟
        context.setSimTime(nextEvent.getTriggerTime());

        // 记录流水日志
        EventLogEntryDto logEntry = new EventLogEntryDto();
        logEntry.setSimTime(nextEvent.getTriggerTime());
        logEntry.setType(nextEvent.getType());
        logEntry.setEventId(nextEvent.getEventId());
        logEntry.setParentEventId(nextEvent.getParentEventId());
        logEntry.setSubjects(nextEvent.getSubjects());
        eventLog.append(logEntry);

        // 分发执行
        SimEventHandler handler = handlerMap.get(nextEvent.getType());
        if (handler != null) {
            try {
                handler.handle(nextEvent, this, context);
            } catch (Exception e) {
                String errorMsg = String.format("事件处理异常，触发熔断: Type=%s, Id=%s, Time=%d, Error=%s",
                        nextEvent.getType(), nextEvent.getEventId(), nextEvent.getTriggerTime(), e.getMessage());

                errorLog.recordEventProcessingError(nextEvent.getEventId(), nextEvent.getType(),
                        nextEvent.getTriggerTime(), errorMsg, e, true);
                log.error(errorMsg, e);

                triggerGlobalSuspend(nextEvent);
            }
        } else {
            log.warn("未找到事件类型 {} 的处理器，忽略执行。", nextEvent.getType());
        }
    }

    /**
     * 连续运行直到指定时间
     * 防止同一时间戳产生无限微小事件
     */
    public synchronized void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;
        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp();
        long currentTime = context.getSimTime();

        while (!eventQueue.isEmpty()) {
            if (globalSuspended) {
                log.warn("runUntil 中止：引擎已暂停");
                break;
            }

            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) {
                // 已推进到目标时间 更新时钟并退出
                if (!globalSuspended && targetSimTime > currentTime) {
                    context.setSimTime(targetSimTime);
                }
                break;
            }

            // 死循环检测
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > maxEventsPerTimestamp) {
                    String errorMsg = String.format("检测到仿真死循环: 时间戳 %d 堆积超过 %d 个零耗时事件",
                            lastProcessedTime, maxEventsPerTimestamp);

                    errorLog.recordDeadLoop(lastProcessedTime, sameTimeEventCount, maxEventsPerTimestamp);
                    triggerGlobalSuspend(nextEvent);
                    throw new SimulationDeadLoopException(errorMsg, lastProcessedTime, sameTimeEventCount);
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 1;
            }

            eventQueue.poll();
            processEvent(nextEvent);
            currentTime = context.getSimTime();
        }

        // 空跑或队列处理完后 确保时钟对齐到目标时间
        if (!globalSuspended && eventQueue.isEmpty() && targetSimTime > currentTime) {
            context.setSimTime(targetSimTime);
        }
    }

    // 事件处理器

    /**
     * 电子围栏控制
     */
    @Component
    public static class FenceControlHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.FENCE_CONTROL; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = event.getPrimarySubject("FENCE");
            Fence fence = context.getFenceMap().get(fenceId);
            if (fence != null) {
                Object data = event.getData();
                String status = null;
                // 兼容枚举和字符串输入
                if (data instanceof FenceStateEnum) {
                    status = ((FenceStateEnum) data).getCode();
                } else if (data instanceof String) {
                    status = (String) data;
                }

                if (status != null) {
                    fence.setStatus(status);
                    // 围栏打开时，清空积压的等待队列
                    if (FenceStateEnum.PASSABLE.getCode().equals(status)) {
                        fence.getWaitingTrucks().clear();
                    }
                    log.info("围栏 {} 状态更新: {}", fenceId, status);
                }
            }
        }
    }

    /**
     * 系统下发任务 -> 设备接收任务
     */
    @Component
    public static class CmdAssignTaskHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_ASSIGN_TASK; }
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
     * 设备接收任务
     */
    @Component
    public static class CmdTaskAckHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_TASK_ACK; }
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("DEVICE");
            BaseDevice device = context.getDevice(deviceId);
            if (device == null) return;

            Map<String, Object> payload = (Map<String, Object>) event.getData();
            device.setCurrWiRefNo((String) payload.get("wiRefNo"));
        }
    }

    /**
     * 集卡移动指令
     */
    @Component
    public static class CmdMoveHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_MOVE; }
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            BaseDevice device = context.getDevice(truckId);
            if (device == null) throw new BusinessException("移动指令异常: 设备不存在");

            if (device.getState() == DeviceStateEnum.WORKING || device.getState() == DeviceStateEnum.CHARGING) {
                throw new BusinessException(String.format("设备 %s 状态(%s)繁忙，无法执行移动", device.getId(), device.getState()));
            }

            Map<String, Object> payload = (Map<String, Object>) event.getData();
            Double speed = (Double) payload.get("speed");
            if (speed == null || speed <= 0) {
                throw new BusinessException("移动参数非法: speed=" + speed);
            }

            Point target = (Point) payload.get("target");
            device.setSpeed(speed);
            device.setCurrentTargetPos(target);

            SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
            moveStart.addSubject("TRUCK", truckId);
        }
    }

    /**
     * 向下一个路径点移动
     * 调用设备自身逻辑计算路径和预计到达时间
     */
    @Component
    public static class MoveStartHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.MOVE_START; }
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
     * 到达目的地
     */
    @Component
    public static class ArrivalHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.ARRIVAL; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            // 兼容多种 Key 获取设备ID
            String id = event.getPrimarySubject("TRUCK");
            if (id == null) id = event.getPrimarySubject("CRANE");
            if (id == null) id = event.getPrimarySubject("DEVICE");

            BaseDevice d = (id != null) ? context.getDevice(id) : null;

            if (d != null) {
                d.onArrival((Point)event.getData(), context.getSimTime(), engine, event.getEventId());

                // 调度 Idle 事件，并透传上下文 Subjects，防止下游丢失设备信息
                SimEvent reportEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                if (event.getSubjects() != null) {
                    event.getSubjects().forEach(reportEvent::addSubject);
                }
                reportEvent.addSubject("DEVICE", d.getId());
            } else {
                log.warn("ARRIVAL 事件处理失败: 无法识别设备ID. EventId={}", event.getEventId());
            }
        }
    }

    /**
     * 设备空闲上报 (REPORT_IDLE)
     */
    @Component
    public static class ReportIdleHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.REPORT_IDLE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            if (event.getSubjects() == null || event.getSubjects().isEmpty()) {
                // 忽略测试产生的无效空事件
                return;
            }

            String id = event.getPrimarySubject("TRUCK");
            if (id == null) id = event.getPrimarySubject("CRANE");
            if (id == null) id = event.getPrimarySubject("DEVICE");
            if (id == null) {
                id = event.getSubjects().values().iterator().next();
            }

            if (id != null) {
                log.info("[Time: {}] 设备 {} 动作结束，进入空闲状态", context.getSimTime(), id);
            } else {
                log.warn("[Time: {}] REPORT_IDLE 无法识别设备ID", context.getSimTime());
            }
        }
    }

    /**
     * 充电指令
     */
    @Component
    public static class CmdChargeHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_CHARGE; }
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

            // 校验位置对准
            Point truckPos = new Point(truck.getPosX(), truck.getPosY());
            Point stationPos = new Point(station.getPosX(), station.getPosY());
            if (GisUtil.getDistance(truckPos, stationPos) > context.getPhysicsConfig().getChargeAlignThreshold()) {
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
     * 开始充电 -> 计算耗时 -> 调度完成
     */
    @Component
    public static class ChargingStartHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CHARGING_START; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            String stationId = event.getPrimarySubject("STATION");
            ChargingStation station = context.getChargingStationMap().get(stationId);

            if (truck != null && station != null) {
                Double rate = station.getChargeRate();
                if (rate == null || rate <= 0) rate = 10.0;

                truck.setState(DeviceStateEnum.CHARGING);
                double currentPower = truck.getPowerLevel() != null ? truck.getPowerLevel() : 0;
                long chargeDurationMS = (long) (((Truck.MAX_POWER_LEVEL - currentPower) / rate) * 1000);
                if (chargeDurationMS <= 0) chargeDurationMS = 1;

                SimEvent fullEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                fullEvent.addSubject("TRUCK", truckId);
                fullEvent.addSubject("STATION", stationId);
            }
        }
    }

    /**
     * 充电完成
     */
    @Component
    public static class ChargeFullHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CHARGE_FULL; }
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

                SimEvent idleEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                idleEvent.addSubject("TRUCK", truckId);
                idleEvent.addSubject("DEVICE", truckId);
            }

            if (station != null) {
                station.setTruckId(null);
                station.setStatus(DeviceStateEnum.IDLE.getCode());
            }
        }
    }

    /**
     * 围栏指令兼容处理
     */
    @Component
    public static class CmdFenceHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_FENCE_TOGGLE; }
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = null;
            String status = null;

            // 提取参数
            if (event.getData() instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) event.getData();
                fenceId = (String) map.get("nodeId");
                Object statusObj = map.get("status");
                if (statusObj != null) {
                    status = String.valueOf(statusObj);
                }
            } else if (event.getData() instanceof String) {
                status = (String) event.getData();
            } else if (event.getData() instanceof FenceStateEnum) {
                status = ((FenceStateEnum) event.getData()).getCode();
            }

            if (fenceId != null) {
                Fence f = context.getFenceMap().get(fenceId);
                if (f != null && status != null) f.setStatus(status);
            } else if (status != null) {
                for(Fence f : context.getFenceMap().values()) f.setStatus(status);
            }
        }
    }

    /**
     * 吊具移动指令
     */
    @Component
    public static class CmdCraneMoveHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_CRANE_MOVE; }
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String craneId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(craneId);
            if (device == null) return;

            if (device.getState() == DeviceStateEnum.WORKING) {
                throw new BusinessException(String.format("设备 %s 正在作业中，无法执行移动", craneId));
            }

            Map<String, Object> payload = (Map<String, Object>) event.getData();
            CraneMoveReq req = (CraneMoveReq) payload.get("req");
            Double speed = (Double) payload.get("speed");
            if (speed == null || speed <= 0) {
                throw new BusinessException("speed 参数无效");
            }
            double distance = req.getDistance() != null ? req.getDistance() : 0;

            // 计算目标坐标
            double posX = device.getPosX() != null ? device.getPosX() : 0;
            double posY = device.getPosY() != null ? device.getPosY() : 0;
            Point targetPoint;
            if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
                targetPoint = new Point(posX + distance, posY);
            } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
                targetPoint = new Point(posX, posY + distance);
            } else {
                targetPoint = new Point(posX + distance, posY);
            }

            device.setSpeed(speed);
            device.setCurrentTargetPos(targetPoint);

            SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
            moveStart.addSubject("CRANE", craneId);
        }
    }

    /**
     * 吊具操作通用处理 (Pick/Set)
     */
    @Component
    public static class CmdCraneOpHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_CRANE_OP; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            CraneOperationReq req = (CraneOperationReq) event.getData();
            String craneId = req.getCraneId();
            BaseDevice device = context.getDevice(craneId);

            if (device != null) {
                // 移动中禁止作业
                if (device.getState() == DeviceStateEnum.MOVING) {
                    throw new BusinessException(String.format("逻辑错误：设备 %s 移动中无法执行抓/放箱！", craneId));
                }
                device.setState(DeviceStateEnum.WORKING);
            }

            SimEvent opEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + req.getDurationMS(), req.getAction(), null);
            opEvent.addSubject("CRANE", craneId);
        }
    }

    /**
     * 抓箱完成 (FETCH_DONE)
     * 更新箱子位置：地面/集卡 -> 设备
     */
    @Component
    public static class FetchDoneHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.FETCH_DONE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                // 作业完成，释放状态
                device.setState(DeviceStateEnum.IDLE);

                String wiRefNo = device.getCurrWiRefNo();
                if (wiRefNo == null) {
                    log.warn("事件[FETCH_DONE]: 设备 [{}] 未绑定作业指令，跳过。", deviceId);
                    return;
                }
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) {
                    BizTypeEnum bizType = wi.getMoveKind();
                    boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
                    boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());
                    boolean allowedFetch = isFetchDevice;

                    // 特例：如果是放箱设备，且集卡已到位，也允许抓取（中转场景）
                    if (!allowedFetch && isPutDevice && wi.getCarryCheId() != null && wi.getContainerId() != null) {
                        Container c = context.getContainerMap().get(wi.getContainerId());
                        if (c != null && wi.getCarryCheId().equals(c.getCurrentPos())) {
                            allowedFetch = true;
                        }
                    }

                    if (!allowedFetch) {
                        if (common.util.BizTypeUtil.requiresFetchDevice(bizType)) {
                            if (!isPutDevice || wi.getCarryCheId() == null) {
                                log.warn("事件[FETCH_DONE]: 设备 [{}] 与指令 [{}] 抓箱设备不匹配", deviceId, wiRefNo);
                            }
                        }
                        return;
                    }

                    // 物理距离校验：防止隔空抓箱
                    if (wi.getCarryCheId() != null) {
                        Container c = context.getContainerMap().get(wi.getContainerId());
                        if (c != null && c.getCurrentPos().equals(wi.getCarryCheId())) {
                            BaseDevice truck = context.getDevice(wi.getCarryCheId());
                            if (truck != null) {
                                double dist = GisUtil.getDistance(
                                        new Point(device.getPosX(), device.getPosY()),
                                        new Point(truck.getPosX(), truck.getPosY())
                                );
                                if (dist > 5.0) {
                                    log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m)，无法抓箱。指令: {}",
                                            deviceId, truck.getId(), dist, wiRefNo);
                                    return;
                                }
                            }
                        }
                    }

                    // 更新位置
                    if (wi.getContainerId() != null) {
                        Container container = context.getContainerMap().get(wi.getContainerId());
                        if (container != null) {
                            String oldPos = container.getCurrentPos();
                            container.setCurrentPos(device.getId());
                            log.info("[Time: {}] [FETCH_DONE] 设备 [{}] 抓取箱 [{}]。位置: {} -> {}",
                                    context.getSimTime(), deviceId, container.getContainerId(), oldPos, device.getId());
                        } else {
                            log.warn("[FETCH_DONE] 箱号 {} 未找到", wi.getContainerId());
                        }
                    }
                }
            }
        }
    }

    /**
     * 放箱完成 (PUT_DONE)
     * 更新箱子位置：设备 -> 目标位/集卡
     */
    @Component
    public static class PutDoneHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.PUT_DONE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                device.setState(DeviceStateEnum.IDLE);

                String wiRefNo = device.getCurrWiRefNo();
                if (wiRefNo == null) {
                    log.warn("事件[PUT_DONE]: 设备 [{}] 无指令", deviceId);
                    return;
                }
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) {
                    BizTypeEnum bizType = wi.getMoveKind();
                    boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
                    boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());

                    // 1. 终点放箱
                    if (isPutDevice) {
                        if (wi.getContainerId() != null) {
                            Container container = context.getContainerMap().get(wi.getContainerId());
                            if (container != null && wi.getToPos() != null) {
                                container.setCurrentPos(wi.getToPos());
                                log.info("[Time: {}] [PUT_DONE] 设备 [{}] 放箱 [{}] 至最终位置 [{}]",
                                        context.getSimTime(), deviceId, container.getContainerId(), wi.getToPos());
                            }
                        }
                        SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
                        completeEvent.addSubject("WI", device.getCurrWiRefNo());

                        // 2. 中转放箱 (放到集卡上)
                    } else if (isFetchDevice && wi.getCarryCheId() != null) {
                        BaseDevice truck = context.getDevice(wi.getCarryCheId());

                        // 物理距离校验
                        if (truck != null) {
                            double dist = GisUtil.getDistance(
                                    new Point(device.getPosX(), device.getPosY()),
                                    new Point(truck.getPosX(), truck.getPosY())
                            );
                            if (dist > 5.0) {
                                log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m)，无法放箱。指令: {}",
                                        deviceId, truck.getId(), dist, wiRefNo);
                                return;
                            }
                        }

                        if (wi.getContainerId() != null) {
                            Container container = context.getContainerMap().get(wi.getContainerId());
                            if (container != null) {
                                String oldPos = container.getCurrentPos();
                                container.setCurrentPos(wi.getCarryCheId());
                                log.info("[Time: {}] [PUT_DONE] 设备 [{}] 放箱 [{}] 至集卡 [{}] (中转). 从 [{}] 变更为 [{}]",
                                        context.getSimTime(), deviceId, container.getContainerId(), wi.getCarryCheId(), oldPos, wi.getCarryCheId());
                            }
                        }
                    } else if (common.util.BizTypeUtil.requiresPutDevice(bizType)) {
                        log.warn("事件[PUT_DONE]: 设备 [{}] 既不是抓箱也不是放箱设备，指令: {}", deviceId, wiRefNo);
                    }
                }
            }
        }
    }

    /**
     * 指令完结
     */
    @Component
    public static class WiCompleteHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.WI_COMPLETE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String wiRefNo = event.getPrimarySubject("WI");
            WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
            if (doneWi != null) {
                doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
            }
        }
    }
}