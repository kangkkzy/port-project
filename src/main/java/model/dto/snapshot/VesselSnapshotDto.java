package model.dto.snapshot;

import lombok.Data;

/**
 * 船舶快照 DTO，用于前端地图展示
 */
@Data
public class VesselSnapshotDto {

    private String vesselId;       // 船只ID
    private String vesselBerth;    // 泊位号
    private Double berthLocation;  // 泊位沿岸坐标（用于映射到X轴）
    private String sideTo;         // 靠泊方向
    private Double length;         // 船长
}