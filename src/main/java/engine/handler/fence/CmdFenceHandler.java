package engine.handler.fence;

import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.entity.Fence;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 围栏指令兼容处理
 */
@Component
public class CmdFenceHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_FENCE_TOGGLE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String fenceId = null;
        String status = null;

        // 提取参数
        if (event.getData() instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) event.getData();
            fenceId = (String) map.get("nodeId");
            Object statusObj = map.get("status");
            if (statusObj != null) {
                status = String.valueOf(statusObj);
            }
        } else if (event.getData() instanceof String) {
            status = (String) event.getData();
        } else if (event.getData() instanceof FenceStateEnum) {
            status = ((FenceStateEnum) event.getData()).getCode();
        }

        if (fenceId != null) {
            Fence f = context.getFenceMap().get(fenceId);
            if (f != null && status != null) f.setStatus(status);
        } else if (status != null) {
            for (Fence f : context.getFenceMap().values()) f.setStatus(status);
        }
    }
}


