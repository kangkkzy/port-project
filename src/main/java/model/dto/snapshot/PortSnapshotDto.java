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
    /**
     * 靠泊船舶快照（用于前端地图展示）
     */
    private List<VesselSnapshotDto> vessels;
    private List<WorkInstructionSnapshotDto> workInstructions;

    /**
     * 集装箱快照（用于前端渲染）
     */
    private List<ContainerSnapshotDto> containers;
}




