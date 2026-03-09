package engine.handler.charging;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.ChargingStation;
import model.entity.DevicePhysicsParam;
import model.entity.Truck;
import org.springframework.stereotype.Component;

/**
 * 开始充电 -> 计算耗时 -> 调度完成
 *
 * 严格模式：所有参数必须从外部获取，不允许使用默认值兜底
 */
@Component
public class ChargingStartHandler implements SimEventHandler {

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
            // 从充电桩获取充电速率，不允许默认值兜底
            Double rate = station.getChargeRate();
            if (rate == null || rate <= 0) {
                throw new BusinessException("充电桩 [" + stationId + "] 未配置合法充电速率");
            }

            // 从外部物理参数获取集卡最大电量，不允许使用 Truck.MAX_POWER_LEVEL 兜底
            DevicePhysicsParam physicsParam = context.getDevicePhysicsParamMap().get(truckId);
            if (physicsParam == null) {
                throw new BusinessException(String.format("设备 [%s] 物理参数缺失，请确保在场景初始化时由外部算法下发了该数据", truckId));
            }
            Double maxPower = physicsParam.getMaxPower();
            if (maxPower == null || maxPower <= 0) {
                throw new BusinessException(String.format("设备 [%s] 未配置最大电量 (maxPower)", truckId));
            }

            truck.setState(DeviceStateEnum.CHARGING);
            double currentPower = truck.getPowerLevel() != null ? truck.getPowerLevel() : 0;
            long chargeDurationMS = (long) (((maxPower - currentPower) / rate) * 1000);
            if (chargeDurationMS <= 0) {
                throw new BusinessException(String.format("物理推演错误: 充电耗时计算为0，当前电量=%.1f，最大电量=%.1f", currentPower, maxPower));
            }

            SimEvent fullEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + chargeDurationMS, EventTypeEnum.CHARGE_FULL, null);
            fullEvent.addSubject("TRUCK", truckId);
            fullEvent.addSubject("STATION", stationId);
        }
    }
}
