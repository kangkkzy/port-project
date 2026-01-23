package common.util;

import model.entity.BaseDevice;
import model.entity.Point;

public class GisUtil {

    /**
     * 计算两点间的距离
     */
    public static double getDistance(Point p1, Point p2) {
        return Math.hypot(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }

    /**
     * 物理引擎：推动设备向目标点移动一步
     * @param device 要移动的设备
     * @param target 目标点坐标
     * @param speed  当前速度 (米/秒)
     * @param deltaMS 这次心跳经过了多少毫秒
     * @return true=已到达该点; false=还在路上
     */
    public static boolean moveTowards(BaseDevice device, Point target, double speed, long deltaMS) {
        // 这一步理论上能走多远 (距离 = 速度 x 时间)
        double stepDistance = speed * (deltaMS / 1000.0);

        Point current = new Point(device.getPosX(), device.getPosY());
        double totalDistance = getDistance(current, target);

        // 如果剩余距离比一步的距离还短 直接瞬移到终点
        if (totalDistance <= stepDistance) {
            device.setPosX(target.getX());
            device.setPosY(target.getY());
            return true;
        }

        // 还没到 按比例移动 X 和 Y (相似三角形)
        double ratio = stepDistance / totalDistance;
        double newX = device.getPosX() + (target.getX() - device.getPosX()) * ratio;
        double newY = device.getPosY() + (target.getY() - device.getPosY()) * ratio;

        device.setPosX(newX);
        device.setPosY(newY);
        return false;
    }
}