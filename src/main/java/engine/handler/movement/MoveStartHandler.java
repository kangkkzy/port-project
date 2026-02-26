package engine.handler.movement;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import org.springframework.stereotype.Component;

/**
 * 向下一个路径点移动：调用设备自身逻辑计算路径和预计到达时间
 */
@Component
public class MoveStartHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.MOVE_START;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimaryDeviceId();
        BaseDevice device = context.getDevice(deviceId);
        if (device != null) {
            device.onMoveStart(context.getSimTime(), engine, event.getEventId());
        }
    }
}