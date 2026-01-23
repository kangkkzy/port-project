package service.algorithm.impl;

import common.Result;
import common.consts.ErrorCodes;
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
import service.algorithm.ExternalAlgorithmApi;

import java.util.LinkedList;

/**
 * 外部算法 API 的具体实现类
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    private final GlobalContext context = GlobalContext.getInstance();

    @Autowired
    private SimulationEngine engine;

    @Override
    public Result moveTruck(MoveCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.TRUCK_NOT_FOUND);

        // 将请求的 List<Point> 转换为实体需要的 Queue<Point>
        truck.setWaypoints(new LinkedList<>(req.getPoints()));

        // 将外部移动指令转化为仿真事件 注入当前时间
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, truck.getId(), null);

        return Result.success();
    }

    @Override
    public Result assignTask(AssignTaskReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.TRUCK_NOT_FOUND);

        truck.setCurrWiRefNo(req.getWiRefNo());
        return Result.success();
    }

    @Override
    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error(ErrorCodes.FENCE_NOT_FOUND);

        FenceStateEnum targetStatus = FenceStateEnum.getByCode(req.getStatus());
        if (targetStatus == null) return Result.error(ErrorCodes.INVALID_FENCE_STATUS);

        // 接受外部算法的状态设定 注入仿真事件
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.FENCE_CONTROL, req.getFenceId(), targetStatus);

        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        // 根据外部指令 计算下一个目标推演时间
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}