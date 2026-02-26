package engine.handler.movement;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Point;
import org.springframework.stereotype.Component;

/**
 * 到达目的地
 */
@Component
@Slf4j
public class ArrivalHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.ARRIVAL;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String id = event.getPrimaryDeviceId();
        BaseDevice d = (id != null) ? context.getDevice(id) : null;

        if (d != null) {
            d.onArrival((Point) event.getData(), context.getSimTime(), engine, event.getEventId());

            // 调度 Idle 事件，并透传上下文 Subjects，防止下游丢失设备信息
            SimEvent reportEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
            if (event.getSubjects() != null) {
                event.getSubjects().forEach(reportEvent::addSubject);
            }
            reportEvent.addSubject("DEVICE", d.getId());
        } else {
            log.warn("ARRIVAL 事件处理失败: 无法识别设备ID. EventId={}", event.getEventId());
        }
    }
}


