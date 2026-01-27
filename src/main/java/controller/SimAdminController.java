package controller;

import common.Result;
import model.bo.GlobalContext;
import model.entity.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仿真场景管理接口：重置与装载
 */
@RestController
@RequestMapping("/sim/admin")
public class SimAdminController {

    /**
     * 清空当前场景
     */
    @PostMapping("/reset")
    public Result reset() {
        synchronized (GlobalContext.getInstance()) {
            GlobalContext.getInstance().clearAll();
            return Result.success("重置成功");
        }
    }

    /**
     * 从请求体装载一个新的场景
     *
     * 注意：这里直接使用实体类作为载体，不包含任何调度逻辑，只负责初始化数据。
     */
    @PostMapping("/load")
    public Result load(@RequestBody ScenarioLoadRequest req) {
        synchronized (GlobalContext.getInstance()) {
            GlobalContext ctx = GlobalContext.getInstance();
            ctx.clearAll();

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
     * 场景装载请求体
     */
    public static class ScenarioLoadRequest {
        private List<Truck> trucks;
        private List<QcDevice> qcDevices;
        private List<AscDevice> ascDevices;
        private List<Fence> fences;
        private List<ChargingStation> chargingStations;
        private List<YardBlock> yardBlocks;
        private List<WorkInstruction> workInstructions;
        private List<Container> containers;

        public List<Truck> getTrucks() { return trucks; }
        public void setTrucks(List<Truck> trucks) { this.trucks = trucks; }

        public List<QcDevice> getQcDevices() { return qcDevices; }
        public void setQcDevices(List<QcDevice> qcDevices) { this.qcDevices = qcDevices; }

        public List<AscDevice> getAscDevices() { return ascDevices; }
        public void setAscDevices(List<AscDevice> ascDevices) { this.ascDevices = ascDevices; }

        public List<Fence> getFences() { return fences; }
        public void setFences(List<Fence> fences) { this.fences = fences; }

        public List<ChargingStation> getChargingStations() { return chargingStations; }
        public void setChargingStations(List<ChargingStation> chargingStations) { this.chargingStations = chargingStations; }

        public List<YardBlock> getYardBlocks() { return yardBlocks; }
        public void setYardBlocks(List<YardBlock> yardBlocks) { this.yardBlocks = yardBlocks; }

        public List<WorkInstruction> getWorkInstructions() { return workInstructions; }
        public void setWorkInstructions(List<WorkInstruction> workInstructions) { this.workInstructions = workInstructions; }

        public List<Container> getContainers() { return containers; }
        public void setContainers(List<Container> containers) { this.containers = containers; }
    }
}
