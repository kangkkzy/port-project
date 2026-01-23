package controller;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import service.CommandExecutionService;

@RestController
@RequestMapping("/sim/command")
public class SimCommandController {

    @Autowired
    private CommandExecutionService commandService;

    // 控制移动 外部算法下达车辆路径规划
    @PostMapping("/move")
    public Result move(@RequestBody MoveCommandReq req) {
        return commandService.moveTruck(req);
    }

    // 分配任务 外部算法进行任务指派
    @PostMapping("/assign")
    public Result assign(@RequestBody AssignTaskReq req) {
        return commandService.assignTask(req);
    }

    // 控制栅栏 外部算法直接设定栅栏状态
    @PostMapping("/fence")
    public Result controlFence(@RequestBody FenceControlReq req) {
        return commandService.toggleFence(req);
    }

    // 推进时间 外部算法驱动仿真世界的时间流动
    @PostMapping("/step")
    public Result stepTime(@RequestParam long stepMS) {
        commandService.stepTime(stepMS);
        return Result.success();
    }
}
