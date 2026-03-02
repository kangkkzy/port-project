package engine.handler.movement;

import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import lombok.extern.slf4j.Slf4j;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Point;
import org.springframework.stereotype.Component;

import java.util.List;

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
            // 保存速度，因为在 onArrival 中会被清空
            Double savedSpeed = d.getSpeed();

            // 先调用 onArrival 更新位置
            d.onArrival((Point) event.getData(), context.getSimTime(), engine, event.getEventId());

            // 检查是否还有剩余目标点需要移动
            List<Point> remainingTargets = d.getRemainingMoveTargets();
            if (remainingTargets != null && !remainingTargets.isEmpty()) {
                // 取出下一个目标点
                Point nextTarget = remainingTargets.remove(0);
                d.setCurrentTargetPos(nextTarget);

                // 恢复速度用于下一段移动
                d.setSpeed(savedSpeed);

                // 调度下一个MOVE_START事件
                SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
                moveStart.addSubject("TRUCK", d.getId());

                // 如果所有目标点都到达了，清空列表
                if (remainingTargets.isEmpty()) {
                    d.setRemainingMoveTargets(null);
                }
            } else {
                // 没有剩余目标点，调度 Idle 事件
                SimEvent reportEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                if (event.getSubjects() != null) {
                    event.getSubjects().forEach(reportEvent::addSubject);
                }
                reportEvent.addSubject("DEVICE", d.getId());
            }
        } else {
            log.warn("ARRIVAL 事件处理失败: 无法识别设备ID. EventId={}", event.getEventId());
        }
    }
}








