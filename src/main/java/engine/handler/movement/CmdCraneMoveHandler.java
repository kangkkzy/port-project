package engine.handler.movement;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.CraneMoveReq;
import model.entity.AscDevice;
import model.entity.BaseDevice;
import model.entity.Point;
import model.entity.QcDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;
import model.dto.config.MapPathDto;

/**
 * 起重机移动指令处理器（纯离散版本）
 *
 * 职责：
 * - 校验移动指令合法性
 * - 计算目标坐标
 * - 纯离散耗时计算并调度 ARRIVAL 事件
 * - 设置虚拟插值字段用于前端动画
 */
@Component
public class CmdCraneMoveHandler implements SimEventHandler {

    // 浮点数比较极小值常量
    private static final double EPSILON = 1e-4;

    private static final Logger log = LoggerFactory.getLogger(CmdCraneMoveHandler.class);

    private final MapDataService mapDataService;

    @Autowired
    public CmdCraneMoveHandler(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_CRANE_MOVE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String craneId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(craneId);
        if (device == null) {
            throw new BusinessException(String.format(
                    "吊具移动指令错误：设备 [%s] 不存在，外部算法下发了无效的设备ID", craneId));
        }

        if (device.getState() == DeviceStateEnum.WORKING) {
            throw new BusinessException(String.format("设备 %s 正在作业中，无法执行移动", craneId));
        }

        // 解析指令
        CraneMoveReq req = (CraneMoveReq) event.getData();
        Double speed = req.getSpeed();
        if (speed == null || speed <= 0) {
            throw new BusinessException("speed 参数无效");
        }
        double distance = req.getDistance() != null ? req.getDistance() : 0;

        // 当前坐标
        double posX = device.getPosX() != null ? device.getPosX() : 0;
        double posY = device.getPosY() != null ? device.getPosY() : 0;
        Point targetPoint;

        DeviceTypeEnum deviceType = device.getType();

        // 从外部配置获取容差值 不允许使用默认值兜底
        double tolerance;
        if (deviceType == DeviceTypeEnum.QC) {
            tolerance = mapDataService.getParameter("qcRailTolerance");
        } else if (deviceType == DeviceTypeEnum.ASC) {
            tolerance = mapDataService.getParameter("ascRailTolerance");
        } else {
            throw new BusinessException(String.format("未知的起重机类型: %s", deviceType));
        }

        // 计算目标坐标
        if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
            // 水平移动：仅允许 QC 在其轨道上水平移动
            if (deviceType == DeviceTypeEnum.ASC) {
                throw new BusinessException(String.format("ASC [%s] 只能沿紫色轨道做垂直移动，禁止水平移动", craneId));
            }

            targetPoint = new Point(posX + distance, posY);

            if (deviceType == DeviceTypeEnum.QC) {
                // QC 只能在 QC 轨道上水平移动 Y 坐标必须保持不变
                // 校验当前是否在轨道上（距离大于 EPSILON 才需要校验）
                if (Math.abs(distance) > EPSILON) {
                    MapPathDto qcRail = mapDataService.getQcRail();
                    if (qcRail == null) {
                        throw new BusinessException("严重错误: 地图未配置 QC 轨道");
                    }
                    double expectedY = qcRail.getPosition();
                    if (Math.abs(posY - expectedY) > tolerance) {
                        log.error("严重错误: QC [{}] 不在 QC 轨道上 (当前 Y={}, 轨道 Y={})，触发熔断暂停",
                                craneId, posY, expectedY);
                        throw new BusinessException(String.format("QC [%s] 不在 QC 轨道上，无法移动", craneId));
                    }
                    // 校验目标点也在轨道范围内
                    double targetX = targetPoint.getX();
                    if (targetX < qcRail.getStartPoint() || targetX > qcRail.getEndPoint()) {
                        log.error("严重错误: QC [{}] 移动目标 X={} 超出轨道范围 [{}, {}]，触发熔断暂停",
                                craneId, targetX, qcRail.getStartPoint(), qcRail.getEndPoint());
                        throw new BusinessException(String.format("QC [%s] 移动目标超出轨道范围", craneId));
                    }
                }
            }
        } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
            // 垂直移动：QC 不能垂直移动，ASC 只能在 ASC 轨道上垂直移动
            targetPoint = new Point(posX, posY + distance);

            if (deviceType == DeviceTypeEnum.QC) {
                // QC 不能垂直移动
                throw new BusinessException(String.format("QC [%s] 不能垂直移动，只能水平移动", craneId));
            } else if (deviceType == DeviceTypeEnum.ASC) {
                // ASC 只能在 ASC 轨道上垂直移动，X 坐标必须保持不变
                MapPathDto ascRail = mapDataService.getAscRailAtPosition(posX);
                if (ascRail == null) {
                    log.error("严重错误: ASC [{}] 不在任何 ASC 轨道上 (X={})，触发熔断暂停",
                            craneId, posX);
                    throw new BusinessException(String.format("ASC [%s] 不在任何 ASC 轨道上", craneId));
                }
                // 校验 Y 在轨道范围内
                double targetY = targetPoint.getY();
                if (targetY < ascRail.getStartPoint() || targetY > ascRail.getEndPoint()) {
                    log.error("严重错误: ASC [{}] 移动目标 Y={} 超出轨道范围 [{}, {}]，触发熔断暂停",
                            craneId, targetY, ascRail.getStartPoint(), ascRail.getEndPoint());
                    throw new BusinessException(String.format("ASC [%s] 移动目标超出轨道范围", craneId));
                }
            }
        } else {
            targetPoint = new Point(posX + distance, posY);
        }

        // 物理轴向锁定校验 - 从外部配置获取允许的轴向偏差
        double axisTolerance;
        if (deviceType == DeviceTypeEnum.QC) {
            axisTolerance = mapDataService.getParameter("qcAxisTolerance");
        } else if (deviceType == DeviceTypeEnum.ASC) {
            axisTolerance = mapDataService.getParameter("ascAxisTolerance");
        } else {
            throw new BusinessException(String.format("未知的起重机类型: %s", deviceType));
        }

        if (device instanceof QcDevice) {
            double deltaY = Math.abs(targetPoint.getY() - posY);
            if (deltaY > axisTolerance) {
                throw new BusinessException(String.format(
                        "物理违规: 岸桥(QC) [%s] 只能沿 X 轴水平移动！当前Y=%.1f，目标Y=%.1f，偏差=%.2f",
                        craneId, posY, targetPoint.getY(), deltaY));
            }
        } else if (device instanceof AscDevice) {
            double deltaX = Math.abs(targetPoint.getX() - posX);
            if (deltaX > axisTolerance) {
                throw new BusinessException(String.format(
                        "物理违规: 场桥(ASC) [%s] 只能沿 Y 轴垂直移动！当前X=%.1f，目标X=%.1f，偏差=%.2f",
                        craneId, posX, targetPoint.getX(), deltaX));
            }
        }

        // 路网校验
        double targetX = targetPoint.getX();
        double targetY = targetPoint.getY();
        boolean isOnPath = mapDataService.isPositionOnPath(device.getType().name(), targetX, targetY);
        if (!isOnPath) {
            log.error("脱轨异常: 起重机 [{}] 目标坐标 ({}, {}) 不在有效轨道路网上", craneId, targetX, targetY);
            throw new BusinessException(String.format(
                    "脱轨异常: 起重机 [%s] 目标坐标 (%.1f, %.1f) 不在港口有效路网上！", craneId, targetX, targetY));
        }

        //   纯离散耗时计算
        double travelDistance;
        if (device instanceof QcDevice) {
            travelDistance = Math.abs(targetX - posX);
        } else if (device instanceof AscDevice) {
            travelDistance = Math.abs(targetY - posY);
        } else {
            travelDistance = Math.abs(targetX - posX) + Math.abs(targetY - posY);
        }

        long durationMs;
        if (speed <= 0 || travelDistance <= 0) {
            durationMs = 0;
        } else {
            durationMs = (long) ((travelDistance / speed) * 1000);
        }

        long currentSimTime = context.getSimTime();
        long arrivalTime = currentSimTime + durationMs;

        //   设置运动字段
        device.setTargetX(targetX);
        device.setTargetY(targetY);
        device.setMoveSpeed(speed);
        device.setSpeed(speed);
        device.setState(DeviceStateEnum.MOVING);

        // 清空剩余目标列表（起重机通常是单段直线移动）
        device.setRemainingMoveTargets(null);

        // 设置虚拟插值字段（用于前端平滑动画）
        device.setMoveStartTime(currentSimTime);
        device.setMoveStartPosX(posX);
        device.setMoveStartPosY(posY);
        device.setExpectedArrivalTime(arrivalTime);

        //  直接调度 ARRIVAL 事件
        SimEvent arrivalEvent = engine.scheduleEvent(
                event.getEventId(),
                arrivalTime,
                EventTypeEnum.ARRIVAL,
                targetPoint
        );
        arrivalEvent.addSubject("CRANE", craneId);

        log.info("[CMD_CRANE_MOVE] 起重机 [{}] 调度 ARRIVAL: ({},{}) -> ({},{}), " +
                        "距离={}m, 速度={}m/s, 耗时={}ms, 到达时间={}",
                craneId,
                String.format("%.1f", posX), String.format("%.1f", posY),
                String.format("%.1f", targetX), String.format("%.1f", targetY),
                String.format("%.1f", travelDistance), speed, durationMs, arrivalTime);
    }
}
