package controller;

import common.Result;
import model.dto.request.AssignTaskReq;
import model.dto.request.CraneOperationReq; // 【修复】引入大机作业请求类
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import service.algorithm.ExternalAlgorithmApi;

@RestController
@RequestMapping("/sim/command")
public class SimCommandController {

    // 依赖注入接口
    @Autowired
    private ExternalAlgorithmApi algorithmApi;

    // 控制移动
    @PostMapping("/move")
    public Result move(@RequestBody MoveCommandReq req) {
        return algorithmApi.moveDevice(req);
    }

    // 分配任务
    @PostMapping("/assign")
    public Result assign(@RequestBody AssignTaskReq req) {
        return algorithmApi.assignTask(req);
    }

    // 控制栅栏
    @PostMapping("/fence")
    public Result controlFence(@RequestBody FenceControlReq req) {
        return algorithmApi.toggleFence(req);
    }

    //  控制桥吊/龙门吊作业 (抓箱/放箱)
    @PostMapping("/crane/operate")
    public Result operateCrane(@RequestBody CraneOperationReq req) {
        return algorithmApi.operateCrane(req);
    }

    // 推进时间
    @PostMapping("/step")
    public Result stepTime(@RequestParam long stepMS) {
        algorithmApi.stepTime(stepMS);
        return Result.success();
    }
}