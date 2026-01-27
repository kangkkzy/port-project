package controller;

import common.Result;
import model.dto.request.*;
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
}