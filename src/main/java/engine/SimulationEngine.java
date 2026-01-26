package engine;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.consts.WiStatusEnum;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Fence;
import model.entity.Point;
import model.entity.Truck;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;

@Component
@Slf4j
public class SimulationEngine {

    private final GlobalContext context = GlobalContext.getInstance();

    //  优先队列 按发生时间自动排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    /**
     * 向未来注册一个事件
     */
    public void scheduleEvent(long triggerTime, EventTypeEnum type, String targetId, Object data) {
        eventQueue.add(new SimEvent(triggerTime, type, targetId, data));
    }

    /**
     * 外部算法驱动接口 推演仿真系统
     */
    public void runUntil(long targetSimTime) {
        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            if (nextEvent.getTriggerTime() > targetSimTime) {
                break;
            }

            eventQueue.poll();
            context.setSimTime(nextEvent.getTriggerTime());
            handleEvent(nextEvent);
        }
        context.setSimTime(targetSimTime);
    }

    /**
     * 事件消费中心
     */
    private void handleEvent(SimEvent event) {
        long now = context.getSimTime();
        String targetId = event.getTargetId();

        // 获取设备：集卡、岸桥、龙门吊
        BaseDevice device = context.getDevice(targetId);

        switch (event.getType()) {
            case MOVE_START:
                if (device != null) device.onMoveStart(now, this);
                break;

            case ARRIVAL:
                if (device != null) {
                    device.onArrival((Point) event.getData(), now, this);

                    // 外部算法控制移动到达终点后 如果该集卡是被指派去充电的 则触发充电开始事件
                    if (device instanceof Truck && device.getWaypoints().isEmpty()) {
                        Truck truck = (Truck) device;
                        if (truck.isNeedCharge() && truck.getTargetStationId() != null) {
                            scheduleEvent(now, EventTypeEnum.CHARGING_START, targetId, null);
                        }
                    }
                }
                break;

            // 充电事件 (由外部API触发移动抵达后开始

            case CHARGING_START:
                log.info("时间:{} 集卡:{} 到达充电桩，开始充电", now, targetId);
                if (device instanceof Truck) {
                    device.setState(DeviceStateEnum.CHARGING);
                    // 充电耗时 (常量: 1小时 = 3600000ms)
                    long chargeDurationMS = 3600000L;
                    scheduleEvent(now + chargeDurationMS, EventTypeEnum.CHARGE_FULL, targetId, null);
                }
                break;

            case CHARGE_FULL:
                if (device instanceof Truck) {
                    Truck truck = (Truck) device;
                    // 充满电量
                    truck.setPowerLevel(100.0);
                    truck.setNeedCharge(false);
                    truck.setState(DeviceStateEnum.IDLE);

                    // 释放充电桩
                    ChargingStation station = context.getChargingStationMap().get(truck.getTargetStationId());
                    if (station != null) {
                        station.setTruckId(null);
                        station.setStatus(DeviceStateEnum.IDLE.getCode());
                    }
                    truck.setTargetStationId(null);

                    log.info("时间:{} 集卡:{} 充电完成，上报空闲", now, targetId);
                    scheduleEvent(now, EventTypeEnum.REPORT_IDLE, targetId, null);
                }
                break;

            case REPORT_IDLE:
                log.info("时间:{} 集卡:{} 上报空闲，等待外部算法分配新指令", now, targetId);
                break;

            //  作业协同事件
            case FETCH_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成抓箱", now, device.getId());
                    WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                    if (wi != null && wi.getCarryCheId() != null) {
                        log.info("协同触发: 工单:{} 集卡:{} 开始运输", wi.getWiRefNo(), wi.getCarryCheId());
                        scheduleEvent(now, EventTypeEnum.MOVE_START, wi.getCarryCheId(), null);
                    }
                }
                break;

            case PUT_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成放箱", now, device.getId());
                    scheduleEvent(now, EventTypeEnum.WI_COMPLETE, device.getCurrWiRefNo(), null);
                }
                break;

            case WI_COMPLETE:
                WorkInstruction doneWi = context.getWorkInstructionMap().get(targetId);
                if (doneWi != null) {
                    // 通过枚举设置状态
                    doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
                    log.info("时间:{} 工单:{} 作业结束，设备释放", now, targetId);
                }
                break;

            // 栅栏控制事件
            case FENCE_CONTROL:
                Fence fence = context.getFenceMap().get(targetId);
                if (fence != null) {
                    FenceStateEnum targetStatus = (FenceStateEnum) event.getData();
                    fence.setStatus(targetStatus.getCode());
                    if (FenceStateEnum.PASSABLE.equals(targetStatus)) {
                        fence.onOpen(now, this);
                    }
                }
                break;

            default:
                break;
        }
    }
}