package engine.handler.charging;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.ChargingStation;
import model.entity.Truck;
import org.springframework.stereotype.Component;

/**
 * 开始充电 -> 计算耗时 -> 调度完成
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


