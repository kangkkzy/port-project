package model.dto.config;

import lombok.Data;

/**
 * 交接区域配置DTO
 * 定义QC/ASC与集卡进行集装箱交接的指定区域
 */
@Data
public class TransferZoneDto {

    private String zoneId;
    private String name;

    /** 区域类型: QC_YARD 或 YARD_TRUCK */
    private String type;

    private String description;

    private Double qcPosition;       // QC所在的X坐标（用于QC交接区）
    private Double ascPosition;      // ASC所在的Y坐标（用于ASC交接区）
    private Double[] truckPositions; // 集卡所在的Y坐标数组
    private Double[] xRange;         // X坐标范围 [min, max]（兼容旧格式）
    private Double[] yRange;         // Y坐标范围 [min, max]（兼容旧格式）

    /** Bounding Box 格式（与 xRange/yRange 二选一，推荐使用此格式） */
    private Double posX;    // 左上角 X 坐标
    private Double posY;    // 左上角 Y 坐标
    private Double width;   // 宽度（沿 X 轴）
    private Double length;  // 长度（沿 Y 轴）

    private String relatedBerth;
    private String relatedYard;
    private String[] relatedPaths;
}
