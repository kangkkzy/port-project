package model.dto.snapshot;

import lombok.Data;

import java.util.List;

/**
 * 栅栏状态快照
 */
@Data
public class FenceSnapshotDto {
    private String nodeId; // 栅栏id
    private String blockCode;  // 区域码
    private Double posX;  // 中心X坐标
    private Double posY; // 中心Y坐标
    private Double radius; // 范围
    private Double speedLimit;  // 速度限制
    private String status; // 状态

    /**
     * 当前被该栅栏阻挡的车辆ID列表
     */
    private List<String> waitingTrucks;
}