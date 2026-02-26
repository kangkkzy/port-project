package engine.handler.movement;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import org.springframework.stereotype.Component;

/**
 * 设备空闲上报 (REPORT_IDLE)
 */
@Component
@Slf4j
public class ReportIdleHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.REPORT_IDLE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        if (event.getSubjects() == null || event.getSubjects().isEmpty()) {
            // 忽略测试产生的无效空事件
            return;
        }

        String id = event.getPrimaryDeviceId();
        if (id == null) {
            id = event.getSubjects().values().iterator().next();
        }

        if (id != null) {
            log.info("[Time: {}] 设备 {} 动作结束，进入空闲状态", context.getSimTime(), id);
        } else {
            log.warn("[Time: {}] REPORT_IDLE 无法识别设备ID", context.getSimTime());
        }
    }
}


