package engine.handler.wi;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.CraneOperationReq;
import model.entity.BaseDevice;
import org.springframework.stereotype.Component;

/**
 * 吊具操作通用处理 (Pick/Set)
 */
@Component
public class CmdCraneOpHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_CRANE_OP;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        CraneOperationReq req = (CraneOperationReq) event.getData();
        String craneId = req.getCraneId();
        BaseDevice device = context.getDevice(craneId);

        if (device != null) {
            // 移动中禁止作业
            if (device.getState() == DeviceStateEnum.MOVING) {
                throw new BusinessException(String.format("逻辑错误：设备 %s 移动中无法执行抓/放箱！", craneId));
            }
            device.setState(DeviceStateEnum.WORKING);
        }

        SimEvent opEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime() + req.getDurationMS(), req.getAction(), null);
        opEvent.addSubject("CRANE", craneId);
    }
}

