package controller;

import common.Result;
import common.consts.DeviceStateEnum;
import engine.context.GlobalContext;
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
     * 获取当前仿真时刻的状态快照（离散仿真：状态仅在事件处理时改变）
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
        snapshot.setVessels(buildVesselSnapshots(ctx));
        snapshot.setWorkInstructions(buildWiSnapshots(ctx));
        snapshot.setContainers(buildContainerSnapshots(ctx));

        return Result.success("查询成功", snapshot);
    }

    /**
     * 构建所有设备的快照
     */
    private List<DeviceSnapshotDto> buildDeviceSnapshots(GlobalContext ctx) {
        List<DeviceSnapshotDto> allDevices = new ArrayList<>();

        // 集卡 (Truck) - 修复：传入 ctx 参数
        allDevices.addAll(mapToSnapshot(ctx.getTruckMap().values(), ctx));

        // 岸桥 (QC) - 修复：传入 ctx 参数
        allDevices.addAll(mapToSnapshot(ctx.getQcMap().values(), ctx));

        // 龙门吊 (ASC) - 修复：传入 ctx 参数
        allDevices.addAll(mapToSnapshot(ctx.getAscMap().values(), ctx));

        return allDevices;
    }

    /**
     * 通用设备映射逻辑
     * 修复：在方法签名中增加 GlobalContext ctx 参数
     */
    private List<DeviceSnapshotDto> mapToSnapshot(Collection<? extends BaseDevice> devices, GlobalContext ctx) {
        return devices.stream().map(device -> {
            DeviceSnapshotDto dto = new DeviceSnapshotDto();

            // 基础属性 所有设备有
            dto.setId(device.getId());
            dto.setType(device.getType());
            dto.setState(device.getState());

            // 移动中使用插值坐标（否则离散仿真只在 ARRIVAL 时更新坐标，前端看不到运动过程）
            if (DeviceStateEnum.MOVING.equals(device.getState())) {
                Point p = device.getInterpolatedPos(ctx.getSimTime());
                dto.setPosX(p.getX());
                dto.setPosY(p.getY());
            } else {
                dto.setPosX(device.getPosX());
                dto.setPosY(device.getPosY());
            }
            dto.setCurrWiRefNo(device.getCurrWiRefNo());

            // 特有属性 (仅集卡)
            if (device instanceof Truck) {
                Truck truck = (Truck) device;
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

    private List<VesselSnapshotDto> buildVesselSnapshots(GlobalContext ctx) {
        return ctx.getVesselMap().values().stream().map(v -> {
            VesselSnapshotDto dto = new VesselSnapshotDto();
            dto.setVesselId(v.getVesselId());
            dto.setVesselBerth(v.getVesselBerth());
            dto.setBerthLocation(v.getBerthLocation());
            dto.setSideTo(v.getSideTo());
            dto.setLength(v.getLength());
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

    private List<ContainerSnapshotDto> buildContainerSnapshots(GlobalContext ctx) {
        return ctx.getContainerMap().values().stream().map(container -> {
            ContainerSnapshotDto dto = new ContainerSnapshotDto();
            dto.setContainerId(container.getContainerId());
            dto.setCurrentPos(container.getCurrentPos());
            dto.setStatus(container.getStatus());
            dto.setPosX(container.getPosX());
            dto.setPosY(container.getPosY());
            return dto;
        }).collect(Collectors.toList());
    }
}