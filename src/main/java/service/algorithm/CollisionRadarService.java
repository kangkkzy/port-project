package service.algorithm;

import common.exception.BusinessException;
import common.exception.CollisionException;
import engine.context.GlobalContext;
import model.entity.TrajectorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 时空防碰雷达服务
 * 负责校验设备轨迹是否会发生碰撞，并在通过校验后将轨迹注册到全局注册表
 */
@Service
public class CollisionRadarService {

    private static final Logger log = LoggerFactory.getLogger(CollisionRadarService.class);

    /**
     * 校验新轨迹片段并注册到全局轨迹表
     * @param deviceId 设备ID
     * @param newSegments 要添加的新轨迹片段列表
     * @param context 全局上下文
     * @param safeDistance 安全距离（米），小于此距离视为碰撞
     */
    public void validateAndRegister(String deviceId, List<TrajectorySegment> newSegments,
                                    GlobalContext context, double safeDistance) {
        if (newSegments == null || newSegments.isEmpty()) {
            return;
        }

        // 获取当前所有其他设备的轨迹
        List<TrajectorySegment> existingTrajectories = context.getFutureTrajectories().stream()
                .filter(seg -> !seg.getDeviceId().equals(deviceId))
                .collect(java.util.stream.Collectors.toList());

        // 遍历新轨迹的每个片段
        for (TrajectorySegment newSeg : newSegments) {
            // 与所有现有轨迹进行碰撞检测
            for (TrajectorySegment existingSeg : existingTrajectories) {
                checkCollision(deviceId, newSeg, existingSeg, safeDistance);
            }
        }

        // 校验通过，将新轨迹添加到注册表
        for (TrajectorySegment segment : newSegments) {
            context.addTrajectorySegment(segment);
        }

        log.info("[CollisionRadar] 设备 [{}] 轨迹校验通过，已注册 {} 个轨迹片段",
                deviceId, newSegments.size());
    }

    /**
     * 检测两个轨迹片段是否会发生碰撞
     */
    private void checkCollision(String deviceIdA, TrajectorySegment segA,
                                TrajectorySegment segB, double safeDistance) {
        // 计算时间交集
        long overlapStart = Math.max(segA.getStartTime(), segB.getStartTime());
        long overlapEnd = Math.min(segA.getEndTime(), segB.getEndTime());

        // 如果没有时间交集，安全
        if (overlapStart >= overlapEnd) {
            return;
        }

        // 计算在这个时间窗口内，两车实时坐标距离平方的最小值
        double[] result = calculateMinDistanceSquared(
                segA.getStartX(), segA.getStartY(), segA.getEndX(), segA.getEndY(),
                segA.getStartTime(), segA.getEndTime(),
                segB.getStartX(), segB.getStartY(), segB.getEndX(), segB.getEndY(),
                segB.getStartTime(), segB.getEndTime(),
                overlapStart, overlapEnd
        );

        double minDistanceSquared = result[0];
        double collisionTime = result[1];
        double collisionX = result[2];
        double collisionY = result[3];

        // 检查是否碰撞
        if (minDistanceSquared < safeDistance * safeDistance) {
            String errorMsg = String.format(
                    "严重碰撞预警：设备 %s 与 设备 %s 将在仿真时间 %d 发生碰撞，交汇坐标附近为 (%.1f, %.1f)",
                    deviceIdA, segB.getDeviceId(), (long) collisionTime, collisionX, collisionY);
            log.error("[CollisionRadar] {}", errorMsg);
            throw new CollisionException(
                    errorMsg,
                    deviceIdA,
                    segB.getDeviceId(),
                    (long) collisionTime,
                    collisionX,
                    collisionY
            );
        }
    }

    /**
     * 计算两个线性运动轨迹在指定时间窗口内的最小距离平方
     * @return [最小距离平方, 碰撞发生时间, 碰撞X坐标, 碰撞Y坐标]
     */
    private double[] calculateMinDistanceSquared(
            double x1s, double y1s, double x1e, double y1e, long t1s, long t1e,
            double x2s, double y2s, double x2e, double y2e, long t2s, long t2e,
            long overlapStart, long overlapEnd) {

        // 计算速度向量（米/毫秒）
        double v1x = (t1e > t1s) ? (x1e - x1s) / (t1e - t1s) : 0;
        double v1y = (t1e > t1s) ? (y1e - y1s) / (t1e - t1s) : 0;
        double v2x = (t2e > t2s) ? (x2e - x2s) / (t2e - t2s) : 0;
        double v2y = (t2e > t2s) ? (y2e - y2s) / (t2e - t2s) : 0;

        // 相对位置和相对速度
        double dx = x2s - x1s;
        double dy = y2s - y1s;
        double dvx = v2x - v1x;
        double dvy = v2y - v1y;

        // 距离平方函数: D(t)^2 = (dx + dvx*t)^2 + (dy + dvy*t)^2
        // 展开: = (dvx^2 + dvy^2)*t^2 + 2*(dx*dvx + dy*dvy)*t + (dx^2 + dy^2)
        // 这是一个二次函数: at^2 + bt + c
        double a = dvx * dvx + dvy * dvy;
        double b = 2 * (dx * dvx + dy * dvy);
        double c = dx * dx + dy * dy;

        double minDistanceSquared;
        double minTime;

        if (a < 1e-10) {
            // 相对速度接近零，两车距离近似恒定
            minDistanceSquared = c;
            minTime = overlapStart;
        } else {
            // 二次函数求导: 2at + b = 0 => t = -b/(2a)
            double criticalPoint = -b / (2 * a);

            if (criticalPoint >= overlapStart && criticalPoint <= overlapEnd) {
                // 极值点在时间窗口内
                minDistanceSquared = a * criticalPoint * criticalPoint + b * criticalPoint + c;
                minTime = criticalPoint;
            } else {
                // 极值点在时间窗口外，最小值在端点
                double distStart = a * overlapStart * overlapStart + b * overlapStart + c;
                double distEnd = a * overlapEnd * overlapEnd + b * overlapEnd + c;
                if (distStart < distEnd) {
                    minDistanceSquared = distStart;
                    minTime = overlapStart;
                } else {
                    minDistanceSquared = distEnd;
                    minTime = overlapEnd;
                }
            }
        }

        // 计算碰撞位置（最近点）
        // 将时间转换为相对于两车起始时间的偏移
        double t1 = (minTime - t1s);
        double t2 = (minTime - t2s);

        // 确保时间偏移不为负（设备可能还未开始移动）
        if (t1 < 0) t1 = 0;
        if (t2 < 0) t2 = 0;

        // 计算两车在最近距离时刻的位置
        double x1 = x1s + v1x * t1;
        double y1 = y1s + v1y * t1;
        double x2 = x2s + v2x * t2;
        double y2 = y2s + v2y * t2;

        double collisionX = (x1 + x2) / 2;
        double collisionY = (y1 + y2) / 2;

        return new double[]{minDistanceSquared, minTime, collisionX, collisionY};
    }
}

