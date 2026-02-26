package service.algorithm.impl;

import common.consts.DeviceTypeEnum;
import common.exception.BusinessException;
import common.util.SafetyValidator;
import engine.log.SimulationStatisticsService;
import engine.context.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;
import service.algorithm.WorkInstructionValidator;

/**
 * 安全与时间协同校验：
 * - 岸桥防碰撞
 * - 集卡与岸桥时间协同
 */
@Component
public class SafetyAndSyncValidator implements WorkInstructionValidator {

    private final SafetyValidator safetyValidator;
    private final SimulationStatisticsService statisticsService;
    private final GlobalContext context = GlobalContext.getInstance();

    public SafetyAndSyncValidator(SafetyValidator safetyValidator,
                                  SimulationStatisticsService statisticsService) {
        this.safetyValidator = safetyValidator;
        this.statisticsService = statisticsService;
    }

    @Override
    public void validate(WorkInstruction wi, AssignTaskReq req) {
        if (wi == null || req == null || req.getDeviceType() == null) {
            return;
        }
        // 仅对岸桥设备生效
        if (!DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            return;
        }
        checkSafetyAndSync(req, wi);
    }

    private void checkSafetyAndSync(AssignTaskReq req, WorkInstruction wi) {
        // 此时调用 safetyValidator 可能会抛出 "缺少位置坐标配置" 异常
        double targetX = safetyValidator.parsePositionToX(wi.getToPos());

        // 此时调用 checkQcInterference 可能会抛出 "缺少关键算法参数" 异常
        if (!safetyValidator.checkQcInterference(req.getDeviceId(), targetX)) {
            statisticsService.recordSafetyViolation();
            throw new BusinessException("岸桥防碰撞预警");
        }

        String truckId = findTruckId(wi);
        if (truckId != null) {
            // 同上，可能抛出参数缺失异常
            long wait = safetyValidator.checkTimeSync(truckId, req.getDeviceId(), wi.getToPos());
            if (wait == -1) {
                statisticsService.recordSyncFailure();
                throw new BusinessException("集卡协同超时");
            }
            statisticsService.recordWaitTime(wait);
        }
    }

    private String findTruckId(WorkInstruction wi) {
        if (isValidTruck(wi.getFetchCheId())) return wi.getFetchCheId();
        if (isValidTruck(wi.getCarryCheId())) return wi.getCarryCheId();
        if (isValidTruck(wi.getPutCheId())) return wi.getPutCheId();
        return null;
    }

    private boolean isValidTruck(String id) {
        return id != null && context.getTruckMap().containsKey(id);
    }
}


