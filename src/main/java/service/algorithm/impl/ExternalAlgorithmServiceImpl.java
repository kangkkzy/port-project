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
 * 外部算法 API 的具体实现类
 */
@Service
public class ExternalAlgorithmServiceImpl implements ExternalAlgorithmApi {

    // 校验设备是否存在以及当前状态
    private final GlobalContext context = GlobalContext.getInstance();
    // 生成和注册未来事件
    private final SimulationEngine engine;
    // 引入物理配置
    private final DevicePhysicsService physicsService;
    // 引入决策
    private final TaskDecisionService taskDecisionService;

    @Autowired
    public ExternalAlgorithmServiceImpl(SimulationEngine engine,
                                        DevicePhysicsService physicsService,
                                        TaskDecisionService taskDecisionService) {
        this.engine = engine;
        this.physicsService = physicsService;
        this.taskDecisionService = taskDecisionService;
    }

    /**
     * 【移动控制】
     * 业务场景 外部算法规划好了一条集卡路线 下发给仿真系统执行移动
     */
    @Override
    public Result moveDevice(MoveCommandReq req) {
        //  实体校验
        BaseDevice device = context.getDevice(req.getTruckId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        //   物理属性注入 (Fail-fast：查不到配置直接拒绝移动并报错
        try {
            double accurateSpeed = physicsService.getHorizontalSpeed(device.getId());
            device.setSpeed(accurateSpeed);
        } catch (BusinessException e) {
            return Result.error("缺少集卡 [" + device.getId() + "] 的物理速度配置，禁止移动。");
        }

        //  路径装填与事件触发
        device.setWaypoints(new LinkedList<>(req.getPoints()));
        SimEvent moveEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveEvent.addSubject("TRUCK", device.getId());

        return Result.success();
    }

    /**
     * 【龙门吊/桥吊 移动控制】
     * 业务场景：控制岸桥或龙门吊沿着轨道横移 或者起升/下降吊具
     */
    @Override
    public Result moveCrane(CraneMoveReq req) {
        BaseDevice device = context.getDevice(req.getCraneId());
        if (device == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        double activeSpeed = 0.0;

        //  根据请求类型（横向/垂直），动态读取不同的物理速度配置
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

        //  物理耗时计算：耗时(ms) = 距离 / 速度 * 1000
        long travelTimeMS = (long) ((req.getDistance() / activeSpeed) * 1000);

        device.setState(req.getMoveType());
        //  直接在未来注册到达事件
        SimEvent arrEvent = engine.scheduleEvent(null, context.getSimTime() + travelTimeMS, EventTypeEnum.ARRIVAL, null);
        arrEvent.addSubject("CRANE", device.getId());

        return Result.success();
    }

    /**
     * 【任务指派与电量决策
     */
    @Override
    public AssignTaskResp assignTask(AssignTaskReq req) {
        // 外部算法判断
        return taskDecisionService.evaluateAndDecide(req);
    }

    /**
     * 【交通控制】
     * 业务场景：外部算法根据拥堵 远程控制某个路口虚拟栅栏的锁死或放行
     */
    @Override
    public Result toggleFence(FenceControlReq req) {
        Fence fence = context.getFenceMap().get(req.getFenceId());
        if (fence == null) return Result.error(ErrorCodes.FENCE_NOT_FOUND);

        FenceStateEnum targetStatus = FenceStateEnum.getByCode(req.getStatus());
        if (targetStatus == null) return Result.error(ErrorCodes.INVALID_FENCE_STATUS);

        // 生成栅栏控制事件 推演引擎消费该事件时 会顺便把被堵住的集卡唤醒
        SimEvent fenceEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.FENCE_CONTROL, targetStatus);
        fenceEvent.addSubject("FENCE", req.getFenceId());

        return Result.success();
    }

    /**
     * 【龙门吊/桥吊 作业控制】
     * 业务场景：控制龙门吊/桥吊 进行抓箱(FETCH)或放箱(PUT)动作。
     */
    @Override
    public Result operateCrane(CraneOperationReq req) {
        BaseDevice crane = context.getDevice(req.getCraneId());
        if (crane == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        //  req.getDurationMS() (动作耗时) 可以是算法预估的也可以是读取的
        long finishTime = context.getSimTime() + req.getDurationMS();
        // 在未来的 finishTime 点 触发动作完成事件 (如 FETCH_DONE / PUT_DONE)
        // 引擎收到该事件后 会自动触发下一步的事件
        SimEvent opEvent = engine.scheduleEvent(null, finishTime, req.getAction(), null);
        opEvent.addSubject("CRANE", crane.getId());

        return Result.success();
    }

    /**
     *  充电指令
     * 业务场景 外部算法决定让某辆集卡去指定的充电桩充电。
     */
    @Override
    public Result chargeTruck(ChargeCommandReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) return Result.error(ErrorCodes.DEVICE_NOT_FOUND);

        ChargingStation station = context.getChargingStationMap().get(req.getStationId());
        if (station == null) return Result.error("指定的充电桩不存在");

        // 确认充电桩是否可用
        if (!station.isAvailable()) return Result.error("该充电桩当前被占用或不可用");

        //  锁定充电桩资源 防止其他集卡被派往此处
        station.setTruckId(truck.getId());
        station.setStatus(DeviceStateEnum.WORKING.getCode());

        //  修改集卡状态与目标
        truck.setNeedCharge(true);
        truck.setTargetStationId(station.getStationCode());

        //  覆盖路径 强制集卡向充电桩坐标行驶
        Point stationPos = new Point(station.getPosX(), station.getPosY());
        truck.getWaypoints().clear();
        truck.getWaypoints().add(stationPos);

        // 生成移动事件
        SimEvent moveEvent = engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveEvent.addSubject("TRUCK", truck.getId());

        return Result.success();
    }

    /**
     * 【仿真时钟推进】
     */
    @Override
    public void stepTime(long stepMS) {
        // 计算目标绝对时间 并驱动引擎消费这期间的所有事件
        long targetTime = context.getSimTime() + stepMS;
        engine.runUntil(targetTime);
    }
}