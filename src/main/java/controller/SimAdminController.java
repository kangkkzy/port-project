package controller;

import common.Result;
import engine.SimulationEngine;
import engine.SimEvent;
import lombok.Data;
import engine.context.GlobalContext;
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
@RequestMapping("/sim/engine")
public class SimAdminController {

    private final SimulationEngine engine;

    public SimAdminController(SimulationEngine engine) {
        this.engine = engine;
    }

    /**
     * 单步执行接口
     * 当系统处于暂停状态时，调用该接口只会从事件队列中取出并执行1个事件
     * 供前端慢动作单步调试
     */
    @PostMapping("/step")
    public Result step() {
        SimEvent event = engine.step();
        if (event == null) {
            return Result.error("单步执行失败：引擎正在运行或队列为空");
        }
        return Result.success("单步执行成功", event);
    }

    /**
     * 获取引擎运行状态
     */
    @PostMapping("/status")
    public Result getStatus() {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("isRunning", engine.isRunning());
        res.put("isPaused", engine.isPaused());
        res.put("globalSuspended", engine.isGlobalSuspended());
        res.put("simTime", engine.getSimTime());
        return Result.success("引擎状态", res);
    }

    /**
     * 暂停引擎
     */
    @PostMapping("/pause")
    public Result pause() {
        engine.pause();
        return Result.success("引擎已暂停");
    }

    /**
     * 恢复引擎运行
     */
    @PostMapping("/resume")
    public Result resume() {
        engine.resume();
        return Result.success("引擎已恢复运行");
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
}
