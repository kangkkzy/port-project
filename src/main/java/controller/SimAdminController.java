package controller;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimulationEngine;
import lombok.Data;
import engine.context.GlobalContext;
import model.entity.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.MapDataService;
import model.dto.config.MapPathDto;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 仿真场景管理接口：重置与读取
 */
@RestController
@RequestMapping("/sim/admin")
public class SimAdminController {

    private final SimulationEngine engine;
    private final MapDataService mapDataService;

    public SimAdminController(SimulationEngine engine, MapDataService mapDataService) {
        this.engine = engine;
        this.mapDataService = mapDataService;
    }

    /**
     * 清空当前场景（含全局数据与引擎事件队列/暂停状态，离散仿真重置后无待处理事件）
     * 先持引擎锁再持上下文锁，避免与单事件推进并发导致读到一半清空的数据
     */
    @PostMapping("/reset")
    public Result reset() {
        synchronized (engine) {
            synchronized (GlobalContext.getInstance()) {
                GlobalContext.getInstance().clearAll();
                engine.reset();
            }
        }
        return Result.success("重置成功");
    }

    /**
     * 一键初始化默认仿真场景（便于前端“加载场景 + 动画演示/调试”闭环）
     * - 地图路网由 MapConfigInitializer 启动时加载（map-config.json）
     * - 此处仅注入一套最小可运行实体：TRUCK/QC/ASC + 充电桩 + 围栏 + 船舶
     */
    @PostMapping("/init")
    public Result initDefaultScene() {
        synchronized (engine) {
            synchronized (GlobalContext.getInstance()) {
                GlobalContext ctx = GlobalContext.getInstance();
                ctx.clearAll();
                engine.reset();

                // 速度参数来自 map-config.json（缺失则使用保底值）
                double truckSpeed = safeGetParameter("truckSpeed", 5.0);
                double qcSpeed = safeGetParameter("qcSpeed", 10.0);
                double ascSpeed = safeGetParameter("ascSpeed", 8.0);

                // 轨道/道路位置来自 map-config.json paths（缺失则使用前端默认沙盘坐标）
                double truckRoadY = findFirstPathPosition("TRUCK_ROAD", "HORIZONTAL").orElse(200.0);
                double qcRailY = findFirstPathPosition("QC_RAIL", "HORIZONTAL").orElse(140.0);
                double ascRailX = findFirstPathPosition("ASC_RAIL", "VERTICAL").orElse(175.0);
                // ASC 还需要获取轨道的起止点范围，初始化在起点位置
                double[] ascRailRange = findFirstPathRange("ASC_RAIL", "VERTICAL").orElse(new double[]{0.0, 800.0});
                double ascRailStartY = ascRailRange[0];
                double ascRailEndY = ascRailRange[1];
                // 获取集卡道路的起止点范围，用于设置集卡的初始 X 坐标
                double[] truckRoadRange = findFirstPathRange("TRUCK_ROAD", "HORIZONTAL").orElse(new double[]{0.0, 800.0});
                double truckRoadStartX = truckRoadRange[0];

                // ============ 设备 ============
                Truck truck = new Truck();
                truck.setId("TRUCK_01");
                truck.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
                truck.setPosX(truckRoadStartX);  // 在集卡道路的起点 X
                truck.setPosY(truckRoadY);
                truck.setState(DeviceStateEnum.IDLE);
                truck.setSpeed(truckSpeed);
                truck.setPowerLevel(100.0);
                truck.setConsumeRate(0.01);
                ctx.getTruckMap().put(truck.getId(), truck);

                QcDevice qc = new QcDevice();
                qc.setId("QC_01");
                qc.setType(DeviceTypeEnum.QC);
                qc.setPosX(80.0);
                qc.setPosY(qcRailY);
                qc.setState(DeviceStateEnum.IDLE);
                qc.setSpeed(qcSpeed);
                ctx.getQcMap().put(qc.getId(), qc);

                AscDevice asc = new AscDevice();
                asc.setId("ASC_01");
                asc.setType(DeviceTypeEnum.ASC);
                asc.setPosX(ascRailX);
                asc.setPosY(ascRailStartY);  // ASC 在垂直轨道起点
                asc.setState(DeviceStateEnum.IDLE);
                asc.setSpeed(ascSpeed);
                ctx.getAscMap().put(asc.getId(), asc);

                // ============ 基础设施 ============
                Fence fence = new Fence();
                fence.setNodeId("FENCE_01");
                fence.setBlockCode("YARD_A");
                fence.setPosX(300.0);
                fence.setPosY(truckRoadY);
                fence.setRadius(18.0);
                fence.setSpeedLimit(2.5);
                fence.setStatus(FenceStateEnum.PASSABLE.getCode());
                ctx.getFenceMap().put(fence.getNodeId(), fence);

                ChargingStation station = new ChargingStation();
                station.setStationCode("CS_01");
                station.setStatus(DeviceStateEnum.IDLE.getCode());
                station.setPowName("POW_01");
                station.setBlockCode("YARD_C");
                station.setRowPosition(1);
                station.setPosX(750.0);
                station.setPosY(550.0);
                station.setChargeRate(5.0);
                ctx.getChargingStationMap().put(station.getStationCode(), station);

                // ============ 船舶 ============
                Vessel vessel = new Vessel();
                vessel.setVesselId("VESSEL_01");
                vessel.setVesselBerth("BERTH_01");
                vessel.setBerthLocation(120.0);
                vessel.setSideTo("L");
                vessel.setLength(160.0);
                ctx.getVesselMap().put(vessel.getVesselId(), vessel);

                return Result.success("初始化默认场景成功");
            }
        }
    }

    /**
     * 从请求装载新场景
     */
    @PostMapping("/load")
    public Result load(@RequestBody ScenarioLoadRequest req) {
        synchronized (engine) {
            synchronized (GlobalContext.getInstance()) {
                GlobalContext ctx = GlobalContext.getInstance();
                ctx.clearAll();
                engine.reset();

                //  逐个注入实体到内存 Map 中
                if (req.getTrucks() != null) {
                    req.getTrucks().forEach(t -> ctx.getTruckMap().put(t.getId(), t));
                }
                if (req.getQcDevices() != null) {
                    req.getQcDevices().forEach(q -> ctx.getQcMap().put(q.getId(), q));
                }
                if (req.getAscDevices() != null) {
                    req.getAscDevices().forEach(a -> ctx.getAscMap().put(a.getId(), a));
                }
                if (req.getFences() != null) {
                    // 栅栏以 NodeId (或者专门的 FenceId) 作为 Key
                    req.getFences().forEach(f -> ctx.getFenceMap().put(f.getNodeId(), f));
                }
                if (req.getChargingStations() != null) {
                    req.getChargingStations().forEach(s -> ctx.getChargingStationMap().put(s.getStationCode(), s));
                }
                if (req.getYardBlocks() != null) {
                    req.getYardBlocks().forEach(b -> ctx.getYardBlockMap().put(b.getBlockCode(), b));
                }

                //  装载业务数据
                if (req.getWorkInstructions() != null) {
                    req.getWorkInstructions().forEach(w -> ctx.getWorkInstructionMap().put(w.getWiRefNo(), w));
                }
                if (req.getContainers() != null) {
                    req.getContainers().forEach(c -> ctx.getContainerMap().put(c.getContainerId(), c));
                }

                return Result.success("场景装载成功");
            }
        }
    }

    /**
     * 场景装载请求体 DTO
     * 这是一个聚合对象 用来接的 JSON 包
     */
    @Data
    public static class ScenarioLoadRequest {
        // 物理设备
        private List<Truck> trucks;
        private List<QcDevice> qcDevices;
        private List<AscDevice> ascDevices;

        // 基础设施
        private List<Fence> fences;
        private List<ChargingStation> chargingStations;
        private List<YardBlock> yardBlocks;

        // 业务数据
        private List<WorkInstruction> workInstructions;
        private List<Container> containers;
    }

    private double safeGetParameter(String key, double fallback) {
        try {
            return mapDataService.getParameter(key);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Optional<Double> findFirstPathPosition(String pathType, String direction) {
        try {
            return mapDataService.getAllPaths().stream()
                    .filter(p -> pathType.equalsIgnoreCase(p.getPathType()))
                    .filter(p -> direction.equalsIgnoreCase(p.getDirection()))
                    .map(MapPathDto::getPosition)
                    .filter(v -> v != null)
                    .sorted(Comparator.naturalOrder())
                    .findFirst();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 获取指定类型和方向的第一条路径的起止点范围
     */
    private Optional<double[]> findFirstPathRange(String pathType, String direction) {
        try {
            return mapDataService.getAllPaths().stream()
                    .filter(p -> pathType.equalsIgnoreCase(p.getPathType()))
                    .filter(p -> direction.equalsIgnoreCase(p.getDirection()))
                    .filter(p -> p.getStartPoint() != null && p.getEndPoint() != null)
                    .sorted(Comparator.comparing(MapPathDto::getPosition))
                    .map(p -> new double[]{p.getStartPoint(), p.getEndPoint()})
                    .findFirst();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}