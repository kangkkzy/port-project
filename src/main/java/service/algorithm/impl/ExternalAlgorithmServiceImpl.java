package service.algorithm.impl;

import common.Result;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.entity.BaseDevice;
import model.entity.Fence;
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
    public Result moveDevice(MoveCommandReq req) {
        // 【核心变更】不再只获取集卡，而是获取任何种类的设备
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        device.setWaypoints(new LinkedList<>(req.getPoints()));
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, device.getId(), null);

        return Result.success();
    }

    @Override
    public Result assignTask(AssignTaskReq req) {
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        device.setCurrWiRefNo(req.getWiRefNo());
        return Result.success();
    }

    @Override
    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error(ErrorCodes.FENCE_NOT_FOUND);

        FenceStateEnum targetStatus = FenceStateEnum.getByCode(req.getStatus());
        if (targetStatus == null) return Result.error(ErrorCodes.INVALID_FENCE_STATUS);

        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.FENCE_CONTROL, req.getFenceId(), targetStatus);

        return Result.success();
    }

    /**
     *  处理桥吊/龙门吊的作业耗时：将作业行为注册到未来的时间点完成
     */
    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        // 计算未来的动作完成时间 = 当前时间 + 该作业预计耗时
        long finishTime = context.getSimTime() + req.getDurationMS();

        // 注册作业完成事件
        engine.scheduleEvent(finishTime, req.getAction(), crane.getId(), null);

        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}