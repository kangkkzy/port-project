package engine.handler.movement;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Point;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 集卡移动指令
 */
@Component
public class CmdMoveHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_MOVE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String truckId = event.getPrimarySubject("TRUCK");
        BaseDevice device = context.getDevice(truckId);
        if (device == null) throw new BusinessException("移动指令异常: 设备不存在");

        if (device.getState() == DeviceStateEnum.WORKING || device.getState() == DeviceStateEnum.CHARGING) {
            throw new BusinessException(String.format("设备 %s 状态(%s)繁忙，无法执行移动", device.getId(), device.getState()));
        }

        Map<String, Object> payload = (Map<String, Object>) event.getData();
        Double speed = (Double) payload.get("speed");
        if (speed == null || speed <= 0) {
            throw new BusinessException("移动参数非法: speed=" + speed);
        }

        Point target = (Point) payload.get("target");
        device.setSpeed(speed);
        device.setCurrentTargetPos(target);

        SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveStart.addSubject("TRUCK", truckId);
    }
}

