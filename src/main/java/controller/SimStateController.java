package controller;

import common.Result;
import common.consts.DeviceStateEnum;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.snapshot.*;
import model.entity.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.DevicePhysicsParamService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仿真状态查询接口
 */
@RestController
@RequestMapping("/sim/state")
public class SimStateController {

    private final SimulationEngine engine;
    private final DevicePhysicsParamService devicePhysicsParamService;

    public SimStateController(SimulationEngine engine, DevicePhysicsParamService devicePhysicsParamService) {
        this.engine = engine;
        this.devicePhysicsParamService = devicePhysicsParamService;
    }

    /**
     * 调试用：返回整个 GlobalContext，包含所有内部状态
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

        // 添加引擎熔断状态
        snapshot.setGlobalSuspended(engine.isGlobalSuspended());
        // 将枚举类型转换为字符串，便于前端展示
        java.util.Set<String> bizTypes = engine.getSuspendedBizTypes().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        snapshot.setSuspendedBizTypes(bizTypes);
        snapshot.setSuspendedEventIds(engine.getSuspendedEventIds());

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
     * 设置仿真播放速度
     * @param speed 速度倍率 (0.1 - 10.0)
     */
    @PostMapping("/speed")
    public Result setPlaybackSpeed(@RequestParam(defaultValue = "1.0") double speed) {
        if (speed < 0.1 || speed > 10.0) {
            return Result.error("速度范围应在 0.1 - 10.0 之间");
        }
        engine.setPlaybackSpeed(speed);
        return Result.success("播放速度已设置为 " + speed + "x");
    }

    /**
     * 获取当前播放速度
     */
    @GetMapping("/speed")
    public Result getPlaybackSpeed() {
        return Result.success("当前播放速度", engine.getPlaybackSpeed());
    }

    /**
     * 获取指定设备的物理参数
     * @param deviceId 设备ID
     */
    @GetMapping("/device/physics")
    public Result getDevicePhysicsParam(@RequestParam String deviceId) {
        DevicePhysicsParam param = devicePhysicsParamService.getPhysicsParam(deviceId);
        return Result.success("查询成功", param);
    }

    /**
     * 同步设备物理参数（从外部接口拉取）
     * @param deviceIds 设备ID列表（逗号分隔）
     */
    @PostMapping("/device/physics/sync")
    public Result syncDevicePhysicsParams(@RequestParam String deviceIds) {
        List<String> ids = Arrays.asList(deviceIds.split(","));
        devicePhysicsParamService.syncPhysicsParams(ids);
        return Result.success("同步成功");
    }

    /**
     * 预加载所有设备的物理参数
     */
    @PostMapping("/device/physics/preload")
    public Result preloadAllPhysicsParams() {
        GlobalContext ctx = GlobalContext.getInstance();
        List<String> allDeviceIds = new ArrayList<>();
        allDeviceIds.addAll(ctx.getTruckMap().keySet());
        allDeviceIds.addAll(ctx.getQcMap().keySet());
        allDeviceIds.addAll(ctx.getAscMap().keySet());

        devicePhysicsParamService.preloadAllParams(allDeviceIds);
        return Result.success("预加载完成，共 " + allDeviceIds.size() + " 台设备");
    }

    /**
     * 获取所有已缓存的设备物理参数
     */
    @GetMapping("/device/physics/all")
    public Result getAllPhysicsParams() {
        Map<String, DevicePhysicsParam> params = GlobalContext.getInstance().getDevicePhysicsParamMap();
        return Result.success("查询成功", params);
    }

    /**
     * 构建所有设备的快照（集卡、岸桥、龙门吊）
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

            // 移动中使用插值坐标（使用 getRealTimePosX/Y 方法）
            if (DeviceStateEnum.MOVING.equals(device.getState())) {
                dto.setPosX(device.getRealTimePosX(ctx.getSimTime()));
                dto.setPosY(device.getRealTimePosY(ctx.getSimTime()));
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

    /**
     * 构建围栏快照列表
     */
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

    /**
     * 构建充电桩快照列表
     */
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

    /**
     * 构建船舶快照列表
     */
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

    /**
     * 构建作业指令快照列表
     */
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

    /**
     * 构建集装箱快照列表
     */
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