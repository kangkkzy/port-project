package service.algorithm.impl;

import common.config.PhysicsConfig;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import engine.SimEvent;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsParamService;
import service.algorithm.DevicePhysicsService;

import java.util.Collection;

/**
 * 设备物理推演服务实现
 * 实现曼哈顿正交移动（L型路径）和起重机单轴移动
 * 核心特性：动态物理到达触发 - 废除预估时间，完全由物理Tick真实触碰终点时触发事件
 * 速度通过 DevicePhysicsParamService 从外部接口获取（不在代码里硬编码）。
 */
@Service
public class DevicePhysicsServiceImpl implements DevicePhysicsService {

    private static final Logger log = LoggerFactory.getLogger(DevicePhysicsServiceImpl.class);

    @Autowired
    private PhysicsConfig physicsConfig;

    @Autowired
    private DevicePhysicsParamService devicePhysicsParamService;

    @Override
    public double getHorizontalSpeed(String deviceId) {
        return devicePhysicsParamService.getPhysicsParam(deviceId).getHorizontalSpeed();
    }

    @Override
    public double getVerticalHoistSpeed(String deviceId) {
        Double verticalSpeed = devicePhysicsParamService.getPhysicsParam(deviceId).getVerticalSpeed();
        return verticalSpeed != null ? verticalSpeed : 0.0;
    }

    @Override
    public double getPowerConsumeRate(String deviceId) {
        Double rate = devicePhysicsParamService.getPhysicsParam(deviceId).getPowerConsumeRate();
        return rate != null ? rate : 0.1;
    }

    @Override
    public double getLoadedConsumeCoefficient(String deviceId) {
        Double coefficient = devicePhysicsParamService.getPhysicsParam(deviceId).getLoadedConsumeCoefficient();
        return coefficient != null ? coefficient : 1.5;
    }

    @Override
    public double getSafePowerThreshold(String deviceId) {
        Double threshold = devicePhysicsParamService.getPhysicsParam(deviceId).getSafePowerThreshold();
        return threshold != null ? threshold : 20.0;
    }

    /**
     * 物理推演：更新所有移动中设备的坐标
     * - 集卡：曼哈顿正交移动（先走X轴，再走Y轴）
     * - 起重机：单轴移动（QC 沿 X，ASC 沿 Y）
     * - 动态到达触发：当物理坐标真实触碰终点时，自动触发 REPORT_IDLE 事件
     */
    @Override
    public void updateMovingDevices(GlobalContext context, SimulationEngine simulationEngine, double deltaTimeSec) {
        if (context == null || deltaTimeSec <= 0) return;

        Collection<BaseDevice> devices = context.getAllDevices();
        if (devices == null) return;

        for (BaseDevice device : devices) {
            if (device.getState() != DeviceStateEnum.MOVING) continue;
            if (device.getTargetX() == null || device.getTargetY() == null) continue;

            double speed = device.getMoveSpeed() != null ? device.getMoveSpeed() : getDefaultSpeed(device);
            double step = speed * deltaTimeSec;

            double currentX = device.getPosX();
            double currentY = device.getPosY();
            double targetX = device.getTargetX();
            double targetY = device.getTargetY();

            DeviceTypeEnum type = device.getType();
            boolean isTruck = type == DeviceTypeEnum.INTERNAL_TRUCK
                    || type == DeviceTypeEnum.EXTERNAL_TRUCK
                    || type == DeviceTypeEnum.OIL_TRUCK
                    || type == DeviceTypeEnum.ELECTRIC_TRUCK;

            if (isTruck) {
                // === 集卡：曼哈顿正交移动（L型路径）===
                // 严格先走完 X 轴，再走 Y 轴
                double dx = targetX - currentX;
                double dy = targetY - currentY;
                boolean reachedX = Math.abs(dx) <= getArrivalThreshold();
                boolean reachedY = Math.abs(dy) <= getArrivalThreshold();

                if (!reachedX) {
                    // X 轴未对齐，沿 X 轴移动
                    double dirX = Math.signum(dx);
                    double newX = currentX + dirX * step;
                    // 防止 overshoot（超调）
                    if ((dirX > 0 && newX > targetX) || (dirX < 0 && newX < targetX)) {
                        newX = targetX;
                    }
                    device.setPosX(newX);
                } else if (!reachedY) {
                    // X 已对齐，沿 Y 轴移动
                    double dirY = Math.signum(dy);
                    double newY = currentY + dirY * step;
                    if ((dirY > 0 && newY > targetY) || (dirY < 0 && newY < targetY)) {
                        newY = targetY;
                    }
                    device.setPosY(newY);
                }
            } else {
                // === 起重机：单轴移动 ===
                if (type == DeviceTypeEnum.QC) {
                    // QC 沿 X 轴移动
                    double dx = targetX - currentX;
                    if (Math.abs(dx) > getArrivalThreshold()) {
                        double dir = Math.signum(dx);
                        double newX = currentX + dir * step;
                        if ((dir > 0 && newX > targetX) || (dir < 0 && newX < targetX)) {
                            newX = targetX;
                        }
                        device.setPosX(newX);
                    }
                } else if (type == DeviceTypeEnum.ASC) {
                    // ASC 沿 Y 轴移动
                    double dy = targetY - currentY;
                    if (Math.abs(dy) > getArrivalThreshold()) {
                        double dir = Math.signum(dy);
                        double newY = currentY + dir * step;
                        if ((dir > 0 && newY > targetY) || (dir < 0 && newY < targetY)) {
                            newY = targetY;
                        }
                        device.setPosY(newY);
                    }
                }
            }

            // 物理坐标日志落库输出，用于排查轨迹
            log.info("[Physics-Tick] 设备 [{}] 正在移动 -> 当前坐标: ({}, {}), 目标: ({}, {})",
                    device.getId(), String.format("%.1f", device.getPosX()), String.format("%.1f", device.getPosY()),
                    String.format("%.1f", targetX), String.format("%.1f", targetY));

            // 【核心修复】：动态物理到达判定！当物理坐标真实贴合目标时，动态触发结算！
            boolean reachedX = Math.abs(device.getTargetX() - device.getPosX()) <= getArrivalThreshold();
            boolean reachedY = Math.abs(device.getTargetY() - device.getPosY()) <= getArrivalThreshold();

            if (reachedX && reachedY) {
                // 完美对齐终点
                device.setPosX(device.getTargetX());
                device.setPosY(device.getTargetY());
                device.setTargetX(null);
                device.setTargetY(null);
                device.setMoveSpeed(null);
                device.setState(DeviceStateEnum.IDLE);

                log.info(">>> [Physics-Arrival] 物理触碰终点！设备 [{}] 已停稳在 ({}, {}), 状态置为 IDLE。",
                        device.getId(), String.format("%.1f", device.getPosX()), String.format("%.1f", device.getPosY()));

                // 动态生成报闲事件入队，唤醒业务状态机
                if (simulationEngine != null) {
                    SimEvent idleEvent = new SimEvent(null, context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                    idleEvent.addSubject("DEVICE", device.getId());
                    if (type == DeviceTypeEnum.QC || type == DeviceTypeEnum.ASC) {
                        idleEvent.addSubject("CRANE", device.getId());
                    } else {
                        idleEvent.addSubject("TRUCK", device.getId());
                    }
                    simulationEngine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.REPORT_IDLE, null);
                }
            }
        }
    }

    /** 根据设备类型通过外部接口获取速度 */
    private double getDefaultSpeed(BaseDevice device) {
        String deviceId = device.getId();
        if (deviceId == null) {
            deviceId = "";
        }
        return devicePhysicsParamService.getPhysicsParam(deviceId).getHorizontalSpeed();
    }

    /** 获取到达判定阈值（从配置读取） */
    private double getArrivalThreshold() {
        if (physicsConfig != null) {
            return physicsConfig.getArrivalThreshold();
        }
        return 0.5;
    }
}
