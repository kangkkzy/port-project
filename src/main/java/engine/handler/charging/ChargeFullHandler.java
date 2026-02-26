package engine.handler.charging;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.entity.ChargingStation;
import model.entity.Truck;
import org.springframework.stereotype.Component;

/**
 * 充电完成
 */
@Component
public class ChargeFullHandler implements SimEventHandler {

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

