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
import service.algorithm.ExternalAlgorithmApi;
import service.algorithm.TaskDecisionService;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部算法 API 实现
 * 职责：仅做参数校验与事件转发，绝不补充默认值。
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEngine engine;
    private final TaskDecisionService taskDecisionService;

    @Autowired
    public ExternalAlgorithmServiceImpl(SimulationEngine engine,
                                        TaskDecisionService taskDecisionService) {
        this.engine = engine;
        this.taskDecisionService = taskDecisionService;
    }

    @Override
    public Result moveDevice(MoveCommandReq req) {
        // 1. 校验设备存在
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        // 2. [严格校验] 速度必须由外部指定
        if (req.getSpeed() == null || req.getSpeed() <= 0) {
            throw new BusinessException("移动指令错误: 必须明确指定移动速度 (speed)，且值必须大于0");
        }

        // 3. [严格校验] 目标点必须由外部指定
        if (req.getTargetPoint() == null) {
            throw new BusinessException("移动指令错误: 必须明确指定目标坐标 (targetPoint)");
        }

        // 4. 构造事件负载 (纯透传)
        Map<String, Object> payload = new HashMap<>();
        payload.put("target", req.getTargetPoint());
        payload.put("speed", req.getSpeed());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", device.getId());
        return Result.success();
    }

    @Override
    public Result moveCrane(CraneMoveReq req) {
        BaseDevice device = context.getDevice(req.getCraneId());
        if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        // [严格校验] 速度必须由外部指定
        if (req.getSpeed() == null || req.getSpeed() <= 0) {
            throw new BusinessException("起重机移动指令错误: 必须明确指定速度 (speed)");
        }

        if (req.getDistance() == null || req.getDistance() <= 0) {
            throw new BusinessException("起重机移动指令错误: 必须明确指定距离 (distance)");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("req", req);
        // 直接使用外部传入的速度，不查配置
        payload.put("speed", req.getSpeed());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_MOVE, payload);
        event.addSubject("CRANE", req.getCraneId());
        return Result.success();
    }

    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        AssignTaskResp resp = taskDecisionService.evaluateAndDecide(req);

        Map<String, Object> payload = new HashMap<>();
        payload.put("wiRefNo", req.getWiRefNo());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_ASSIGN_TASK, payload);
        event.addSubject("DEVICE", req.getDeviceId());

        return resp;
    }

    @Override
    public Result toggleFence(FenceControlReq req) {
        if (req.getStatus() == null) {
            throw new BusinessException("栅栏控制错误: 状态 (status) 不能为空");
        }
        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_FENCE_TOGGLE, FenceStateEnum.getByCode(req.getStatus()));
        event.addSubject("FENCE", req.getFenceId());
        return Result.success();
    }

    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        if (req.getDurationMS() <= 0) {
            throw new BusinessException("起重机操作错误: 必须明确指定操作耗时 (durationMS)");
        }

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_OP, req);
        event.addSubject("CRANE", crane.getId());
        return Result.success();
    }

    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        if (truck.getType() != DeviceTypeEnum.ELECTRIC_TRUCK) {
            throw new BusinessException("仅电集卡支持充电操作");
        }

        if (req.getStationId() == null) {
            throw new BusinessException("充电指令错误: 必须明确指定目标充电桩ID (stationId)");
        }

        ChargingStation station = context.getChargingStationMap().get(req.getStationId());
        if (station == null || !station.isAvailable()) {
            throw new BusinessException("充电桩不可用或正忙");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("stationId", station.getStationCode());

        SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CHARGE, payload);
        event.addSubject("TRUCK", truck.getId());
        return Result.success();
    }

    @Override
    public void stepTime(long stepMS) {
        if (stepMS <= 0) {
            throw new BusinessException("时间步进必须大于0");
        }
        engine.runUntil(context.getSimTime() + stepMS);
    }
}