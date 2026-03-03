package model.dto.config;

/**
 * 交接区域配置DTO
 * 定义QC/ASC与集卡进行集装箱交接的指定区域
 */
public class TransferZoneDto {
    private String zoneId;
    private String name;
    private Double qcPosition;      // QC所在的X坐标（用于QC交接区）
    private Double ascPosition;     // ASC所在的Y坐标（用于ASC交接区）
    private Double[] truckPositions; // 集卡所在的Y坐标数组
    private Double[] xRange;        // X坐标范围 [min, max]
    private Double[] yRange;        // Y坐标范围 [min, max]

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getQcPosition() { return qcPosition; }
    public void setQcPosition(Double qcPosition) { this.qcPosition = qcPosition; }

    public Double getAscPosition() { return ascPosition; }
    public void setAscPosition(Double ascPosition) { this.ascPosition = ascPosition; }

    public Double[] getTruckPositions() { return truckPositions; }
    public void setTruckPositions(Double[] truckPositions) { this.truckPositions = truckPositions; }

    public Double[] getxRange() { return xRange; }
    public void setxRange(Double[] xRange) { this.xRange = xRange; }

    public Double[] getyRange() { return yRange; }
    public void setyRange(Double[] yRange) { this.yRange = yRange; }
}
