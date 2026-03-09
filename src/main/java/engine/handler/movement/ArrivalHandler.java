package engine.handler.movement;

import common.consts.DeviceStateEnum;
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
 * 到达事件处理器（纯离散版本）
 *
 * 核心职责：
 * 1. 更新设备的真实坐标
 * 2. 检查剩余目标点，如果还有则调度下一段 ARRIVAL 事件
 * 3. 如果已走完所有轨迹点，调度 REPORT_IDLE 事件
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
        String deviceId = event.getPrimaryDeviceId();
        if (deviceId == null) {
            log.warn("ARRIVAL 事件处理失败: 无法识别设备ID. EventId={}", event.getEventId());
            return;
        }

        BaseDevice device = context.getDevice(deviceId);
        if (device == null) {
            log.warn("ARRIVAL 事件处理失败: 设备 {} 不存在", deviceId);
            return;
        }

        // ==================== 任务1：更新真实坐标 ====================
        Point arrivedPoint = (Point) event.getData();
        if (arrivedPoint == null) {
            log.warn("ARRIVAL 事件数据为空，设备 {} 无法更新坐标", deviceId);
            // 仍然继续处理，可能没有更多目标点了
        } else {
            // 直接更新真实坐标
            device.setPosX(arrivedPoint.getX());
            device.setPosY(arrivedPoint.getY());
            log.debug("设备 [{}] 到达坐标: ({}, {})", deviceId,
                    String.format("%.1f", arrivedPoint.getX()),
                    String.format("%.1f", arrivedPoint.getY()));
        }

        // ==================== 任务2：分段路径接力调度 ====================
        List<Point> remainingTargets = device.getRemainingMoveTargets();

        if (remainingTargets != null && !remainingTargets.isEmpty()) {
            // 还有剩余目标点，调度下一段移动
            Point nextTarget = remainingTargets.remove(0);

            // 计算当前位置到下一个目标的距离
            double currentX = device.getPosX();
            double currentY = device.getPosY();
            double distance = Math.sqrt(
                    Math.pow(nextTarget.getX() - currentX, 2) +
                            Math.pow(nextTarget.getY() - currentY, 2)
            );

            // 获取设备速度
            Double speed = device.getSpeed();
            if (speed == null || speed <= 0) {
                speed = device.getMoveSpeed();
            }

            // 计算当前段耗时（毫秒）
            long duration;
            if (speed == null || speed <= 0 || distance <= 0) {
                duration = 0;
            } else {
                duration = (long) ((distance / speed) * 1000);
            }

            // 计算下一段到达时间
            long currentSimTime = context.getSimTime();
            long nextArrivalTime = currentSimTime + duration;

            // 更新设备的虚拟插值字段（极其重要，用于前端平滑动画）
            device.setMoveStartTime(currentSimTime);
            device.setMoveStartPosX(currentX);
            device.setMoveStartPosY(currentY);
            device.setTargetX(nextTarget.getX());
            device.setTargetY(nextTarget.getY());
            device.setExpectedArrivalTime(nextArrivalTime);

            // 如果所有目标点都到达了，清空列表
            if (remainingTargets.isEmpty()) {
                device.setRemainingMoveTargets(null);
            }

            // 直接调度下一个 ARRIVAL 事件（不再调度无意义的 MOVE_START）
            SimEvent nextArrivalEvent = engine.scheduleEvent(
                    event.getEventId(),
                    nextArrivalTime,
                    EventTypeEnum.ARRIVAL,
                    nextTarget
            );

            // 附加设备主题
            if (device.getType() != null &&
                    (device.getType().name().equals("ASC") || device.getType().name().equals("QC"))) {
                nextArrivalEvent.addSubject("CRANE", deviceId);
            } else {
                nextArrivalEvent.addSubject("TRUCK", deviceId);
            }

            log.info("设备 [{}] 调度下一段 ARRIVAL: ({}, {}) -> ({}, {}), " +
                            "距离={}m, 速度={}m/s, 耗时={}ms, 到达时间={}",
                    deviceId,
                    String.format("%.1f", currentX), String.format("%.1f", currentY),
                    String.format("%.1f", nextTarget.getX()), String.format("%.1f", nextTarget.getY()),
                    String.format("%.1f", distance), speed, duration, nextArrivalTime);

            return;
        }

        // ==================== 任务3：终点处理与 IDLE 报告 ====================
        // 没有剩余目标点，说明已经走完所有轨迹点

        // 清空相关状态
        device.setRemainingMoveTargets(null);
        device.setTargetX(null);
        device.setTargetY(null);
        device.setMoveSpeed(null);

        // 清理虚拟插值字段
        device.setMoveStartTime(0);
        device.setMoveStartPosX(null);
        device.setMoveStartPosY(null);
        device.setExpectedArrivalTime(0);

        // 将设备状态重置为 IDLE
        device.setState(DeviceStateEnum.IDLE);

        // 调度空闲报告事件
        SimEvent reportEvent = engine.scheduleEvent(
                event.getEventId(),
                context.getSimTime(),
                EventTypeEnum.REPORT_IDLE,
                null
        );

        // 附加设备主题
        if (device.getType() != null &&
                (device.getType().name().equals("ASC") || device.getType().name().equals("QC"))) {
            reportEvent.addSubject("CRANE", deviceId);
        } else {
            reportEvent.addSubject("TRUCK", deviceId);
        }
        reportEvent.addSubject("DEVICE", deviceId);

        log.info("设备 [{}] 已到达终点，切换为 IDLE 状态", deviceId);
    }
}
