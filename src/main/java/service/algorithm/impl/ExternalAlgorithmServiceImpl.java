package service.algorithm.impl;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.ChargeCommandReq;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Fence;
import model.entity.Point;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsService;
import service.algorithm.ExternalAlgorithmApi;
import service.algorithm.TaskDecisionService;

import java.util.LinkedList;

/**
 * 外部算法 API 的具体实现类 (适配器)
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEngine engine;
    private final DevicePhysicsService physicsService;
    private final TaskDecisionService taskDecisionService; // 新增决策大脑

    @Autowired
    public ExternalAlgorithmServiceImpl(SimulationEngine engine,
                                        DevicePhysicsService physicsService,
                                        TaskDecisionService taskDecisionService) {
        this.engine = engine;
        this.physicsService = physicsService;
        this.taskDecisionService = taskDecisionService;
    }

    @Override
    public Result moveDevice(MoveCommandReq req) {
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        try {
            double accurateSpeed = physicsService.getHorizontalSpeed(device.getId());
            device.setSpeed(accurateSpeed);
        } catch (BusinessException e) {
            return Result.error("缺少集卡 [" + device.getId() + "] 的物理速度配置，禁止移动。");
        }

        device.setWaypoints(new LinkedList<>(req.getPoints()));
        SimEvent moveEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveEvent.addSubject("TRUCK", device.getId());

        return Result.success();
    }

    @Override
    public Result moveCrane(CraneMoveReq req) {
        BaseDevice device = context.getDevice(req.getCraneId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        double activeSpeed = 0.0;

        try {
            if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
                activeSpeed = physicsService.getHorizontalSpeed(device.getId());
            } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
                activeSpeed = physicsService.getVerticalHoistSpeed(device.getId());
            }
        } catch (BusinessException e) {
            return Result.error("缺少物理速度配置，作业取消。");
        }

        if (activeSpeed <= 0.0) return Result.error("设备速度配置异常");

        long travelTimeMS = (long) ((req.getDistance() / activeSpeed) * 1000);

        device.setState(req.getMoveType());
        SimEvent arrEvent = engine.scheduleEvent(null, context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, null);
        arrEvent.addSubject("CRANE", device.getId());

        return Result.success();
    }

    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        // 解耦：将具体的预测与调度决策完全委托给外部算法的决策服务
        return taskDecisionService.evaluateAndDecide(req);
    }

    @Override
    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error(ErrorCodes.FENCE_NOT_FOUND);

        FenceStateEnum targetStatus = FenceStateEnum.getByCode(req.getStatus());
        if (targetStatus == null) return Result.error(ErrorCodes.INVALID_FENCE_STATUS);

        SimEvent fenceEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.FENCE_CONTROL, targetStatus);
        fenceEvent.addSubject("FENCE", req.getFenceId());

        return Result.success();
    }

    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        long finishTime = context.getSimTime() + req.getDurationMS();
        SimEvent opEvent = engine.scheduleEvent(null, finishTime, req.getAction(), null);
        opEvent.addSubject("CRANE", crane.getId());

        return Result.success();
    }

    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        ChargingStation station = context.getChargingStationMap().get(req.getStationId());
        if (station == null) return Result.error("指定的充电桩不存在");

        if (!station.isAvailable()) return Result.error("该充电桩当前被占用或不可用");

        station.setTruckId(truck.getId());
        station.setStatus(DeviceStateEnum.WORKING.getCode());

        truck.setNeedCharge(true);
        truck.setTargetStationId(station.getStationCode());

        Point stationPos = new Point(station.getPosX(), station.getPosY());
        truck.getWaypoints().clear();
        truck.getWaypoints().add(stationPos);

        SimEvent moveEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveEvent.addSubject("TRUCK", truck.getId());

        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}