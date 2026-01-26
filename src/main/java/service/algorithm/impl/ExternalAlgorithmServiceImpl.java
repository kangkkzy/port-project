package service.algorithm.impl;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.exception.BusinessException;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.ChargeCommandReq;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Fence;
import model.entity.Point;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsService;
import service.algorithm.ExternalAlgorithmApi;

import java.util.LinkedList;

/**
 * 外部算法 API 的具体实现类
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEngine engine;
    private final DevicePhysicsService physicsService;
    @Autowired
    public ExternalAlgorithmServiceImpl(SimulationEngine engine, DevicePhysicsService physicsService) {
        this.engine = engine;
        this.physicsService = physicsService;
    }

    @Override
    public Result moveDevice(MoveCommandReq req) {
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        try {
            // 在起步前 动态获取该集卡的当前精确速度配置
            double accurateSpeed = physicsService.getHorizontalSpeed(device.getId());
            device.setSpeed(accurateSpeed);
        } catch (BusinessException e) {
            // 没有配置默认速度 直接报错
            return Result.error("缺少集卡 [" + device.getId() + "] 的物理速度配置，禁止移动。");
        }

        device.setWaypoints(new LinkedList<>(req.getPoints()));
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, device.getId(), null);

        return Result.success();
    }

    /**
     * 龙门吊/桥吊的移动（横向/垂直）
     */
    @Override
    public Result moveCrane(CraneMoveReq req) {
        BaseDevice device = context.getDevice(req.getCraneId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        double activeSpeed = 0.0;

        try {
            // 根据移动维度 精确获取对应速度
            if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
                activeSpeed = physicsService.getHorizontalSpeed(device.getId());
            } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
                activeSpeed = physicsService.getVerticalHoistSpeed(device.getId());
            }
        } catch (BusinessException e) {
            // 查不到速度直接报错
            return Result.error("缺少大机 [" + device.getId() + "] 维度[" + req.getMoveType().getDesc() + "]的物理速度配置，作业取消。");
        }

        if (activeSpeed <= 0.0) return Result.error("设备速度配置小于等于0，物理参数异常");

        // 时间 = 距离 / 速度 (乘以1000转换为毫秒)
        long travelTimeMS = (long) ((req.getDistance() / activeSpeed) * 1000);

        device.setState(req.getMoveType());
        engine.scheduleEvent(context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, device.getId(), null);

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

    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        long finishTime = context.getSimTime() + req.getDurationMS();
        engine.scheduleEvent(finishTime, req.getAction(), crane.getId(), null);

        return Result.success();
    }

    // 充电时时间
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

        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, truck.getId(), null);

        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}