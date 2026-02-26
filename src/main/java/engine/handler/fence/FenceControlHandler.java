package engine.handler.fence;

import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.entity.Fence;
import org.springframework.stereotype.Component;

/**
 * 电子围栏控制
 */
@Component
@Slf4j
public class FenceControlHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.FENCE_CONTROL;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String fenceId = event.getPrimarySubject("FENCE");
        Fence fence = context.getFenceMap().get(fenceId);
        if (fence != null) {
            Object data = event.getData();
            String status = null;
            // 兼容枚举和字符串输入
            if (data instanceof FenceStateEnum) {
                status = ((FenceStateEnum) data).getCode();
            } else if (data instanceof String) {
                status = (String) data;
            }

            if (status != null) {
                fence.setStatus(status);
                // 围栏打开时，清空积压的等待队列
                if (FenceStateEnum.PASSABLE.getCode().equals(status)) {
                    fence.getWaitingTrucks().clear();
                }
                log.info("围栏 {} 状态更新: {}", fenceId, status);
            }
        }
    }
}

