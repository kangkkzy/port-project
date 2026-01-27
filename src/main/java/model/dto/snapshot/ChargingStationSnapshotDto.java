package model.dto.snapshot;

import lombok.Data;

/**
 * 充电桩状态快照
 */
@Data
public class ChargingStationSnapshotDto {

    /**
     * 充电桩编码
     */
    private String stationCode;

    /**
     * 充电桩当前状态
     */
    private String status;

    /**
     * 充电桩位置坐标 X
     */
    private Double posX;

    /**
     * 充电桩位置坐标 Y
     */
    private Double posY;

    /**
     * 当前正在充电的卡车ID
     */
    private String truckId;

    /**
     * 充电速率
     */
    private Double chargeRate;
}