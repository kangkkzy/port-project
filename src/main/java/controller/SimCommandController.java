package controller;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.ChargeCommandReq;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import service.algorithm.ExternalAlgorithmApi;

@RestController
@RequestMapping("/sim/command")
public class SimCommandController {

    @Autowired
    private ExternalAlgorithmApi algorithmApi;

    // 移动控制

    @PostMapping("/truck/move")
    public Result moveTruck(@RequestBody MoveCommandReq req) {
        return algorithmApi.moveDevice(req);
    }

    @PostMapping("/crane/move")
    public Result moveCrane(@RequestBody CraneMoveReq req) {
        return algorithmApi.moveCrane(req);
    }

    //  业务与环境控制

    @PostMapping("/assign")
    public Result assign(@RequestBody AssignTaskReq req) {
        return algorithmApi.assignTask(req);
    }

    @PostMapping("/crane/operate")
    public Result operateCrane(@RequestBody CraneOperationReq req) {
        return algorithmApi.operateCrane(req);
    }

    @PostMapping("/fence")
    public Result controlFence(@RequestBody FenceControlReq req) {
        return algorithmApi.toggleFence(req);
    }

    @PostMapping("/truck/charge")
    public Result chargeTruck(@RequestBody ChargeCommandReq req) {
        return algorithmApi.chargeTruck(req);
    }

    //  仿真时钟推演

    @PostMapping("/step")
    public Result stepTime(@RequestParam long stepMS) {
        algorithmApi.stepTime(stepMS);
        return Result.success();
    }
}