package model.dto.snapshot;

import lombok.Data;

import java.util.List;

/**
 * 仿真世界总快照 DTO
 */
@Data
public class PortSnapshotDto {

    /**
     * 仿真时间戳 (毫秒)
     */
    private long simTime;

    private List<DeviceSnapshotDto> devices;
    private List<FenceSnapshotDto> fences;
    private List<ChargingStationSnapshotDto> chargingStations;
    private List<WorkInstructionSnapshotDto> workInstructions;
}

