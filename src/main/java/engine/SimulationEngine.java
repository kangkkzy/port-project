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

    // 优先队列 按发生时间自动排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    // 同一绝对时间戳下，最多允许消费 10000 个事件 (看门狗防死循环)
    private static final int MAX_EVENTS_PER_TIMESTAMP = 10000;

    /**
     * 向未来注册一个事件，并返回事件对象以便于后续修改
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

            // 1. 取消检查
            if (nextEvent.isCancelled()) {
                log.debug("跳过已取消的事件: {}", nextEvent.getEventId());
                continue;
            }

            // 2. 防死循环机制
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

            // 3. 异常隔离
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

            // 充电流转逻辑

            case CHARGING_START:
                if (device instanceof Truck) {
                    Truck truck = (Truck) device;
                    device.setState(DeviceStateEnum.CHARGING);

                    String stationId = event.getPrimarySubject("STATION");
                    ChargingStation station = context.getChargingStationMap().get(stationId);

                    double powerNeeded = Truck.MAX_POWER_LEVEL - truck.getPowerLevel();
                    double rate = (station != null && station.getChargeRate() != null) ? station.getChargeRate() : 0.00001;
                    long chargeDurationMS = (long) (powerNeeded / rate);

                    log.info("时间:{} 集卡:{} 开始充电。预计耗时:{}ms", now, primaryTargetId, chargeDurationMS);

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
                // 集卡空闲：等待外部算法做任何决策 (接新单、外集卡离港、内集卡回车库等)
                log.info("时间:{} 集卡:{} 上报空闲，由外部算法接管决策", now, primaryTargetId);
                break;

            // ✅ 彻底业务解耦的 纯物理协同逻辑

            case FETCH_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成抓起动作 (集装箱离开原位置)", now, device.getId());
                    WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                    if (wi != null) {
                        // 场景：如果当前抓箱设备是“终点大机”(PutChe)，说明集装箱必定是从集卡上抓起来的！
                        // 无论是内集卡卸船/中转，还是外集卡进场(RECV)，集卡此时都空了！
                        if (device.getId().equals(wi.getPutCheId()) && wi.getCarryCheId() != null) {
                            log.info("物理协同: 集卡 {} 已卸空，释放交由外部算法决策去向", wi.getCarryCheId());
                            // 触发 REPORT_IDLE：外部算法接收到空闲信号后，自行决定是接新单还是空车离港
                            SimEvent idleEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.REPORT_IDLE, null);
                            idleEvent.addSubject("TRUCK", wi.getCarryCheId());
                        }
                    }
                }
                break;

            case PUT_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成放置动作 (集装箱落位)", now, device.getId());
                    WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                    if (wi != null) {

                        // 场景A：【起点大机】完成了放箱动作 (此时箱子一定放到了集卡上)
                        if (device.getId().equals(wi.getFetchCheId()) && wi.getCarryCheId() != null) {

                            if (wi.getPutCheId() != null) {
                                // A.1：有下游起重机 (如装船、卸船、中转)。集卡必须运过去。
                                log.info("物理协同: 集卡 {} 装载完成，有下游设备[{}]，触发移动", wi.getCarryCheId(), wi.getPutCheId());
                                SimEvent moveEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.MOVE_START, null);
                                moveEvent.addSubject("TRUCK", wi.getCarryCheId());
                            } else {
                                // A.2：无下游起重机 (如提箱出港 DLVR)。放到集卡上港口作业就结束了。
                                log.info("物理协同: 集卡 {} 装载完成且无下游设备，释放交由外部算法处理离港", wi.getCarryCheId());
                                // 触发 REPORT_IDLE：外部算法接收到外集卡有货且空闲，直接调用 moveDevice 指令将其调离出港口
                                SimEvent idleEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.REPORT_IDLE, null);
                                idleEvent.addSubject("TRUCK", wi.getCarryCheId());

                                // 既然无下游设备，本工单已彻底完成
                                SimEvent wiCompleteEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.WI_COMPLETE, null);
                                wiCompleteEvent.addSubject("WI", device.getCurrWiRefNo());
                            }
                            device.setState(DeviceStateEnum.IDLE); // 起点大机恢复空闲
                        }

                        // 场景B：【终点大机】完成了放箱动作 (此时箱子一定放到了堆场或船上)
                        else if (device.getId().equals(wi.getPutCheId())) {
                            log.info("物理协同: 终点落箱完成，工单归档");
                            SimEvent wiCompleteEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.WI_COMPLETE, null);
                            wiCompleteEvent.addSubject("WI", device.getCurrWiRefNo());
                            device.setState(DeviceStateEnum.IDLE); // 终点大机恢复空闲
                        }
                    }
                }
                break;

            case WI_COMPLETE:
                String wiRefNo = event.getPrimarySubject("WI");
                WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
                if (doneWi != null) {
                    doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
                    log.info("时间:{} 工单:{} 作业彻底结束，相关资源释放", now, wiRefNo);
                }
                break;

            // 环境控制事件

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