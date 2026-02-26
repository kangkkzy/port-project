package engine.handler.wi;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备接收任务
 */
@Component
public class CmdTaskAckHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_TASK_ACK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimarySubject("DEVICE");
        BaseDevice device = context.getDevice(deviceId);
        if (device == null) return;

        Map<String, Object> payload = (Map<String, Object>) event.getData();
        device.setCurrWiRefNo((String) payload.get("wiRefNo"));
    }
}

