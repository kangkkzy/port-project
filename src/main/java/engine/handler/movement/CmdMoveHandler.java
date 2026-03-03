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
 * 支持两种模式：
 * 1. 简化模式：只设置 targetPoint，引擎自动计算关键点（同路径）
 * 2. 精确模式：设置 pathPoints，引擎按序执行（跨路径）
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

        // === 精确模式：外部算法已提供轨迹点列表 ===
        if (payload.getPathPoints() != null && !payload.getPathPoints().isEmpty()) {
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
        }
        // === 简化模式：引擎自动计算关键点 ===
        else {
            String deviceType = device.getType().name();
            List<Double> keyPoints = mapDataService.getKeyPointsBetween(deviceType, startX, startY, endX, endY);

            boolean isHorizontal = Math.abs(endX - startX) > Math.abs(endY - startY);

            for (Double keyPoint : keyPoints) {
                if (isHorizontal) {
                    remainingTargets.add(new Point(keyPoint, endY));
                } else {
                    remainingTargets.add(new Point(endX, keyPoint));
                }
            }
            // 添加最终目标
            remainingTargets.add(target);
        }

        // 将剩余目标列表存入设备
        device.setRemainingMoveTargets(remainingTargets);

        // 设置第一段的速度和目标
        device.setSpeed(speed);
        device.setCurrentTargetPos(remainingTargets.get(0));

        // 调度MOVE_START事件
        SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveStart.addSubject("TRUCK", truckId);

        log.info("[CMD_MOVE] 设备 [{}] 移动轨迹: {} -> {}", truckId,
                String.format("(%.1f,%.1f)", startX, startY),
                String.format("(%.1f,%.1f)", endX, endY));
    }
}