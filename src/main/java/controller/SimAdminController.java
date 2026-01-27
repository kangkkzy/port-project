package controller;

import common.Result;
import lombok.Data;
import model.bo.GlobalContext;
import model.entity.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仿真场景管理接口：重置与读取
 */
@RestController
@RequestMapping("/sim/admin")
public class SimAdminController {

    /**
     * 清空当前场景 重置可以清零
     */
    @PostMapping("/reset")
    public Result reset() {
        // 锁住 GlobalContext 防止 Engine 线程在 Tick 的过程数据清空
        synchronized (GlobalContext.getInstance()) {
            GlobalContext.getInstance().clearAll();
            return Result.success("重置成功");
        }
    }

    /**
     * 从请求一个新的场景
     */
    @PostMapping("/load")
    public Result load(@RequestBody ScenarioLoadRequest req) {
        // 同样需要加锁，确保 "清空旧数据 -> 装载新数据" 这个过程是原子的。
        // 如果不加锁，可能出现仿真引擎读到了一半的集卡数据，但岸桥数据还没配好的中间状态。
        synchronized (GlobalContext.getInstance()) {
            GlobalContext ctx = GlobalContext.getInstance();

            //   先清场 防止新旧数据 ID 冲突或混杂
            ctx.clearAll();

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
}
