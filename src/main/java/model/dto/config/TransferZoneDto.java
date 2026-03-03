package model.dto.config;

/**
 * 交接区域配置DTO
 * 定义QC/ASC与集卡进行集装箱交接的指定区域
 */
public class TransferZoneDto {
    private String zoneId;
    private String name;

    /**
     * 区域类型: QC_TRANSFER 或 ASC_TRANSFER
     */
    private String type;

    /**
     * 区域描述
     */
    private String description;

    private Double qcPosition;      // QC所在的X坐标（用于QC交接区）
    private Double ascPosition;     // ASC所在的Y坐标（用于ASC交接区）
    private Double[] truckPositions; // 集卡所在的Y坐标数组
    private Double[] xRange;        // X坐标范围 [min, max]
    private Double[] yRange;        // Y坐标范围 [min, max]

    /**
     * 关联的泊位
     */
    private String relatedBerth;

    /**
     * 关联的堆场
     */
    private String relatedYard;

    /**
     * 关联的路径ID列表
     */
    private String[] relatedPaths;

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public String getRelatedBerth() { return relatedBerth; }
    public void setRelatedBerth(String relatedBerth) { this.relatedBerth = relatedBerth; }

    public String getRelatedYard() { return relatedYard; }
    public void setRelatedYard(String relatedYard) { this.relatedYard = relatedYard; }

    public String[] getRelatedPaths() { return relatedPaths; }
    public void setRelatedPaths(String[] relatedPaths) { this.relatedPaths = relatedPaths; }
}
