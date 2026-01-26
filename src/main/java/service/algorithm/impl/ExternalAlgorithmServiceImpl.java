package service.algorithm.impl;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.request.ChargeCommandReq;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.FenceControlReq;
import model.dto.request.MoveCommandReq;
import model.entity.AscDevice;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Fence;
import model.entity.Point;
import model.entity.QcDevice;
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
    public Result moveDevice(MoveCommandReq req) {
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

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

        // 判断移动维度，获取对应的速度
        if (DeviceStateEnum.MOVE_HORIZONTAL.equals(req.getMoveType())) {
            // 龙门吊/桥吊横向移动速度
            activeSpeed = device.getSpeed();
        } else if (DeviceStateEnum.MOVE_VERTICAL.equals(req.getMoveType())) {
            // 吊具垂直移动速度 (从龙门吊/桥吊特有字段中获取)
            if (device instanceof QcDevice) {
                activeSpeed = ((QcDevice) device).getHoistSpeed();
            } else if (device instanceof AscDevice) {
                activeSpeed = ((AscDevice) device).getHoistSpeed();
            }
        }

        if (activeSpeed <= 0.0) return Result.error("设备速度配置异常，无法计算移动时间");

        // 时间 = 距离 / 速度 (乘以1000转换为毫秒)
        long travelTimeMS = (long) ((req.getDistance() / activeSpeed) * 1000);

        // 设置龙门吊/桥吊当前工作状态 (横向或垂直)
        device.setState(req.getMoveType());

        // 向未来注册到达事件
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

    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        ChargingStation station = context.getChargingStationMap().get(req.getStationId());
        if (station == null) return Result.error("指定的充电桩不存在");

        if (!station.isAvailable()) return Result.error("该充电桩当前被占用或不可用");

        //  锁定充电桩
        station.setTruckId(truck.getId());
        station.setStatus(DeviceStateEnum.WORKING.getCode());

        //  更新集卡状态
        truck.setNeedCharge(true);
        truck.setTargetStationId(station.getStationCode());

        //  将充电桩位置设为集卡的移动目标
        Point stationPos = new Point(station.getPosX(), station.getPosY());
        truck.getWaypoints().clear();
        truck.getWaypoints().add(stationPos);

        //  触发移动事件
        engine.scheduleEvent(context.getSimTime(), EventTypeEnum.MOVE_START, truck.getId(), null);

        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}