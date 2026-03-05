package model.dto.snapshot;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 仿真世界总快照 DTO
 */
@Data
public class PortSnapshotDto {

    /**
     * 仿真时间戳 (毫秒)
     */
    private long simTime;

    /**
     * 引擎是否处于全局熔断状态
     */
    private boolean globalSuspended;

    /**
     * 被挂起的业务类型集合
     */
    private Set<String> suspendedBizTypes;

    /**
     * 导致熔断的事件ID集合
     */
    private Set<String> suspendedEventIds;

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








