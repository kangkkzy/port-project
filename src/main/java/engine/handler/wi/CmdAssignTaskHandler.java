package engine.handler.wi;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统下发任务 -> 设备接收任务
 */
@Component
public class CmdAssignTaskHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_ASSIGN_TASK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimaryDeviceId();
        BaseDevice device = context.getDevice(deviceId);
        if (device == null) return;

        Map<String, Object> payload = (Map<String, Object>) event.getData();
        SimEvent ackEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.CMD_TASK_ACK, payload);
        ackEvent.addSubject("DEVICE", deviceId);
    }
}

