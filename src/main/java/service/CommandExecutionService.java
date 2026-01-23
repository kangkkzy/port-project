package service;

import common.Result;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.entity.Fence;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommandExecutionService {

    private final GlobalContext context = GlobalContext.getInstance();

    @Autowired
    private SimulationEngine engine;

    // 外部算法下达路径
    public Result moveTruck(MoveCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error("车辆不存在");

        // 纯状态绑定
        truck.setWaypoints(req.getPoints());

        // 将外部移动指令转化为仿真事件 注入当前时间
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, truck.getId(), null);

        return Result.success();
    }

    // 外部算法绑定任务
    public Result assignTask(AssignTaskReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error("车辆不存在");

        // 纯状态绑定
        truck.setCurrentTask(req.getTaskId());
        return Result.success();
    }

    // 外部算法控制栅栏
    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error("栅栏不存在");

        FenceStateEnum targetStatus = FenceStateEnum.getByCode(req.getStatus());
        if (targetStatus == null) return Result.error("非法的栅栏状态码");

        //   接受外部算法的状态设定
        // 直接将外部要求的状态作为 Data 注入仿真事件
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.FENCE_CONTROL, req.getFenceId(), targetStatus);

        return Result.success();
    }

    // 外部算法推进时间
    public void stepTime(long stepMS) {
        // 根据外部指令 计算下一个目标推演时间
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}