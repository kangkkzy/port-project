package engine;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.consts.WiStatusEnum;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.entity.*;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;

@Component
@Slf4j
public class SimulationEngine {

    private final GlobalContext context = GlobalContext.getInstance();

    //  优先队列 按发生时间自动排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    // 同一绝对时间戳下，最多允许消费 10000 个事件
    private static final int MAX_EVENTS_PER_TIMESTAMP = 10000;

    /**
     * 向未来注册一个事件 并返回事件对象以便于后续修改
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        return event;
    }

    /**
     * 取消特定事件 (用于打断充电、取消排队等)
     */
    public void cancelEvent(String eventId) {
        for (SimEvent event : eventQueue) {
            if (event.getEventId().equals(eventId)) {
                event.setCancelled(true);
                log.info("事件 {} 已被标记为取消", eventId);
                break;
            }
        }
    }

    /**
     * 外部算法驱动接口 推演仿真系统
     */
    public void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;

        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            if (nextEvent.getTriggerTime() > targetSimTime) {
                break;
            }

            eventQueue.poll();

            //  取消检查 跳过已取消的事件
            if (nextEvent.isCancelled()) {
                log.debug("跳过已取消的事件: {}", nextEvent.getEventId());
                continue;
            }

            //  防死循环机制
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > MAX_EVENTS_PER_TIMESTAMP) {
                    log.error("检测到时间戳 {} 发生死循环，强制终止当前时间步的推演", lastProcessedTime);
                    break;
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 0;
            }

            context.setSimTime(nextEvent.getTriggerTime());

            //  异常隔离 单个事件的异常不应阻塞整个仿真时钟
            try {
                handleEvent(nextEvent);
            } catch (Exception e) {
                log.error("处理事件异常! EventID: {}, Type: {}, Error: {}", nextEvent.getEventId(), nextEvent.getType(), e.getMessage(), e);
            }
        }
        context.setSimTime(targetSimTime);
    }

    /**
     * 事件消费中心
     */
    private void handleEvent(SimEvent event) {
        long now = context.getSimTime();

        // 获取多主体 确定主要操作对象
        String primaryTargetId = event.getPrimarySubject("TRUCK");
        if (primaryTargetId == null) primaryTargetId = event.getPrimarySubject("CRANE");
        if (primaryTargetId == null) primaryTargetId = event.getPrimarySubject("FENCE");

        BaseDevice device = context.getDevice(primaryTargetId);

        switch (event.getType()) {
            case MOVE_START:
                if (device != null) device.onMoveStart(now, this, event.getEventId());
                break;

            case ARRIVAL:
                if (device != null) {
                    Point reachedPoint = (Point) event.getData();
                    device.onArrival(reachedPoint, now, this, event.getEventId());

                    // 外部算法控制移动到达终点后 如果该集卡是被指派去充电的 则触发充电开始事件
                    if (device instanceof Truck && device.getWaypoints().isEmpty()) {
                        Truck truck = (Truck) device;
                        if (truck.getTargetStationId() != null) {
                            SimEvent chargeEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.CHARGING_START, null);
                            chargeEvent.addSubject("TRUCK", truck.getId());
                            chargeEvent.addSubject("STATION", truck.getTargetStationId());
                        }
                    }
                }
                break;

            // 充电事件

            case CHARGING_START:
                if (device instanceof Truck) {
                    Truck truck = (Truck) device;
                    device.setState(DeviceStateEnum.CHARGING);

                    // 获取目标充电桩
                    String stationId = event.getPrimarySubject("STATION");
                    ChargingStation station = context.getChargingStationMap().get(stationId);

                    // 计算充电耗时
                    double powerNeeded = Truck.MAX_POWER_LEVEL - truck.getPowerLevel();
                    double rate = (station != null && station.getChargeRate() != null) ? station.getChargeRate() : 0.00001;
                    long chargeDurationMS = (long) (powerNeeded / rate);

                    log.info("时间:{} 集卡:{} 到达充电桩[{}]，开始充电。当前电量:{}, 预计耗时:{}ms",
                            now, primaryTargetId, stationId, truck.getPowerLevel(), chargeDurationMS);

                    // 注册充满电事件 (这个事件ID被记录以便外部算法后续随时 cancelEvent 打断充电)
                    SimEvent chargeFullEvent = scheduleEvent(event.getEventId(), now + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                    chargeFullEvent.addSubject("TRUCK", primaryTargetId);
                    chargeFullEvent.addSubject("STATION", stationId);
                }
                break;

            case CHARGE_FULL:
                if (device instanceof Truck) {
                    Truck truck = (Truck) device;
                    truck.setPowerLevel(Truck.MAX_POWER_LEVEL);
                    truck.setNeedCharge(false);
                    truck.setState(DeviceStateEnum.IDLE);

                    // 释放充电桩
                    String stationId = event.getPrimarySubject("STATION");
                    ChargingStation station = context.getChargingStationMap().get(stationId);
                    if (station != null) {
                        station.setTruckId(null);
                        station.setStatus(DeviceStateEnum.IDLE.getCode());
                    }
                    truck.setTargetStationId(null);

                    log.info("时间:{} 集卡:{} 充电完成，上报空闲", now, primaryTargetId);
                    SimEvent idleEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.REPORT_IDLE, null);
                    idleEvent.addSubject("TRUCK", primaryTargetId);
                }
                break;

            case REPORT_IDLE:
                log.info("时间:{} 集卡:{} 上报空闲，等待外部算法分配新指令", now, primaryTargetId);
                break;

            // 作业协同事件
            case FETCH_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成抓箱", now, device.getId());
                    WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                    if (wi != null && wi.getCarryCheId() != null) {
                        log.info("协同触发: 工单:{} 集卡:{} 开始运输", wi.getWiRefNo(), wi.getCarryCheId());
                        SimEvent moveEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.MOVE_START, null);
                        moveEvent.addSubject("TRUCK", wi.getCarryCheId());
                    }
                }
                break;

            case PUT_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成放箱", now, device.getId());
                    SimEvent wiCompleteEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.WI_COMPLETE, null);
                    wiCompleteEvent.addSubject("WI", device.getCurrWiRefNo());
                }
                break;

            case WI_COMPLETE:
                String wiRefNo = event.getPrimarySubject("WI");
                WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
                if (doneWi != null) {
                    doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
                    log.info("时间:{} 工单:{} 作业结束，设备释放", now, wiRefNo);
                }
                break;

            // 栅栏控制事件
            case FENCE_CONTROL:
                Fence fence = context.getFenceMap().get(primaryTargetId);
                if (fence != null) {
                    FenceStateEnum targetStatus = (FenceStateEnum) event.getData();
                    fence.setStatus(targetStatus.getCode());
                    if (FenceStateEnum.PASSABLE.equals(targetStatus)) {
                        fence.onOpen(now, this, event.getEventId());
                    }
                }
                break;

            default:
                break;
        }
    }
}