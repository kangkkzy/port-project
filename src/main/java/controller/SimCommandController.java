package controller;

import common.Result;
import model.dto.request.*;
import model.dto.snapshot.*;
import model.bo.GlobalContext;
import model.dto.response.AssignTaskResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import service.algorithm.ExternalAlgorithmApi;

/**
 * 仿真系统命令控制器
 */
@RestController
@RequestMapping("/sim/command")
public class SimCommandController {

    private final ExternalAlgorithmApi algorithmApi;
    @Autowired
    public SimCommandController(ExternalAlgorithmApi algorithmApi) {
        this.algorithmApi = algorithmApi;
    }

    //  移动控制 集卡/桥吊/龙门吊

    @PostMapping("/truck/move")
    public Result moveTruck(@RequestBody MoveCommandReq req) {
        return algorithmApi.moveDevice(req);
    }

    @PostMapping("/crane/move")
    public Result moveCrane(@RequestBody CraneMoveReq req) {
        return algorithmApi.moveCrane(req);
    }

    //   业务与环境控制 处理任务分配 设备具体操作及环境设施变更

    @PostMapping("/assign")
    public Result assign(@RequestBody AssignTaskReq req) {
        AssignTaskResp resp = algorithmApi.assignTask(req);
        return Result.success(resp);
    }
    // 吊起操作的接口
    @PostMapping("/crane/operate")
    public Result operateCrane(@RequestBody CraneOperationReq req) {
        return algorithmApi.operateCrane(req);
    }
    // 围栏
    @PostMapping("/fence")
    public Result controlFence(@RequestBody FenceControlReq req) {
        return algorithmApi.toggleFence(req);
    }
    // 电集卡充电
    @PostMapping("/truck/charge")
    public Result chargeTruck(@RequestBody ChargeCommandReq req) {
        return algorithmApi.chargeTruck(req);
    }

    //  仿真时钟

    @PostMapping("/step")
    public Result stepTime(@RequestParam long stepMS) {
        algorithmApi.stepTime(stepMS);
        return Result.success();
    }

    /**
     * 取消事件
     */
    @PostMapping("/event/cancel")
    public Result cancelEvent(@RequestBody model.dto.request.CancelEventReq req) {
        return algorithmApi.cancelEvent(req.getEventId());
    }

    /**
     * 批量接收这一帧所有的控制指令 并推进时间 返回新的状态快照
     */
    @PostMapping("/stepWithCommands")
    public Result stepWithCommands(@RequestBody StepWithCommandsReq req) {
        //  批量下发指令
        if (req.getTruckMoves() != null) {
            req.getTruckMoves().forEach(algorithmApi::moveDevice);
        }
        if (req.getCraneMoves() != null) {
            req.getCraneMoves().forEach(algorithmApi::moveCrane);
        }
        if (req.getFenceControls() != null) {
            req.getFenceControls().forEach(algorithmApi::toggleFence);
        }
        if (req.getCraneOps() != null) {
            req.getCraneOps().forEach(algorithmApi::operateCrane);
        }
        if (req.getChargeCommands() != null) {
            req.getChargeCommands().forEach(algorithmApi::chargeTruck);
        }

        //  推进时间
        algorithmApi.stepTime(req.getStepMS());

        //  返回当前快照
        GlobalContext ctx = GlobalContext.getInstance();
        PortSnapshotDto snapshot = new PortSnapshotDto();
        snapshot.setSimTime(ctx.getSimTime());

        // 设备
        snapshot.setDevices(
                ctx.getTruckMap().values().stream().map(t -> {
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
                }).toList()
        );

        // 栅栏
        snapshot.setFences(
                ctx.getFenceMap().values().stream().map(f -> {
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
                }).toList()
        );

        // 充电桩
        snapshot.setChargingStations(
                ctx.getChargingStationMap().values().stream().map(s -> {
                    ChargingStationSnapshotDto dto = new ChargingStationSnapshotDto();
                    dto.setStationCode(s.getStationCode());
                    dto.setStatus(s.getStatus());
                    dto.setPosX(s.getPosX());
                    dto.setPosY(s.getPosY());
                    dto.setTruckId(s.getTruckId());
                    dto.setChargeRate(s.getChargeRate());
                    return dto;
                }).toList()
        );

        // 作业指令
        snapshot.setWorkInstructions(
                ctx.getWorkInstructionMap().values().stream().map(wi -> {
                    WorkInstructionSnapshotDto dto = new WorkInstructionSnapshotDto();
                    dto.setWiRefNo(wi.getWiRefNo());
                    dto.setContainerId(wi.getContainerId());
                    dto.setMoveKind(wi.getMoveKind());
                    dto.setFromPos(wi.getFromPos());
                    dto.setToPos(wi.getToPos());
                    dto.setWiStatus(wi.getWiStatus());
                    dto.setDispatchCheId(wi.getDispatchCheId());
                    return dto;
                }).toList()
        );

        return Result.success("step 完成", snapshot);
    }
}