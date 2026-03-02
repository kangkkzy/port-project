package model.dto.config;

import java.util.List;

/**
 * 地图路径配置数据传输对象
 * 用于前端获取路径信息和路径验证
 *
 * JSON 格式示例:
 * {
 *   "pathType": "TRUCK_ROAD",     // 路径类型: TRUCK_ROAD / QC_RAIL / ASC_RAIL
 *   "direction": "HORIZONTAL",    // 方向: HORIZONTAL(水平) / VERTICAL(垂直)
 *   "position": 200,              // 路径坐标值（水平路径为Y坐标，垂直路径为X坐标）
 *   "startPoint": 0,              // 路径起点坐标
 *   "endPoint": 800,              // 路径终点坐标
 *   "keyPoints": [0, 100, 200, 300, 400, 500, 600, 700, 800]  // 可选，关键点列表（用于显示）
 * }
 */
public class MapPathDto {

    /**
     * 路径类型
     * TRUCK_ROAD - 集卡道路
     * QC_RAIL - 桥吊轨道
     * ASC_RAIL - 龙门吊轨道
     */
    private String pathType;

    /**
     * 路径方向
     * HORIZONTAL - 水平方向（y坐标固定，x变化）
     * VERTICAL - 垂直方向（x坐标固定，y变化）
     */
    private String direction;

    /**
     * 路径坐标值
     * 水平路径：表示Y坐标
     * 垂直路径：表示X坐标
     */
    private Double position;

    /**
     * 路径起点坐标（米）
     */
    private Double startPoint;

    /**
     * 路径终点坐标（米）
     */
    private Double endPoint;

    /**
     * 路径上的关键点坐标列表（可选，用于显示）
     */
    private List<Double> keyPoints;

    // ==================== 业务方法 ====================

    /**
     * 判断给定点是否在该路径上
     * @param x X坐标
     * @param y Y坐标
     * @param tolerance 允许的误差范围（米）
     * @return 是否在路径上
     */
    public boolean containsPoint(double x, double y, double tolerance) {
        if ("HORIZONTAL".equals(direction)) {
            // 水平路径：检查Y坐标是否在position上，X坐标是否在起止范围内
            return Math.abs(y - position) <= tolerance
                    && x >= startPoint - tolerance && x <= endPoint + tolerance;
        } else if ("VERTICAL".equals(direction)) {
            // 垂直路径：检查X坐标是否在position上，Y坐标是否在起止范围内
            return Math.abs(x - position) <= tolerance
                    && y >= startPoint - tolerance && y <= endPoint + tolerance;
        }
        return false;
    }

    // ==================== Getter/Setter ====================

    public String getPathType() { return pathType; }
    public void setPathType(String pathType) { this.pathType = pathType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Double getPosition() { return position; }
    public void setPosition(Double position) { this.position = position; }

    public Double getStartPoint() { return startPoint; }
    public void setStartPoint(Double startPoint) { this.startPoint = startPoint; }

    public Double getEndPoint() { return endPoint; }
    public void setEndPoint(Double endPoint) { this.endPoint = endPoint; }

    public List<Double> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<Double> keyPoints) { this.keyPoints = keyPoints; }
}
