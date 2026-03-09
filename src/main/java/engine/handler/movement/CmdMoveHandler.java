package engine.handler.movement;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.MoveCommandReq;
import model.entity.BaseDevice;
import model.entity.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;
import service.algorithm.TrajectoryValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 集卡移动指令处理器
 *
 * DES架构职责：
 * - 作为执行器，负责物理约束校验和时间推演
 * - 外部算法负责"算路"并下发轨迹点
 * - 引擎负责验证路径合法性和执行移动
 *
 * 严格模式：外部算法必须提供完整的 pathPoints 轨迹点列表，
 * 引擎不再自动计算关键点（已删除 getKeyPointsBetween 调用）
 */
@Component
public class CmdMoveHandler implements SimEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CmdMoveHandler.class);

    private final MapDataService mapDataService;

    @Autowired
    public CmdMoveHandler(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_MOVE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String truckId = event.getPrimarySubject("TRUCK");
        BaseDevice device = context.getDevice(truckId);
        if (device == null) throw new BusinessException("移动指令异常: 设备不存在");

        if (device.getState() == DeviceStateEnum.WORKING || device.getState() == DeviceStateEnum.CHARGING) {
            throw new BusinessException(String.format("设备 %s 状态(%s)繁忙，无法执行移动", device.getId(), device.getState()));
        }

        MoveCommandReq payload = (MoveCommandReq) event.getData();
        Double speed = payload.getSpeed();
        if (speed == null || speed <= 0) {
            throw new BusinessException("移动参数非法: speed=" + speed);
        }

        Point target = payload.getTargetPoint();
        double startX = device.getPosX();
        double startY = device.getPosY();
        double endX = target.getX();
        double endY = target.getY();

        // 集卡道路校验：确保目标点在集卡道路上（严格校验，不允许偏离）
        String deviceTypeStr = device.getType().name();
        boolean targetOnRoad = mapDataService.isPositionOnPath(deviceTypeStr, endX, endY);
        if (!targetOnRoad) {
            log.error("严重错误: 集卡 [{}] 移动目标坐标 ({}, {}) 不在集卡道路网上，触发熔断暂停",
                    truckId, endX, endY);
            throw new BusinessException(String.format("集卡 [%s] 移动目标坐标 (%.1f, %.1f) 不在集卡道路网上", truckId, endX, endY));
        }

        // 额外校验：集卡的起始位置也必须在道路上
        boolean startOnRoad = mapDataService.isPositionOnPath(deviceTypeStr, startX, startY);
        if (!startOnRoad) {
            log.error("严重错误: 集卡 [{}] 当前不在集卡道路网上 (当前位置: {}, {})，触发熔断暂停",
                    truckId, startX, startY);
            throw new BusinessException(String.format("集卡 [%s] 当前不在集卡道路网上 (当前位置: %.1f, %.1f)", truckId, startX, startY));
        }

        List<Point> remainingTargets = new ArrayList<>();

        // === 严格模式：外部算法必须提供完整的行驶轨迹点列表 ===
        // 引擎不再自动计算关键点（删除 getKeyPointsBetween 调用）
        if (payload.getPathPoints() == null || payload.getPathPoints().isEmpty()) {
            throw new BusinessException("外部算法必须提供完整的行驶轨迹点 (pathPoints)，引擎不再自动计算路径");
        }

        List<Point> externalPath = payload.getPathPoints();

        // 校验路径合法性（如果启用）
        if (Boolean.TRUE.equals(payload.getEnforcePathValidation())) {
            String deviceType = device.getType().name();
            TrajectoryValidationResult validation = mapDataService.validateTrajectory(deviceType, externalPath);
            if (!validation.isValid()) {
                String errorMsg = String.format("移动轨迹不合法，脱离路网: %s。轨迹点: %s",
                        validation.getErrorMessage(), externalPath);
                log.error(errorMsg);
                throw new BusinessException(errorMsg);
            }
        }

        // 使用外部提供的轨迹点（从起点开始拼接）
        remainingTargets.add(new Point(startX, startY));
        remainingTargets.addAll(externalPath);

        // 确保最终目标在列表中
        Point lastPoint = remainingTargets.get(remainingTargets.size() - 1);
        if (lastPoint.getX() != endX || lastPoint.getY() != endY) {
            remainingTargets.add(target);
        }

        // 将剩余目标列表存入设备（用于分段移动）
        device.setRemainingMoveTargets(remainingTargets);

        // === 运动意图字段：记录目标坐标 ===
        device.setTargetX(endX);
        device.setTargetY(endY);
        device.setMoveSpeed(speed);
        device.setState(DeviceStateEnum.MOVING);

        // 设置第一段的速度和目标
        device.setSpeed(speed);
        // 使用 targetX/targetY 字段代替 setCurrentTargetPos
        if (remainingTargets.get(0) != null) {
            device.setTargetX(remainingTargets.get(0).getX());
            device.setTargetY(remainingTargets.get(0).getY());
        }

        // === 纯离散事件调度：计算耗时并立即调度 ARRIVAL 事件 ===
        // 取出第一个目标点（从索引1开始，因为索引0是当前位置）
        Point firstTarget = remainingTargets.get(1);

        // 计算当前位置到目标点的欧式距离
        double distance = Math.sqrt(
                Math.pow(firstTarget.getX() - startX, 2) +
                        Math.pow(firstTarget.getY() - startY, 2)
        );

        // 计算耗时（毫秒），处理 speed 为 0 的除零风险
        long duration;
        if (speed <= 0 || distance <= 0) {
            duration = 0;
        } else {
            duration = (long) ((distance / speed) * 1000);
        }

        // 计算预期到达时间
        long currentSimTime = context.getSimTime();
        long arrivalTime = currentSimTime + duration;

        // 设置虚拟状态插值字段，用于前端平滑动画
        device.setMoveStartTime(currentSimTime);
        device.setMoveStartPosX(startX);
        device.setMoveStartPosY(startY);
        device.setExpectedArrivalTime(arrivalTime);

        // 立即调度 ARRIVAL 事件
        SimEvent arrivalEvent = engine.scheduleEvent(
                event.getEventId(),
                arrivalTime,
                EventTypeEnum.ARRIVAL,
                firstTarget
        );
        arrivalEvent.addSubject("TRUCK", truckId);

        log.info("[CMD_MOVE] 设备 [{}] 调度 ARRIVAL 事件: ({}, {}) -> ({}, {}), " +
                        "距离={}m, 速度={}m/s, 预计耗时={}ms, 到达时间={}",
                truckId,
                String.format("%.1f", startX), String.format("%.1f", startY),
                String.format("%.1f", firstTarget.getX()), String.format("%.1f", firstTarget.getY()),
                String.format("%.1f", distance), speed, duration, arrivalTime);
    }
}