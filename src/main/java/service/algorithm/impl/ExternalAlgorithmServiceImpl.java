package service.algorithm.impl;

import common.Result;
import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.*;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.ChargingStation;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.ExternalAlgorithmApi;
import service.algorithm.MapDataService;
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
    private final TaskDecisionService taskDecisionService;
    private final MapDataService mapDataService;

    @Autowired
    public ExternalAlgorithmServiceImpl(SimulationEngine engine,
                                        TaskDecisionService taskDecisionService,
                                        MapDataService mapDataService) {
        this.engine = engine;
        this.taskDecisionService = taskDecisionService;
        this.mapDataService = mapDataService;
    }

    /**
     * 下发集卡移动指令
     */
    @Override
    public Result moveDevice(MoveCommandReq req) {
        synchronized (context) {
            //  校验设备存在
            BaseDevice device = context.getDevice(req.getTruckId());
            if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

            //  速度必须由外部指定
            if (req.getSpeed() == null || req.getSpeed() <= 0) {
                throw new BusinessException("移动指令错误: 必须明确指定移动速度 (speed)，且值必须大于0");
            }

            //  目标点必须由外部指定
            if (req.getTargetPoint() == null) {
                throw new BusinessException("移动指令错误: 必须明确指定目标坐标 (targetPoint)");
            }

            //  验证目标点是否在有效路径上
            double targetX = req.getTargetPoint().getX();
            double targetY = req.getTargetPoint().getY();
            String deviceType = device.getType().name();

            if (!mapDataService.isPositionOnPath(deviceType, targetX, targetY)) {
                throw new BusinessException(String.format(
                        "集卡移动指令错误: 目标位置 (%.1f, %.1f) 不在有效道路路径上，设备类型: %s",
                        targetX, targetY, deviceType
                ));
            }

            //  构造事件负载 - 使用 MoveCommandReq 对象
            MoveCommandReq payload = new MoveCommandReq();
            payload.setTruckId(req.getTruckId());
            payload.setTargetPoint(req.getTargetPoint());
            payload.setSpeed(req.getSpeed());
            payload.setEnforcePathValidation(true);

            SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_MOVE, payload);
            event.addSubject("TRUCK", device.getId());
            return Result.success();
        }
    }

    /**
     * 下发 岸桥/龙门吊 移动指令
     */
    @Override
    public Result moveCrane(CraneMoveReq req) {
        synchronized (context) {
            BaseDevice device = context.getDevice(req.getCraneId());
            if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

            //  速度必须由外部指定
            if (req.getSpeed() == null || req.getSpeed() <= 0) {
                throw new BusinessException("起重机移动指令错误: 必须明确指定速度 (speed)");
            }

            // 距离可以為負值(向相反方向移動)，但不能為null
            if (req.getDistance() == null) {
                throw new BusinessException("起重机移动指令错误: 必须明确指定距离 (distance)");
            }

            // 使用 CraneMoveReq 对象作为 payload
            SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_MOVE, req);
            event.addSubject("CRANE", req.getCraneId());
            return Result.success();
        }
    }

    /**
     * 下发任务指派指令
     */
    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        synchronized (context) {
            AssignTaskResp resp = taskDecisionService.assignTask(req);

            Map<String, Object> payload = new HashMap<>();
            payload.put("wiRefNo", req.getWiRefNo());

            SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_ASSIGN_TASK, payload);
            event.addSubject("DEVICE", req.getDeviceId());

            return resp;
        }
    }

    /**
     * 控制交通栅栏状态
     */
    @Override
    public Result toggleFence(FenceControlReq req) {
        synchronized (context) {
            if (req.getStatus() == null) {
                throw new BusinessException("栅栏控制错误: 状态 (status) 不能为空");
            }
            SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_FENCE_TOGGLE, FenceStateEnum.getByCode(req.getStatus()));
            event.addSubject("FENCE", req.getFenceId());
            return Result.success();
        }
    }

    /**
     * 控制起重机执行具体作业（抓/放）
     */
    @Override
    public Result operateCrane(CraneOperationReq req) {
        synchronized (context) {
            BaseDevice crane = context.getDevice(req.getCraneId());
            if (crane == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

            if (req.getDurationMS() <= 0) {
                throw new BusinessException("起重机操作错误: 必须明确指定操作耗时 (durationMS)");
            }

            SimEvent event = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_CRANE_OP, req);
            event.addSubject("CRANE", crane.getId());
            return Result.success();
        }
    }

    /**
     * 触发集卡充电流程
     */
    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        synchronized (context) {
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
    }

    /**
     * 取消指定事件
     */
    @Override
    public Result cancelEvent(String eventId) {
        synchronized (context) {
            if (eventId == null || eventId.trim().isEmpty()) {
                throw new BusinessException("事件ID不能为空");
            }
            boolean cancelled = engine.cancelEvent(eventId);
            if (cancelled) {
                return Result.success("事件已取消");
            } else {
                return Result.error("事件不存在或已被处理");
            }
        }
    }

    /**
     * 单事件推进：处理下一个事件
     * 这是离散仿真的核心机制：一次只处理一个事件，确保全局时钟严格按事件时间推进
     * 决策和路径规划由外部算法实现，仿真引擎只负责按时间顺序处理事件
     *
     * @return 处理的事件信息，如果没有事件则返回null
     */
    @Override
    public model.dto.snapshot.EventLogEntryDto stepNextEvent() {
        synchronized (context) {
            SimEvent processedEvent = engine.step();
            if (processedEvent == null) {
                return null;
            }

            // 构造返回的事件信息
            model.dto.snapshot.EventLogEntryDto eventDto = new model.dto.snapshot.EventLogEntryDto();
            eventDto.setSimTime(processedEvent.getTriggerTime());
            eventDto.setType(processedEvent.getType());
            eventDto.setEventId(processedEvent.getEventId());
            eventDto.setParentEventId(processedEvent.getParentEventId());
            eventDto.setSubjects(processedEvent.getSubjects());

            return eventDto;
        }
    }
}