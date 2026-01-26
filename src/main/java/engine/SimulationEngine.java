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

/**
 * 离散事件仿真引擎
 */
@Component
@Slf4j
public class SimulationEngine {

    // 获取全局上下文 包含当前仿真时间、设备、地图、工单等状态
    private final GlobalContext context = GlobalContext.getInstance();

    // 事件优先队列  如果时间相同根据创建顺序排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    // 用于防止出现 0 耗时的死循环
    private static final int MAX_EVENTS_PER_TIMESTAMP = 10000;

    /**
     * 向未来注册（调度）一个事件
     * @param parentEventId 触发该事件的父事件ID（用于事件溯源和现场复盘）
     * @param triggerTime   事件发生的绝对时间戳
     * @param type          事件类型 (如：到达、抓箱完成、开始移动)
     * @param data          事件携带的数据 (如：目标坐标点)
     * @return 返回生成的事件对象，以便后续为其添加参与的主体（Subject）
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        return event;
    }

    /**
     * 取消充电或排队未来事件的
     */
    @SuppressWarnings("unused")
    public void cancelEvent(String eventId) {
        for (SimEvent event : eventQueue) {
            if (event.getEventId().equals(eventId)) {
                // 仅做标记 不立即从队列移除 消费时会跳过
                event.setCancelled(true);
                log.info("事件 {} 已被标记为取消", eventId);
                break;
            }
        }
    }

    /**
     * 仿真推演主循环
     */
    public void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;

        // 只要队列有事件 就一直处理
        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            // 如果下一个事件发生的时间超过了当前推演的目标时间 则暂停处理 跳出循环
            // 仿真时钟停留在 targetSimTime
            if (nextEvent.getTriggerTime() > targetSimTime) {
                break;
            }

            // 从队列中取出该事件
            eventQueue.poll();

            //  取消检查 跳过已作废的事件
            if (nextEvent.isCancelled()) {
                log.debug("跳过已取消的事件: {}", nextEvent.getEventId());
                continue;
            }

            //  防死循环机制
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > MAX_EVENTS_PER_TIMESTAMP) {
                    log.error("检测到时间戳 {} 发生死循环，强制终止当前时间步的推演", lastProcessedTime);
                    break; // 强制熔断
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 0; // 时间推进 计数器归零
            }

            //   更新全局仿真时钟到当前事件发生的时间点
            context.setSimTime(nextEvent.getTriggerTime());

            // 单个事件处理失败不能导致整个引擎崩溃
            try {
                handleEvent(nextEvent);
            } catch (Exception e) {
                log.error("处理事件异常! EventID: {}, Type: {}, Error: {}", nextEvent.getEventId(), nextEvent.getType(), e.getMessage(), e);
            }
        }
        // 推演结束，更新时钟到目标时间
        context.setSimTime(targetSimTime);
    }

    /**
     * 根据不同的事件类型，触发对应的物理状态变更或连锁事件。
     */
    private void handleEvent(SimEvent event) {
        long now = context.getSimTime();

        // 提取主要参与主体（集卡优先 其次桥吊/龙门吊 其次栅栏）
        String primaryTargetId = event.getPrimarySubject("TRUCK");
        if (primaryTargetId == null) primaryTargetId = event.getPrimarySubject("CRANE");
        if (primaryTargetId == null) primaryTargetId = event.getPrimarySubject("FENCE");

        BaseDevice device = context.getDevice(primaryTargetId);

        switch (event.getType()) {
            // 物理移动流转

            case MOVE_START:
                // 设备开始移动（计算到下一个途径点的耗时 并生成一个未来的 ARRIVAL 事件
                if (device != null) device.onMoveStart(now, this, event.getEventId());
                break;

            case ARRIVAL:
                if (device != null) {
                    Point reachedPoint = (Point) event.getData();
                    // 处理到达逻辑（更新坐标 扣减电量
                    device.onArrival(reachedPoint, now, this, event.getEventId());
                    if (device instanceof Truck truck && device.getWaypoints().isEmpty()) {
                        if (truck.getTargetStationId() != null) {
                            SimEvent chargeEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.CHARGING_START, null);
                            chargeEvent.addSubject("TRUCK", truck.getId());
                            chargeEvent.addSubject("STATION", truck.getTargetStationId());
                        }
                    }
                }
                break;

            //  充电流转逻辑

            case CHARGING_START:
                if (device instanceof Truck truck) {
                    device.setState(DeviceStateEnum.CHARGING);

                    String stationId = event.getPrimarySubject("STATION");
                    ChargingStation station = context.getChargingStationMap().get(stationId);

                    // 动态计算充电耗时 = 缺少的电量 / 充电速率
                    double powerNeeded = Truck.MAX_POWER_LEVEL - truck.getPowerLevel();
                    double rate = (station != null && station.getChargeRate() != null) ? station.getChargeRate() : 0.00001;
                    long chargeDurationMS = (long) (powerNeeded / rate);

                    log.info("时间:{} 集卡:{} 开始充电。预计耗时:{}ms", now, primaryTargetId, chargeDurationMS);

                    // 向未来注册 充电完成 事件
                    SimEvent chargeFullEvent = scheduleEvent(event.getEventId(), now + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                    chargeFullEvent.addSubject("TRUCK", primaryTargetId);
                    chargeFullEvent.addSubject("STATION", stationId);
                }
                break;

            case CHARGE_FULL:
                if (device instanceof Truck truck) {
                    // 恢复满电状态
                    truck.setPowerLevel(Truck.MAX_POWER_LEVEL);
                    truck.setNeedCharge(false);
                    truck.setState(DeviceStateEnum.IDLE);

                    // 释放充电桩资源
                    String stationId = event.getPrimarySubject("STATION");
                    ChargingStation station = context.getChargingStationMap().get(stationId);
                    if (station != null) {
                        station.setTruckId(null);
                        station.setStatus(DeviceStateEnum.IDLE.getCode());
                    }
                    truck.setTargetStationId(null);

                    log.info("时间:{} 集卡:{} 充电完成，上报空闲", now, primaryTargetId);
                    // 触发 集卡空闲 通知外部算法可以派发新任务了
                    SimEvent idleEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.REPORT_IDLE, null);
                    idleEvent.addSubject("TRUCK", primaryTargetId);
                }
                break;

            case REPORT_IDLE:
                // 外部算法此时会检测到集卡 IDLE 进而调用 /sim/command/assign 分配新任务
                log.info("时间:{} 集卡:{} 上报空闲，由外部算法接管决策", now, primaryTargetId);
                break;

            // 事件协同逻辑

            case FETCH_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成抓起动作 (集装箱离开原位置)", now, device.getId());
                    WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                    if (wi != null) {
                        // 场景判定：抓箱设备是 终点桥吊/龙门吊 (如卸船时的岸桥/进港时的龙门吊
                        if (device.getId().equals(wi.getPutCheId()) && wi.getCarryCheId() != null) {
                            log.info("物理协同: 集卡 {} 已卸空，释放交由外部算法决策去向", wi.getCarryCheId());
                            // 集卡空了 触发 REPORT_IDLE 释放集卡
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

                        // 场景A： 起点桥吊/龙门吊 完成了放箱
                        if (device.getId().equals(wi.getFetchCheId()) && wi.getCarryCheId() != null) {
                            if (wi.getPutCheId() != null) {
                                // A.1 有终点桥吊/龙门吊 集卡载着箱子开始移动
                                log.info("物理协同: 集卡 {} 装载完成，有下游设备[{}]，触发移动", wi.getCarryCheId(), wi.getPutCheId());
                                SimEvent moveEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.MOVE_START, null);
                                moveEvent.addSubject("TRUCK", wi.getCarryCheId());
                            } else {
                                // A.2 无终点桥吊/龙门吊 (外集卡提箱直接离港) 直接作业结束
                                log.info("物理协同: 集卡 {} 装载完成且无下游设备，释放交由外部算法处理离港", wi.getCarryCheId());
                                SimEvent idleEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.REPORT_IDLE, null);
                                idleEvent.addSubject("TRUCK", wi.getCarryCheId());

                                SimEvent wiCompleteEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.WI_COMPLETE, null);
                                wiCompleteEvent.addSubject("WI", device.getCurrWiRefNo());
                            }
                            device.setState(DeviceStateEnum.IDLE); // 释放起点桥吊/龙门吊
                        }

                        // 场景B： 终点桥吊/龙门吊 完成了放箱
                        else if (device.getId().equals(wi.getPutCheId())) {
                            log.info("物理协同: 终点落箱完成，工单归档");
                            // 触发工单完成事件
                            SimEvent wiCompleteEvent = scheduleEvent(event.getEventId(), now, EventTypeEnum.WI_COMPLETE, null);
                            wiCompleteEvent.addSubject("WI", device.getCurrWiRefNo());
                            device.setState(DeviceStateEnum.IDLE); // 释放终点桥吊/龙门吊
                        }
                    }
                }
                break;

            case WI_COMPLETE:
                // 更新工单状态为已完成 释放相关资源
                String wiRefNo = event.getPrimarySubject("WI");
                WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
                if (doneWi != null) {
                    doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
                    log.info("时间:{} 工单:{} 作业彻底结束，相关资源释放", now, wiRefNo);
                }
                break;

            // 环境控制事件

            case FENCE_CONTROL:
                // 处理动态栅栏的开启/锁死 控制交通流
                Fence fence = context.getFenceMap().get(primaryTargetId);
                if (fence != null) {
                    FenceStateEnum targetStatus = (FenceStateEnum) event.getData();
                    fence.setStatus(targetStatus.getCode());
                    // 如果栅栏打开 触发被堵塞车辆的恢复移动
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