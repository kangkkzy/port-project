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
 * 核心仿真引擎类
 * 负责维护仿真时钟、管理事件优先队列、调度事件处理以及处理全局异常熔断逻辑。
 * 遵循“单一时钟、单一事件链”原则，一旦发生异常立即全局暂停。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {

    // 依赖注入
    private final PhysicsConfig physicsConfig; // 物理配置参数
    private final SimulationEventLog eventLog; // 成功事件日志记录器
    private final SimulationErrorLog errorLog; // 错误日志记录器
    private final GlobalContext context = GlobalContext.getInstance(); // 全局上下文（存储所有实体状态）

    // 核心数据结构

    /**
     * 事件优先队列
     * 核心驱动源，按事件触发时间（triggerTime）排序。
     * 仿真引擎不断从队列头部取出时间最早的事件执行。
     */
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();

    /**
     * 事件处理器映射表
     * Key: 事件类型枚举, Value: 对应的处理器实例
     */
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);

    /** Spring 自动注入 */
    private final List<SimEventHandler> handlerBeans;

    /**
     * 事件ID映射表
     * 用于快速查找事件以便取消操作。
     */
    private final Map<String, SimEvent> eventIdMap = new ConcurrentHashMap<>();

    //  全局控制标志

    /**
     * 全局暂停
     * 只要出现一个未捕获异常，该标志立即置为 true。
     * 此时引擎将拒绝执行任何新事件，直到 reset() 被调用。
     */
    private volatile boolean globalSuspended = false;

    // 以下集合仅用于日志记录和状态查询 帮助定位是哪个业务或事件导致了暂停
    private final java.util.Set<common.consts.BizTypeEnum> suspendedBizTypes = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> suspendedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * 初始化方法
     * 将 Spring 容器中的 Handler 注册到 map 中
     */
    @Override
    public void afterPropertiesSet() {
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    /**
     * 调度新事件
     * * @param parentEventId 父事件ID（用于溯源）
     * @param triggerTime   触发时间（仿真时间戳）
     * @param type          事件类型
     * @param data          事件携带的数据负载
     * @return 创建并入队的事件对象
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        eventIdMap.put(event.getEventId(), event);
        return event;
    }

    /**
     * 取消指定事件
     * * @param eventId 要取消的事件ID
     * @return 如果事件存在且标记成功返回 true，否则 false
     */
    public boolean cancelEvent(String eventId) {
        SimEvent event = eventIdMap.get(eventId);
        if (event == null) {
            return false;
        }
        event.setCancelled(true); // 删除标记 实际执行时会跳过
        return true;
    }

    /**
     * 辅助 尝试从报错事件中提取业务类型
     * 仅用于错误日志记录 方便排查是哪个作业指令出的问题
     */
    private common.consts.BizTypeEnum getBizTypeFromEvent(SimEvent event) {
        if (event == null) return null;

        //  尝试从 payload (Map) 中获取 wiRefNo
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String wiRefNo = (String) payload.get("wiRefNo");
            if (wiRefNo != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) return wi.getMoveKind();
            }
        }
        //  尝试从 Subject (WI) 获取
        String wiRefNoFromSubject = event.getPrimarySubject("WI");
        if (wiRefNoFromSubject != null) {
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNoFromSubject);
            if (wi != null) return wi.getMoveKind();
        }
        //  尝试从关联的设备反查当前指令
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
        //  递归查找父事件链
        String parentEventId = event.getParentEventId();
        if (parentEventId != null) {
            SimEvent parentEvent = eventIdMap.get(parentEventId);
            if (parentEvent != null) return getBizTypeFromEvent(parentEvent);
        }
        return null;
    }

    /**
     * 检查是否处于全局暂停状态
     */
    private boolean isGlobalSuspended() {
        return globalSuspended;
    }

    /**
     * 触发全局暂停
     * 当发生未捕获异常时调用。记录错误上下文，并锁死引擎。
     */
    private void triggerGlobalSuspend(SimEvent event) {
        this.globalSuspended = true;

        if (event != null) {
            log.error("！！！仿真引擎触发全局暂停！！！错误源头事件: Id={}, Type={}", event.getEventId(), event.getType());
            // 记录元数据供外部查询接口使用
            suspendedEventIds.add(event.getEventId());
            common.consts.BizTypeEnum bizType = getBizTypeFromEvent(event);
            if (bizType != null) {
                suspendedBizTypes.add(bizType);
            }
        } else {
            log.error("！！！仿真引擎触发全局暂停！！！（未知事件源）");
        }
    }

    public java.util.Set<common.consts.BizTypeEnum> getSuspendedBizTypes() {
        return new java.util.HashSet<>(suspendedBizTypes);
    }

    public java.util.Set<String> getSuspendedEventIds() {
        return new java.util.HashSet<>(suspendedEventIds);
    }

    /**
     * 重置仿真
     * 清空队列、重置暂停标志、清除错误记录。
     * 通常由用户在前端点击“重置”或“重新开始”触发。
     */
    public synchronized void reset() {
        eventQueue.clear(); // 【关键】必须清空队列
        eventIdMap.clear();
        suspendedBizTypes.clear();
        suspendedEventIds.clear();
        globalSuspended = false; // 解除锁定
        log.info("仿真引擎已重置，全局暂停状态已解除。");
    }

    /**
     * 单步执行下一个事件
     * 1. 检查全局暂停标志。
     * 2. 取出队首事件。
     * 3. 调用 processEvent 执行。
     * * @return 执行的事件对象，如果队列为空或已暂停则返回 null
     */
    public synchronized SimEvent stepNextEvent() {
        // 安全检查：如果已暂停，拒绝执行
        if (globalSuspended) {
            log.warn("拒绝执行：仿真引擎处于全局暂停状态，请检查错误日志并重置。");
            return null;
        }

        if (eventQueue.isEmpty()) {
            return null;
        }

        SimEvent nextEvent = eventQueue.poll();
        if (nextEvent == null) {
            return null;
        }

        // 处理核心逻辑
        processEvent(nextEvent);

        return nextEvent;
    }

    /**
     * 内部核心方法：处理单个事件
     * 包含完整的 try-catch 逻辑 确保任何异常都会触发全局暂停。
     */
    private void processEvent(SimEvent nextEvent) {
        //   检查事件是否已被取消
        if (nextEvent.isCancelled()) {
            eventIdMap.remove(nextEvent.getEventId());
            return;
        }

        //   检查事件是否暂停
        if (globalSuspended) {
            return;
        }

        //  从映射中移除当前事件
        eventIdMap.remove(nextEvent.getEventId());

        //  更新全局仿真时钟
        context.setSimTime(nextEvent.getTriggerTime());

        //  记录历史日志
        EventLogEntryDto logEntry = new EventLogEntryDto();
        logEntry.setSimTime(nextEvent.getTriggerTime());
        logEntry.setType(nextEvent.getType());
        logEntry.setEventId(nextEvent.getEventId());
        logEntry.setParentEventId(nextEvent.getParentEventId());
        logEntry.setSubjects(nextEvent.getSubjects());
        eventLog.append(logEntry);

        //  分发到具体 Handler 执行
        SimEventHandler handler = handlerMap.get(nextEvent.getType());
        if (handler != null) {
            try {
                handler.handle(nextEvent, this, context);
            } catch (Exception e) {
                //  异常熔断逻辑
                String errorMsg = String.format("事件处理异常，引擎全局暂停: Type=%s, Id=%s, Time=%d",
                        nextEvent.getType(), nextEvent.getEventId(), nextEvent.getTriggerTime());

                // 记录错误日志
                errorLog.recordEventProcessingError(nextEvent.getEventId(), nextEvent.getType(),
                        nextEvent.getTriggerTime(), errorMsg, e, true);
                log.error(errorMsg, e);

                // 立即触发全局暂停
                triggerGlobalSuspend(nextEvent);
            }
        } else {
            log.warn("事件类型 {} 没有对应的处理器，事件将被忽略", nextEvent.getType());
        }
    }

    /**
     * 连续运行直到指定时间
     * 包含死循环检测机制（同一时间戳事件过多）
     * 循环中会不断检查 globalSuspended 标志
     *
     * @param targetSimTime 目标仿真时间
     */
    public synchronized void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;
        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp();

        while (!eventQueue.isEmpty()) {
            // 每次循环前检查暂停状态
            if (globalSuspended) {
                log.warn("runUntil 中止：引擎已全局暂停");
                break;
            }

            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) {
                break; // 已达到目标时间 停止
            }

            //  死循环保护机制
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > maxEventsPerTimestamp) {
                    String errorMsg = String.format("仿真死循环检测: 时间戳 %d 发生死循环（超过 %d 个零耗时事件）",
                            lastProcessedTime, maxEventsPerTimestamp);
                    errorLog.recordDeadLoopError(lastProcessedTime, sameTimeEventCount, maxEventsPerTimestamp, errorMsg);

                    // 死循环也视为严重错误 触发暂停
                    triggerGlobalSuspend(nextEvent);
                    throw new SimulationDeadLoopException(errorMsg, lastProcessedTime, sameTimeEventCount);
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 1;
            }

            // 取出并执行
            eventQueue.poll();
            processEvent(nextEvent);
        }

        // 只有正常结束才更新时钟到目标时间 避免界面时间跳变
        if (!globalSuspended) {
            context.setSimTime(targetSimTime);
        }
    }
// 内部handler类 实现接口 处理特定类型的事件

    /**
     * 电子围栏控制处理器
     * 处理 FENCE_CONTROL 事件，开启或关闭围栏。
     */
    @org.springframework.stereotype.Component
    public static class FenceControlHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.FENCE_CONTROL; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = event.getPrimarySubject("FENCE");
            Fence fence = context.getFenceMap().get(fenceId);
            if (fence != null) {
                FenceStateEnum status = (FenceStateEnum) event.getData();
                fence.setStatus(status.getCode());
                // 如果围栏变为通过状态，清空等待队列
                if (FenceStateEnum.PASSABLE.equals(status)) {
                    fence.getWaitingTrucks().clear();
                }
                log.info("栅栏 {} 状态已更新为: {}", fenceId, status.getDesc());
            }
        }
    }

    /**
     * 指令下发处理器
     * 收到 CMD_ASSIGN_TASK 后，生成 ACK 确认事件。
     */
    @org.springframework.stereotype.Component
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
            // 调度 ACK 事件
            SimEvent ackEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CMD_TASK_ACK, payload);
            ackEvent.addSubject("DEVICE", deviceId);
        }
    }

    /**
     * 任务确认处理器
     * 设备确认接收任务 绑定指令号 状态置为 WORKING。
     */
    @org.springframework.stereotype.Component
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
            if (device.getType() == DeviceTypeEnum.ASC || device.getType() == DeviceTypeEnum.QC) {
                device.setState(DeviceStateEnum.WORKING);
            }
        }
    }

    /**
     * 车辆移动指令处理器
     * 设置速度和目标 并调度 MOVE_START 事件
     */
    @org.springframework.stereotype.Component
    public static class CmdMoveHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_MOVE; }
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
            // 立即开始移动
            SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
            moveStart.addSubject("TRUCK", truckId);
        }
    }

    /**
     * 移动开始处理器
     * 调用设备的 onMoveStart 方法（计算路径、预计到达时间等）。
     */
    @org.springframework.stereotype.Component
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
     * 到达处理器
     * 设备到达目标点 更新位置 并上报 IDLE 状态
     */
    @org.springframework.stereotype.Component
    public static class ArrivalHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.ARRIVAL; }
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
     * 空闲上报处理器
     * 仅记录日志 表明设备已完成动作
     */
    @org.springframework.stereotype.Component
    public static class ReportIdleHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.REPORT_IDLE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String id = event.getPrimarySubject("TRUCK");
            if(id==null) id = event.getPrimarySubject("CRANE");
            log.info("设备 {} 动作结束，当前空闲", id);
        }
    }

    /**
     * 充电指令处理器
     * 校验位置对准 绑定充电桩 调度 CHARGING_START
     */
    @org.springframework.stereotype.Component
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

            // 距离校验
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
     * 开始充电处理器
     * 计算充电所需时间，调度 CHARGE_FULL 事件。
     */
    @org.springframework.stereotype.Component
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
                double powerNeeded = Truck.MAX_POWER_LEVEL - currentPower;
                long chargeDurationMS = (long) ((powerNeeded / rate) * 1000);
                if (chargeDurationMS <= 0) chargeDurationMS = 1;
                // 安排充电完成事件
                SimEvent fullEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                fullEvent.addSubject("TRUCK", truckId);
                fullEvent.addSubject("STATION", stationId);
            }
        }
    }

    /**
     * 充电完成处理器
     * 恢复电量，解绑充电桩，设备恢复 IDLE。
     */
    @org.springframework.stereotype.Component
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
     * 围栏命令转换处理器
     * 将 CMD_FENCE_TOGGLE 转换为 FENCE_CONTROL 事件
     * 修复: 兼容 Map 类型参数，避免空指针异常
     */
    @org.springframework.stereotype.Component
    public static class CmdFenceHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_FENCE_TOGGLE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            // 修复: 兼容处理
            String fenceId = null;
            Integer status = null;

            if (event.getData() instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) event.getData();
                fenceId = (String) map.get("nodeId");
                status = (Integer) map.get("status");
            } else if (event.getData() instanceof Integer) {
                status = (Integer) event.getData();
            } else if (event.getData() instanceof FenceStateEnum) {
                status = ((FenceStateEnum) event.getData()).getCode();
            }

            if (fenceId != null) {
                Fence f = context.getFenceMap().get(fenceId);
                if (f != null && status != null) f.setStatus(status);
            } else if (status != null) {
                // 没指定ID就全改
                for(Fence f : context.getFenceMap().values()) f.setStatus(status);
            }
        }
    }

    /**
     * 吊具移动处理器
     * 计算移动时间（水平/垂直），调度 ARRIVAL 事件
     */
    @org.springframework.stereotype.Component
    public static class CmdCraneMoveHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_CRANE_MOVE; }
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
            double distance = req.getDistance() != null ? req.getDistance() : 0;
            long travelTimeMS = (long) ((distance / speed) * 1000);

            device.setState(req.getMoveType());
            double posX = device.getPosX() != null ? device.getPosX() : 0;
            double posY = device.getPosY() != null ? device.getPosY() : 0;

            // 计算新的坐标点
            Point targetPoint;
            if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
                targetPoint = new Point(posX + distance, posY);
            } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
                targetPoint = new Point(posX, posY + distance);
            } else {
                targetPoint = new Point(posX + distance, posY);
            }

            SimEvent arrEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, targetPoint);
            arrEvent.addSubject("CRANE", device.getId());
        }
    }

    /**
     * 吊具通用操作处理器
     * 处理如 抓箱(PICKUP)、放箱(SETDOWN) 等操作 计算耗时后调度结果事件
     */
    @org.springframework.stereotype.Component
    public static class CmdCraneOpHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.CMD_CRANE_OP; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            CraneOperationReq req = (CraneOperationReq) event.getData();
            // 调度操作完成事件（例如 FETCH_DONE 或 PUT_DONE）
            SimEvent opEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + req.getDurationMS(), req.getAction(), null);
            opEvent.addSubject("CRANE", req.getCraneId());
        }
    }

    /**
     * 抓箱完成处理器
     * 核心业务逻辑：校验设备是否匹配指令，更新集装箱位置（从 地面/集卡 -> 设备）
     */
    @org.springframework.stereotype.Component
    public static class FetchDoneHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.FETCH_DONE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                String wiRefNo = device.getCurrWiRefNo();
                if (wiRefNo == null) {
                    log.warn("事件[FETCH_DONE]: 设备 [{}] 未绑定作业指令，跳过处理", deviceId);
                    return;
                }
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) {
                    BizTypeEnum bizType = wi.getMoveKind();
                    // 校验当前设备是否是该指令指定的抓箱设备
                    boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
                    boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());
                    boolean allowedFetch = isFetchDevice;

                    // 特殊情况：如果是放箱设备 且集卡已在集装箱位置 也允许抓取
                    if (!allowedFetch && isPutDevice && wi.getCarryCheId() != null && wi.getContainerId() != null) {
                        Container c = context.getContainerMap().get(wi.getContainerId());
                        if (c != null && wi.getCarryCheId().equals(c.getCurrentPos())) {
                            allowedFetch = true;
                        }
                    }

                    if (!allowedFetch) {
                        // 如果业务类型需要抓箱但当前设备不匹配 记录错误
                        if (bizType != null && common.util.BizTypeUtil.requiresFetchDevice(bizType)) {
                            if (!isPutDevice || wi.getCarryCheId() == null) {
                                log.warn("事件[FETCH_DONE]: 设备 [{}] 不是指令 [{}] 的抓箱设备", deviceId, wiRefNo);
                            }
                        }
                        if (!allowedFetch) return;
                    }

                    // 移动集装箱 位置变为当前设备ID（表示箱子在设备上）
                    if (wi.getContainerId() != null) {
                        Container container = context.getContainerMap().get(wi.getContainerId());
                        if (container != null) {
                            String oldPos = container.getCurrentPos();
                            container.setCurrentPos(device.getId());
                            log.info("事件[FETCH_DONE]: 设备 [{}] 完成抓箱。集装箱 [{}] 位置已从 [{}] 更新为设备上的 [{}]",
                                    deviceId, container.getContainerId(), oldPos, device.getId());
                        } else {
                            log.warn("事件[FETCH_DONE]: 指令 [{}] 引用的集装箱 [{}] 在系统中未找到", wiRefNo, wi.getContainerId());
                        }
                    } else {
                        log.warn("事件[FETCH_DONE]: 设备 [{}] 完成抓箱动作，但指令 [{}] 无箱号", deviceId, wiRefNo);
                    }
                } else {
                    log.warn("事件[FETCH_DONE]: 设备 [{}] 完成抓箱动作，但未绑定有效指令", deviceId);
                }
            }
        }
    }

    /**
     * 放箱完成处理器
     * 核心业务逻辑：更新集装箱位置（设备 -> 目标位置/集卡），完成指令。
     */
    @org.springframework.stereotype.Component
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
                    log.warn("事件[PUT_DONE]: 设备 [{}] 未绑定作业指令，跳过处理", deviceId);
                    return;
                }
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) {
                    BizTypeEnum bizType = wi.getMoveKind();
                    boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
                    boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());

                    // 情况1：当前是指令的放箱设备（结束流程）
                    if (isPutDevice) {
                        if (wi.getContainerId() != null) {
                            Container container = context.getContainerMap().get(wi.getContainerId());
                            if (container != null && wi.getToPos() != null) {
                                container.setCurrentPos(wi.getToPos());
                                log.info("事件[PUT_DONE]: 设备 [{}] 完成放箱。集装箱 [{}] 位置已更新为最终位置 [{}]",
                                        deviceId, container.getContainerId(), wi.getToPos());
                            }
                        }
                        // 触发指令完成事件
                        SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
                        completeEvent.addSubject("WI", device.getCurrWiRefNo());

                        // 情况2：当前是抓箱设备，且要把箱子放到集卡上（中转流程）
                    } else if (isFetchDevice && wi.getCarryCheId() != null) {
                        if (wi.getContainerId() != null) {
                            Container container = context.getContainerMap().get(wi.getContainerId());
                            if (container != null) {
                                String oldPos = container.getCurrentPos();
                                container.setCurrentPos(wi.getCarryCheId());
                                log.info("事件[PUT_DONE]: 设备 [{}] 完成放箱到集卡。集装箱 [{}] 位置已从 [{}] 更新为 [{}]",
                                        deviceId, container.getContainerId(), oldPos, wi.getCarryCheId());
                            }
                        }
                    } else if (bizType != null && common.util.BizTypeUtil.requiresPutDevice(bizType)) {
                        log.warn("事件[PUT_DONE]: 设备 [{}] 不是指令 [{}] 的抓箱/放箱设备", deviceId, wiRefNo);
                    }
                }
            }
        }
    }

    /**
     * 指令完成处理器
     * 标记 WorkInstruction 状态为 COMPLETED。
     */
    @org.springframework.stereotype.Component
    public static class WiCompleteHandler implements SimEventHandler {
        @Override
        public EventTypeEnum getType() { return EventTypeEnum.WI_COMPLETE; }
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String wiRefNo = event.getPrimarySubject("WI");
            WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
            if (doneWi != null) doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
        }
    }
}