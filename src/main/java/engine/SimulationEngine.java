package engine;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.consts.WiStatusEnum;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.entity.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 离散事件仿真引擎
 */
@Component
@Slf4j
public class SimulationEngine implements InitializingBean {

    // 全局上下文 存储设备、工单和仿真时钟
    private final GlobalContext context = GlobalContext.getInstance();

    // 优先队列 保证事件永远按 triggerTime 从小到大
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();

    //防止死循环
    private static final int MAX_EVENTS_PER_TIMESTAMP = 10000;

    // 处理器映射表 EventType -> EventHandler
    private final Map<EventTypeEnum, EventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);

    @Override
    public void afterPropertiesSet() {
        //  注册外部指令处理器
        register(EventTypeEnum.CMD_MOVE, new CmdMoveHandler());
        register(EventTypeEnum.CMD_CHARGE, new CmdChargeHandler());
        register(EventTypeEnum.CMD_FENCE_TOGGLE, new CmdFenceHandler());
        register(EventTypeEnum.CMD_CRANE_MOVE, new CmdCraneMoveHandler());
        register(EventTypeEnum.CMD_CRANE_OP, new CmdCraneOpHandler());

        // 任务指派握手
        register(EventTypeEnum.CMD_ASSIGN_TASK, new CmdAssignTaskHandler());
        register(EventTypeEnum.CMD_TASK_ACK, new CmdTaskAckHandler());

        // 注册内部物理流程处理器
        register(EventTypeEnum.MOVE_START, new MoveStartHandler());
        register(EventTypeEnum.ARRIVAL, new ArrivalHandler());
        register(EventTypeEnum.CHARGING_START, new ChargingStartHandler());
        register(EventTypeEnum.CHARGE_FULL, new ChargeFullHandler());
        register(EventTypeEnum.REPORT_IDLE, new ReportIdleHandler());

        //  作业协同 (抓/放/完成)
        register(EventTypeEnum.FETCH_DONE, new FetchDoneHandler());
        register(EventTypeEnum.PUT_DONE, new PutDoneHandler());
        register(EventTypeEnum.WI_COMPLETE, new WiCompleteHandler());
        register(EventTypeEnum.FENCE_CONTROL, new FenceControlHandler());
    }

    private void register(EventTypeEnum type, EventHandler handler) {
        handlerMap.put(type, handler);
    }

    /**
     * 调度一个新事件
     * @param triggerTime 触发时间 (ms)
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        return event;
    }

    /**
     * 推进仿真时钟直到目标时间
     */
    public void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;

        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            // 如果最早事件的时间超过了目标时间 暂停处理
            if (nextEvent.getTriggerTime() > targetSimTime) break;

            eventQueue.poll();
            if (nextEvent.isCancelled()) continue;

            // 死循环检测
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > MAX_EVENTS_PER_TIMESTAMP) {
                    log.error("检测到时间戳 {} 发生死循环，强制熔断", lastProcessedTime);
                    break;
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 0;
            }

            // 更新系统时钟 (Time Warp)
            context.setSimTime(nextEvent.getTriggerTime());

            // 分发事件
            EventHandler handler = handlerMap.get(nextEvent.getType());
            if (handler != null) {
                try {
                    handler.handle(nextEvent, this, context);
                } catch (Exception e) {
                    log.error("事件处理异常: {}", nextEvent, e);
                }
            } else {
                log.warn("未找到事件 {} 的处理器", nextEvent.getType());
            }
        }
        // 强制对齐到目标时间
        context.setSimTime(targetSimTime);
    }

    interface EventHandler {
        void handle(SimEvent event, SimulationEngine engine, GlobalContext context);
    }

    // 事件处理

    /**
     * 任务指派：零延迟异步确认
     */
    static class CmdAssignTaskHandler implements EventHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("DEVICE");
            if (deviceId == null) deviceId = event.getPrimarySubject("TRUCK");

            BaseDevice device = context.getDevice(deviceId);
            if (device == null) return;

            Map<String, Object> payload = (Map<String, Object>) event.getData();

            // 使用 context.getSimTime() 作为触发时间 实现 逻辑异步
            SimEvent ackEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CMD_TASK_ACK, payload);
            ackEvent.addSubject("DEVICE", deviceId);
        }
    }

    /**
     * 任务确认：绑定工单，设备变忙
     */
    static class CmdTaskAckHandler implements EventHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("DEVICE");
            BaseDevice device = context.getDevice(deviceId);
            if (device == null) return;

            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String wiRefNo = (String) payload.get("wiRefNo");

            device.setCurrWiRefNo(wiRefNo);

            // ASC/QC  确认任务后立即进入工作状态
            if (device.getType() == DeviceTypeEnum.ASC || device.getType() == DeviceTypeEnum.QC) {
                device.setState(DeviceStateEnum.WORKING);
            }
            log.info("设备 {} 确认任务 {}, 绑定完成", deviceId, wiRefNo);
        }
    }

    /**
     * 移动指令
     */
    static class CmdMoveHandler implements EventHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            BaseDevice device = context.getDevice(truckId);
            if (device == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();

            device.setSpeed((Double) payload.get("speed"));
            device.setWaypoints(new LinkedList<>((List<Point>) payload.get("points")));

            SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
            moveStart.addSubject("TRUCK", truckId);
        }
    }

    /**
     * 充电指令
     */
    static class CmdChargeHandler implements EventHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            if (truck == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String stationId = (String) payload.get("stationId");
            ChargingStation station = context.getChargingStationMap().get(stationId);

            if (station != null && station.isAvailable()) {
                station.setTruckId(truckId);
                station.setStatus(DeviceStateEnum.WORKING.getCode()); // 锁定桩
                truck.setNeedCharge(true);
                truck.setTargetStationId(stationId);
                truck.setWaypoints(new LinkedList<>((List<Point>) payload.get("points")));

                SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
                moveStart.addSubject("TRUCK", truckId);
            }
        }
    }

    // 栅栏控制
    static class CmdFenceHandler implements EventHandler {
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
     * 起重机移动
     */
    static class CmdCraneMoveHandler implements EventHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String craneId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(craneId);
            if (device == null) return;
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            CraneMoveReq req = (CraneMoveReq) payload.get("req");
            Double speed = (Double) payload.get("speed");

            long travelTimeMS = (long) ((req.getDistance() / speed) * 1000);
            device.setState(req.getMoveType());

            SimEvent arrEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, null);
            arrEvent.addSubject("CRANE", device.getId());
        }
    }

    /**
     * 起重机操作
     */
    static class CmdCraneOpHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            CraneOperationReq req = (CraneOperationReq) event.getData();
            SimEvent opEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + req.getDurationMS(), req.getAction(), null);
            opEvent.addSubject("CRANE", req.getCraneId());
        }
    }

    //   物理流程

    static class MoveStartHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("TRUCK");
            if (deviceId == null) deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) device.onMoveStart(context.getSimTime(), engine, event.getEventId());
        }
    }

    static class ArrivalHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String id = event.getPrimarySubject("TRUCK");
            if(id==null) id = event.getPrimarySubject("CRANE");
            BaseDevice d = context.getDevice(id);
            if(d != null) {
                d.onArrival((Point)event.getData(), context.getSimTime(), engine, event.getEventId());

                //  电集卡到达目标充电桩 自动开始充电
                if (d instanceof Truck truck && truck.getType() == DeviceTypeEnum.ELECTRIC_TRUCK && d.getWaypoints().isEmpty()) {
                    if (truck.getTargetStationId() != null) {
                        SimEvent chargeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CHARGING_START, null);
                        chargeEvent.addSubject("TRUCK", truck.getId());
                        chargeEvent.addSubject("STATION", truck.getTargetStationId());
                    }
                }
            }
        }
    }

    /**
     * 开始充电
     */
    static class ChargingStartHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            String stationId = event.getPrimarySubject("STATION");
            ChargingStation station = context.getChargingStationMap().get(stationId);

            if (truck != null) {
                //  充电桩必须存在
                if (station == null) {
                    throw new IllegalArgumentException("仿真异常: 试图在不存在的充电桩 [" + stationId + "] 进行充电");
                }

                //  费率必须配置且大于0
                Double rate = station.getChargeRate();
                if (rate == null || rate <= 0) {
                    throw new IllegalArgumentException("配置错误: 充电桩 [" + stationId + "] 的充电速率配置无效 (当前值: " + rate + ")，无法计算时长");
                }

                truck.setState(DeviceStateEnum.CHARGING);
                double powerNeeded = Truck.MAX_POWER_LEVEL - truck.getPowerLevel();

                //   计算
                long chargeDurationMS = (long) (powerNeeded / rate);

                // 预约 充电完成 事件
                SimEvent fullEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
                fullEvent.addSubject("TRUCK", truckId);
                fullEvent.addSubject("STATION", stationId);

                log.info("集卡 {} 开始充电，需补充电量: {}, 预计耗时: {}ms", truckId, powerNeeded, chargeDurationMS);
            } else {
                log.warn("充电事件异常: 未找到集卡 ID [{}]", truckId);
            }
        }
    }

    static class ChargeFullHandler implements EventHandler {
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

    static class ReportIdleHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String truckId = event.getPrimarySubject("TRUCK");
            Truck truck = context.getTruckMap().get(truckId);
            if (truck == null) return;
            log.info("集卡 {} 上报空闲", truckId);
            truck.setState(DeviceStateEnum.IDLE);
        }
    }

    /**
     * 抓箱完成处理
     */
    static class FetchDoneHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null && device.getId().equals(wi.getPutCheId()) && wi.getCarryCheId() != null) {
                    SimEvent idleEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                    idleEvent.addSubject("TRUCK", wi.getCarryCheId());
                }
            }
        }
    }

    /**
     * 放箱完成处理
     */
    static class PutDoneHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String deviceId = event.getPrimarySubject("CRANE");
            BaseDevice device = context.getDevice(deviceId);
            if (device != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null) {
                    // [场景A] 装车逻辑 (Loading: Yard -> Truck)
                    // 场景：当前设备是任务的 FetchChe (提箱者，如场桥)，
                    // 它刚刚完成 Put (放到集卡上)。此时集卡(CarryChe) 获得了箱子。
                    if (device.getId().equals(wi.getFetchCheId()) && wi.getCarryCheId() != null) {
                        if (wi.getPutCheId() != null) {
                            // 还有下一程 (例如：运往岸桥) -> 触发集卡移动
                            SimEvent moveEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
                            moveEvent.addSubject("TRUCK", wi.getCarryCheId());
                        } else {
                            // 无下一程，任务链结束 -> 完结工单，释放集卡
                            SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
                            completeEvent.addSubject("WI", device.getCurrWiRefNo());
                            SimEvent idleEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                            idleEvent.addSubject("TRUCK", wi.getCarryCheId());
                        }
                        device.setState(DeviceStateEnum.IDLE); // 起重机任务完成，变空闲
                    }
                    // [场景B] 卸车逻辑结束 (Unloading Finish)
                    // 场景：当前设备是任务的 PutChe (堆场吊)，完成最终放箱(放到堆场)。
                    else if (device.getId().equals(wi.getPutCheId())) {
                        SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
                        completeEvent.addSubject("WI", device.getCurrWiRefNo());
                        device.setState(DeviceStateEnum.IDLE);
                    }
                }
            }
        }
    }

    // 工单完成
    static class WiCompleteHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String wiRefNo = event.getPrimarySubject("WI");
            WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
            if (doneWi != null) doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
        }
    }

    // 栅栏控制
    static class FenceControlHandler implements EventHandler {
        @Override
        public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
            String fenceId = event.getPrimarySubject("FENCE");
            Fence fence = context.getFenceMap().get(fenceId);
            if (fence != null) {
                FenceStateEnum status = (FenceStateEnum) event.getData();
                fence.setStatus(status.getCode());
                if (FenceStateEnum.PASSABLE.equals(status)) {
                    fence.onOpen(context.getSimTime(), engine, event.getEventId());
                }
            }
        }
    }
}