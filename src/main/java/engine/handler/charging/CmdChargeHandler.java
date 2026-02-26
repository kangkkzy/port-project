package engine.handler.charging;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import common.util.GisUtil;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.entity.ChargingStation;
import model.entity.Point;
import model.entity.Truck;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 充电指令
 */
@Component
public class CmdChargeHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_CHARGE;
    }

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

