package common.util;

import model.entity.Point;

public class GisUtil {

    public static double getDistance(Point p1, Point p2) {
        return Math.hypot(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }

    /**
     * 计算通过当前速度 走完这段路需要多少毫秒
     */
    public static long calculateTravelTimeMS(Point current, Point target, double speed) {
        double distance = getDistance(current, target);
        // 时间 = 距离 / 速度
        return (long) ((distance / speed) * 1000);
    }
}