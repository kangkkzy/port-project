package controller;

import common.Result;
import model.bo.GlobalContext;
import model.dto.snapshot.*;
import model.entity.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sim/state")
public class SimStateController {

    /**
     * 兼容旧接口：直接返回内部 GlobalContext。
     * 建议新接入方优先使用 /snapshot。
     */
    @GetMapping("/all")
    public Result getAllState() {
        return Result.success("查询成功", GlobalContext.getInstance());
    }

    /**
     * 推荐接口：返回解耦后的仿真世界快照 DTO
     */
    @GetMapping("/snapshot")
    public Result getSnapshot() {
        GlobalContext ctx = GlobalContext.getInstance();
        PortSnapshotDto snapshot = new PortSnapshotDto();
        snapshot.setSimTime(ctx.getSimTime());

        // 设备快照
        snapshot.setDevices(buildDeviceSnapshots(ctx));
        // 栅栏
        snapshot.setFences(buildFenceSnapshots(ctx));
        // 充电桩
        snapshot.setChargingStations(buildChargingStationSnapshots(ctx));
        // 作业指令
        snapshot.setWorkInstructions(buildWiSnapshots(ctx));

        return Result.success("查询成功", snapshot);
    }

    private List<DeviceSnapshotDto> buildDeviceSnapshots(GlobalContext ctx) {
        Map<String, Truck> trucks = ctx.getTruckMap();
        Map<String, QcDevice> qcs = ctx.getQcMap();
        Map<String, AscDevice> ascs = ctx.getAscMap();

        return
                // 集卡
                trucks.values().stream().map(t -> {
                    DeviceSnapshotDto dto = new DeviceSnapshotDto();
                    dto.setId(t.getId());
                    dto.setType(t.getType());
                    dto.setState(t.getState());
                    dto.setPosX(t.getPosX());
                    dto.setPosY(t.getPosY());
                    dto.setPowerLevel(t.getPowerLevel());
                    dto.setNeedCharge(t.isNeedCharge());
                    dto.setCurrWiRefNo(t.getCurrWiRefNo());
                    return dto;
                }).collect(Collectors.toList())
                // 简化：目前只把 Truck 暴露给外部算法，如需 ASC/QC 也暴露，可以在此处追加映射。
                ;
    }

    private List<FenceSnapshotDto> buildFenceSnapshots(GlobalContext ctx) {
        return ctx.getFenceMap().values().stream().map(f -> {
            FenceSnapshotDto dto = new FenceSnapshotDto();
            dto.setNodeId(f.getNodeId());
            dto.setBlockCode(f.getBlockCode());
            dto.setPosX(f.getPosX());
            dto.setPosY(f.getPosY());
            dto.setRadius(f.getRadius());
            dto.setSpeedLimit(f.getSpeedLimit());
            dto.setStatus(f.getStatus());
            dto.setWaitingTrucks(f.getWaitingTrucks());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<ChargingStationSnapshotDto> buildChargingStationSnapshots(GlobalContext ctx) {
        return ctx.getChargingStationMap().values().stream().map(s -> {
            ChargingStationSnapshotDto dto = new ChargingStationSnapshotDto();
            dto.setStationCode(s.getStationCode());
            dto.setStatus(s.getStatus());
            dto.setPosX(s.getPosX());
            dto.setPosY(s.getPosY());
            dto.setTruckId(s.getTruckId());
            dto.setChargeRate(s.getChargeRate());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<WorkInstructionSnapshotDto> buildWiSnapshots(GlobalContext ctx) {
        return ctx.getWorkInstructionMap().values().stream().map(wi -> {
            WorkInstructionSnapshotDto dto = new WorkInstructionSnapshotDto();
            dto.setWiRefNo(wi.getWiRefNo());
            dto.setContainerId(wi.getContainerId());
            dto.setMoveKind(wi.getMoveKind());
            dto.setFromPos(wi.getFromPos());
            dto.setToPos(wi.getToPos());
            dto.setWiStatus(wi.getWiStatus());
            dto.setDispatchCheId(wi.getDispatchCheId());
            return dto;
        }).collect(Collectors.toList());
    }
}
