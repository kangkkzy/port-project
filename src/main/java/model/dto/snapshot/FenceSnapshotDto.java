package model.dto.snapshot;

import lombok.Data;

import java.util.List;

/**
 * 栅栏状态快照
 */
@Data
public class FenceSnapshotDto {
    private String nodeId;
    private String blockCode;
    private Double posX;
    private Double posY;
    private Double radius;
    private Double speedLimit;
    private String status;

    /**
     * 当前被该栅栏阻挡的车辆ID列表
     */
    private List<String> waitingTrucks;
}