package model.dto.snapshot;

import lombok.Data;

/**
 * 充电桩状态快照
 */
@Data
public class ChargingStationSnapshotDto {
    private String stationCode;
    private String status;
    private Double posX;
    private Double posY;

    private String truckId;
    private Double chargeRate;
}
