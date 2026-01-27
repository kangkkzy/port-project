package service.algorithm.impl;

import common.Result;
import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.dto.request.*;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsService;
import service.algorithm.ExternalAlgorithmApi;
import service.algorithm.TaskDecisionService;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部算法 API 实现
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEngine engine;
    private final DevicePhysicsService physicsService;
    private final TaskDecisionService taskDecisionService;

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

        double speed;
        try {
            speed = physicsService.getHorizontalSpeed(device.getId());
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("points", req.getPoints());
        payload.put("speed", speed);

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", device.getId());
        return Result.success();
    }

    @Override
    public Result moveCrane(CraneMoveReq req) {
        BaseDevice device = context.getDevice(req.getCraneId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        double activeSpeed;
        try {
            if ("0421".equals(req.getMoveType().getCode())) {
                activeSpeed = physicsService.getHorizontalSpeed(device.getId());
            } else {
                activeSpeed = physicsService.getVerticalHoistSpeed(device.getId());
            }
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("req", req);
        payload.put("speed", activeSpeed);

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_MOVE, payload);
        event.addSubject("CRANE", req.getCraneId());
        return Result.success();
    }

    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        // 1. 基础校验
        AssignTaskResp resp = taskDecisionService.evaluateAndDecide(req);

        // 2. 生成任务指令事件
        Map<String, Object> payload = new HashMap<>();
        payload.put("wiRefNo", req.getWiRefNo());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_ASSIGN_TASK, payload);
        event.addSubject("DEVICE", req.getDeviceId());

        return resp;
    }

    @Override
    public Result toggleFence(FenceControlReq req) {
        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_FENCE_TOGGLE, FenceStateEnum.getByCode(req.getStatus()));
        event.addSubject("FENCE", req.getFenceId());
        return Result.success();
    }

    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);
        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_OP, req);
        event.addSubject("CRANE", crane.getId());
        return Result.success();
    }

    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        // [关键修正] 油集卡不支持充电
        if (truck.getType() != DeviceTypeEnum.ELECTRIC_TRUCK) {
            return Result.error("仅电集卡支持充电操作");
        }

        ChargingStation station = context.getChargingStationMap().get(req.getStationId());
        if (station == null || !station.isAvailable()) return Result.error("充电桩不可用");

        if (req.getPoints() == null || req.getPoints().isEmpty()) {
            return Result.error("必须提供前往充电桩的路径点");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("stationId", station.getStationCode());
        payload.put("points", req.getPoints());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CHARGE, payload);
        event.addSubject("TRUCK", truck.getId());
        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        engine.runUntil(context.getSimTime() + stepMS);
    }
}