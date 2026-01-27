package controller;

import common.Result;
import model.bo.GlobalContext;
import model.dto.snapshot.*;
import model.entity.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仿真状态查询接口
 */
@RestController
@RequestMapping("/sim/state")
public class SimStateController {

    /**
     * 调试用所有接口
     */
    @GetMapping("/all")
    public Result getAllState() {
        return Result.success("查询成功", GlobalContext.getInstance());
    }

    /**
     * 获取当前仿真帧的完整快照
     */
    @GetMapping("/snapshot")
    public Result getSnapshot() {
        GlobalContext ctx = GlobalContext.getInstance();
        PortSnapshotDto snapshot = new PortSnapshotDto();
        snapshot.setSimTime(ctx.getSimTime());

        // 组装各类实体的快照数据
        snapshot.setDevices(buildDeviceSnapshots(ctx));
        snapshot.setFences(buildFenceSnapshots(ctx));
        snapshot.setChargingStations(buildChargingStationSnapshots(ctx));
        snapshot.setWorkInstructions(buildWiSnapshots(ctx));

        return Result.success("查询成功", snapshot);
    }

    /**
     * 构建所有设备的快照
     */
    private List<DeviceSnapshotDto> buildDeviceSnapshots(GlobalContext ctx) {
        List<DeviceSnapshotDto> allDevices = new ArrayList<>();

        //  集卡 (Truck)
        allDevices.addAll(mapToSnapshot(ctx.getTruckMap().values()));

        //  岸桥 (QC)
        allDevices.addAll(mapToSnapshot(ctx.getQcMap().values()));

        //  龙门吊 (ASC)
        allDevices.addAll(mapToSnapshot(ctx.getAscMap().values()));

        return allDevices;
    }

    /**
     * 通用设备映射逻辑
     */
    private List<DeviceSnapshotDto> mapToSnapshot(Collection<? extends BaseDevice> devices) {
        return devices.stream().map(device -> {
            DeviceSnapshotDto dto = new DeviceSnapshotDto();

            // 基础属性 所有设备有
            dto.setId(device.getId());
            dto.setType(device.getType());
            dto.setState(device.getState());
            dto.setPosX(device.getPosX());
            dto.setPosY(device.getPosY());
            dto.setCurrWiRefNo(device.getCurrWiRefNo());

            //  特有属性 (仅集卡
            if (device instanceof Truck truck) {
                dto.setPowerLevel(truck.getPowerLevel());
                dto.setNeedCharge(truck.isNeedCharge());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    private List<FenceSnapshotDto> buildFenceSnapshots(GlobalContext ctx) {
        return ctx.getFenceMap().values().stream().map(f -> {
            FenceSnapshotDto dto = new FenceSnapshotDto();
            dto.setNodeId(f.getNodeId());
            dto.setBlockCode(f.getBlockCode());
            // 静态属性
            dto.setPosX(f.getPosX());
            dto.setPosY(f.getPosY());
            dto.setRadius(f.getRadius());
            dto.setSpeedLimit(f.getSpeedLimit());
            // 动态状态 (是否阻塞 + 等待队列)
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
            // 占用情况
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
            // 起止点与状态流转
            dto.setFromPos(wi.getFromPos());
            dto.setToPos(wi.getToPos());
            dto.setWiStatus(wi.getWiStatus());
            dto.setDispatchCheId(wi.getDispatchCheId());
            return dto;
        }).collect(Collectors.toList());
    }
}