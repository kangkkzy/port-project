package controller;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import service.CommandService;

@RestController
@RequestMapping("/sim/command")
public class SimCommandController {

    @Autowired
    private CommandService commandService;

    //  控制移动
    @PostMapping("/move")
    public Result move(@RequestBody MoveCommandReq req) {
        return commandService.moveTruck(req);
    }

    //  分配任务
    @PostMapping("/assign")
    public Result assign(@RequestBody AssignTaskReq req) {
        return commandService.assignTask(req);
    }

    //  开关栅栏
    @PostMapping("/fence")
    public Result controlFence(@RequestBody FenceControlReq req) {
        return commandService.toggleFence(req);
    }
}
