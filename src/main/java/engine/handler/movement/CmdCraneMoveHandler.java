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
import model.entity.BaseDevice;
import model.entity.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;
import model.dto.config.MapPathDto;

/**
 * 吊具移动指令
 */
@Component
public class CmdCraneMoveHandler implements SimEventHandler {

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

        // 🟢 修改点：直接将载荷转为 CraneMoveReq 对象
        CraneMoveReq req = (CraneMoveReq) event.getData();
        Double speed = req.getSpeed();
        if (speed == null || speed <= 0) {
            throw new BusinessException("speed 参数无效");
        }
        double distance = req.getDistance() != null ? req.getDistance() : 0;

        // 计算目标坐标
        double posX = device.getPosX() != null ? device.getPosX() : 0;
        double posY = device.getPosY() != null ? device.getPosY() : 0;
        Point targetPoint;

        DeviceTypeEnum deviceType = device.getType();
        double tolerance = 3.0;
        try {
            if (deviceType == DeviceTypeEnum.QC) {
                tolerance = mapDataService.getParameter("qcRailTolerance");
            } else if (deviceType == DeviceTypeEnum.ASC) {
                tolerance = mapDataService.getParameter("ascRailTolerance");
            }
        } catch (Exception e) {
            // 使用默认值
        }

        if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
            // 水平移动：仅允许 QC 在其红色轨道上水平移动
            if (deviceType == DeviceTypeEnum.ASC) {
                // ASC 被严格限制在紫色虚线轨道上，仅允许沿轨道做垂直移动
                throw new BusinessException(String.format("ASC [%s] 只能沿紫色轨道做垂直移动，禁止水平移动", craneId));
            }

            targetPoint = new Point(posX + distance, posY);

            if (deviceType == DeviceTypeEnum.QC) {
                // QC 只能在 QC 轨道上水平移动，Y 坐标必须保持不变
                if (Math.abs(distance) > 0.1) {
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

        device.setSpeed(speed);
        device.setCurrentTargetPos(targetPoint);

        SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveStart.addSubject("CRANE", craneId);
    }
}