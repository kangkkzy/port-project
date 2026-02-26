package engine.handler.movement;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.CraneMoveReq;
import model.entity.BaseDevice;
import model.entity.Point;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 吊具移动指令
 */
@Component
public class CmdCraneMoveHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_CRANE_MOVE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String craneId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(craneId);
        if (device == null) return;

        if (device.getState() == DeviceStateEnum.WORKING) {
            throw new BusinessException(String.format("设备 %s 正在作业中，无法执行移动", craneId));
        }

        Map<String, Object> payload = (Map<String, Object>) event.getData();
        CraneMoveReq req = (CraneMoveReq) payload.get("req");
        Double speed = (Double) payload.get("speed");
        if (speed == null || speed <= 0) {
            throw new BusinessException("speed 参数无效");
        }
        double distance = req.getDistance() != null ? req.getDistance() : 0;

        // 计算目标坐标
        double posX = device.getPosX() != null ? device.getPosX() : 0;
        double posY = device.getPosY() != null ? device.getPosY() : 0;
        Point targetPoint;
        if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
            targetPoint = new Point(posX + distance, posY);
        } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
            targetPoint = new Point(posX, posY + distance);
        } else {
            targetPoint = new Point(posX + distance, posY);
        }

        device.setSpeed(speed);
        device.setCurrentTargetPos(targetPoint);

        SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveStart.addSubject("CRANE", craneId);
    }
}


