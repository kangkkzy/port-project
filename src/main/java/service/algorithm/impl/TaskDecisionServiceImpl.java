package service.algorithm.impl;

import common.consts.ErrorCodes;
import common.exception.BusinessException;
import common.util.GisUtil;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.Point;
import model.entity.Truck;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsService;
import service.algorithm.MapDataService;
import service.algorithm.TaskDecisionService;
/**
 * 充电决策
 */
@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();
    private final DevicePhysicsService physicsService;
    private final MapDataService mapDataService;

    @Autowired
    public TaskDecisionServiceImpl(DevicePhysicsService physicsService, MapDataService mapDataService) {
        this.physicsService = physicsService;
        this.mapDataService = mapDataService;
    }

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        Truck truck = context.getTruckMap().get(req.getTruckId());
        if (truck == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) throw new BusinessException("指定的作业指令不存在");

        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(truck.getId());

        //  动态计算真实耗电量 ( 基于 GIS 和物理配置)
        double estimatedCost = calculateDynamicCost(truck, wi);
        resp.setEstimatedCost(estimatedCost);

        //  获取该车型的安全电量冗余阈值
        double safeThreshold = physicsService.getSafePowerThreshold(truck.getId());

        //  外部算法执行调度决策 (当前电量 vs 预估消耗 + 安全冗余)
        if (truck.getPowerLevel() < estimatedCost + safeThreshold) {
            // 决策：电量不足，强制重定向到充电任务
            resp.setNextAction("REDIRECT_TO_CHARGE");
            resp.setAssignedWiRefNo("CHARGE_TASK_" + truck.getId());
            truck.setNeedCharge(true);
        } else {
            // 决策 电量充足 按原计划执行业务任务
            resp.setNextAction("PROCEED_TASK");
            resp.setAssignedWiRefNo(req.getWiRefNo());
            truck.setCurrWiRefNo(req.getWiRefNo());
        }

        return resp;
    }

    /**
     * 内部算法核心：根据 GIS 坐标动态估算集卡执行该指令的总耗电量
     */
    private double calculateDynamicCost(Truck truck, WorkInstruction wi) {
        //   获取物理耗电率与重载系数
        double emptyConsumeRate = physicsService.getPowerConsumeRate(truck.getId());
        double loadedCoeff = physicsService.getLoadedConsumeCoefficient(truck.getId());

        // 动态计算重载耗电率
        double loadedConsumeRate = emptyConsumeRate * loadedCoeff;

        //  获取三点绝对坐标
        Point currentPos = new Point(truck.getPosX(), truck.getPosY());
        Point fetchPos = mapDataService.resolveCoordinate(wi.getFromPos()); // 抓箱位置
        Point putPos = mapDataService.resolveCoordinate(wi.getToPos());     // 放箱位置

        //  计算两段距离
        //  当前位置前往抓箱点 (空驶)
        double emptyDistance = GisUtil.getDistance(currentPos, fetchPos);
        //  抓箱点前往放箱点 (重载)
        double loadedDistance = GisUtil.getDistance(fetchPos, putPos);

        //  计算总耗电
        double emptyCost = emptyDistance * emptyConsumeRate;
        double loadedCost = loadedDistance * loadedConsumeRate;

        return emptyCost + loadedCost;
    }
}