package common.util;

import model.entity.Point;

public class GisUtil {

    public static double getDistance(Point p1, Point p2) {
        if (p1 == null || p2 == null) {
            return 0;
        }
        Double x1 = p1.getX() != null ? p1.getX() : 0;
        Double y1 = p1.getY() != null ? p1.getY() : 0;
        Double x2 = p2.getX() != null ? p2.getX() : 0;
        Double y2 = p2.getY() != null ? p2.getY() : 0;
        return Math.hypot(x1 - x2, y1 - y2);
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